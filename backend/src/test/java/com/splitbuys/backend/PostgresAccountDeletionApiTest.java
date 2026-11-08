package com.splitbuys.backend;

import com.splitbuys.backend.support.UsesPostgres;

/** The same account-deletion tests against PostgreSQL (the production database). */
@UsesPostgres
class PostgresAccountDeletionApiTest extends AccountDeletionApiTest {
}
