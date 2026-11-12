package com.splitbuys.backend.ledger;

import com.splitbuys.backend.common.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
class InsightsController {
    private final InsightsService insights;

    InsightsController(InsightsService insights) {
        this.insights = insights;
    }

    @GetMapping("/spending")
    SpendingInsights spending(@CurrentUser long userId) {
        return insights.spending(userId);
    }
}
