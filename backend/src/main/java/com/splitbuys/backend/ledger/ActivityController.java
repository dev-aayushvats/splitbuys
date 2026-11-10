package com.splitbuys.backend.ledger;

import com.splitbuys.backend.common.security.CurrentUser;
import com.splitbuys.backend.common.web.PageCursor;
import com.splitbuys.backend.common.web.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
class ActivityController {
    private final ActivityService activity;

    ActivityController(ActivityService activity) {
        this.activity = activity;
    }

    @GetMapping("/activity")
    PageResponse<ActivityItem> activity(@CurrentUser long userId,
                                        @RequestParam(defaultValue = "" + PageCursor.DEFAULT_LIMIT) int limit,
                                        @RequestParam(required = false) String cursor) {
        return activity.list(userId, limit, cursor);
    }
}
