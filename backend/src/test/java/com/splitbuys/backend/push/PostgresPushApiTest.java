package com.splitbuys.backend.push;

import com.splitbuys.backend.support.UsesPostgres;

/** The same notification tests against PostgreSQL (covers the Postgres migration and Hibernate's schema validation). */
@UsesPostgres
class PostgresPushApiTest extends PushApiTest {
}
