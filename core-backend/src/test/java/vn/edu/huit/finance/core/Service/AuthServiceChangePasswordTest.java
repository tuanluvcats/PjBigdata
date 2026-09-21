package vn.edu.huit.finance.core.Service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.huit.finance.core.Dto.ChangePasswordRequestDto;
import vn.edu.huit.finance.core.Entity.User;
import vn.edu.huit.finance.core.Exception.InvalidPasswordException;
import vn.edu.huit.finance.core.Exception.UserNotFoundException;
import vn.edu.huit.finance.core.Repository.UserRepository;
import vn.edu.huit.finance.core.Security.CurrentUserProvider;

import java.lang.reflect.Method;
import java.util.List;

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
    // The access token that authorised the request; the service has to revoke it once the password has changed.
    private static final String ACCESS_TOKEN = "access.jwt.token";

    @Mock private UserRepository userRepository;
    @Mock private CurrentUserProvider currentUserProvider;
    @Mock private AuditLogService auditLogService;
    @Mock private TokenBlacklistService tokenBlacklistService;

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
        ReflectionTestUtils.setField(service, "tokenBlacklistService", tokenBlacklistService);

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

        service.changePassword(request(OLD_PASSWORD, NEW_PASSWORD), ACCESS_TOKEN);

        assertNotEquals(NEW_PASSWORD, user.getPassword());
        assertTrue(user.getPassword().startsWith("$2"), "expected a BCrypt hash");
        assertTrue(passwordEncoder.matches(NEW_PASSWORD, user.getPassword()));
        assertFalse(passwordEncoder.matches(OLD_PASSWORD, user.getPassword()));
        verify(userRepository).save(user);
        verify(auditLogService).logAction(eq(EMAIL), eq("CHANGE_PASSWORD"), anyString());
    }

    @Test
    void changePassword_revokesTheAccessTokenOfTheRequest_afterThePasswordIsSaved() {
        when(currentUserProvider.getCurrentUser()).thenReturn(user);

        service.changePassword(request(OLD_PASSWORD, NEW_PASSWORD), ACCESS_TOKEN);

        InOrder inOrder = inOrder(userRepository, tokenBlacklistService);
        inOrder.verify(userRepository).save(user);
        inOrder.verify(tokenBlacklistService).revoke(ACCESS_TOKEN);
    }

    @Test
    void changePassword_doesNotSwallowAFailedRevocation_soTheTransactionRollsTheNewPasswordBack() {
        when(currentUserProvider.getCurrentUser()).thenReturn(user);
        doThrow(new IllegalStateException("database went away")).when(tokenBlacklistService).revoke(ACCESS_TOKEN);

        assertThrows(IllegalStateException.class, () -> service.changePassword(request(OLD_PASSWORD, NEW_PASSWORD), ACCESS_TOKEN));

        // The failure reaches the transaction proxy, which rolls the saved password back with it (see TokenRevocationRollbackTest).
        verifyNoInteractions(auditLogService);
    }

    @Test
    void changePassword_andLogout_runInAWritableTransaction() throws NoSuchMethodException {
        List<Method> writers = List.of(
                AuthService.class.getMethod("changePassword", ChangePasswordRequestDto.class, String.class),
                AuthService.class.getMethod("logout", String.class, String.class));

        for (Method method : writers) {
            Transactional transactional = AnnotatedElementUtils.findMergedAnnotation(method, Transactional.class);
            assertNotNull(transactional, method.getName() + " must be @Transactional");
            assertFalse(transactional.readOnly(), method.getName() + " writes, so it cannot be read-only");
        }
    }

    @Test
    void changePassword_throwsInvalidPassword_andKeepsHash_whenOldPasswordIsWrong() {
        when(currentUserProvider.getCurrentUser()).thenReturn(user);
        String hashBefore = user.getPassword();

        assertThrows(InvalidPasswordException.class, () -> service.changePassword(request("wrong-old", NEW_PASSWORD), ACCESS_TOKEN));

        assertEquals(hashBefore, user.getPassword());
        verify(userRepository, never()).save(any());
        verifyNoInteractions(auditLogService, tokenBlacklistService);
    }

    @Test
    void changePassword_throwsInvalidPassword_whenNewPasswordEqualsOld() {
        when(currentUserProvider.getCurrentUser()).thenReturn(user);
        String hashBefore = user.getPassword();

        assertThrows(InvalidPasswordException.class, () -> service.changePassword(request(OLD_PASSWORD, OLD_PASSWORD), ACCESS_TOKEN));

        assertEquals(hashBefore, user.getPassword());
        verify(userRepository, never()).save(any());
        verifyNoInteractions(auditLogService, tokenBlacklistService);
    }

    @Test
    void changePassword_passesOnUserNotFound_andTouchesNothing_whenTheCallerCannotBeResolved() {
        when(currentUserProvider.getCurrentUser()).thenThrow(new UserNotFoundException("gone"));

        assertThrows(UserNotFoundException.class, () -> service.changePassword(request(OLD_PASSWORD, NEW_PASSWORD), ACCESS_TOKEN));

        verifyNoInteractions(userRepository, auditLogService, tokenBlacklistService);
    }
}
