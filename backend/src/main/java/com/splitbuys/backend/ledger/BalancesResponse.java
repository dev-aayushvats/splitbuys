package com.splitbuys.backend.ledger;

import java.util.List;

/** {@code totalSpentPaise} is the sum of every expense in the group, so clients need not download the whole history. */
public record BalancesResponse(List<BalanceDto> balances, List<TransferDto> settleUp, long totalSpentPaise) {
}
