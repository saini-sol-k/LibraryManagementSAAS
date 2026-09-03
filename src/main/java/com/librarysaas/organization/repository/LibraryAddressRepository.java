package com.librarysaas.organization.repository;

import com.librarysaas.organization.entity.LibraryAddress;
import com.librarysaas.organization.entity.LibraryAddressKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LibraryAddressRepository extends JpaRepository<LibraryAddress, LibraryAddressKey> {

    @Query("SELECT la FROM LibraryAddress la JOIN FETCH la.address "
            + "WHERE la.id.libraryId = :libraryId ORDER BY la.isPrimary DESC, la.id.addressType ASC")
    List<LibraryAddress> findByLibraryId(@Param("libraryId") Long libraryId);

    /** Owner-scoped read: an address id alone is never enough to reach a row. */
    @Query("SELECT la FROM LibraryAddress la JOIN FETCH la.address "
            + "WHERE la.id.libraryId = :libraryId AND la.id.addressId = :addressId")
    Optional<LibraryAddress> findByLibraryIdAndAddressId(
            @Param("libraryId") Long libraryId, @Param("addressId") Long addressId);

    @Query("SELECT la FROM LibraryAddress la "
            + "WHERE la.id.libraryId = :libraryId AND la.id.addressType = :addressType")
    Optional<LibraryAddress> findByLibraryIdAndAddressType(
            @Param("libraryId") Long libraryId, @Param("addressType") String addressType);

    @Query("SELECT la FROM LibraryAddress la WHERE la.id.libraryId = :libraryId AND la.isPrimary = true")
    List<LibraryAddress> findPrimaryByLibraryId(@Param("libraryId") Long libraryId);
}
