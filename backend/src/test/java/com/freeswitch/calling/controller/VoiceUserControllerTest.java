package com.freeswitch.calling.controller;

import com.freeswitch.calling.dto.DeleteVoiceUserResponse;
import com.freeswitch.calling.dto.VoiceUserResponse;
import com.freeswitch.calling.exception.ProtectedVoiceUserException;
import com.freeswitch.calling.exception.VoiceUserAlreadyExistsException;
import com.freeswitch.calling.exception.VoiceUserNotFoundException;
import com.freeswitch.calling.model.VoiceUserSource;
import com.freeswitch.calling.model.VoiceUserStatus;
import com.freeswitch.calling.service.VoiceUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VoiceUserController.class)
class VoiceUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private VoiceUserService voiceUserService;

    @Test
    void createUser_returns201WithoutPassword() throws Exception {
        when(voiceUserService.createUser(any()))
                .thenReturn(new VoiceUserResponse("1003", "Test User", VoiceUserStatus.ACTIVE, VoiceUserSource.PROVISIONED_BY_API));

        mockMvc.perform(post("/api/v1/voice/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"extension":"1003","password":"1234","name":"Test User"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.extension").value("1003"))
                .andExpect(jsonPath("$.name").value("Test User"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("1234"))));
    }

    @Test
    void createUser_missingPasswordReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/voice/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"extension":"1003","name":"Test User"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
    }

    @Test
    void createUser_missingNameReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/voice/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"extension":"1003","password":"1234"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
    }

    @Test
    void createUser_invalidExtensionReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/voice/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"extension":"../../etc/passwd","password":"1234","name":"Test User"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
    }

    @Test
    void createUser_duplicateExtensionReturns409() throws Exception {
        when(voiceUserService.createUser(any())).thenThrow(new VoiceUserAlreadyExistsException("1001"));

        mockMvc.perform(post("/api/v1/voice/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"extension":"1001","password":"newpassword","name":"Another User"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("VOICE_USER_ALREADY_EXISTS"));
    }

    @Test
    void listUsers_returnsAllUsers() throws Exception {
        when(voiceUserService.listUsers()).thenReturn(List.of(
                new VoiceUserResponse("1001", "Extension 1001", VoiceUserStatus.ACTIVE, VoiceUserSource.EXISTING_EXTERNAL_USER),
                new VoiceUserResponse("1003", "Test User", VoiceUserStatus.ACTIVE, VoiceUserSource.PROVISIONED_BY_API)));

        mockMvc.perform(get("/api/v1/voice/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$[0].extension").value("1001"))
                .andExpect(jsonPath("$[1].extension").value("1003"));
    }

    @Test
    void getUser_returns404WhenMissing() throws Exception {
        when(voiceUserService.getUser(eq("1099"))).thenThrow(new VoiceUserNotFoundException("1099"));

        mockMvc.perform(get("/api/v1/voice/users/1099"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("VOICE_USER_NOT_FOUND"));
    }

    @Test
    void getUser_invalidExtensionPathVariableReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/voice/users/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
    }

    @Test
    void deleteUser_returnsDeletedStatus() throws Exception {
        when(voiceUserService.deleteUser("1003")).thenReturn(new DeleteVoiceUserResponse("1003", "DELETED"));

        mockMvc.perform(delete("/api/v1/voice/users/1003"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.extension").value("1003"))
                .andExpect(jsonPath("$.status").value("DELETED"));
    }

    @Test
    void deleteUser_protectedExtensionReturns403() throws Exception {
        when(voiceUserService.deleteUser("1001")).thenThrow(new ProtectedVoiceUserException("1001"));

        mockMvc.perform(delete("/api/v1/voice/users/1001"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("VOICE_USER_PROTECTED"));
    }

    @Test
    void listUsers_corsOriginAllowed_returns200AndCorsHeaders() throws Exception {
        when(voiceUserService.listUsers()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/voice/users")
                        .header("Origin", "http://192.168.1.3:5173"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Access-Control-Allow-Origin", "http://192.168.1.3:5173"));
    }

    @Test
    void preflight_corsOptionsRequest_returnsOkAndCorsHeaders() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options("/api/v1/voice/users")
                        .header("Origin", "http://192.168.1.3:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Access-Control-Allow-Origin", "http://192.168.1.3:5173"));
    }
}
