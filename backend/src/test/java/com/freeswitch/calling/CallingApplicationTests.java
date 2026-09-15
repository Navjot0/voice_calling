package com.freeswitch.calling;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Verifies the Spring context loads even when FreeSWITCH is unreachable -
 * {@link com.freeswitch.calling.freeswitch.FreeSwitchClient} connects
 * asynchronously and must never fail application startup.
 */
@SpringBootTest
class CallingApplicationTests {

    @Test
    void contextLoads() {
    }
}
