package com.splitbuys.backend.backup;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Operations endpoints, behind the admin token (see {@code AdminTokenFilter}). Restore is deliberately not over HTTP. */
@RestController
@RequestMapping("/api/v1/admin/backups")
class BackupController {
    private final BackupService backups;

    BackupController(BackupService backups) {
        this.backups = backups;
    }

    /** Dumps the database to a new file on the server and returns what it contains. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    BackupResult create() {
        return backups.backup();
    }

    @GetMapping
    List<BackupInfo> list() {
        return backups.list();
    }
}
