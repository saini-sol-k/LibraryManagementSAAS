package com.librarysaas.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The one and only carrier of a newly generated initial password.
 *
 * This type exists so the plaintext password can never leak through a shared
 * user DTO: nothing else in the application returns it, no GET endpoint produces
 * this type, and the password is not persisted in any form other than its BCrypt
 * hash. Once this response is serialised the plaintext is gone from the server.
 *
 * toString() redacts the password on every nested type, matching what
 * AuthController's LoginRequest already does, so an accidental log statement or
 * a debugger dump cannot spill it.
 */
@Schema(description = "Result of onboarding a customer. Carries the initial password once and never again.")
public class CustomerOnboardingResponse {

    private final OrganizationSummary organization;
    private final LibrarySummary library;
    private final UserSummary user;
    private final InitialCredentials initialCredentials;

    public CustomerOnboardingResponse(OrganizationSummary organization, LibrarySummary library,
                                      UserSummary user, InitialCredentials initialCredentials) {
        this.organization = organization;
        this.library = library;
        this.user = user;
        this.initialCredentials = initialCredentials;
    }

    public OrganizationSummary getOrganization() {
        return organization;
    }

    public LibrarySummary getLibrary() {
        return library;
    }

    public UserSummary getUser() {
        return user;
    }

    public InitialCredentials getInitialCredentials() {
        return initialCredentials;
    }

    @Override
    public String toString() {
        return "CustomerOnboardingResponse{organization=" + organization
                + ", library=" + library + ", user=" + user + ", initialCredentials=****}";
    }

    public static class OrganizationSummary {
        private final Long organizationId;
        private final String organizationCode;
        private final String name;

        public OrganizationSummary(Long organizationId, String organizationCode, String name) {
            this.organizationId = organizationId;
            this.organizationCode = organizationCode;
            this.name = name;
        }

        public Long getOrganizationId() {
            return organizationId;
        }

        public String getOrganizationCode() {
            return organizationCode;
        }

        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return "OrganizationSummary{organizationId=" + organizationId
                    + ", organizationCode='" + organizationCode + "', name='" + name + "'}";
        }
    }

    public static class LibrarySummary {
        private final Long libraryId;
        private final String libraryCode;
        private final String name;
        private final String timezone;
        private final Integer seatCount;
        private final Integer seatsCreated;
        private final String seatRange;

        public LibrarySummary(Long libraryId, String libraryCode, String name, String timezone,
                              Integer seatCount, Integer seatsCreated, String seatRange) {
            this.libraryId = libraryId;
            this.libraryCode = libraryCode;
            this.name = name;
            this.timezone = timezone;
            this.seatCount = seatCount;
            this.seatsCreated = seatsCreated;
            this.seatRange = seatRange;
        }

        public Long getLibraryId() {
            return libraryId;
        }

        public String getLibraryCode() {
            return libraryCode;
        }

        public String getName() {
            return name;
        }

        public String getTimezone() {
            return timezone;
        }

        @Schema(description = "Configured total seat count for the new library.")
        public Integer getSeatCount() {
            return seatCount;
        }

        @Schema(description = "How many seat rows were created. Equals the seat count for a new library.")
        public Integer getSeatsCreated() {
            return seatsCreated;
        }

        @Schema(description = "Inclusive range of the generated seat numbers, e.g. \"1 - 100\".")
        public String getSeatRange() {
            return seatRange;
        }

        @Override
        public String toString() {
            return "LibrarySummary{libraryId=" + libraryId + ", libraryCode='" + libraryCode
                    + "', name='" + name + "', timezone='" + timezone
                    + "', seatCount=" + seatCount + ", seatsCreated=" + seatsCreated
                    + ", seatRange='" + seatRange + "'}";
        }
    }

    public static class UserSummary {
        private final Long userId;
        private final String username;
        private final String email;
        private final String roleCode;

        public UserSummary(Long userId, String username, String email, String roleCode) {
            this.userId = userId;
            this.username = username;
            this.email = email;
            this.roleCode = roleCode;
        }

        public Long getUserId() {
            return userId;
        }

        public String getUsername() {
            return username;
        }

        public String getEmail() {
            return email;
        }

        @Schema(description = "Role granted to the customer administrator. Never SUPER_ADMIN.")
        public String getRoleCode() {
            return roleCode;
        }

        @Override
        public String toString() {
            return "UserSummary{userId=" + userId + ", username='" + username
                    + "', email='" + email + "', roleCode='" + roleCode + "'}";
        }
    }

    /**
     * Shown to the product owner once, at creation, and never retrievable again.
     */
    @Schema(description = "Sensitive. Returned only by the creation call; no GET endpoint exposes it.")
    public static class InitialCredentials {
        private final String username;
        private final String temporaryPassword;

        public InitialCredentials(String username, String temporaryPassword) {
            this.username = username;
            this.temporaryPassword = temporaryPassword;
        }

        public String getUsername() {
            return username;
        }

        @Schema(description = "Plaintext initial password. Stored only as a BCrypt hash. "
                + "Shown once - it cannot be read back afterwards.")
        public String getTemporaryPassword() {
            return temporaryPassword;
        }

        @Override
        public String toString() {
            return "InitialCredentials{username='" + username + "', temporaryPassword=****}";
        }
    }
}
