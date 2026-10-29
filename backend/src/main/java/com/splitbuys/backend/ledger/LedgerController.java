package com.splitbuys.backend.ledger;

import com.splitbuys.backend.common.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/groups/{groupId}")
class LedgerController {
    private final LedgerService ledger;
    private final BalanceService balances;

    LedgerController(LedgerService ledger, BalanceService balances) {
        this.ledger = ledger;
        this.balances = balances;
    }

    @GetMapping("/expenses")
    List<ExpenseDto> expenses(@CurrentUser long userId, @PathVariable long groupId) {
        return ledger.listExpenses(groupId, userId);
    }

    @PostMapping("/expenses")
    @ResponseStatus(HttpStatus.CREATED)
    ExpenseDto addExpense(@CurrentUser long userId, @PathVariable long groupId, @Valid @RequestBody ExpenseRequest req) {
        return ledger.addExpense(groupId, userId, req);
    }

    @PutMapping("/expenses/{expenseId}")
    ExpenseDto updateExpense(@CurrentUser long userId, @PathVariable long groupId, @PathVariable long expenseId,
                             @Valid @RequestBody ExpenseRequest req) {
        return ledger.updateExpense(groupId, expenseId, userId, req);
    }

    @DeleteMapping("/expenses/{expenseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteExpense(@CurrentUser long userId, @PathVariable long groupId, @PathVariable long expenseId) {
        ledger.deleteExpense(groupId, expenseId, userId);
    }

    @GetMapping("/settlements")
    List<SettlementDto> settlements(@CurrentUser long userId, @PathVariable long groupId) {
        return ledger.listSettlements(groupId, userId);
    }

    @PostMapping("/settlements")
    @ResponseStatus(HttpStatus.CREATED)
    SettlementDto addSettlement(@CurrentUser long userId, @PathVariable long groupId,
                                @Valid @RequestBody SettlementRequest req) {
        return ledger.addSettlement(groupId, userId, req);
    }

    @DeleteMapping("/settlements/{settlementId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteSettlement(@CurrentUser long userId, @PathVariable long groupId, @PathVariable long settlementId) {
        ledger.deleteSettlement(groupId, settlementId, userId);
    }

    @GetMapping("/balances")
    BalancesResponse balances(@CurrentUser long userId, @PathVariable long groupId) {
        return balances.balances(groupId, userId);
    }
}
