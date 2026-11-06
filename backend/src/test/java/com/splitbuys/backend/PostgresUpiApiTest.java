package com.splitbuys.backend;

import com.splitbuys.backend.support.UsesPostgres;

/** The same UPI tests against PostgreSQL (covers the Postgres migration and Hibernate's schema validation). */
@UsesPostgres
class PostgresUpiApiTest extends UpiApiTest {
}
