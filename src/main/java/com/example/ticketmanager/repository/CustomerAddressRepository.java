package com.example.ticketmanager.repository;

import com.example.ticketmanager.entity.CustomerAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, Long> {

    @Query("SELECT ca FROM CustomerAddress ca WHERE " +
           "(LOWER(ca.customerEmail) = LOWER(:email) OR LOWER(ca.customerPhone) = LOWER(:phone)) " +
           "ORDER BY ca.updatedAt DESC")
    List<CustomerAddress> findByCustomerEmailOrPhone(@Param("email") String email, @Param("phone") String phone);

    @Query("SELECT ca FROM CustomerAddress ca WHERE " +
           "(LOWER(ca.customerEmail) = LOWER(:email) OR LOWER(ca.customerPhone) = LOWER(:phone)) " +
           "AND (:userId IS NULL OR ca.createdBy.id = :userId OR " +
           "     EXISTS (SELECT 1 FROM AppUser u JOIN u.roles r WHERE u.id = ca.createdBy.id AND r.name = 'ROLE_ADMIN')) " +
           "ORDER BY ca.updatedAt DESC")
    List<CustomerAddress> findByCustomerEmailOrPhoneWithAccess(@Param("email") String email, 
                                                            @Param("phone") String phone, 
                                                            @Param("userId") Long userId);

    @Query("SELECT ca FROM CustomerAddress ca WHERE " +
           "LOWER(ca.customerEmail) = LOWER(:email) AND LOWER(ca.customerPhone) = LOWER(:phone)")
    List<CustomerAddress> findByCustomerEmailAndPhone(@Param("email") String email, @Param("phone") String phone);

    @Query("SELECT ca FROM CustomerAddress ca WHERE " +
           "LOWER(ca.customerEmail) = LOWER(:email) AND LOWER(ca.customerPhone) = LOWER(:phone) AND " +
           "(:userId IS NULL OR ca.createdBy.id = :userId OR " +
           "     EXISTS (SELECT 1 FROM AppUser u JOIN u.roles r WHERE u.id = ca.createdBy.id AND r.name = 'ROLE_ADMIN'))")
    List<CustomerAddress> findByCustomerEmailAndPhoneWithAccess(@Param("email") String email, 
                                                             @Param("phone") String phone, 
                                                             @Param("userId") Long userId);

    @Query("SELECT ca FROM CustomerAddress ca WHERE ca.id = :addressId AND " +
           "(:userId IS NULL OR ca.createdBy.id = :userId OR " +
           "     EXISTS (SELECT 1 FROM AppUser u JOIN u.roles r WHERE u.id = ca.createdBy.id AND r.name = 'ROLE_ADMIN'))")
    Optional<CustomerAddress> findByIdWithAccess(@Param("addressId") Long addressId, @Param("userId") Long userId);
}
