package com.splitbuys.backend.web;

import com.splitbuys.backend.auth.AuthInterceptor;
import com.splitbuys.backend.service.LedgerService;
import com.splitbuys.backend.web.Dto.ExpenseDto;
import com.splitbuys.backend.web.Dto.ExpenseRequest;
import com.splitbuys.backend.web.Dto.SettlementDto;
import com.splitbuys.backend.web.Dto.SettlementRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LedgerController {
    private final LedgerService ledger;

    public LedgerController(LedgerService ledger) {
        this.ledger = ledger;
    }

    @GetMapping("/api/groups/{groupId}/expenses")
    List<ExpenseDto> expenses(@RequestAttribute(AuthInterceptor.USER_ID) long userId, @PathVariable long groupId) {
        return ledger.listExpenses(groupId, userId);
    }

    @PostMapping("/api/groups/{groupId}/expenses")
    @ResponseStatus(HttpStatus.CREATED)
    ExpenseDto addExpense(@RequestAttribute(AuthInterceptor.USER_ID) long userId, @PathVariable long groupId,
                          @Valid @RequestBody ExpenseRequest req) {
        return ledger.addExpense(groupId, userId, req);
    }

    @PutMapping("/api/groups/{groupId}/expenses/{expenseId}")
    ExpenseDto updateExpense(@RequestAttribute(AuthInterceptor.USER_ID) long userId, @PathVariable long groupId,
                             @PathVariable long expenseId, @Valid @RequestBody ExpenseRequest req) {
        return ledger.updateExpense(groupId, expenseId, userId, req);
    }

    @DeleteMapping("/api/groups/{groupId}/expenses/{expenseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteExpense(@RequestAttribute(AuthInterceptor.USER_ID) long userId, @PathVariable long groupId,
                       @PathVariable long expenseId) {
        ledger.deleteExpense(groupId, expenseId, userId);
    }

    @GetMapping("/api/groups/{groupId}/settlements")
    List<SettlementDto> settlements(@RequestAttribute(AuthInterceptor.USER_ID) long userId, @PathVariable long groupId) {
        return ledger.listSettlements(groupId, userId);
    }

    @PostMapping("/api/groups/{groupId}/settlements")
    @ResponseStatus(HttpStatus.CREATED)
    SettlementDto addSettlement(@RequestAttribute(AuthInterceptor.USER_ID) long userId, @PathVariable long groupId,
                                @Valid @RequestBody SettlementRequest req) {
        return ledger.addSettlement(groupId, userId, req);
    }

    @DeleteMapping("/api/groups/{groupId}/settlements/{settlementId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteSettlement(@RequestAttribute(AuthInterceptor.USER_ID) long userId, @PathVariable long groupId,
                          @PathVariable long settlementId) {
        ledger.deleteSettlement(groupId, settlementId, userId);
    }
}
