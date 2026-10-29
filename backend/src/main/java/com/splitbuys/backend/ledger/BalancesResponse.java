package com.splitbuys.backend.ledger;

import java.util.List;

public record BalancesResponse(List<BalanceDto> balances, List<TransferDto> settleUp) {
}
