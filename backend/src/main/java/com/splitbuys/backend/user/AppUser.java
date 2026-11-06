package com.splitbuys.backend.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_user")
public class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    private String name;

    /** Where this person wants to be paid (a UPI ID such as asha@okbank). Private: see ProfileDto vs UserDto. */
    private String upiId;

    protected AppUser() {
    }

    public AppUser(String email) {
        this.email = email;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getName() {
        return name;
    }

    public String getUpiId() {
        return upiId;
    }

    /** Pass an already-normalised ID (see UpiId), or null to remove it. */
    public void setUpiId(String upiId) {
        this.upiId = upiId;
    }

    public void rename(String name) {
        this.name = name.trim();
    }

    // Identity is the database id; a not-yet-saved instance is only equal to itself.
    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof AppUser u && id != null && id.equals(u.getId()));
    }

    @Override
    public int hashCode() {
        return AppUser.class.hashCode();
    }
}
