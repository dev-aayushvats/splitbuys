package com.splitbuys.backend.ledger;

/** One row of an aggregate query: a user's total within a group. */
record AmountByUser(Long groupId, Long userId, Long total) {
}
