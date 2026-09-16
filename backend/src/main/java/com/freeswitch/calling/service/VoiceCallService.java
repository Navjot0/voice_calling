package com.freeswitch.calling.service;

import com.freeswitch.calling.dto.CallResponse;
import com.freeswitch.calling.dto.CreateCallRequest;
import com.freeswitch.calling.dto.CreateCallResponse;
import com.freeswitch.calling.exception.CallNotFoundException;
import com.freeswitch.calling.exception.InvalidCallRequestException;
import com.freeswitch.calling.freeswitch.FreeSwitchClient;
import com.freeswitch.calling.model.Call;
import com.freeswitch.calling.model.CallDirection;
import com.freeswitch.calling.model.CallStatus;
import com.freeswitch.calling.repository.CallRepository;
import com.freeswitch.calling.repository.PersistentCallRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Orchestrates outbound call initiation: validates the business rules the
 * DTO's bean validation cannot express, records the call before it is sent
 * to FreeSWITCH (so events for it can be correlated as soon as they arrive),
 * and delegates the actual origination to {@link FreeSwitchClient}.
 */
@Service
public class VoiceCallService {

    private static final Logger log = LoggerFactory.getLogger(VoiceCallService.class);

    private final FreeSwitchClient freeSwitchClient;
    private final CallRepository callRepository;
    private final PersistentCallRepository cdrRepository;

    public VoiceCallService(FreeSwitchClient freeSwitchClient, CallRepository callRepository,
                             PersistentCallRepository cdrRepository) {
        this.freeSwitchClient = freeSwitchClient;
        this.callRepository = callRepository;
        this.cdrRepository = cdrRepository;
    }

    public CreateCallResponse createOutboundCall(CreateCallRequest request) {
        String from = request.from().trim();
        String to = request.to().trim();

        if (from.equals(to)) {
            throw new InvalidCallRequestException("Source and destination extensions must be different");
        }

        // The FreeSWITCH-assigned channel UUID for the originating leg becomes this
        // call's ID; it is pre-generated here and handed to FreeSWITCH as the
        // origination_uuid channel variable (see FreeSwitchClient#originate), rather
        // than left for FreeSWITCH to choose and reported back later, so the caller
        // gets the definitive call ID synchronously in the API response.
        String callId = UUID.randomUUID().toString();

        log.info("Creating outbound call callId={} from={} to={}", callId, from, to);

        Call call = new Call(callId, from, to, CallDirection.OUTBOUND, CallStatus.INITIATED);
        // Saved before originate() is called so the event listener can find this
        // call as soon as FreeSWITCH emits the first channel event for it.
        callRepository.save(call);

        try {
            freeSwitchClient.originate(callId, from, to);
        } catch (RuntimeException e) {
            call.markTerminal(CallStatus.FAILED);
            log.error("Originate failed callId={} error={}", callId, e.getMessage());
            throw e;
        }

        log.info("Call initiated callId={}", callId);
        return new CreateCallResponse(call.getCallId(), call.getStatus(), call.getFrom(), call.getTo(), call.getDirection());
    }

    /**
     * A single call's snapshot, by ID. Checks the live, in-memory store
     * first (so an in-progress call gets its current status); if the call
     * isn't there - most likely because it finished and the application has
     * since restarted, since {@link com.freeswitch.calling.repository.InMemoryCallRepository}
     * doesn't survive a restart - falls back to the archived {@code cdr} row,
     * so a completed call served up via {@link #listCalls()} always remains
     * viewable by ID afterward too.
     */
    public CallResponse getCall(String callId) {
        return callRepository.findById(callId)
                .map(CallResponse::from)
                .or(() -> cdrRepository.findByCallId(callId))
                .orElseThrow(() -> new CallNotFoundException(callId));
    }

    /**
     * Call history from the {@code cdr} table, most recently started first.
     * Only finished calls appear here - see {@link PersistentCallRepository#findCallHistory()}.
     */
    public List<CallResponse> listCalls() {
        return cdrRepository.findCallHistory();
    }

    /**
     * Ends a call in progress. Only ever looks at the live, in-memory store -
     * unlike {@link #getCall}, this deliberately does not fall back to the
     * {@code cdr} archive, since a call that has fallen out of memory (e.g.
     * after an application restart) has no ESL channel this instance can
     * confirm is even still its own to hang up.
     *
     * <p>Idempotent: hanging up a call that has already reached a terminal
     * state is a no-op rather than an error, so a double click or a race with
     * the call ending naturally doesn't surface a spurious failure.
     *
     * <p>Returns the call's snapshot as of the moment the hangup command was
     * accepted - not its final state, which arrives asynchronously once
     * FreeSWITCH's {@code CHANNEL_HANGUP_COMPLETE} event reaches
     * {@code FreeSwitchEventListener}; poll {@link #getCall} to observe it.
     */
    public CallResponse hangupCall(String callId) {
        Call call = callRepository.findById(callId)
                .orElseThrow(() -> new CallNotFoundException(callId));

        if (!call.isTerminal()) {
            log.info("Hanging up call callId={}", callId);
            freeSwitchClient.hangup(callId);
        }
        return CallResponse.from(call);
    }
}
