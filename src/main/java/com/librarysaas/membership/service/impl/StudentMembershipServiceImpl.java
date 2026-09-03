package com.librarysaas.membership.service.impl;

import com.librarysaas.common.exception.BusinessException;
import com.librarysaas.common.exception.ConflictException;
import com.librarysaas.common.exception.ForbiddenException;
import com.librarysaas.common.exception.ResourceNotFoundException;
import com.librarysaas.library.entity.Library;
import com.librarysaas.library.repository.LibraryRepository;
import com.librarysaas.membership.dto.StudentMembershipRequest;
import com.librarysaas.membership.dto.StudentMembershipResponse;
import com.librarysaas.membership.dto.StudentMembershipUpdateRequest;
import com.librarysaas.membership.entity.StudentMembership;
import com.librarysaas.membership.repository.StudentMembershipRepository;
import com.librarysaas.membership.service.StudentMembershipService;
import com.librarysaas.security.TenantAuthorizationService;
import com.librarysaas.student.entity.Student;
import com.librarysaas.student.repository.StudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Student membership rules.
 *
 * Two properties are load bearing and are the reason the checks are ordered the
 * way they are:
 *
 * <ul>
 *   <li>A membership never crosses a tenant. The library comes from the path or
 *       from the membership's own row, never from a header or a request body,
 *       and the student is matched against that library by a plain repository
 *       query. That query does not consult the caller's authorities, so a super
 *       admin cannot create a membership the rule would otherwise forbid. This
 *       is the mistake corrected in Phase 2C and it is not repeated here.</li>
 *   <li>Rows are never deleted. Ending a membership is a status change, so
 *       history survives and student_fee.membership_id keeps pointing at a real
 *       row once finance exists.</li>
 * </ul>
 */
@Service
public class StudentMembershipServiceImpl implements StudentMembershipService {

    static final String STATUS_ACTIVE = "ACTIVE";
    static final String STATUS_EXPIRED = "EXPIRED";
    static final String STATUS_CANCELLED = "CANCELLED";

    /**
     * The closed set of membership statuses. Nothing in the schema constrains
     * this column, so the service is the only thing enforcing it.
     */
    private static final Set<String> ALLOWED_STATUSES =
            Set.of(STATUS_ACTIVE, STATUS_EXPIRED, STATUS_CANCELLED);

    /** No membership can carry this id, so it excludes nothing from a search. */
    private static final long NO_EXCLUSION = -1L;

    private final StudentMembershipRepository membershipRepository;
    private final StudentRepository studentRepository;
    private final LibraryRepository libraryRepository;
    private final TenantAuthorizationService tenantAuthorizationService;

    @Autowired
    public StudentMembershipServiceImpl(StudentMembershipRepository membershipRepository,
                                        StudentRepository studentRepository,
                                        LibraryRepository libraryRepository,
                                        TenantAuthorizationService tenantAuthorizationService) {
        this.membershipRepository = membershipRepository;
        this.studentRepository = studentRepository;
        this.libraryRepository = libraryRepository;
        this.tenantAuthorizationService = tenantAuthorizationService;
    }

    /* ---------------------------------------------------------------- reads */

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('STUDENT_VIEW')")
    public List<StudentMembershipResponse> getLibraryMemberships(Long libraryId, String status) {
        requireLibrary(libraryId);

        List<StudentMembership> memberships = status == null || status.isBlank()
                ? membershipRepository.findAllByLibraryIdWithStudent(libraryId)
                : membershipRepository.findAllByLibraryIdAndStatusWithStudent(
                        libraryId, normaliseStatus(status));

        return memberships.stream().map(StudentMembershipResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('STUDENT_VIEW')")
    public List<StudentMembershipResponse> getStudentMemberships(Long studentId) {
        // The student resolves first so an unknown id is 404 rather than 403.
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found", "STUDENT_NOT_FOUND"));

        requireLibraryAccess(libraryIdOf(student));

        return membershipRepository.findAllByStudentIdWithStudent(studentId).stream()
                .map(StudentMembershipResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('STUDENT_VIEW')")
    public StudentMembershipResponse getMembership(Long membershipId) {
        return StudentMembershipResponse.from(requireMembership(membershipId));
    }

    /* --------------------------------------------------------------- writes */

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('STUDENT_UPDATE')")
    public StudentMembershipResponse createMembership(Long libraryId, StudentMembershipRequest request) {
        Library library = requireLibrary(libraryId);
        Student student = requireStudentInLibrary(request.getStudentId(), libraryId);

        LocalDate startDate = request.getStartDate();
        LocalDate endDate = request.getEndDate();
        requireValidPeriod(startDate, endDate);

        String number = request.getMembershipNumber().trim();
        if (membershipRepository.existsByLibraryLibraryIdAndMembershipNumber(libraryId, number)) {
            throw new ConflictException(
                    "Membership number " + number + " is already used in this library",
                    "MEMBERSHIP_NUMBER_ALREADY_EXISTS");
        }

        requireNoActiveOverlap(student.getStudentId(), startDate, endDate, NO_EXCLUSION);

        LocalDateTime now = LocalDateTime.now();
        Long actor = currentUserId();

        StudentMembership membership = new StudentMembership();
        membership.setLibrary(library);
        membership.setStudent(student);
        membership.setMembershipNumber(number);
        membership.setStartDate(startDate);
        membership.setEndDate(endDate);
        membership.setStatus(STATUS_ACTIVE);
        membership.setAutoRenew(Boolean.TRUE.equals(request.getAutoRenew()));
        membership.setCreatedAt(now);
        membership.setCreatedBy(actor);
        membership.setUpdatedAt(now);
        membership.setUpdatedBy(actor);

        return StudentMembershipResponse.from(membershipRepository.saveAndFlush(membership));
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('STUDENT_UPDATE')")
    public StudentMembershipResponse updateMembership(Long membershipId,
                                                      StudentMembershipUpdateRequest request) {
        StudentMembership membership = requireMembership(membershipId);

        LocalDate startDate = request.getStartDate();
        LocalDate endDate = request.getEndDate();
        requireValidPeriod(startDate, endDate);

        String number = request.getMembershipNumber().trim();
        Long libraryId = membership.getLibrary().getLibraryId();
        if (membershipRepository.existsByLibraryAndNumberExcluding(libraryId, number, membershipId)) {
            throw new ConflictException(
                    "Membership number " + number + " is already used in this library",
                    "MEMBERSHIP_NUMBER_ALREADY_EXISTS");
        }

        // Only an active membership occupies a period, so only then can moving
        // the dates collide with another one.
        if (STATUS_ACTIVE.equals(membership.getStatus())) {
            requireNoActiveOverlap(membership.getStudent().getStudentId(), startDate, endDate, membershipId);
        }

        membership.setMembershipNumber(number);
        membership.setStartDate(startDate);
        membership.setEndDate(endDate);
        membership.setAutoRenew(Boolean.TRUE.equals(request.getAutoRenew()));
        membership.setUpdatedAt(LocalDateTime.now());
        membership.setUpdatedBy(currentUserId());

        return StudentMembershipResponse.from(membershipRepository.saveAndFlush(membership));
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('STUDENT_UPDATE')")
    public StudentMembershipResponse updateStatus(Long membershipId, String status) {
        StudentMembership membership = requireMembership(membershipId);
        String newStatus = normaliseStatus(status);

        // Setting the status a membership already holds changes nothing, so it
        // is accepted as a no-op rather than treated as an error.
        if (newStatus.equals(membership.getStatus())) {
            return StudentMembershipResponse.from(membership);
        }

        // Reactivating has to respect the one-active-membership rule, otherwise
        // status would be a way around the overlap check.
        if (STATUS_ACTIVE.equals(newStatus)) {
            requireNoActiveOverlap(membership.getStudent().getStudentId(),
                    membership.getStartDate(), membership.getEndDate(), membershipId);
        }

        membership.setStatus(newStatus);
        membership.setUpdatedAt(LocalDateTime.now());
        membership.setUpdatedBy(currentUserId());

        return StudentMembershipResponse.from(membershipRepository.saveAndFlush(membership));
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('STUDENT_UPDATE')")
    public StudentMembershipResponse renewMembership(Long membershipId,
                                                     StudentMembershipUpdateRequest request) {
        StudentMembership previous = requireMembership(membershipId);

        LocalDate startDate = request.getStartDate();
        LocalDate endDate = request.getEndDate();
        requireValidPeriod(startDate, endDate);

        Library library = previous.getLibrary();
        Student student = previous.getStudent();
        String number = request.getMembershipNumber().trim();

        if (membershipRepository.existsByLibraryLibraryIdAndMembershipNumber(
                library.getLibraryId(), number)) {
            throw new ConflictException(
                    "Membership number " + number + " is already used in this library",
                    "MEMBERSHIP_NUMBER_ALREADY_EXISTS");
        }

        // The membership being renewed is about to be closed, so it is not an
        // obstacle to its own successor. Any other active membership still is.
        requireNoActiveOverlap(student.getStudentId(), startDate, endDate, membershipId);

        LocalDateTime now = LocalDateTime.now();
        Long actor = currentUserId();

        // History is closed, never rewritten: the old period keeps its dates.
        previous.setStatus(STATUS_EXPIRED);
        previous.setUpdatedAt(now);
        previous.setUpdatedBy(actor);
        membershipRepository.saveAndFlush(previous);

        StudentMembership successor = new StudentMembership();
        successor.setLibrary(library);
        successor.setStudent(student);
        successor.setMembershipNumber(number);
        successor.setStartDate(startDate);
        successor.setEndDate(endDate);
        successor.setStatus(STATUS_ACTIVE);
        successor.setAutoRenew(Boolean.TRUE.equals(request.getAutoRenew()));
        successor.setCreatedAt(now);
        successor.setCreatedBy(actor);
        successor.setUpdatedAt(now);
        successor.setUpdatedBy(actor);

        return StudentMembershipResponse.from(membershipRepository.saveAndFlush(successor));
    }

    /* ------------------------------------------------------------ internals */

    /**
     * Resolves the library and confirms the caller belongs to it. The library is
     * looked up first so an unknown id is 404 rather than 403.
     */
    private Library requireLibrary(Long libraryId) {
        Library library = libraryRepository.findById(libraryId)
                .orElseThrow(() -> new ResourceNotFoundException("Library not found", "LIBRARY_NOT_FOUND"));

        requireLibraryAccess(libraryId);
        return library;
    }

    /**
     * Resolves a membership and authorises against the library on its own row,
     * so a membership id from another tenant is refused rather than served.
     */
    private StudentMembership requireMembership(Long membershipId) {
        StudentMembership membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Membership not found", "STUDENT_MEMBERSHIP_NOT_FOUND"));

        requireLibraryAccess(libraryIdOf(membership));
        return membership;
    }

    /**
     * The tenant boundary for this module.
     *
     * A membership may only ever join a student to the library that student
     * already belongs to. The library comparison is a plain field comparison on
     * data the caller has no influence over, so it holds for every role,
     * including a super admin whose tenant access is unrestricted.
     */
    private Student requireStudentInLibrary(Long studentId, Long libraryId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found", "STUDENT_NOT_FOUND"));

        Long studentLibraryId = student.getLibrary() == null
                ? null
                : student.getLibrary().getLibraryId();

        if (studentLibraryId == null || !studentLibraryId.equals(libraryId)) {
            throw new BusinessException("Student does not belong to this library",
                    "STUDENT_NOT_IN_LIBRARY");
        }
        return student;
    }

    /** The period is a closed range, so it must cover at least one day. */
    private void requireValidPeriod(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || !endDate.isAfter(startDate)) {
            throw new BusinessException("End date must be after start date",
                    "INVALID_MEMBERSHIP_PERIOD");
        }
    }

    /**
     * A student holds at most one active membership at a time, so a new or
     * moved period must not overlap another active one.
     */
    private void requireNoActiveOverlap(Long studentId, LocalDate startDate, LocalDate endDate,
                                        Long excludedMembershipId) {
        List<StudentMembership> clashes = membershipRepository.findActiveOverlapping(
                studentId, startDate, endDate, excludedMembershipId);

        if (!clashes.isEmpty()) {
            StudentMembership clash = clashes.get(0);
            throw new ConflictException(
                    "This student already has an active membership from " + clash.getStartDate()
                            + " to " + clash.getEndDate(),
                    "STUDENT_MEMBERSHIP_OVERLAP");
        }
    }

    /** Membership statuses are a closed set; anything else is a business error. */
    private String normaliseStatus(String requested) {
        String status = requested == null ? "" : requested.trim().toUpperCase();
        if (!ALLOWED_STATUSES.contains(status)) {
            throw new BusinessException(
                    "Invalid membership status: " + requested + ". Allowed: "
                            + String.join(", ", ALLOWED_STATUSES),
                    "INVALID_MEMBERSHIP_STATUS");
        }
        return status;
    }

    private Long libraryIdOf(Student student) {
        Long libraryId = student.getLibrary() == null ? null : student.getLibrary().getLibraryId();
        if (libraryId == null) {
            // A student with no library cannot be tenant-checked, so it must not
            // be readable rather than defaulting open.
            throw new ResourceNotFoundException("Student not found", "STUDENT_NOT_FOUND");
        }
        return libraryId;
    }

    private Long libraryIdOf(StudentMembership membership) {
        Long libraryId = membership.getLibrary() == null
                ? null
                : membership.getLibrary().getLibraryId();
        if (libraryId == null) {
            throw new ResourceNotFoundException("Membership not found", "STUDENT_MEMBERSHIP_NOT_FOUND");
        }
        return libraryId;
    }

    private Long currentUserId() {
        return tenantAuthorizationService.getCurrentUserId().orElse(null);
    }

    /**
     * Converts only an authorisation denial into ForbiddenException. Anything
     * else propagates so infrastructure failures still surface as INTERNAL_ERROR
     * rather than being masked as a permission problem.
     */
    private void requireLibraryAccess(Long libraryId) {
        try {
            tenantAuthorizationService.requireLibraryAccess(currentUserId(), libraryId);
        } catch (AccessDeniedException e) {
            throw new ForbiddenException("You do not have permission to perform this operation");
        }
    }
}
