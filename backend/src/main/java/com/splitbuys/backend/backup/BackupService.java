package com.splitbuys.backend.backup;

import com.splitbuys.backend.common.error.ConflictException;
import com.splitbuys.backend.common.error.DependencyFailureException;
import com.splitbuys.backend.common.error.InvalidRequestException;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import javax.sql.DataSource;
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Logical backups of the application data, written to a directory on the server, and the matching restore.
 *
 * <p>A backup is a gzip-compressed plain-SQL file in the same shape {@code pg_dump} produces for data: one
 * {@code COPY ... FROM stdin} block per table, so it can also be loaded with {@code psql}. It is taken inside a single
 * read-only {@code REPEATABLE READ} transaction, so every table reflects the same instant. It is done in-process with
 * PostgreSQL's COPY protocol, so no {@code pg_dump} binary (which must match the server's version) is needed.
 *
 * <p>The schema is not in the file: it belongs to Flyway. Restore therefore needs a database already migrated to the same
 * schema version, and replaces the data in all-or-nothing fashion. Sign-in codes are deliberately not backed up.
 */
@Service
public class BackupService {
    private static final Logger log = LoggerFactory.getLogger(BackupService.class);

    /** Parents before children, so the same order is valid for loading (and reversed for truncating). */
    static final List<String> TABLES = List.of("app_user", "expense_group", "group_member", "expense", "expense_share", "settlement");
    /** Tables whose {@code id} comes from an identity sequence that must be advanced after loading explicit ids. */
    private static final List<String> IDENTITY_TABLES = List.of("app_user", "expense_group", "expense", "expense_share", "settlement");

    private static final String PREFIX = "splitbuys-";
    private static final String SUFFIX = ".sql.gz";
    private static final String FORMAT = "1";
    private static final String TRAILER = "-- end of dump";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC);

    private final DataSource dataSource;
    private final Path dir;
    private final int keep;
    private final Clock clock;
    private final ReentrantLock lock = new ReentrantLock();

    BackupService(DataSource dataSource, Clock clock,
                  @Value("${app.backup.dir}") String dir,
                  @Value("${app.backup.keep}") int keep) {
        this.dataSource = dataSource;
        this.clock = clock;
        this.dir = Path.of(dir).toAbsolutePath().normalize();
        this.keep = Math.max(1, keep);
    }

    public Path directory() {
        return dir;
    }

    /** Takes a backup now. */
    public BackupResult backup() {
        return exclusively(() -> dump(PREFIX));
    }

    /** Backups on the server, newest first. */
    public List<BackupInfo> list() {
        try (Stream<Path> files = Files.exists(dir) ? Files.list(dir) : Stream.empty()) {
            return files.filter(BackupService::isBackup)
                    .map(p -> new BackupInfo(p.getFileName().toString(), size(p), modified(p)))
                    .sorted(Comparator.comparing(BackupInfo::createdAt).reversed().thenComparing(BackupInfo::file, Comparator.reverseOrder()))
                    .toList();
        } catch (IOException e) {
            throw new DependencyFailureException("BACKUP_FAILED", "Could not read the backup directory.", e);
        }
    }

    /**
     * Replaces all application data with the contents of {@code file}. Validated before anything is touched (complete file,
     * same schema version), takes a safety backup first if there is data to lose, and runs in one transaction: either
     * everything is restored or nothing changes.
     */
    public BackupResult restore(Path file) {
        return exclusively(() -> doRestore(file));
    }

    /* ------------------------------------------------------------------ backup */

    private BackupResult dump(String prefix) {
        requirePostgres();
        Instant now = clock.instant();
        Path target = dir.resolve(prefix + STAMP.format(now) + SUFFIX);
        Path partial = dir.resolve(target.getFileName() + ".part");
        try {
            prepareDirectory();
            Files.deleteIfExists(partial);
            createPrivateFile(partial);
            Map<String, Long> rows = new LinkedHashMap<>();
            String schemaVersion;
            try (Connection c = dataSource.getConnection()) {
                // one snapshot for every table, like pg_dump
                c.setAutoCommit(false);
                c.setReadOnly(true);
                c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                schemaVersion = schemaVersion(c);
                CopyManager copy = c.unwrap(PGConnection.class).getCopyAPI();
                try (OutputStream out = Files.newOutputStream(partial);
                     Writer w = new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(out), StandardCharsets.UTF_8))) {
                    w.write("-- splitbuys data backup\n-- format: " + FORMAT + "\n-- created: " + now
                            + "\n-- schema_version: " + schemaVersion + "\n-- database: " + databaseVersion(c) + "\n\n");
                    for (String table : TABLES) {
                        String columns = String.join(", ", columns(c, table));
                        w.write("COPY " + table + " (" + columns + ") FROM stdin;\n");
                        w.flush();
                        rows.put(table, copy.copyOut("COPY " + table + " (" + columns + ") TO STDOUT", w));
                        w.write("\\.\n\n");
                    }
                    for (String table : IDENTITY_TABLES) {
                        w.write("SELECT setval(pg_get_serial_sequence('" + table + "', 'id'), "
                                + "COALESCE((SELECT MAX(id) FROM " + table + "), 1), (SELECT MAX(id) FROM " + table + ") IS NOT NULL);\n");
                    }
                    w.write(TRAILER + " (" + rows + ")\n");
                }
                c.commit();
            }
            move(partial, target);
            prune();
            log.info("Backup written: {} ({} bytes) rows={}", target.getFileName(), size(target), rows);
            return new BackupResult(target.getFileName().toString(), size(target), now, schemaVersion, rows);
        } catch (IOException | SQLException e) {
            quietlyDelete(partial);
            throw new DependencyFailureException("BACKUP_FAILED", "The backup could not be completed; see the server log.", e);
        }
    }

    private void prune() throws IOException {
        List<BackupInfo> all = list();
        for (BackupInfo old : all.subList(Math.min(keep, all.size()), all.size())) {
            Files.deleteIfExists(dir.resolve(old.file()));
            log.info("Removed old backup {}", old.file());
        }
    }

    /* ----------------------------------------------------------------- restore */

    private BackupResult doRestore(Path file) {
        requirePostgres();
        if (!Files.isRegularFile(file)) {
            throw new InvalidRequestException("Backup file not found: " + file);
        }
        Scan scan = scan(file);
        if (!scan.complete()) {
            throw new InvalidRequestException("The backup file is incomplete (no end marker); refusing to restore it.");
        }
        try (Connection probe = dataSource.getConnection()) {
            String current = schemaVersion(probe);
            if (!current.equals(scan.schemaVersion())) {
                throw new ConflictException("SCHEMA_MISMATCH", "The backup is from schema version " + scan.schemaVersion()
                        + " but the database is at " + current + ". Migrate (or restore into a database at the same version).");
            }
            if (hasData(probe)) {
                // never destroy data without a way back
                BackupResult safety = dump(PREFIX + "pre-restore-");
                log.warn("Existing data found; safety backup written to {} before restoring", safety.file());
            }
        } catch (SQLException e) {
            throw new DependencyFailureException("RESTORE_FAILED", "The restore could not be started; see the server log.", e);
        }

        Map<String, Long> rows = new LinkedHashMap<>();
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement();
                 BufferedReader in = reader(file)) {
                List<String> reversed = new ArrayList<>(TABLES);
                Collections.reverse(reversed);
                st.execute("TRUNCATE TABLE " + String.join(", ", reversed) + " RESTART IDENTITY CASCADE");
                CopyManager copy = c.unwrap(PGConnection.class).getCopyAPI();
                String copySql = null;
                String table = null;
                StringBuilder block = null;
                for (String line = in.readLine(); line != null; line = in.readLine()) {
                    if (copySql != null) {
                        if (line.equals("\\.")) {
                            rows.put(table, copy.copyIn(copySql, new StringReader(block.toString())));
                            copySql = null;
                        } else {
                            block.append(line).append('\n');
                        }
                    } else if (line.isBlank() || line.startsWith("--")) {
                        continue;
                    } else if (line.startsWith("COPY ") && line.endsWith(" FROM stdin;")) {
                        table = line.substring(5, line.indexOf(' ', 5));
                        if (!TABLES.contains(table)) {
                            throw new InvalidRequestException("The backup refers to an unknown table: " + table);
                        }
                        copySql = line.substring(0, line.length() - 1);
                        block = new StringBuilder();
                    } else if (line.startsWith("SELECT setval(pg_get_serial_sequence(") && line.endsWith(";")) {
                        st.execute(line);
                    } else {
                        throw new InvalidRequestException("Unexpected content in the backup file; refusing to restore it.");
                    }
                }
                c.commit();
            } catch (IOException | SQLException | RuntimeException e) {
                c.rollback(); // nothing changed
                throw e;
            }
        } catch (IOException | SQLException e) {
            throw new DependencyFailureException("RESTORE_FAILED", "The restore failed and was rolled back; see the server log.", e);
        }
        log.warn("Restored {} rows={}", file.getFileName(), rows);
        return new BackupResult(file.getFileName().toString(), size(file), modified(file), scan.schemaVersion(), rows);
    }

    private record Scan(String schemaVersion, boolean complete) {
    }

    /** Reads the whole file once, cheaply, to check it is a complete backup before anything is modified. */
    private Scan scan(Path file) {
        String version = null;
        boolean complete = false;
        try (BufferedReader in = reader(file)) {
            for (String line = in.readLine(); line != null; line = in.readLine()) {
                if (line.startsWith("-- schema_version: ")) {
                    version = line.substring("-- schema_version: ".length()).trim();
                } else if (line.startsWith(TRAILER)) {
                    complete = true;
                }
            }
        } catch (IOException e) {
            throw new InvalidRequestException("The backup file could not be read (is it a complete .sql.gz backup?).");
        }
        if (version == null) {
            throw new InvalidRequestException("This is not a splitbuys backup file.");
        }
        return new Scan(version, complete);
    }

    /* ----------------------------------------------------------------- helpers */

    private <T> T exclusively(java.util.function.Supplier<T> work) {
        if (!lock.tryLock()) {
            throw new ConflictException("BACKUP_IN_PROGRESS", "A backup or restore is already running.");
        }
        try {
            return work.get();
        } finally {
            lock.unlock();
        }
    }

    private void requirePostgres() {
        try (Connection c = dataSource.getConnection()) {
            String product = c.getMetaData().getDatabaseProductName();
            if (!"PostgreSQL".equals(product)) {
                throw new ConflictException("BACKUP_UNSUPPORTED",
                        "Backups need PostgreSQL (this database is " + product + "). For SQLite, copy the database file.");
            }
        } catch (SQLException e) {
            throw new DependencyFailureException("BACKUP_FAILED", "The database could not be reached.", e);
        }
    }

    private static String schemaVersion(Connection c) throws SQLException {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1")) {
            return rs.next() ? rs.getString(1) : "none";
        }
    }

    private static String databaseVersion(Connection c) throws SQLException {
        return c.getMetaData().getDatabaseProductName() + " " + c.getMetaData().getDatabaseProductVersion();
    }

    private static List<String> columns(Connection c, String table) throws SQLException {
        List<String> names = new ArrayList<>();
        try (var ps = c.prepareStatement("SELECT column_name FROM information_schema.columns "
                + "WHERE table_schema = current_schema() AND table_name = ? ORDER BY ordinal_position")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    names.add(rs.getString(1));
                }
            }
        }
        if (names.isEmpty()) {
            throw new SQLException("Table not found: " + table);
        }
        return names;
    }

    private static boolean hasData(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            for (String table : TABLES) {
                try (ResultSet rs = st.executeQuery("SELECT EXISTS (SELECT 1 FROM " + table + ")")) {
                    if (rs.next() && rs.getBoolean(1)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static BufferedReader reader(Path file) throws IOException {
        InputStream raw = Files.newInputStream(file);
        return new BufferedReader(new InputStreamReader(new GZIPInputStream(raw), StandardCharsets.UTF_8));
    }

    private void prepareDirectory() throws IOException {
        if (!Files.exists(dir)) {
            try {
                Files.createDirectories(dir, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
            } catch (UnsupportedOperationException e) {
                Files.createDirectories(dir);
            }
        }
    }

    /** Backups hold everyone's email addresses: owner-only from the moment the file exists. */
    private static void createPrivateFile(Path file) throws IOException {
        try {
            Files.createFile(file, PosixFilePermissions.asFileAttribute(
                    java.util.Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)));
        } catch (UnsupportedOperationException e) {
            Files.createFile(file);
        }
    }

    private static void move(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static boolean isBackup(Path p) {
        String name = p.getFileName().toString();
        return name.startsWith(PREFIX) && name.endsWith(SUFFIX) && Files.isRegularFile(p);
    }

    private static long size(Path p) {
        try {
            return Files.size(p);
        } catch (IOException e) {
            return -1;
        }
    }

    private static Instant modified(Path p) {
        try {
            return Files.getLastModifiedTime(p).toInstant();
        } catch (IOException e) {
            return Instant.EPOCH;
        }
    }

    private static void quietlyDelete(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException ignored) {
            // best effort: a leftover .part file is not listed as a backup
        }
    }
}
