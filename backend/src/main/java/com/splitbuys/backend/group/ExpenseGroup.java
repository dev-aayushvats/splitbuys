package com.splitbuys.backend.group;

import com.splitbuys.backend.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

@Entity
@Table(name = "expense_group")
public class ExpenseGroup {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private long version;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String inviteToken;

    private Instant createdAt = Instant.now();

    @ManyToMany
    @JoinTable(name = "group_member")
    @OrderBy("id ASC")
    private Set<AppUser> members = new LinkedHashSet<>();

    protected ExpenseGroup() {
    }

    ExpenseGroup(String name, String inviteToken, AppUser creator) {
        this.name = name.trim();
        this.inviteToken = inviteToken;
        this.members.add(creator);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getInviteToken() {
        return inviteToken;
    }

    public Set<AppUser> getMembers() {
        return Collections.unmodifiableSet(members);
    }

    public boolean hasMember(long userId) {
        return members.stream().anyMatch(u -> u.getId() == userId);
    }

    public Optional<AppUser> findMember(long userId) {
        return members.stream().filter(u -> u.getId() == userId).findFirst();
    }

    void rename(String newName) {
        this.name = newName.trim();
    }

    void rotateInviteToken(String token) {
        this.inviteToken = token;
    }

    void addMember(AppUser user) {
        members.add(user);
    }

    void removeMember(long userId) {
        members.removeIf(u -> u.getId() == userId);
    }
}
