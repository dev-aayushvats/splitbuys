package com.splitbuys.backend.group;

import com.splitbuys.backend.common.security.CurrentUser;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
class FriendController {
    private final FriendService friends;

    FriendController(FriendService friends) {
        this.friends = friends;
    }

    @GetMapping("/friends")
    List<FriendDto> friends(@CurrentUser long userId) {
        return friends.list(userId);
    }
}
