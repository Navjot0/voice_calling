package com.freeswitch.calling.controller;

import com.freeswitch.calling.dto.CallResponse;
import com.freeswitch.calling.dto.CreateCallRequest;
import com.freeswitch.calling.dto.CreateCallResponse;
import com.freeswitch.calling.service.VoiceCallService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST entry point for programmable call control.
 *
 * <p>This controller only translates HTTP requests to/from
 * {@link VoiceCallService} calls - it never talks to FreeSWITCH directly.
 */
@RestController
@RequestMapping("/api/v1/voice/calls")
public class VoiceCallController {

    private final VoiceCallService voiceCallService;

    public VoiceCallController(VoiceCallService voiceCallService) {
        this.voiceCallService = voiceCallService;
    }

    @PostMapping
    public ResponseEntity<CreateCallResponse> createCall(@Valid @RequestBody CreateCallRequest request) {
        CreateCallResponse response = voiceCallService.createOutboundCall(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{callId}")
    public ResponseEntity<CallResponse> getCall(@PathVariable String callId) {
        return ResponseEntity.ok(voiceCallService.getCall(callId));
    }

    /**
     * Call history from the {@code cdr} table, most recently started first.
     * Only finished calls appear here - a call still in progress isn't
     * archived yet, so use {@link #getCall} for its live status.
     */
    @GetMapping
    public ResponseEntity<List<CallResponse>> listCalls() {
        return ResponseEntity.ok(voiceCallService.listCalls());
    }

    /**
     * Ends a call in progress. Accepted (202) rather than OK, since this only
     * confirms FreeSWITCH accepted the hangup command - the call's actual
     * terminal status arrives asynchronously and is only visible via a
     * subsequent {@link #getCall} once it lands.
     */
    @PostMapping("/{callId}/hangup")
    public ResponseEntity<CallResponse> hangupCall(@PathVariable String callId) {
        return ResponseEntity.accepted().body(voiceCallService.hangupCall(callId));
    }
}
