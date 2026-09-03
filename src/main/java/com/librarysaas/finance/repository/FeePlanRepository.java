package com.librarysaas.finance.repository;

import com.librarysaas.finance.entity.FeePlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FeePlanRepository extends JpaRepository<FeePlan, Long> {

    /**
     * Scoping the lookup to the library is what stops plan-id probing across
     * tenants: an id from another library reads as absent.
     */
    Optional<FeePlan> findByFeePlanIdAndLibraryLibraryId(Long feePlanId, Long libraryId);

    @Query("SELECT p FROM FeePlan p WHERE p.library.libraryId = :libraryId ORDER BY p.name")
    List<FeePlan> findAllByLibrary(@Param("libraryId") Long libraryId);

    @Query("SELECT p FROM FeePlan p WHERE p.library.libraryId = :libraryId AND p.status = :status "
            + "ORDER BY p.name")
    List<FeePlan> findAllByLibraryAndStatus(@Param("libraryId") Long libraryId,
                                            @Param("status") String status);

    /** Names are unique per library (uk_fee_plan_library_name). */
    boolean existsByLibraryLibraryIdAndName(Long libraryId, String name);

    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM FeePlan p "
            + "WHERE p.library.libraryId = :libraryId AND p.name = :name "
            + "AND p.feePlanId <> :excludedId")
    boolean existsByLibraryAndNameExcluding(@Param("libraryId") Long libraryId,
                                            @Param("name") String name,
                                            @Param("excludedId") Long excludedId);
}
