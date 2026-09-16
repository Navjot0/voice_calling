package com.freeswitch.calling.freeswitch;

import com.freeswitch.calling.model.Call;
import com.freeswitch.calling.model.CallStatus;
import com.freeswitch.calling.repository.CallRepository;
import com.freeswitch.calling.repository.PersistentCallRepository;
import org.freeswitch.esl.client.IEslEventListener;
import org.freeswitch.esl.client.transport.event.EslEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * Translates raw FreeSWITCH channel events into {@link Call} state
 * transitions.
 *
 * <p>Depends only on {@link CallRepository} (not {@link FreeSwitchClient} or
 * the service layer) so that {@link FreeSwitchClient} can register this
 * listener without creating a circular bean dependency.
 *
 * <p>CHANNEL_CREATE, CHANNEL_PROGRESS, CHANNEL_ANSWER and CHANNEL_BRIDGE
 * update the call's live, in-memory state (see {@link CallRepository}); the
 * switch below is the seam for adding more events later (e.g. for transfer,
 * hold, mute). CHANNEL_HANGUP_COMPLETE both finalizes that live state and -
 * exactly once, since a call-detail record is a one-time historical entry,
 * not something updated afterward - archives the finished call via
 * {@link PersistentCallRepository} into the pre-existing {@code cdr} table.
 */
@Component
public class FreeSwitchEventListener implements IEslEventListener {

    private static final Logger log = LoggerFactory.getLogger(FreeSwitchEventListener.class);

    private final CallRepository callRepository;
    private final PersistentCallRepository cdrRecorder;

    public FreeSwitchEventListener(CallRepository callRepository, PersistentCallRepository cdrRecorder) {
        this.callRepository = callRepository;
        this.cdrRecorder = cdrRecorder;
    }

    @Override
    public void eventReceived(EslEvent event) {
        try {
            handleEvent(event);
        } catch (Exception e) {
            log.error("Error handling FreeSWITCH event: {}", e.getMessage(), e);
        }
    }

    @Override
    public void backgroundJobResultReceived(EslEvent event) {
        // The originate command is submitted via bgapi (see FreeSwitchClient#originate),
        // so its result arrives here asynchronously. Call state is correlated via the
        // pre-assigned origination_uuid on CHANNEL_* events instead of this Job-UUID,
        // so this is logged for diagnostics only.
        Map<String, String> headers = event.getEventHeaders();
        log.debug("Background job result jobUuid={} body={}", headers.get("Job-UUID"), event.getEventBodyLines());
    }

    private void handleEvent(EslEvent event) {
        Map<String, String> headers = event.getEventHeaders();
        String eventName = headers.get("Event-Name");
        String callId = headers.get("Unique-ID");
        if (eventName == null || callId == null) {
            return;
        }

        Optional<Call> maybeCall = callRepository.findById(callId);
        if (maybeCall.isEmpty()) {
            // Not a channel this platform originated/tracks (e.g. the bridged B-leg
            // to the destination extension, which carries its own, different UUID).
            return;
        }
        Call call = maybeCall.get();

        switch (eventName) {
            case "CHANNEL_CREATE" -> {
                log.info("Call channel created callId={}", callId);
                call.updateStatus(CallStatus.INITIATED);
            }
            case "CHANNEL_PROGRESS" -> {
                log.info("Call ringing callId={}", callId);
                call.updateStatus(CallStatus.RINGING);
            }
            case "CHANNEL_ANSWER" -> {
                log.info("Call answered callId={}", callId);
                call.markAnswered();
            }
            case "CHANNEL_BRIDGE" -> {
                // Other-Leg-Unique-ID on the A-leg's own CHANNEL_BRIDGE event is the
                // FreeSWITCH-assigned UUID of the bridged `to` channel - captured here
                // purely for the cdr table's bleg_uuid column, not used for lookups
                // (this platform only ever tracks/finds calls by the A-leg's UUID).
                String blegUuid = headers.get("Other-Leg-Unique-ID");
                if (blegUuid != null) {
                    log.info("Call bridged callId={} blegUuid={}", callId, blegUuid);
                    call.recordBridgeLeg(blegUuid);
                }
            }
            case "CHANNEL_HANGUP" -> log.info("Call hangup signaled callId={} cause={}",
                    callId, headers.get("Hangup-Cause"));
            case "CHANNEL_HANGUP_COMPLETE" -> {
                boolean alreadyTerminal = call.isTerminal();
                String hangupCause = headers.get("Hangup-Cause");
                CallStatus terminalStatus = CallStatus.resolveTerminal(call.getAnsweredAt() != null, hangupCause);
                call.markTerminal(terminalStatus, hangupCause);
                log.info("Call completed callId={} status={} cause={}", callId, terminalStatus, hangupCause);
                if (!alreadyTerminal) {
                    // Exactly one CDR row per call, written only on the transition
                    // into a terminal state - never on a possible duplicate event.
                    cdrRecorder.record(call);
                }
            }
            default -> {
                // No-op: outside the tracked event set for this step.
            }
        }
    }
}
