package com.librarysaas.organization.repository;

import com.librarysaas.organization.entity.UserLibrary;
import com.librarysaas.organization.entity.UserLibraryKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserLibraryRepository extends JpaRepository<UserLibrary, UserLibraryKey> {
    
    @Query("SELECT ul FROM UserLibrary ul WHERE ul.id.userId = :userId AND ul.id.libraryId = :libraryId")
    Optional<UserLibrary> findByUserIdAndLibraryId(@Param("userId") Long userId, @Param("libraryId") Long libraryId);
    
    @Query("SELECT ul FROM UserLibrary ul WHERE ul.id.userId = :userId AND ul.status = 'ACTIVE'")
    List<UserLibrary> findActiveByUserId(@Param("userId") Long userId);
    
    @Query("SELECT ul FROM UserLibrary ul WHERE ul.id.userId = :userId AND ul.isPrimary = true")
    Optional<UserLibrary> findPrimaryByUserId(@Param("userId") Long userId);
    
    @Query("SELECT ul FROM UserLibrary ul WHERE ul.id.libraryId = :libraryId AND ul.status = 'ACTIVE'")
    List<UserLibrary> findActiveByLibraryId(@Param("libraryId") Long libraryId);
    
    /**
     * Check if a user has an ACTIVE membership in a library.
     * Returns true only if the membership exists AND status = 'ACTIVE'.
     * This provides query-level enforcement of membership visibility.
     */
    @Query("SELECT CASE WHEN COUNT(ul) > 0 THEN true ELSE false END FROM UserLibrary ul " +
           "WHERE ul.id.userId = :userId AND ul.id.libraryId = :libraryId AND ul.status = 'ACTIVE'")
    boolean existsInLibrary(@Param("userId") Long userId, @Param("libraryId") Long libraryId);
}
