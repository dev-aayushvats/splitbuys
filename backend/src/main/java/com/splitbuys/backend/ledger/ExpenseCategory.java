package com.splitbuys.backend.ledger;

/** What an expense was for. A short fixed list on purpose: free-text categories fragment ("food", "Food ", "dinner") and cannot be summed. */
public enum ExpenseCategory {
    FOOD, GROCERIES, TRAVEL, STAY, UTILITIES, ENTERTAINMENT, SHOPPING, OTHER
}
