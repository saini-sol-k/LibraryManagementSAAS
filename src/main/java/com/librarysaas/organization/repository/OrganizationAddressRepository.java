package com.librarysaas.organization.repository;

import com.librarysaas.organization.entity.OrganizationAddress;
import com.librarysaas.organization.entity.OrganizationAddressKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrganizationAddressRepository
        extends JpaRepository<OrganizationAddress, OrganizationAddressKey> {

    @Query("SELECT oa FROM OrganizationAddress oa JOIN FETCH oa.address "
            + "WHERE oa.id.organizationId = :organizationId ORDER BY oa.isPrimary DESC, oa.id.addressType ASC")
    List<OrganizationAddress> findByOrganizationId(@Param("organizationId") Long organizationId);

    /**
     * Looks the link up by owner AND address id. Scoping the read to the owner is
     * what keeps one tenant from reaching another tenant's address by guessing an id.
     */
    @Query("SELECT oa FROM OrganizationAddress oa JOIN FETCH oa.address "
            + "WHERE oa.id.organizationId = :organizationId AND oa.id.addressId = :addressId")
    Optional<OrganizationAddress> findByOrganizationIdAndAddressId(
            @Param("organizationId") Long organizationId, @Param("addressId") Long addressId);

    @Query("SELECT oa FROM OrganizationAddress oa "
            + "WHERE oa.id.organizationId = :organizationId AND oa.id.addressType = :addressType")
    Optional<OrganizationAddress> findByOrganizationIdAndAddressType(
            @Param("organizationId") Long organizationId, @Param("addressType") String addressType);

    @Query("SELECT oa FROM OrganizationAddress oa "
            + "WHERE oa.id.organizationId = :organizationId AND oa.isPrimary = true")
    List<OrganizationAddress> findPrimaryByOrganizationId(@Param("organizationId") Long organizationId);
}
