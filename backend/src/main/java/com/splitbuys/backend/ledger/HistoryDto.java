package com.splitbuys.backend.ledger;

import com.splitbuys.backend.user.UserDto;
import java.time.Instant;

/**
 * One change to an expense. {@code before} is empty for a creation, {@code after} for a deletion. {@code undoable} says
 * whether undo would be accepted right now (an edit only while it is still the latest change; a delete only once).
 */
public record HistoryDto(Long id, long expenseId, HistoryAction action, UserDto actor, Instant at,
                         SnapshotDto before, SnapshotDto after, boolean undoable) {
}
