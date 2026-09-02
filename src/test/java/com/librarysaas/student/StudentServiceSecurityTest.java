package com.librarysaas.student;

import com.librarysaas.library.entity.Library;
import com.librarysaas.library.repository.LibraryRepository;
import com.librarysaas.security.TenantAuthorizationService;
import com.librarysaas.security.repository.UserRepository;
import com.librarysaas.security.repository.UserTenantRepository;
import com.librarysaas.student.dto.StudentResponse;
import com.librarysaas.student.entity.Student;
import com.librarysaas.student.repository.StudentRepository;
import com.librarysaas.student.service.StudentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
public class StudentServiceSecurityTest {

    @Autowired
    private StudentService studentService;

    @MockBean
    private StudentRepository studentRepository;

    @MockBean
    private LibraryRepository libraryRepository;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private UserTenantRepository userTenantRepository;

    @Test
    @WithMockUser(username = "alice", authorities = {"STUDENT_VIEW"})
    public void userWithPermissionAndMembershipCanViewStudent() {
        // setup mocks: user -> id 10, membership exists, student exists in library 1
        when(userRepository.findByUsernameOrEmail("alice")).thenReturn(Optional.of(new com.librarysaas.security.model.User() {
            @Override public Long getUserId() { return 10L; }
        }));

        when(userTenantRepository.existsInLibrary(10L, 1L)).thenReturn(1);

        Library lib = new Library();
        lib.setLibraryId(1L);
        when(libraryRepository.findById(1L)).thenReturn(Optional.of(lib));

        Student s = new Student();
        s.setStudentId(5L);
        s.setLibrary(lib);
        when(studentRepository.findById(5L)).thenReturn(Optional.of(s));

        StudentResponse resp = studentService.getStudent(5L, 1L);
        assertNotNull(resp);
        assertEquals(5L, resp.getId());
    }

    @Test
    @WithMockUser(username = "alice", authorities = {"OTHER"})
    public void userWithoutPermissionIsDenied() {
        when(userRepository.findByUsernameOrEmail("alice")).thenReturn(Optional.of(new com.librarysaas.security.model.User() {
            @Override public Long getUserId() { return 10L; }
        }));
        when(userTenantRepository.existsInLibrary(10L, 1L)).thenReturn(1);

        // lack of STUDENT_VIEW should cause method security to deny access
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> {
            studentService.getStudent(5L, 1L);
        });
    }

    @Test
    @WithMockUser(username = "alice", authorities = {"STUDENT_VIEW"})
    public void userInWrongLibraryIsDenied() {
        when(userRepository.findByUsernameOrEmail("alice")).thenReturn(Optional.of(new com.librarysaas.security.model.User() {
            @Override public Long getUserId() { return 10L; }
        }));
        // user is not a member of library 2
        when(userTenantRepository.existsInLibrary(10L, 2L)).thenReturn(0);

        // ensure repository returns a student that belongs to library 2
        Library lib2 = new Library();
        lib2.setLibraryId(2L);
        Student s2 = new Student();
        s2.setStudentId(5L);
        s2.setLibrary(lib2);
        when(studentRepository.findById(5L)).thenReturn(Optional.of(s2));

        assertThrows(com.librarysaas.common.exception.ForbiddenException.class, () -> {
            studentService.getStudent(5L, 2L);
        });
    }

    @Test
    @WithMockUser(username = "alice", authorities = {"STUDENT_VIEW"})
    public void headerSpoofingCannotBypassMembership() {
        when(userRepository.findByUsernameOrEmail("alice")).thenReturn(Optional.of(new com.librarysaas.security.model.User() {
            @Override public Long getUserId() { return 10L; }
        }));
        // user is not a member of spoofed library 999
        when(userTenantRepository.existsInLibrary(10L, 999L)).thenReturn(0);

        // simulate TenantContext set by header
        com.librarysaas.security.TenantContext.setLibraryId(999L);

        // ensure repository returns a student that belongs to library 999 (so check fails)
        Library lib999 = new Library();
        lib999.setLibraryId(999L);
        Student s999 = new Student();
        s999.setStudentId(5L);
        s999.setLibrary(lib999);
        when(studentRepository.findById(5L)).thenReturn(Optional.of(s999));

        try {
            assertThrows(com.librarysaas.common.exception.ForbiddenException.class, () -> studentService.getStudent(5L, null));
        } finally {
            com.librarysaas.security.TenantContext.clear();
        }
    }

    @Test
    @WithMockUser(username = "alice", authorities = {"STUDENT_VIEW"})
    public void databaseFailureDuringMembershipCheckIsNotMaskedAsForbidden() {
        when(userRepository.findByUsernameOrEmail("alice")).thenReturn(Optional.of(new com.librarysaas.security.model.User() {
            @Override public Long getUserId() { return 10L; }
        }));

        Library lib = new Library();
        lib.setLibraryId(1L);
        Student s = new Student();
        s.setStudentId(5L);
        s.setLibrary(lib);
        when(studentRepository.findById(5L)).thenReturn(Optional.of(s));

        // The membership lookup itself fails for infrastructure reasons.
        when(userTenantRepository.existsInLibrary(10L, 1L))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("db down"));

        // Must surface as an unexpected error (-> INTERNAL_ERROR / 500), not as a 403.
        assertThrows(org.springframework.dao.DataAccessResourceFailureException.class,
                () -> studentService.getStudent(5L, 1L));
    }

    @Test
    @WithMockUser(username = "alice", authorities = {"STUDENT_VIEW"})
    public void missingStudentThrowsResourceNotFoundWithStudentNotFoundCode() {
        when(userRepository.findByUsernameOrEmail("alice")).thenReturn(Optional.of(new com.librarysaas.security.model.User() {
            @Override public Long getUserId() { return 10L; }
        }));
        when(userTenantRepository.existsInLibrary(10L, 1L)).thenReturn(1);
        when(studentRepository.findById(1001111L)).thenReturn(Optional.empty());

        var ex = assertThrows(com.librarysaas.common.exception.ResourceNotFoundException.class,
                () -> studentService.getStudent(1001111L, 1L));
        assertEquals("Student not found", ex.getMessage());
        assertEquals("STUDENT_NOT_FOUND", ex.getErrorCode());
    }
}
