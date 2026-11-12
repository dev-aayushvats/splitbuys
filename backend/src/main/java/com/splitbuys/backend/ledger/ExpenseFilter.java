package com.splitbuys.backend.ledger;

import com.splitbuys.backend.common.error.InvalidRequestException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * What the user typed into the search box and the category chips, in the form the queries take. The queries never see a null:
 * "no text" is the pattern {@code %} and "no category" is every category, because a null parameter in a LIKE or IN is typed
 * differently by PostgreSQL and SQLite and is a known source of errors.
 *
 * @param pattern    lower-case LIKE pattern, with {@code !} as the escape character
 * @param categories the categories to keep
 * @param active     whether the user asked for anything at all
 */
record ExpenseFilter(String pattern, List<ExpenseCategory> categories, boolean active) {
    static final int MAX_TEXT = 100;
    static final ExpenseFilter NONE = new ExpenseFilter("%", Arrays.asList(ExpenseCategory.values()), false);

    static ExpenseFilter of(String text, ExpenseCategory category) {
        String q = text == null ? "" : text.strip();
        if (q.length() > MAX_TEXT) {
            throw new InvalidRequestException("Search text can be at most " + MAX_TEXT + " characters");
        }
        if (q.isEmpty() && category == null) {
            return NONE;
        }
        String pattern = q.isEmpty() ? "%" : "%" + escape(q.toLowerCase(Locale.ROOT)) + "%";
        return new ExpenseFilter(pattern, category == null ? NONE.categories() : List.of(category), true);
    }

    /** `%` and `_` in the user's text mean themselves, not "anything". */
    private static String escape(String s) {
        return s.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
