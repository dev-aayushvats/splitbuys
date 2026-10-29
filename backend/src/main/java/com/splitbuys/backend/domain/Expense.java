package com.splitbuys.backend.domain;

import com.splitbuys.backend.split.SplitType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
public class Expense {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false)
    public ExpenseGroup group;

    public String description;
    public long amountPaise;

    @ManyToOne(optional = false)
    public AppUser payer;

    @ManyToOne(optional = false)
    public AppUser createdBy;

    public Instant createdAt = Instant.now();

    @Enumerated(EnumType.STRING)
    public SplitType splitType;

    @OneToMany(mappedBy = "expense", cascade = CascadeType.ALL, orphanRemoval = true)
    public List<ExpenseShare> shares = new ArrayList<>();
}
