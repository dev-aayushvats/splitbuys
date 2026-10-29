package com.splitbuys.backend.group;

import com.splitbuys.backend.user.UserDto;
import java.util.List;

public record GroupDetail(Long id, String name, String inviteToken, List<UserDto> members) {
    static GroupDetail from(ExpenseGroup g) {
        return new GroupDetail(g.getId(), g.getName(), g.getInviteToken(),
                g.getMembers().stream().map(UserDto::from).toList());
    }
}
