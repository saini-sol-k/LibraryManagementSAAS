package com.librarysaas.organization.repository;

import com.librarysaas.organization.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, Long> {
    
    Optional<Organization> findByOrganizationCode(String organizationCode);
    
    List<Organization> findByStatus(String status);
    
    @Query("SELECT o FROM Organization o WHERE o.status = 'ACTIVE' ORDER BY o.name ASC")
    List<Organization> findAllActive();
    
    /**
     * Find all ACTIVE organizations accessible to a user through active UserOrganization memberships.
     * This ensures users can only access organizations they are explicitly members of.
     */
    @Query("SELECT o FROM Organization o " +
           "INNER JOIN UserOrganization uo ON o.organizationId = uo.id.organizationId " +
           "WHERE uo.id.userId = :userId AND uo.status = 'ACTIVE' AND o.status = 'ACTIVE' " +
           "ORDER BY o.name ASC")
    List<Organization> findActiveByUserId(@Param("userId") Long userId);
}
