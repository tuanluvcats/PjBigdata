package vn.edu.huit.finance.core.Controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.edu.huit.finance.core.Config.SecurityConfig;
import vn.edu.huit.finance.core.Entity.User;
import vn.edu.huit.finance.core.Exception.GlobalExceptionHandler;
import vn.edu.huit.finance.core.Exception.InvalidOtpException;
import vn.edu.huit.finance.core.Exception.InvalidPasswordException;
import vn.edu.huit.finance.core.Exception.UserNotFoundException;
import vn.edu.huit.finance.core.Repository.UserRepository;
import vn.edu.huit.finance.core.Security.AuthPaths;
import vn.edu.huit.finance.core.Security.CustomUserDetailsService;
import vn.edu.huit.finance.core.Security.JwtAuthFilter;
import vn.edu.huit.finance.core.Security.JwtUtil;
import vn.edu.huit.finance.core.Service.AuditLogService;
import vn.edu.huit.finance.core.Service.AuthService;
import vn.edu.huit.finance.core.Service.LoginAttemptService;
import vn.edu.huit.finance.core.Service.SocialAuthService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the real security filter chain + JwtAuthFilter + GlobalExceptionHandler around the endpoints of
 * AuthController that take a password: PUT /api/auth/change-password, POST /api/auth/reset-password and
 * POST /api/auth/register. Explicit context classes keep the app class (@EnableJpaAuditing) out of the slice.
 */
@WebMvcTest(AuthController.class)
@ContextConfiguration(classes = {
        AuthController.class, GlobalExceptionHandler.class, SecurityConfig.class, JwtAuthFilter.class})
class AuthControllerPasswordTest {

    private static final String URL = AuthPaths.CHANGE_PASSWORD_URL;
    private static final String FORGOT_PASSWORD_URL = AuthPaths.BASE + "/forgot-password";
    private static final String RESET_PASSWORD_URL = AuthPaths.BASE + "/reset-password";
    private static final String REGISTER_URL = AuthPaths.BASE + "/register";
    private static final String TOKEN = "valid.jwt.token";
    private static final String BAD_TOKEN = "invalid.jwt.token";
    private static final String LOCKED_TOKEN = "locked-user.jwt.token";
    private static final String USERNAME = "alice";
    private static final String LOCKED_USERNAME = "bob";
    private static final String EMAIL = "alice@example.com";
    private static final String OTP = "123456";
    private static final String STRONG_PASSWORD = "NewPass456";
    private static final String VALIDATION_MESSAGE_PREFIX = "Dữ liệu không hợp lệ";
    private static final String NEW_USER_EMAIL = "newbie@example.com";
    private static final String NEW_USER_PHONE = "0901234567";
    // userId in the payload must be ignored: the caller is always taken from the JWT.
    private static final String BODY = "{\"userId\":999,\"oldPassword\":\"OldPass123\",\"newPassword\":\"" + STRONG_PASSWORD + "\"}";

    @Autowired private MockMvc mockMvc;
    @Autowired private PasswordEncoder passwordEncoder;

    @MockBean private AuthService authService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private CustomUserDetailsService userDetailsService;
    @MockBean private UserRepository userRepository;
    @MockBean private LoginAttemptService loginAttemptService;
    @MockBean private AuditLogService auditLogService;
    @MockBean private SocialAuthService socialAuthService;

    @BeforeEach
    void stubJwtForAlice() {
        when(jwtUtil.extractUsername(TOKEN)).thenReturn(USERNAME);
        when(jwtUtil.validateToken(TOKEN)).thenReturn(true);
        when(jwtUtil.extractUsername(BAD_TOKEN)).thenReturn(USERNAME);
        when(jwtUtil.validateToken(BAD_TOKEN)).thenReturn(false);

        User alice = new User();
        alice.setUsername(USERNAME);
        alice.setActive(true);
        alice.setRole("ROLE_USER");
        alice.setLastActionTime(LocalDateTime.now());
        when(userDetailsService.loadApplicationUser(USERNAME)).thenReturn(alice);
        when(userDetailsService.loadUserByUsername(USERNAME)).thenReturn(
                new org.springframework.security.core.userdetails.User(
                        USERNAME, "hash", AuthorityUtils.createAuthorityList("ROLE_USER")));

        // A locked user's token: JwtAuthFilter answers 403 if (and only if) it actually parses it.
        User bob = new User();
        bob.setUsername(LOCKED_USERNAME);
        bob.setActive(false);
        bob.setRole("ROLE_USER");
        when(jwtUtil.extractUsername(LOCKED_TOKEN)).thenReturn(LOCKED_USERNAME);
        when(jwtUtil.validateToken(LOCKED_TOKEN)).thenReturn(true);
        when(userDetailsService.loadApplicationUser(LOCKED_USERNAME)).thenReturn(bob);
    }

    // Passwords that each break one rule of the policy; the rules themselves are covered by StrongPasswordTest.
    static Stream<String> weakPasswords() {
        return Stream.of(
                "Short1a",                  // 7 characters
                "onlyletters",              // no digit
                "12345678",                 // no letter
                "a1" + "b".repeat(71));     // 73 characters: one more than BCrypt reads
    }

    // MockMvc leaves getServletPath() empty, unlike a real container; JwtAuthFilter branches on it, so set it explicitly.
    private MockHttpServletRequestBuilder changePasswordRequest(String body) {
        return put(URL).servletPath(URL).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private MockHttpServletRequestBuilder authorizedPut(String body) {
        return changePasswordRequest(body).header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN);
    }

    private MockHttpServletRequestBuilder resetPasswordRequest(String newPassword) {
        return post(RESET_PASSWORD_URL).servletPath(RESET_PASSWORD_URL).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + EMAIL + "\",\"otp\":\"" + OTP + "\",\"newPassword\":\"" + newPassword + "\"}");
    }

    // The fields the mobile app sends today (RemoteApiHelper.register), including fullName, which the server ignores.
    private static String registerBody(String password, String phone) {
        return "{\"username\":\"" + NEW_USER_EMAIL + "\",\"email\":\"" + NEW_USER_EMAIL + "\",\"password\":\"" + password
                + "\",\"fullName\":\"Newbie\",\"phone\":\"" + phone + "\"}";
    }

    private MockHttpServletRequestBuilder registerRequest(String body) {
        return post(REGISTER_URL).servletPath(REGISTER_URL).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void returns200_andServiceRunsAsTheJwtUser_ignoringUserIdInPayload() throws Exception {
        AtomicReference<String> callerSeenByService = new AtomicReference<>();
        doAnswer(invocation -> {
            callerSeenByService.set(SecurityContextHolder.getContext().getAuthentication().getName());
            return null;
        }).when(authService).changePassword(any());

        mockMvc.perform(authorizedPut(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("Đổi mật khẩu thành công!"));

        assertEquals(USERNAME, callerSeenByService.get());
    }

    @Test
    void rejectsRequestWithoutToken_andNeverReachesTheService() throws Exception {
        mockMvc.perform(changePasswordRequest(BODY))
                .andExpect(status().is4xxClientError());

        verifyNoInteractions(authService);
    }

    @Test
    void rejectsRequestWithInvalidToken_andNeverReachesTheService() throws Exception {
        mockMvc.perform(changePasswordRequest(BODY).header(HttpHeaders.AUTHORIZATION, "Bearer " + BAD_TOKEN))
                .andExpect(status().is4xxClientError());

        verifyNoInteractions(authService);
    }

    @Test
    void otherAuthEndpointsStayPublic() throws Exception {
        mockMvc.perform(post(FORGOT_PASSWORD_URL).servletPath(FORGOT_PASSWORD_URL).param("email", "a@b.c"))
                .andExpect(status().isOk());

        verify(authService).generateAndSendOtp("a@b.c");
    }

    @Test
    void otherAuthEndpointsStillSkipJwtParsing() throws Exception {
        mockMvc.perform(post(FORGOT_PASSWORD_URL).servletPath(FORGOT_PASSWORD_URL).param("email", "a@b.c")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + LOCKED_TOKEN))
                .andExpect(status().isOk());

        verify(authService).generateAndSendOtp("a@b.c");
    }

    @Test
    void returns400WithValidationMessage_whenBodyIsInvalid() throws Exception {
        mockMvc.perform(authorizedPut("{\"oldPassword\":\"\",\"newPassword\":\"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        verifyNoInteractions(authService);
    }

    @ParameterizedTest
    @MethodSource("weakPasswords")
    void changePassword_returns400_andSkipsTheService_whenNewPasswordIsWeak(String weakPassword) throws Exception {
        mockMvc.perform(authorizedPut("{\"oldPassword\":\"OldPass123\",\"newPassword\":\"" + weakPassword + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(startsWith(VALIDATION_MESSAGE_PREFIX)));

        verifyNoInteractions(authService);
    }

    @Test
    void mapsInvalidPasswordException_to400() throws Exception {
        doThrow(new InvalidPasswordException("Mật khẩu cũ không chính xác")).when(authService).changePassword(any());

        mockMvc.perform(authorizedPut(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Mật khẩu cũ không chính xác"));
    }

    @Test
    void mapsUserNotFoundException_to404() throws Exception {
        doThrow(new UserNotFoundException("Không tìm thấy người dùng đang đăng nhập")).when(authService).changePassword(any());

        mockMvc.perform(authorizedPut(BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("Không tìm thấy người dùng đang đăng nhập"));
    }

    @Test
    void resetPassword_returns200_withoutAToken_andHandsTheJsonFieldsToTheService() throws Exception {
        mockMvc.perform(resetPasswordRequest(STRONG_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("Đổi mật khẩu thành công! Vui lòng đăng nhập lại."));

        verify(authService).verifyOtpAndResetPassword(EMAIL, OTP, STRONG_PASSWORD);
    }

    @ParameterizedTest
    @MethodSource("weakPasswords")
    void resetPassword_returns400_andSkipsTheService_whenNewPasswordIsWeak(String weakPassword) throws Exception {
        mockMvc.perform(resetPasswordRequest(weakPassword))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(startsWith(VALIDATION_MESSAGE_PREFIX)));

        verifyNoInteractions(authService);
    }

    @Test
    void resetPassword_returns400_andSkipsTheService_whenEmailAndOtpAreMissing() throws Exception {
        mockMvc.perform(post(RESET_PASSWORD_URL).servletPath(RESET_PASSWORD_URL)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\":\"" + STRONG_PASSWORD + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(startsWith(VALIDATION_MESSAGE_PREFIX)));

        verifyNoInteractions(authService);
    }

    @Test
    void resetPassword_ignoresQueryParameters_soNothingSecretTravelsInTheUrl() throws Exception {
        // What an old client would send: everything in the query string and nothing in the body.
        mockMvc.perform(post(RESET_PASSWORD_URL).servletPath(RESET_PASSWORD_URL).contentType(MediaType.APPLICATION_JSON)
                        .param("email", EMAIL).param("otp", OTP).param("newPassword", STRONG_PASSWORD))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    @Test
    void resetPassword_mapsInvalidOtpException_to400WithItsMessage() throws Exception {
        String message = "Mã OTP không chính xác. Bạn còn 2 lần thử.";
        doThrow(new InvalidOtpException(message)).when(authService).verifyOtpAndResetPassword(any(), any(), any());

        mockMvc.perform(resetPasswordRequest(STRONG_PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(message));
    }

    @Test
    void register_savesAHashedPassword_andIgnoresFieldsTheClientMustNotControl() throws Exception {
        String bodyWithForbiddenFields = registerBody(STRONG_PASSWORD, NEW_USER_PHONE)
                .replace("}", ",\"role\":\"ROLE_ADMIN\",\"walletBalance\":999999999,\"active\":false}");

        mockMvc.perform(registerRequest(bodyWithForbiddenFields))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("Đăng ký thành công!"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        User user = saved.getValue();
        assertEquals(NEW_USER_EMAIL, user.getEmail());
        assertEquals(NEW_USER_EMAIL, user.getUsername());
        assertEquals(NEW_USER_PHONE, user.getPhone());
        assertNotEquals(STRONG_PASSWORD, user.getPassword());
        assertTrue(passwordEncoder.matches(STRONG_PASSWORD, user.getPassword()));
        assertEquals("ROLE_USER", user.getRole());
        assertEquals(0, BigDecimal.ZERO.compareTo(user.getWalletBalance()));
        assertTrue(user.isActive());
    }

    @ParameterizedTest
    @MethodSource("weakPasswords")
    void register_returns400_andSavesNothing_whenThePasswordIsWeak(String weakPassword) throws Exception {
        mockMvc.perform(registerRequest(registerBody(weakPassword, NEW_USER_PHONE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(startsWith(VALIDATION_MESSAGE_PREFIX)));

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_returns400WithTheUsualMessage_whenTheEmailIsAlreadyRegistered() throws Exception {
        when(userRepository.findByEmail(NEW_USER_EMAIL)).thenReturn(Optional.of(new User()));

        mockMvc.perform(registerRequest(registerBody(STRONG_PASSWORD, NEW_USER_PHONE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Email đã được đăng ký. Vui lòng dùng email khác."));

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_returns400WithTheUsualMessage_whenTheUsernameIsTaken() throws Exception {
        when(userRepository.findByUsername(NEW_USER_EMAIL)).thenReturn(Optional.of(new User()));

        mockMvc.perform(registerRequest(registerBody(STRONG_PASSWORD, NEW_USER_PHONE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Tên đăng nhập đã tồn tại."));

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_returns400_whenThePhoneIsNotTenDigits() throws Exception {
        mockMvc.perform(registerRequest(registerBody(STRONG_PASSWORD, "090123456")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("10 chữ số")));

        verify(userRepository, never()).save(any());
    }
}
