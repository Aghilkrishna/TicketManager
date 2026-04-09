package com.example.ticketmanager.repository;

import com.example.ticketmanager.entity.AppUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsername(String username);

    Optional<AppUser> findByEmail(String email);

    Optional<AppUser> findByEmailIgnoreCaseOrPhone(String email, String phone);

    Optional<AppUser> findFirstByEmailIgnoreCaseOrPhoneIn(String email, List<String> phones);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    List<AppUser> findTop10ByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCaseOrPhoneContainingIgnoreCase(
            String username, String email, String phone
    );

    Page<AppUser> findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCaseOrPhoneContainingIgnoreCase(
            String username, String email, String phone, Pageable pageable
    );

    /** Count active users grouped by role name. Used for the admin dashboard User Count chart. */
    @Query("select r.name, count(distinct u.id) from AppUser u join u.roles r where r.active = true and u.enabled = true group by r.name")
    List<Object[]> countUsersByRole();
}
