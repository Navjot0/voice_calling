package com.freeswitch.calling;

import com.freeswitch.calling.freeswitch.FreeSwitchClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end test of the provisioning REST API through real Spring wiring
 * (controller -&gt; service -&gt; repository -&gt; directory service -&gt; a real
 * temp-directory filesystem). The only thing faked is {@link FreeSwitchClient}
 * itself - the ESL boundary - so this runs in CI without a real FreeSWITCH
 * server. For a test against an actual server, see
 * {@code VoiceUserProvisioningLiveFreeSwitchTest}, which is skipped unless
 * explicitly opted into.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class VoiceUserProvisioningIntegrationTest {

    private static Path directory;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) throws IOException {
        directory = Files.createTempDirectory("voice-user-directory-test");
        registry.add("freeswitch.directory.path", () -> directory.toString());
    }

    @AfterAll
    static void cleanUp() throws IOException {
        if (directory == null) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException ignored) {
                    // best-effort cleanup of a test temp directory
                }
            });
        }
    }

    @Autowired
    private MockMvc mockMvc;

    // Replaces the real ESL connection - this is the "mock FreeSWITCH service"
    // called for in the requirements, keeping this test independent of any
    // actual FreeSWITCH server.
    @MockBean
    private FreeSwitchClient freeSwitchClient;

    @Test
    void createVoiceUserEndToEnd() throws Exception {
        when(freeSwitchClient.executeSyncApi(anyString(), anyString())).thenReturn("+OK");

        mockMvc.perform(post("/api/v1/voice/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"extension":"1003","password":"1234","name":"Test User"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.extension").value("1003"))
                .andExpect(jsonPath("$.name").value("Test User"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.password").doesNotExist());

        assertThat(directory.resolve("1003.xml")).exists();
        verify(freeSwitchClient).executeSyncApi("reloadxml", "");

        mockMvc.perform(get("/api/v1/voice/users/1003"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.extension").value("1003"));

        mockMvc.perform(delete("/api/v1/voice/users/1003"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELETED"));

        assertThat(directory.resolve("1003.xml")).doesNotExist();
    }

    @Test
    void creatingSameExtensionTwiceReturns409() throws Exception {
        when(freeSwitchClient.executeSyncApi(anyString(), anyString())).thenReturn("+OK");

        String payload = """
                {"extension":"1004","password":"1234","name":"Second User"}
                """;

        mockMvc.perform(post("/api/v1/voice/users").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/voice/users").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("VOICE_USER_ALREADY_EXISTS"));
    }

    @Test
    void deletingProtectedExtensionReturns403() throws Exception {
        mockMvc.perform(delete("/api/v1/voice/users/1001"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("VOICE_USER_PROTECTED"));
    }
}
