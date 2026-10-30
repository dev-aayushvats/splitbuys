package com.splitbuys.backend;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.splitbuys.backend.support.ApiTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.context.TestPropertySource;

/** Backups are a PostgreSQL feature; on SQLite the endpoint says so clearly instead of failing obscurely. */
@TestPropertySource(properties = "app.admin.token=admin-token-admin-token-admin-token-123")
class AdminSqliteApiTest extends ApiTest {
    @Test
    void backupIsUnsupportedOnSqlite() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/v1/admin/backups").header("X-Admin-Token", "admin-token-admin-token-admin-token-123"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BACKUP_UNSUPPORTED"));
    }
}
