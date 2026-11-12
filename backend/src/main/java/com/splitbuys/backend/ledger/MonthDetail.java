package com.splitbuys.backend.ledger;

import com.splitbuys.backend.user.UserDto;
import java.util.List;

/**
 * One month of the signed-in user's spending in more detail (IST month, groups they are in now).
 *
 * @param sharePaise   what the user's own shares came to
 * @param paidPaise    what the user actually paid out for expenses (the full amount of those they were the payer of), whoever shared them
 * @param expenseCount how many expenses the user had a share in
 * @param daysCounted  days the month has run so far, for a per-day average: the elapsed days for the current month, all of them for a past one
 * @param groups       where the share went, biggest first (at most 5)
 * @param people       who the user shared the most expenses with, as the user's own share of those expenses, biggest first (at most 5)
 */
public record MonthDetail(String month, long sharePaise, long paidPaise, long expenseCount, int daysCounted,
                          List<GroupAmount> groups, List<PersonAmount> people) {
    public record GroupAmount(long groupId, String name, long paise) {
    }

    public record PersonAmount(UserDto user, long paise) {
    }
}
