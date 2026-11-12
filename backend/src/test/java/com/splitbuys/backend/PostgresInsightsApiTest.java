package com.splitbuys.backend;

import com.splitbuys.backend.support.UsesPostgres;

/** The same insights API tests, run against PostgreSQL instead of SQLite. */
@UsesPostgres
class PostgresInsightsApiTest extends InsightsApiTest {
}
