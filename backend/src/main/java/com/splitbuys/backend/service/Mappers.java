package com.splitbuys.backend.service;

import com.splitbuys.backend.domain.AppUser;
import com.splitbuys.backend.domain.Expense;
import com.splitbuys.backend.domain.Settlement;
import com.splitbuys.backend.web.Dto.ExpenseDto;
import com.splitbuys.backend.web.Dto.SettlementDto;
import com.splitbuys.backend.web.Dto.ShareDto;
import com.splitbuys.backend.web.Dto.UserDto;

final class Mappers {
    private Mappers() {
    }

    static UserDto user(AppUser u) {
        return new UserDto(u.id, u.email, u.name);
    }

    static ExpenseDto expense(Expense e) {
        return new ExpenseDto(e.id, e.description, e.amountPaise, user(e.payer), user(e.createdBy), e.createdAt,
                e.splitType, e.shares.stream().map(s -> new ShareDto(user(s.user), s.amountPaise)).toList());
    }

    static SettlementDto settlement(Settlement s) {
        return new SettlementDto(s.id, user(s.fromUser), user(s.toUser), s.amountPaise, user(s.createdBy), s.createdAt);
    }
}
