package com.splitbuys.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "expense_group")
public class ExpenseGroup {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public String name;

    @Column(nullable = false, unique = true)
    public String inviteToken;

    public Instant createdAt = Instant.now();

    @ManyToMany
    @JoinTable(name = "group_member")
    public Set<AppUser> members = new LinkedHashSet<>();

    public boolean hasMember(long userId) {
        return members.stream().anyMatch(u -> u.id == userId);
    }
}
