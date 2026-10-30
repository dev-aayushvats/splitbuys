package com.splitbuys.backend;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.splitbuys.backend.support.ApiTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** With no ADMIN_API_TOKEN configured the admin endpoints simply do not exist. */
class AdminApiTest extends ApiTest {
    @Test
    void adminEndpointsAreNotFoundWithoutAConfiguredToken() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/v1/admin/backups").header("X-Admin-Token", "anything-anything-anything-anything-1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
