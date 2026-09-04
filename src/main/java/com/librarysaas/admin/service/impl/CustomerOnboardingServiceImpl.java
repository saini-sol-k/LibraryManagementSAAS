package com.librarysaas.admin.service.impl;

import com.librarysaas.admin.dto.CustomerOnboardingRequest;
import com.librarysaas.admin.dto.CustomerOnboardingResponse;
import com.librarysaas.admin.service.CustomerOnboardingService;
import com.librarysaas.common.exception.BusinessException;
import com.librarysaas.common.exception.DuplicateResourceException;
import com.librarysaas.common.exception.ForbiddenException;
import com.librarysaas.organization.dto.LibraryCreateRequest;
import com.librarysaas.organization.dto.LibraryResponse;
import com.librarysaas.organization.dto.OrganizationCreateRequest;
import com.librarysaas.organization.dto.OrganizationResponse;
import com.librarysaas.library.entity.Library;
import com.librarysaas.library.repository.LibraryRepository;
import com.librarysaas.organization.service.LibraryService;
import com.librarysaas.organization.service.OrganizationService;
import com.librarysaas.organization.service.UserManagementService;
import com.librarysaas.seat.dto.SeatProvisioningResult;
import com.librarysaas.seat.service.SeatProvisioningService;
import com.librarysaas.security.TenantAuthorizationService;
import com.librarysaas.security.model.User;
import com.librarysaas.security.repository.UserRepository;
import com.librarysaas.security.repository.UserRoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;

/**
 * Creates a customer tenant in one transaction.
 *
 * This orchestrates existing services rather than reimplementing them. Only two
 * things here are genuinely new to the codebase: writing a users row, and
 * writing a user_role row. Everything else - the organization, the library, both
 * memberships - is the same code path an administrator already uses, so a
 * customer created here is indistinguishable from one assembled by hand and
 * every existing tenant guard applies to it unchanged.
 *
 * <p><b>Authorization.</b> Only the platform owner may call this, enforced by
 * the same isSuperAdmin() gate OrganizationServiceImpl already uses for creating
 * an organization. No new role or permission was introduced. The customer's own
 * administrator is granted ORGANIZATION_OWNER, never SUPER_ADMIN.
 *
 * <p><b>Tenancy.</b> Nothing here writes a tenant filter or reads one. The new
 * user reaches its data only through the user_organization and user_library rows
 * this creates, which is exactly what TenantAuthorizationService already
 * consults. No module needed changing to make the new tenant isolated.
 *
 * <p><b>The password</b> is generated here, hashed immediately, and returned
 * once. Only the hash is persisted. It is never logged: this class logs nothing
 * at all, and the response DTO redacts the password in toString().
 */
@Service
public class CustomerOnboardingServiceImpl implements CustomerOnboardingService {

    /** Role granted to the customer's own administrator. Deliberately not SUPER_ADMIN. */
    static final String CUSTOMER_ADMIN_ROLE = "ORGANIZATION_OWNER";

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final int PASSWORD_LENGTH = 16;

    /**
     * Alphabet for generated passwords. 0/O and 1/l/I are left out: the product
     * owner reads this aloud or retypes it for the customer, and a password that
     * cannot be transcribed is a password that gets replaced by a weaker one.
     */
    private static final String PASSWORD_ALPHABET =
            "ABCDEFGHJKMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final OrganizationService organizationService;
    private final LibraryService libraryService;
    private final UserManagementService userManagementService;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final TenantAuthorizationService tenantAuthorizationService;
    private final LibraryRepository libraryRepository;
    private final SeatProvisioningService seatProvisioningService;

    @Autowired
    public CustomerOnboardingServiceImpl(OrganizationService organizationService,
                                         LibraryService libraryService,
                                         UserManagementService userManagementService,
                                         UserRepository userRepository,
                                         UserRoleRepository userRoleRepository,
                                         PasswordEncoder passwordEncoder,
                                         TenantAuthorizationService tenantAuthorizationService,
                                         LibraryRepository libraryRepository,
                                         SeatProvisioningService seatProvisioningService) {
        this.organizationService = organizationService;
        this.libraryService = libraryService;
        this.userManagementService = userManagementService;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordEncoder = passwordEncoder;
        this.tenantAuthorizationService = tenantAuthorizationService;
        this.libraryRepository = libraryRepository;
        this.seatProvisioningService = seatProvisioningService;
    }

    @Override
    @Transactional
    public CustomerOnboardingResponse onboardCustomer(CustomerOnboardingRequest request) {
        // Onboarding a paying customer is a platform action, not a tenant action.
        // This mirrors the check OrganizationServiceImpl.createOrganization makes,
        // so the boundary is the existing one rather than a second definition of
        // who the product owner is.
        if (!tenantAuthorizationService.isSuperAdmin()) {
            throw new ForbiddenException("Only SUPER_ADMIN can onboard a customer");
        }

        String timezone = resolveTimezone(request.getTimezone());
        String username = trimToNull(request.getAdminUsername());
        String email = trimToNull(request.getAdminEmail());

        // Both columns are globally unique across every tenant, so a clash is
        // checked before anything is written. The database constraint remains the
        // real guarantee; this only turns a constraint violation into the error
        // shape the rest of the API already uses.
        requireUsernameAndEmailFree(username, email);

        OrganizationResponse organization = organizationService.createOrganization(
                organizationRequest(request));

        LibraryResponse library = libraryService.createLibrary(
                organization.getOrganizationId(), libraryRequest(request, timezone));

        // The seats the customer is paying for, created inside this same
        // transaction. The library entity is re-read so it is managed here: the
        // provisioner writes the configured count onto it, and a failure
        // anywhere below rolls the seats back with the rest of the tenant.
        Library libraryEntity = libraryRepository.findById(library.getLibraryId())
                .orElseThrow(() -> new BusinessException(
                        "The new library could not be read back", "LIBRARY_CREATION_FAILED"));
        SeatProvisioningResult seats = seatProvisioningService.applySeatCount(
                libraryEntity, request.getSeatCount(),
                tenantAuthorizationService.getCurrentUserId().orElse(null));

        String temporaryPassword = generatePassword();
        User admin = persistAdministrator(request, username, email, temporaryPassword);

        // assignRoleByCode writes nothing when the role code does not exist, which
        // would leave an administrator who can sign in and do nothing at all.
        // Treat that as a failure so the whole tenant rolls back.
        int rolesAssigned = userRoleRepository.assignRoleByCode(admin.getUserId(), CUSTOMER_ADMIN_ROLE);
        if (rolesAssigned != 1) {
            throw new BusinessException("Could not assign the customer administrator role",
                    "ROLE_ASSIGNMENT_FAILED");
        }

        // Order matters: addUserToLibrary refuses a user who is not already an
        // active member of the library's organization.
        userManagementService.addUserToOrganization(organization.getOrganizationId(),
                admin.getUserId(), true);

        // That membership was saved but not flushed, and the organization-membership
        // check inside addUserToLibrary runs as a native query, which reads the
        // database rather than the persistence context. Flush so it sees the row
        // written a moment ago in this same transaction.
        userRepository.flush();

        userManagementService.addUserToLibrary(library.getLibraryId(), admin.getUserId(), true);

        return new CustomerOnboardingResponse(
                new CustomerOnboardingResponse.OrganizationSummary(
                        organization.getOrganizationId(),
                        organization.getOrganizationCode(),
                        organization.getName()),
                new CustomerOnboardingResponse.LibrarySummary(
                        library.getLibraryId(),
                        library.getLibraryCode(),
                        library.getName(),
                        library.getTimezone(),
                        seats.getSeatCount(),
                        seats.getSeatsCreated(),
                        seats.getSeatRange()),
                new CustomerOnboardingResponse.UserSummary(
                        admin.getUserId(),
                        admin.getUsername(),
                        admin.getEmail(),
                        CUSTOMER_ADMIN_ROLE),
                new CustomerOnboardingResponse.InitialCredentials(
                        admin.getUsername(), temporaryPassword));
    }

    private User persistAdministrator(CustomerOnboardingRequest request, String username,
                                      String email, String temporaryPassword) {
        User admin = new User();
        admin.setUsername(username);
        admin.setEmail(email);
        admin.setMobile(trimToNull(request.getAdminMobile()));
        // The plaintext is hashed here and never travels further. Only the hash is
        // held on the entity, so nothing downstream - persistence, logging, an
        // entity dump - can observe the password.
        admin.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        admin.setFirstName(trimToNull(request.getAdminFirstName()));
        admin.setLastName(trimToNull(request.getAdminLastName()));
        admin.setStatus(STATUS_ACTIVE);
        admin.setEmailVerified(false);
        admin.setMobileVerified(false);
        admin.setCreatedAt(LocalDateTime.now());
        admin.setUpdatedAt(LocalDateTime.now());
        admin.setCreatedBy(tenantAuthorizationService.getCurrentUserId().orElse(null));

        // Flushed so the generated id exists before the native user_role insert,
        // which does not see the persistence context.
        return userRepository.saveAndFlush(admin);
    }

    private void requireUsernameAndEmailFree(String username, String email) {
        if (userRepository.findByUsernameOrEmail(username).isPresent()) {
            throw new DuplicateResourceException("Username is already taken",
                    "USERNAME_ALREADY_EXISTS");
        }
        if (userRepository.findByUsernameOrEmail(email).isPresent()) {
            throw new DuplicateResourceException("Email is already registered",
                    "EMAIL_ALREADY_EXISTS");
        }
    }

    private OrganizationCreateRequest organizationRequest(CustomerOnboardingRequest request) {
        OrganizationCreateRequest created = new OrganizationCreateRequest();
        created.setOrganizationCode(codeOrDerived(request.getOrganizationCode(),
                request.getOrganizationName()));
        created.setName(request.getOrganizationName());
        created.setEmail(trimToNull(request.getAdminEmail()));
        return created;
    }

    private LibraryCreateRequest libraryRequest(CustomerOnboardingRequest request, String timezone) {
        LibraryCreateRequest created = new LibraryCreateRequest();
        created.setLibraryCode(codeOrDerived(request.getLibraryCode(), request.getLibraryName()));
        created.setName(request.getLibraryName());
        created.setEmail(trimToNull(request.getAdminEmail()));
        created.setTimezone(timezone);
        return created;
    }

    /**
     * The schema requires a code on both the organization and the library, but the
     * product owner rarely has one in mind, so a missing code is derived from the
     * name. A derived organization code can collide with an existing tenant, and
     * that surfaces as the module's own ORGANIZATION_CODE_ALREADY_EXISTS rather
     * than being silently rewritten - the product owner then supplies one
     * explicitly. A library code cannot collide, because it is unique only within
     * its organization and the organization is new.
     */
    private String codeOrDerived(String supplied, String name) {
        String trimmed = trimToNull(supplied);
        if (trimmed != null) {
            return trimmed;
        }
        if (name == null) {
            return null;
        }
        String derived = name.trim().toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (derived.isEmpty()) {
            return null;
        }
        return derived.length() > 50 ? derived.substring(0, 50).replaceAll("-+$", "") : derived;
    }

    /**
     * A timezone that the reporting module cannot resolve would produce a library
     * whose dashboard fails at read time, long after onboarding looked successful.
     * Reject it here instead, using the same ZoneId lookup reporting performs.
     */
    private String resolveTimezone(String timezone) {
        String trimmed = trimToNull(timezone);
        if (trimmed == null) {
            return null;
        }
        try {
            ZoneId.of(trimmed);
        } catch (DateTimeException e) {
            throw new BusinessException("Timezone is not a recognised IANA zone id", "INVALID_TIMEZONE");
        }
        return trimmed;
    }

    private String generatePassword() {
        StringBuilder password = new StringBuilder(PASSWORD_LENGTH);
        for (int i = 0; i < PASSWORD_LENGTH; i++) {
            password.append(PASSWORD_ALPHABET.charAt(RANDOM.nextInt(PASSWORD_ALPHABET.length())));
        }
        return password.toString();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
