package com.splitbuys.backend.backup;

import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Restore from the command line, then exit. Needs shell access to the server, which is the point: nobody can wipe the
 * database over the network. Usage:
 * <pre>java -jar app.jar --app.restore.file=data/backups/splitbuys-....sql.gz --app.restore.confirm=true --server.port=0</pre>
 */
@Component
@ConditionalOnProperty("app.restore.file")
class RestoreRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(RestoreRunner.class);

    private final BackupService backups;
    private final ConfigurableApplicationContext context;
    private final String file;
    private final boolean confirmed;

    RestoreRunner(BackupService backups, ConfigurableApplicationContext context,
                  @Value("${app.restore.file}") String file, @Value("${app.restore.confirm:false}") boolean confirmed) {
        this.backups = backups;
        this.context = context;
        this.file = file;
        this.confirmed = confirmed;
    }

    @Override
    public void run(ApplicationArguments args) {
        int status = 0;
        if (!confirmed) {
            log.error("Refusing to restore: this REPLACES all application data with {}. Re-run with --app.restore.confirm=true", file);
            status = 2;
        } else {
            try {
                BackupResult r = backups.restore(Path.of(file));
                log.warn("RESTORE COMPLETE from {} (schema {}): {}", r.file(), r.schemaVersion(), r.rows());
            } catch (RuntimeException e) {
                log.error("RESTORE FAILED, database unchanged: {}", e.getMessage());
                status = 1;
            }
        }
        int exit = status;
        System.exit(SpringApplication.exit(context, () -> exit));
    }
}
