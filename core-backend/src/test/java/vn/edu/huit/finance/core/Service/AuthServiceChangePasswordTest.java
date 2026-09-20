package vn.edu.huit.finance.core.Service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import vn.edu.huit.finance.core.Dto.ChangePasswordRequestDto;
import vn.edu.huit.finance.core.Entity.User;
import vn.edu.huit.finance.core.Exception.InvalidPasswordException;
import vn.edu.huit.finance.core.Exception.UserNotFoundException;
import vn.edu.huit.finance.core.Repository.UserRepository;
import vn.edu.huit.finance.core.Security.CurrentUserProvider;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceChangePasswordTest {

    private static final String EMAIL = "alice@example.com";
    private static final String OLD_PASSWORD = "OldPass123";
    private static final String NEW_PASSWORD = "NewPass456";

    @Mock private UserRepository userRepository;
    @Mock private CurrentUserProvider currentUserProvider;
    @Mock private AuditLogService auditLogService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private AuthService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new AuthService();
        ReflectionTestUtils.setField(service, "userRepository", userRepository);
        ReflectionTestUtils.setField(service, "passwordEncoder", passwordEncoder);
        ReflectionTestUtils.setField(service, "currentUserProvider", currentUserProvider);
        ReflectionTestUtils.setField(service, "auditLogService", auditLogService);

        user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setEmail(EMAIL);
        user.setPassword(passwordEncoder.encode(OLD_PASSWORD));
    }

    private ChangePasswordRequestDto request(String oldPassword, String newPassword) {
        ChangePasswordRequestDto dto = new ChangePasswordRequestDto();
        dto.setOldPassword(oldPassword);
        dto.setNewPassword(newPassword);
        return dto;
    }

    @Test
    void changePassword_storesBcryptHashOfNewPasswordAndAudits_whenOldPasswordMatches() {
        when(currentUserProvider.getCurrentUser()).thenReturn(user);

        service.changePassword(request(OLD_PASSWORD, NEW_PASSWORD));

        assertNotEquals(NEW_PASSWORD, user.getPassword());
        assertTrue(user.getPassword().startsWith("$2"), "expected a BCrypt hash");
        assertTrue(passwordEncoder.matches(NEW_PASSWORD, user.getPassword()));
        assertFalse(passwordEncoder.matches(OLD_PASSWORD, user.getPassword()));
        verify(userRepository).save(user);
        verify(auditLogService).logAction(eq(EMAIL), eq("CHANGE_PASSWORD"), anyString());
    }

    @Test
    void changePassword_throwsInvalidPassword_andKeepsHash_whenOldPasswordIsWrong() {
        when(currentUserProvider.getCurrentUser()).thenReturn(user);
        String hashBefore = user.getPassword();

        assertThrows(InvalidPasswordException.class, () -> service.changePassword(request("wrong-old", NEW_PASSWORD)));

        assertEquals(hashBefore, user.getPassword());
        verify(userRepository, never()).save(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void changePassword_throwsInvalidPassword_whenNewPasswordEqualsOld() {
        when(currentUserProvider.getCurrentUser()).thenReturn(user);
        String hashBefore = user.getPassword();

        assertThrows(InvalidPasswordException.class, () -> service.changePassword(request(OLD_PASSWORD, OLD_PASSWORD)));

        assertEquals(hashBefore, user.getPassword());
        verify(userRepository, never()).save(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void changePassword_passesOnUserNotFound_andTouchesNothing_whenTheCallerCannotBeResolved() {
        when(currentUserProvider.getCurrentUser()).thenThrow(new UserNotFoundException("gone"));

        assertThrows(UserNotFoundException.class, () -> service.changePassword(request(OLD_PASSWORD, NEW_PASSWORD)));

        verifyNoInteractions(userRepository, auditLogService);
    }
}
