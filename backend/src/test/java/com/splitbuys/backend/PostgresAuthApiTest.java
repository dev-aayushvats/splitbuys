package com.splitbuys.backend;

import com.splitbuys.backend.support.UsesPostgres;

/** The same Auth API tests, run against PostgreSQL instead of SQLite. */
@UsesPostgres
class PostgresAuthApiTest extends AuthApiTest {
}
