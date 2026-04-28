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

    @Query("select count(distinct u.id) from AppUser u join u.roles r where u.enabled = true and r.active = true and r.name in :roleNames")
    long countEnabledUsersByActiveRoleNames(List<String> roleNames);

    @Query("select distinct u from AppUser u join u.roles r where u.enabled = true and r.active = true and r.name in :roleNames")
    List<AppUser> findEnabledUsersByActiveRoleNames(List<String> roleNames);

    /** Find users by role name */
    @Query("select distinct u from AppUser u join u.roles r where r.name = :roleName")
    Page<AppUser> findByRoleName(String roleName, Pageable pageable);

    /** Find users by role name and enabled status */
    @Query("select distinct u from AppUser u join u.roles r where r.name = :roleName and u.enabled = :enabled")
    Page<AppUser> findByRoleNameAndEnabled(String roleName, Boolean enabled, Pageable pageable);

    /** Find users by enabled status */
    Page<AppUser> findByEnabled(Boolean enabled, Pageable pageable);

    /** Find users by enabled status and search query */
    @Query("select u from AppUser u where u.enabled = :enabled and (lower(u.username) like lower(concat('%', :search, '%')) or lower(u.email) like lower(concat('%', :search, '%')) or lower(u.phone) like lower(concat('%', :search, '%')))")
    Page<AppUser> findByEnabledAndSearch(Boolean enabled, String search, Pageable pageable);

    @Query("""
            select distinct u from AppUser u
            left join u.roles r
            where (:query is null or :query = '' 
                   or lower(u.username) like lower(concat('%', :query, '%'))
                   or lower(u.email) like lower(concat('%', :query, '%'))
                   or lower(coalesce(u.phone, '')) like lower(concat('%', :query, '%')))
              and (:roleName is null or :roleName = '' or r.name = :roleName)
              and (:enabled is null or u.enabled = :enabled)
            """)
    Page<AppUser> findByFilters(String query, String roleName, Boolean enabled, Pageable pageable);
}
