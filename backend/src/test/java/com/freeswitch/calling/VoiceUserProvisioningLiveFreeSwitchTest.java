package com.freeswitch.calling;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The "separate integration profile for a real FreeSWITCH server" variant:
 * unlike {@link VoiceUserProvisioningIntegrationTest}, this does NOT mock
 * {@code FreeSwitchClient} - it exercises the real ESL connection and writes
 * a real file under the real {@code freeswitch.directory.path} (from
 * application.yml / environment variables, e.g. when this test itself runs
 * on the FreeSWITCH box), then cleans up by deleting it again.
 *
 * <p>Skipped by default (including in normal {@code mvn test} / CI runs) so a
 * missing/unreachable FreeSWITCH server never fails the build. To run it
 * deliberately, on a machine with a real reachable FreeSWITCH and the app's
 * usual freeswitch.* configuration:
 *
 * <pre>
 *   RUN_LIVE_FREESWITCH_TESTS=true mvn test -Dtest=VoiceUserProvisioningLiveFreeSwitchTest
 * </pre>
 *
 * <p>Uses extension {@code 1099} - pick a number you are sure is not one of
 * your real extensions before running this against a shared server.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "RUN_LIVE_FREESWITCH_TESTS", matches = "true")
class VoiceUserProvisioningLiveFreeSwitchTest {

    private static final String TEST_EXTENSION = "1099";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createsAndDeletesARealExtensionOnTheLiveServer() throws Exception {
        try {
            mockMvc.perform(post("/api/v1/voice/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"extension":"%s","password":"livetest1234","name":"Live Test User"}
                                    """.formatted(TEST_EXTENSION)))
                    .andExpect(status().isCreated());
        } finally {
            // Best-effort cleanup even if the assertion above failed.
            mockMvc.perform(delete("/api/v1/voice/users/" + TEST_EXTENSION));
        }
    }
}
