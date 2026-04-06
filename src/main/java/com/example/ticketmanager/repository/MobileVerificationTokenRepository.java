package com.example.ticketmanager.repository;

import com.example.ticketmanager.entity.MobileVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MobileVerificationTokenRepository extends JpaRepository<MobileVerificationToken, Long> {
    Optional<MobileVerificationToken> findByToken(String token);
    void deleteByUserId(Long userId);
}
