package com.freeswitch.calling.service;

import com.freeswitch.calling.dto.CallResponse;
import com.freeswitch.calling.dto.CreateCallRequest;
import com.freeswitch.calling.dto.CreateCallResponse;
import com.freeswitch.calling.exception.CallNotFoundException;
import com.freeswitch.calling.exception.FreeSwitchOperationException;
import com.freeswitch.calling.exception.InvalidCallRequestException;
import com.freeswitch.calling.freeswitch.FreeSwitchClient;
import com.freeswitch.calling.model.CallDirection;
import com.freeswitch.calling.model.CallStatus;
import com.freeswitch.calling.repository.CallRepository;
import com.freeswitch.calling.repository.InMemoryCallRepository;
import com.freeswitch.calling.repository.PersistentCallRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VoiceCallServiceTest {

    private FreeSwitchClient freeSwitchClient;
    private CallRepository callRepository;
    private PersistentCallRepository cdrRepository;
    private VoiceCallService voiceCallService;

    @BeforeEach
    void setUp() {
        freeSwitchClient = mock(FreeSwitchClient.class);
        callRepository = new InMemoryCallRepository();
        cdrRepository = mock(PersistentCallRepository.class);
        voiceCallService = new VoiceCallService(freeSwitchClient, callRepository, cdrRepository);
    }

    @Test
    void createOutboundCall_savesCallAndOriginatesWithGeneratedCallId() {
        CreateCallResponse response = voiceCallService.createOutboundCall(new CreateCallRequest("1001", "1002"));

        assertThat(response.callId()).isNotBlank();
        assertThat(response.status()).isEqualTo(CallStatus.INITIATED);
        assertThat(response.direction()).isEqualTo(CallDirection.OUTBOUND);
        assertThat(response.from()).isEqualTo("1001");
        assertThat(response.to()).isEqualTo("1002");

        ArgumentCaptor<String> callIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(freeSwitchClient).originate(callIdCaptor.capture(), anyString(), anyString());
        assertThat(callIdCaptor.getValue()).isEqualTo(response.callId());

        // The call must already be visible to lookups (e.g. from event-handling
        // threads) before originate() is even invoked.
        assertThat(callRepository.findById(response.callId())).isPresent();
    }

    @Test
    void createOutboundCall_rejectsIdenticalSourceAndDestination() {
        assertThatThrownBy(() -> voiceCallService.createOutboundCall(new CreateCallRequest("1001", "1001")))
                .isInstanceOf(InvalidCallRequestException.class);
    }

    @Test
    void createOutboundCall_marksCallFailedWhenOriginateThrows() {
        doThrow(new FreeSwitchOperationException("boom"))
                .when(freeSwitchClient).originate(anyString(), anyString(), anyString());

        assertThatThrownBy(() -> voiceCallService.createOutboundCall(new CreateCallRequest("1001", "1002")))
                .isInstanceOf(FreeSwitchOperationException.class);

        ArgumentCaptor<String> callIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(freeSwitchClient).originate(callIdCaptor.capture(), anyString(), anyString());

        // The call was recorded before originate() was attempted, and must be
        // updated to FAILED rather than left stuck at INITIATED.
        assertThat(callRepository.findById(callIdCaptor.getValue()))
                .isPresent()
                .get()
                .satisfies(call -> assertThat(call.getStatus()).isEqualTo(CallStatus.FAILED));
    }

    @Test
    void getCall_throwsWhenCallIdUnknown() {
        assertThatThrownBy(() -> voiceCallService.getCall("does-not-exist"))
                .isInstanceOf(CallNotFoundException.class);
    }

    @Test
    void getCall_returnsFullLifecycleSnapshot() {
        CreateCallResponse created = voiceCallService.createOutboundCall(new CreateCallRequest("1001", "1002"));

        CallResponse fetched = voiceCallService.getCall(created.callId());

        assertThat(fetched.callId()).isEqualTo(created.callId());
        assertThat(fetched.status()).isEqualTo(CallStatus.INITIATED);
        assertThat(fetched.createdAt()).isNotNull();
        assertThat(fetched.answeredAt()).isNull();
        assertThat(fetched.completedAt()).isNull();
    }

    @Test
    void listCalls_delegatesToCdrRepository() {
        CallResponse historical = new CallResponse("call-uuid-9", CallStatus.COMPLETED, "1001", "1002",
                CallDirection.OUTBOUND, java.time.Instant.now(), java.time.Instant.now(), java.time.Instant.now(),
                42, 40);
        when(cdrRepository.findCallHistory()).thenReturn(List.of(historical));

        List<CallResponse> result = voiceCallService.listCalls();

        assertThat(result).containsExactly(historical);
    }
}
