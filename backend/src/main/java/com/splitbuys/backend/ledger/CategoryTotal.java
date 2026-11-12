package com.splitbuys.backend.ledger;

/** @param totalPaise everything spent in the category; @param mySharePaise the part of it that falls on the caller */
public record CategoryTotal(ExpenseCategory category, long totalPaise, long count, long mySharePaise) {
}
