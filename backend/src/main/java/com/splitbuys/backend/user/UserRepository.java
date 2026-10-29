package com.splitbuys.backend.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface UserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByEmail(String email);
}
