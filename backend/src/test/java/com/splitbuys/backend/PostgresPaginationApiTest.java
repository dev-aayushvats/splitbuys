package com.splitbuys.backend;

import com.splitbuys.backend.support.UsesPostgres;

/** The same pagination tests against PostgreSQL: timestamp comparison and tie-breaking must hold on both databases. */
@UsesPostgres
class PostgresPaginationApiTest extends PaginationApiTest {
}
