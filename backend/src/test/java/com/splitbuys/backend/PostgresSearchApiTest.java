package com.splitbuys.backend;

import com.splitbuys.backend.support.UsesPostgres;

/** The same search tests, run against PostgreSQL instead of SQLite (LIKE, ESCAPE and lower() are where the two differ). */
@UsesPostgres
class PostgresSearchApiTest extends SearchApiTest {
}
