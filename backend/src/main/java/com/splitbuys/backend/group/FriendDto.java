package com.splitbuys.backend.group;

import com.splitbuys.backend.user.UserDto;
import java.util.List;

/**
 * Someone who shares at least one group with the viewer. {@code balancePaise} is positive when they owe the viewer and
 * negative when the viewer owes them, summed over the groups they share (see {@link FriendService}).
 */
public record FriendDto(UserDto user, long balancePaise, List<FriendGroup> groups) {
    /** One shared group and what the two of them owe each other in it. */
    public record FriendGroup(long id, String name, long balancePaise) {
    }
}
