package com.systemdesign.ecommerce;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║         Application Context Load Test                        ║
 * ╚══════════════════════════════════════════════════════════════╝
 *
 * This test verifies that the entire Spring ApplicationContext loads
 * without errors — all beans are wired, configs are valid.
 *
 * INTERVIEW TALKING POINT:
 *   "Context load tests catch misconfigured beans early — before they
 *   fail in production. We run them in CI on every pull request."
 *
 * @ActiveProfiles("test") loads application-test.yml which uses
 * in-memory/embedded versions of infrastructure instead of real DBs.
 * This makes tests fast and removes the need for Docker in CI.
 */
@SpringBootTest
@ActiveProfiles("test")
class EcommerceApplicationTests {

    @Test
    void contextLoads() {
        // If this passes, all beans are wired correctly.
        // No assertions needed — the test passes if no exception is thrown.
    }
}
