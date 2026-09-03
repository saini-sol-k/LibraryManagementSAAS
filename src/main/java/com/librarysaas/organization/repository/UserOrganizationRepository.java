package com.librarysaas.organization.repository;

import com.librarysaas.organization.entity.UserOrganization;
import com.librarysaas.organization.entity.UserOrganizationKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserOrganizationRepository extends JpaRepository<UserOrganization, UserOrganizationKey> {
    
    @Query("SELECT uo FROM UserOrganization uo WHERE uo.id.userId = :userId AND uo.id.organizationId = :organizationId")
    Optional<UserOrganization> findByUserIdAndOrganizationId(@Param("userId") Long userId, @Param("organizationId") Long organizationId);
    
    @Query("SELECT uo FROM UserOrganization uo WHERE uo.id.userId = :userId AND uo.status = 'ACTIVE'")
    List<UserOrganization> findActiveByUserId(@Param("userId") Long userId);
    
    @Query("SELECT uo FROM UserOrganization uo WHERE uo.id.userId = :userId AND uo.isPrimary = true")
    Optional<UserOrganization> findPrimaryByUserId(@Param("userId") Long userId);
    
    @Query("SELECT uo FROM UserOrganization uo WHERE uo.id.organizationId = :organizationId AND uo.status = 'ACTIVE'")
    List<UserOrganization> findActiveByOrganizationId(@Param("organizationId") Long organizationId);
    
    /**
     * Every membership of an organization regardless of status, with the user
     * fetched, for the member list. findActiveByOrganizationId stays the
     * authorisation-facing query; this one is for display only.
     */
    @Query("SELECT uo FROM UserOrganization uo JOIN FETCH uo.user "
            + "WHERE uo.id.organizationId = :organizationId "
            + "ORDER BY uo.isPrimary DESC, uo.joinedAt")
    List<UserOrganization> findAllByOrganizationIdWithUser(@Param("organizationId") Long organizationId);

    /**
     * Check if a user has an ACTIVE membership in an organization.
     * Returns true only if the membership exists AND status = 'ACTIVE'.
     * This provides query-level enforcement of membership visibility.
     */
    @Query("SELECT CASE WHEN COUNT(uo) > 0 THEN true ELSE false END FROM UserOrganization uo " +
           "WHERE uo.id.userId = :userId AND uo.id.organizationId = :organizationId AND uo.status = 'ACTIVE'")
    boolean existsInOrganization(@Param("userId") Long userId, @Param("organizationId") Long organizationId);
}
