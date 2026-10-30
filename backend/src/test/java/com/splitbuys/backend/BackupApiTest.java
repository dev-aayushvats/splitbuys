package com.splitbuys.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.splitbuys.backend.backup.BackupInfo;
import com.splitbuys.backend.backup.BackupResult;
import com.splitbuys.backend.backup.BackupService;
import com.splitbuys.backend.common.error.ConflictException;
import com.splitbuys.backend.common.error.InvalidRequestException;
import com.splitbuys.backend.support.ApiTest;
import com.splitbuys.backend.support.UsesPostgres;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Backup and restore against a real PostgreSQL: the dump, the endpoint's security, and a full wipe-and-restore. */
@UsesPostgres
@TestPropertySource(properties = {
        "app.admin.token=admin-token-admin-token-admin-token-123",
        "app.backup.dir=target/test-backups",
        "app.backup.keep=3"})
class BackupApiTest extends ApiTest {
    private static final String TOKEN = "admin-token-admin-token-admin-token-123";

    @Autowired BackupService backups;
    @Autowired JdbcTemplate jdbc;

    private ResultActions admin(boolean create, String token) throws Exception {
        var req = create ? MockMvcRequestBuilders.post("/api/v1/admin/backups") : MockMvcRequestBuilders.get("/api/v1/admin/backups");
        if (token != null) {
            req.header("X-Admin-Token", token);
        }
        return mvc.perform(req);
    }

    private Path pathOf(String file) {
        return backups.directory().resolve(file);
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    private static List<String> lines(Path gz) throws IOException {
        try (var in = new java.io.BufferedReader(new java.io.InputStreamReader(new GZIPInputStream(Files.newInputStream(gz)), StandardCharsets.UTF_8))) {
            return in.lines().toList();
        }
    }

    private static void writeGz(Path file, String content) throws IOException {
        try (OutputStream out = Files.newOutputStream(file); Writer w = new OutputStreamWriter(new GZIPOutputStream(out), StandardCharsets.UTF_8)) {
            w.write(content);
        }
    }

    @Test
    void theEndpointsNeedTheAdminToken() throws Exception {
        admin(true, null).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        admin(true, "wrong-wrong-wrong-wrong-wrong-wrong-1").andExpect(status().isForbidden());
        admin(false, null).andExpect(status().isForbidden());
        // a signed-in app user is not an admin
        Session user = signIn("Asha");
        mvc.perform(MockMvcRequestBuilders.post("/api/v1/admin/backups").header("Authorization", "Bearer " + user.token())).andExpect(status().isForbidden());
        admin(true, TOKEN).andExpect(status().isCreated());
        admin(false, TOKEN).andExpect(status().isOk());
    }

    @Test
    void aBackupCoversEveryApplicationTableButNotSignInCodes() throws Exception {
        signIn("Asha");
        ResultActions created = admin(true, TOKEN).andExpect(status().isCreated());
        java.util.Map<String, Object> rows = json(created, "$.rows");
        Set<String> dumped = rows.keySet();
        Set<String> inDatabase = Set.copyOf(jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = current_schema() AND table_type = 'BASE TABLE'", String.class));
        Set<String> expected = new java.util.HashSet<>(inDatabase);
        expected.removeAll(Set.of("flyway_schema_history", "otp_code"));
        assertEquals(expected, dumped, "a new table must be added to BackupService.TABLES (or excluded on purpose)");
    }

    @Test
    void theFileIsCompressedPrivateAndPsqlShaped() throws Exception {
        signIn("Asha");
        String file = json(admin(true, TOKEN).andExpect(status().isCreated()), "$.file");
        Path path = pathOf(file);

        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(path)));
        List<String> lines = lines(path);
        assertEquals("-- splitbuys data backup", lines.get(0));
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("COPY app_user (id, email, name) FROM stdin;")));
        assertTrue(lines.contains("\\."), "each COPY block is terminated like pg_dump's");
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("SELECT setval(pg_get_serial_sequence('expense'")));
        assertTrue(lines.get(lines.size() - 1).startsWith("-- end of dump"));
        assertFalse(lines.stream().anyMatch(l -> l.contains("otp_code")), "sign-in codes are not backed up");
    }

    @Test
    void wipeAndRestoreBringsBackEverythingAndKeepsNewIdsUnique() throws Exception {
        Session a = signIn("Asha"), b = signIn("Bo"), c = signIn("Chen");
        long g = groupOf(a, b, c);
        post("/api/v1/groups/" + g + "/expenses", a, equalExpense("Dinner", 9000, a.userId(), a.userId(), b.userId(), c.userId())).andExpect(status().isCreated());
        post("/api/v1/groups/" + g + "/expenses", b, "{\"description\":\"Cab\",\"amountPaise\":1000,\"payerId\":" + b.userId()
                + ",\"splitType\":\"PERCENT\",\"splits\":[{\"userId\":" + a.userId() + ",\"value\":70},{\"userId\":" + b.userId() + ",\"value\":30}]}")
                .andExpect(status().isCreated());
        post("/api/v1/groups/" + g + "/settlements", c, "{\"fromUserId\":" + c.userId() + ",\"toUserId\":" + a.userId() + ",\"amountPaise\":1500}")
                .andExpect(status().isCreated());

        String groupsBefore = get("/api/v1/groups", a).andReturn().getResponse().getContentAsString();
        String expensesBefore = get("/api/v1/groups/" + g + "/expenses", a).andReturn().getResponse().getContentAsString();
        String balancesBefore = get("/api/v1/groups/" + g + "/balances", b).andReturn().getResponse().getContentAsString();
        long usersBefore = count("app_user"), expensesCount = count("expense"), sharesBefore = count("expense_share");

        BackupResult result = backups.backup();
        assertEquals(usersBefore, result.rows().get("app_user"));

        // simulate the database being hard-restarted empty
        jdbc.execute("TRUNCATE TABLE settlement, expense_share, expense, group_member, expense_group, app_user, otp_code RESTART IDENTITY CASCADE");
        get("/api/v1/me", a).andExpect(status().isNotFound());

        backups.restore(pathOf(result.file()));

        assertEquals(usersBefore, count("app_user"));
        assertEquals(expensesCount, count("expense"));
        assertEquals(sharesBefore, count("expense_share"));
        // the same sign-in tokens work again and every API view is identical
        assertEquals(groupsBefore, get("/api/v1/groups", a).andReturn().getResponse().getContentAsString());
        assertEquals(expensesBefore, get("/api/v1/groups/" + g + "/expenses", a).andReturn().getResponse().getContentAsString());
        assertEquals(balancesBefore, get("/api/v1/groups/" + g + "/balances", b).andReturn().getResponse().getContentAsString());

        // identity sequences were advanced past the restored ids: new rows do not collide
        long maxBefore = jdbc.queryForObject("SELECT MAX(id) FROM expense", Long.class);
        long fresh = id(post("/api/v1/groups/" + g + "/expenses", a, equalExpense("After restore", 100, a.userId(), a.userId()))
                .andExpect(status().isCreated()), "$.id");
        assertTrue(fresh > maxBefore);
        Session newcomer = signIn("Newcomer");
        assertTrue(newcomer.userId() > c.userId());
    }

    @Test
    void restoringOverExistingDataTakesASafetyBackupFirst() throws Exception {
        Session a = signIn("Asha");
        groupOf(a);
        BackupResult first = backups.backup();
        long usersNow = count("app_user");
        signIn("Added after the backup");

        backups.restore(pathOf(first.file()));

        assertEquals(usersNow, count("app_user"), "back to the state of the backup");
        List<BackupInfo> files = backups.list();
        assertTrue(files.stream().anyMatch(f -> f.file().contains("pre-restore")), "the state that was replaced is kept: " + files);
    }

    @Test
    void badFilesAreRefusedAndLeaveTheDatabaseUntouched() throws Exception {
        signIn("Asha");
        long usersBefore = count("app_user");
        Path dir = backups.directory();
        String version = json(admin(true, TOKEN), "$.schemaVersion");

        Path truncated = dir.resolve("splitbuys-test-truncated.sql.gz");
        writeGz(truncated, "-- splitbuys data backup\n-- schema_version: " + version + "\n\nCOPY app_user (id, email, name) FROM stdin;\n1\tx@y.z\tX\n");
        assertThrows(InvalidRequestException.class, () -> backups.restore(truncated));

        Path otherVersion = dir.resolve("splitbuys-test-other-version.sql.gz");
        writeGz(otherVersion, "-- splitbuys data backup\n-- schema_version: 999\n-- end of dump ({})\n");
        ConflictException mismatch = assertThrows(ConflictException.class, () -> backups.restore(otherVersion));
        assertEquals("SCHEMA_MISMATCH", mismatch.code());

        Path notABackup = dir.resolve("splitbuys-test-not-a-backup.sql.gz");
        writeGz(notABackup, "hello\n");
        assertThrows(InvalidRequestException.class, () -> backups.restore(notABackup));

        // looks complete, but contains a statement that is not allowed: the whole restore (including the truncate) rolls back
        Path hostile = dir.resolve("splitbuys-test-hostile.sql.gz");
        writeGz(hostile, "-- splitbuys data backup\n-- schema_version: " + version + "\nDROP TABLE app_user;\n-- end of dump ({})\n");
        assertThrows(InvalidRequestException.class, () -> backups.restore(hostile));
        assertEquals(usersBefore, count("app_user"), "nothing changed");

        assertThrows(InvalidRequestException.class, () -> backups.restore(dir.resolve("does-not-exist.sql.gz")));
        for (Path p : List.of(truncated, otherVersion, notABackup, hostile)) {
            Files.deleteIfExists(p);
        }
    }

    @Test
    void onlyTheNewestBackupsAreKept() throws Exception {
        signIn("Asha");
        String last = null;
        for (int i = 0; i < 5; i++) {
            last = backups.backup().file();
        }
        List<BackupInfo> files = backups.list();
        assertEquals(3, files.size());
        assertEquals(last, files.get(0).file());
    }
}
