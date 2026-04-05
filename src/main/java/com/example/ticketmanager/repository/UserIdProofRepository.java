package com.example.ticketmanager.repository;

import com.example.ticketmanager.entity.AppUser;
import com.example.ticketmanager.entity.UserIdProof;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserIdProofRepository extends JpaRepository<UserIdProof, Long> {

    List<UserIdProof> findByUser(AppUser user);

    Optional<UserIdProof> findByUserAndIdProofType(AppUser user, String idProofType);

    @Query("SELECT COUNT(u) > 0 FROM UserIdProof u WHERE u.user = :user AND u.idProofType = :idProofType")
    boolean existsByUserAndIdProofType(@Param("user") AppUser user, @Param("idProofType") String idProofType);

    @Query("SELECT u.idProofType FROM UserIdProof u WHERE u.user = :user")
    List<String> findIdProofTypesByUser(@Param("user") AppUser user);

    @Query("SELECT u FROM UserIdProof u WHERE u.user = :user AND u.idProofType IN :types")
    List<UserIdProof> findByUserAndIdProofTypeIn(@Param("user") AppUser user, @Param("types") List<String> types);
}
