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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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

    public VoiceCallService(FreeSwitchClient freeSwitchClient, CallRepository callRepository) {
        this.freeSwitchClient = freeSwitchClient;
        this.callRepository = callRepository;
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

    public CallResponse getCall(String callId) {
        Call call = callRepository.findById(callId)
                .orElseThrow(() -> new CallNotFoundException(callId));
        return CallResponse.from(call);
    }
}
