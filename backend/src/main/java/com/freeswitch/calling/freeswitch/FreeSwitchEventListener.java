package com.freeswitch.calling.freeswitch;

import com.freeswitch.calling.model.Call;
import com.freeswitch.calling.model.CallStatus;
import com.freeswitch.calling.repository.CallRepository;
import org.freeswitch.esl.client.IEslEventListener;
import org.freeswitch.esl.client.transport.event.EslEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Translates raw FreeSWITCH channel events into {@link Call} state
 * transitions.
 *
 * <p>Depends only on {@link CallRepository} (not {@link FreeSwitchClient} or
 * the service layer) so that {@link FreeSwitchClient} can register this
 * listener without creating a circular bean dependency.
 *
 * <p>Only CHANNEL_CREATE, CHANNEL_PROGRESS, CHANNEL_ANSWER, CHANNEL_HANGUP
 * and CHANNEL_HANGUP_COMPLETE are handled for this step, matching the
 * minimum event set required to track INITIATED -&gt; RINGING -&gt; ANSWERED
 * -&gt; COMPLETED/FAILED/BUSY/NO_ANSWER. The switch below is the seam for
 * adding more events later (e.g. for transfer, hold, mute).
 *
 * <p>Every state-changing branch calls {@link CallRepository#save} again
 * after mutating the call. With the in-memory repository this was
 * technically redundant (the map already holds the same object reference),
 * but with a persistent, database-backed repository each transition must be
 * explicitly re-saved to actually reach storage - so it is done
 * unconditionally here rather than relying on the repository implementation.
 */
@Component
public class FreeSwitchEventListener implements IEslEventListener {

    private static final Logger log = LoggerFactory.getLogger(FreeSwitchEventListener.class);

    private static final Set<String> NO_ANSWER_CAUSES = Set.of(
            "NO_ANSWER", "NO_USER_RESPONSE", "ALLOTTED_TIMEOUT", "ORIGINATOR_CANCEL");

    private final CallRepository callRepository;

    public FreeSwitchEventListener(CallRepository callRepository) {
        this.callRepository = callRepository;
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
                callRepository.save(call);
            }
            case "CHANNEL_PROGRESS" -> {
                log.info("Call ringing callId={}", callId);
                call.updateStatus(CallStatus.RINGING);
                callRepository.save(call);
            }
            case "CHANNEL_ANSWER" -> {
                log.info("Call answered callId={}", callId);
                call.markAnswered();
                callRepository.save(call);
            }
            case "CHANNEL_HANGUP" -> log.info("Call hangup signaled callId={} cause={}",
                    callId, headers.get("Hangup-Cause"));
            case "CHANNEL_HANGUP_COMPLETE" -> {
                CallStatus terminalStatus = resolveTerminalStatus(call, headers.get("Hangup-Cause"));
                call.markTerminal(terminalStatus);
                callRepository.save(call);
                log.info("Call completed callId={} status={} cause={}",
                        callId, terminalStatus, headers.get("Hangup-Cause"));
            }
            default -> {
                // No-op: outside the tracked event set for this step.
            }
        }
    }

    private CallStatus resolveTerminalStatus(Call call, String hangupCause) {
        if (call.getAnsweredAt() != null) {
            return CallStatus.COMPLETED;
        }
        if (hangupCause == null) {
            return CallStatus.FAILED;
        }
        if ("USER_BUSY".equals(hangupCause)) {
            return CallStatus.BUSY;
        }
        if (NO_ANSWER_CAUSES.contains(hangupCause)) {
            return CallStatus.NO_ANSWER;
        }
        return CallStatus.FAILED;
    }
}
