package com.splitbuys.backend.ledger;

/** All money in the API is integer paise (INR). */
final class Money {
    /** ₹100 crore: keeps every sum and product far away from overflow. */
    static final long MAX_PAISE = 1_000_000_000_00L;

    private Money() {
    }
}
