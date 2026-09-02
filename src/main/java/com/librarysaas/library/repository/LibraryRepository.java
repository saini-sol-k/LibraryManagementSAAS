package com.librarysaas.library.repository;

import com.librarysaas.library.entity.Library;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LibraryRepository extends JpaRepository<Library, Long> {
    
    @Query("SELECT l FROM Library l WHERE l.libraryCode = :libraryCode AND l.organization.organizationId = :organizationId")
    Optional<Library> findByLibraryCodeAndOrganizationId(@Param("libraryCode") String libraryCode, @Param("organizationId") Long organizationId);
    
    @Query("SELECT l FROM Library l WHERE l.organization.organizationId = :organizationId ORDER BY l.name ASC")
    List<Library> findByOrganizationId(@Param("organizationId") Long organizationId);
    
    @Query("SELECT l FROM Library l WHERE l.organization.organizationId = :organizationId AND l.status = :status ORDER BY l.name ASC")
    List<Library> findByOrganizationIdAndStatus(@Param("organizationId") Long organizationId, @Param("status") String status);
    
    @Query("SELECT l FROM Library l WHERE l.status = 'ACTIVE' ORDER BY l.name ASC")
    List<Library> findAllActive();
    
    /**
     * Find all ACTIVE libraries accessible to a user through active UserLibrary memberships.
     * This ensures users can only access libraries they are explicitly members of.
     * Libraries are filtered to ACTIVE status as well.
     */
    @Query("SELECT l FROM Library l " +
           "INNER JOIN UserLibrary ul ON l.libraryId = ul.id.libraryId " +
           "WHERE ul.id.userId = :userId AND ul.status = 'ACTIVE' AND l.status = 'ACTIVE' " +
           "ORDER BY l.name ASC")
    List<Library> findActiveByUserId(@Param("userId") Long userId);
}
