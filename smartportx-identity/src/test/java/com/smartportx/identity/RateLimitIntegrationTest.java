package com.smartportx.identity;

import com.smartportx.identity.domain.RoleName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RateLimitIntegrationTest extends BaseIdentityIntegrationTest {

    @Test
    void sixthLoginAttemptWithinWindowIsRateLimited() throws Exception {
        String username = unique("rateu");
        createUser(username, PASSWORD, RoleName.READONLY);

        for (int i = 0; i < 5; i++) {
            assertThat(performLogin(username, PASSWORD).getResponse().getStatus()).isEqualTo(200);
        }

        MvcResult sixth = performLogin(username, PASSWORD);
        assertThat(sixth.getResponse().getStatus()).isEqualTo(429);
        assertThat(sixth.getResponse().getHeader("Retry-After")).isNotBlank();
        assertThat(sixth.getResponse().getContentAsString()).contains("\"code\":\"too_many_attempts\"");
    }
}