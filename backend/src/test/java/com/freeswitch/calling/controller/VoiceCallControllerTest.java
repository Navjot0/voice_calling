package com.freeswitch.calling.controller;

import com.freeswitch.calling.dto.CallResponse;
import com.freeswitch.calling.dto.CreateCallResponse;
import com.freeswitch.calling.exception.CallNotFoundException;
import com.freeswitch.calling.model.CallDirection;
import com.freeswitch.calling.model.CallStatus;
import com.freeswitch.calling.service.VoiceCallService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VoiceCallController.class)
class VoiceCallControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private VoiceCallService voiceCallService;

    @Test
    void createCall_returns201WithBody() throws Exception {
        when(voiceCallService.createOutboundCall(any()))
                .thenReturn(new CreateCallResponse("call-uuid-1", CallStatus.INITIATED, "1001", "1002", CallDirection.OUTBOUND));

        mockMvc.perform(post("/api/v1/voice/calls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"from":"1001","to":"1002"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.callId").value("call-uuid-1"))
                .andExpect(jsonPath("$.status").value("INITIATED"))
                .andExpect(jsonPath("$.direction").value("OUTBOUND"));
    }

    @Test
    void createCall_missingFromReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/voice/calls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"to":"1002"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
    }

    @Test
    void createCall_malformedExtensionReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/voice/calls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"from":"abc","to":"1002"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
    }

    @Test
    void createCall_malformedJsonReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/voice/calls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
    }

    @Test
    void getCall_returnsSnapshotWhenFound() throws Exception {
        when(voiceCallService.getCall(eq("call-uuid-1"))).thenReturn(new CallResponse(
                "call-uuid-1", CallStatus.ANSWERED, "1001", "1002", CallDirection.OUTBOUND,
                Instant.parse("2026-09-14T10:00:00Z"), Instant.parse("2026-09-14T10:00:05Z"), null, null, null));

        mockMvc.perform(get("/api/v1/voice/calls/call-uuid-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ANSWERED"))
                .andExpect(jsonPath("$.answeredAt").exists());
    }

    @Test
    void getCall_returns404WhenNotFound() throws Exception {
        when(voiceCallService.getCall(eq("missing"))).thenThrow(new CallNotFoundException("missing"));

        mockMvc.perform(get("/api/v1/voice/calls/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("CALL_NOT_FOUND"));
    }

    @Test
    void listCalls_returnsHistoryNewestFirst() throws Exception {
        when(voiceCallService.listCalls()).thenReturn(List.of(
                new CallResponse("call-uuid-2", CallStatus.COMPLETED, "1001", "1002", CallDirection.OUTBOUND,
                        Instant.parse("2026-09-14T11:00:00Z"), Instant.parse("2026-09-14T11:00:05Z"),
                        Instant.parse("2026-09-14T11:01:00Z"), 60, 55),
                new CallResponse("call-uuid-1", CallStatus.NO_ANSWER, "1001", "1003", CallDirection.OUTBOUND,
                        Instant.parse("2026-09-14T10:00:00Z"), null, Instant.parse("2026-09-14T10:00:30Z"), 30, 0)));

        mockMvc.perform(get("/api/v1/voice/calls"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$[0].callId").value("call-uuid-2"))
                .andExpect(jsonPath("$[0].duration").value(60))
                .andExpect(jsonPath("$[0].billsec").value(55))
                .andExpect(jsonPath("$[1].status").value("NO_ANSWER"))
                .andExpect(jsonPath("$[1].billsec").value(0));
    }
}
