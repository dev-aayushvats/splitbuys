package com.splitbuys.backend;

import com.splitbuys.backend.support.UsesPostgres;

/** The same Group API tests, run against PostgreSQL instead of SQLite. */
@UsesPostgres
class PostgresGroupApiTest extends GroupApiTest {
}
