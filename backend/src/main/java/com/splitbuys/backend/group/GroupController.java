package com.splitbuys.backend.group;

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
@RequestMapping("/api/v1")
class GroupController {
    private final GroupService groups;

    GroupController(GroupService groups) {
        this.groups = groups;
    }

    @PostMapping("/groups")
    @ResponseStatus(HttpStatus.CREATED)
    GroupDetail create(@CurrentUser long userId, @Valid @RequestBody GroupRequest req) {
        return groups.create(userId, req.name());
    }

    @GetMapping("/groups")
    List<GroupSummary> list(@CurrentUser long userId) {
        return groups.list(userId);
    }

    @GetMapping("/groups/{id}")
    GroupDetail get(@CurrentUser long userId, @PathVariable long id) {
        return groups.get(id, userId);
    }

    @PutMapping("/groups/{id}")
    GroupDetail rename(@CurrentUser long userId, @PathVariable long id, @Valid @RequestBody GroupRequest req) {
        return groups.rename(id, userId, req.name());
    }

    @DeleteMapping("/groups/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@CurrentUser long userId, @PathVariable long id) {
        groups.delete(id, userId);
    }

    @PostMapping("/groups/{id}/invite/regenerate")
    GroupDetail regenerateInvite(@CurrentUser long userId, @PathVariable long id) {
        return groups.regenerateInvite(id, userId);
    }

    @DeleteMapping("/groups/{id}/members/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void leave(@CurrentUser long userId, @PathVariable long id) {
        groups.leave(id, userId);
    }

    @DeleteMapping("/groups/{id}/members/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeMember(@CurrentUser long userId, @PathVariable long id, @PathVariable long memberId) {
        groups.removeMember(id, userId, memberId);
    }

    @GetMapping("/invites/{token}")
    InvitePreview previewInvite(@CurrentUser long userId, @PathVariable String token) {
        return groups.previewInvite(token, userId);
    }

    @PostMapping("/invites/{token}/join")
    GroupDetail join(@CurrentUser long userId, @PathVariable String token) {
        return groups.join(token, userId);
    }
}
