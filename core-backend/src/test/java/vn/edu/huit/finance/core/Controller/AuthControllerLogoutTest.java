package vn.edu.huit.finance.core.Controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.edu.huit.finance.core.Config.SecurityConfig;
import vn.edu.huit.finance.core.Entity.User;
import vn.edu.huit.finance.core.Exception.GlobalExceptionHandler;
import vn.edu.huit.finance.core.Exception.InvalidTokenException;
import vn.edu.huit.finance.core.Repository.UserRepository;
import vn.edu.huit.finance.core.Security.AuthPaths;
import vn.edu.huit.finance.core.Security.CustomUserDetailsService;
import vn.edu.huit.finance.core.Security.JwtAuthFilter;
import vn.edu.huit.finance.core.Security.JwtUtil;
import vn.edu.huit.finance.core.Service.AuditLogService;
import vn.edu.huit.finance.core.Service.AuthService;
import vn.edu.huit.finance.core.Service.LoginAttemptService;
import vn.edu.huit.finance.core.Service.SocialAuthService;
import vn.edu.huit.finance.core.Service.TokenBlacklistService;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Token revocation through the real security filter chain + JwtAuthFilter + GlobalExceptionHandler: POST /api/auth/logout,
 * the 401 that a revoked token gets on every protected endpoint, and /api/auth/refresh refusing a revoked refresh token.
 * Explicit context classes keep the app class (@EnableJpaAuditing) out of the slice.
 */
@WebMvcTest(AuthController.class)
@ContextConfiguration(classes = {
        AuthController.class, GlobalExceptionHandler.class, SecurityConfig.class, JwtAuthFilter.class})
class AuthControllerLogoutTest {

    private static final String LOGOUT_URL = AuthPaths.LOGOUT_URL;
    private static final String CHANGE_PASSWORD_URL = AuthPaths.CHANGE_PASSWORD_URL;
    private static final String REFRESH_URL = AuthPaths.BASE + "/refresh";
    private static final String FORGOT_PASSWORD_URL = AuthPaths.BASE + "/forgot-password";
    private static final String OTHER_PROTECTED_URL = "/api/budgets";
    private static final String TOKEN = "valid.jwt.token";
    private static final String REVOKED_TOKEN = "revoked.jwt.token";
    private static final String BAD_TOKEN = "invalid.jwt.token";
    private static final String REFRESH_TOKEN = "valid.refresh.token";
    private static final String USERNAME = "alice";
    private static final String CHANGE_PASSWORD_BODY = "{\"oldPassword\":\"OldPass123\",\"newPassword\":\"NewPass456\"}";
    private static final String REVOKED_MESSAGE = "Phiên đăng nhập đã bị thu hồi. Vui lòng đăng nhập lại.";

    @Autowired private MockMvc mockMvc;

    @MockBean private AuthService authService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private CustomUserDetailsService userDetailsService;
    @MockBean private UserRepository userRepository;
    @MockBean private LoginAttemptService loginAttemptService;
    @MockBean private AuditLogService auditLogService;
    @MockBean private SocialAuthService socialAuthService;
    @MockBean private TokenBlacklistService tokenBlacklistService;

    @BeforeEach
    void stubJwtForAlice() {
        stubValidToken(TOKEN);
        // Signed and not expired, but on the blacklist.
        stubValidToken(REVOKED_TOKEN);
        when(tokenBlacklistService.isRevoked(REVOKED_TOKEN)).thenReturn(true);
        // Not valid (bad signature or expired): the blacklist is never asked about it.
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
    }

    private void stubValidToken(String token) {
        when(jwtUtil.extractUsername(token)).thenReturn(USERNAME);
        when(jwtUtil.validateToken(token)).thenReturn(true);
    }

    // MockMvc leaves getServletPath() empty, unlike a real container; JwtAuthFilter branches on it, so set it explicitly.
    private static MockHttpServletRequestBuilder logoutRequest() {
        return post(LOGOUT_URL).servletPath(LOGOUT_URL);
    }

    private static MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder builder, String token) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private static MockHttpServletRequestBuilder withJson(MockHttpServletRequestBuilder builder, String body) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static MockHttpServletRequestBuilder refreshRequest(String refreshToken) {
        return withJson(post(REFRESH_URL).servletPath(REFRESH_URL), "{\"refreshToken\":\"" + refreshToken + "\"}");
    }

    // ------------------------------------------------------------------------------------------------------- logout

    @Test
    void logout_returns200_andHandsTheBareAccessTokenOfTheRequestToTheService() throws Exception {
        mockMvc.perform(withToken(logoutRequest(), TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("Đăng xuất thành công!"));

        verify(authService).logout(TOKEN, null);
    }

    @Test
    void logout_alsoHandsOverTheRefreshTokenOfTheBody() throws Exception {
        mockMvc.perform(withToken(withJson(logoutRequest(), "{\"refreshToken\":\"" + REFRESH_TOKEN + "\"}"), TOKEN))
                .andExpect(status().isOk());

        verify(authService).logout(TOKEN, REFRESH_TOKEN);
    }

    @Test
    void logout_takesTheCallerFromTheJwt_notFromTheBody() throws Exception {
        AtomicReference<String> callerSeenByService = new AtomicReference<>();
        doAnswer(invocation -> {
            callerSeenByService.set(SecurityContextHolder.getContext().getAuthentication().getName());
            return null;
        }).when(authService).logout(any(), any());

        mockMvc.perform(withToken(withJson(logoutRequest(), "{\"userId\":999,\"refreshToken\":\"" + REFRESH_TOKEN + "\"}"), TOKEN))
                .andExpect(status().isOk());

        assertEquals(USERNAME, callerSeenByService.get());
    }

    @Test
    void logout_returns400_andSkipsTheService_whenTheRefreshTokenIsAbsurdlyLong() throws Exception {
        mockMvc.perform(withToken(withJson(logoutRequest(), "{\"refreshToken\":\"" + "a".repeat(2049) + "\"}"), TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        verifyNoInteractions(authService);
    }

    @Test
    void logout_needsAToken_andNeverReachesTheService() throws Exception {
        mockMvc.perform(logoutRequest())
                .andExpect(status().is4xxClientError());

        verifyNoInteractions(authService);
    }

    @Test
    void logout_rejectsAnInvalidToken_andNeverReachesTheService() throws Exception {
        mockMvc.perform(withToken(logoutRequest(), BAD_TOKEN))
                .andExpect(status().is4xxClientError());

        verifyNoInteractions(authService);
    }

    @Test
    void logout_mapsInvalidTokenException_to401() throws Exception {
        doThrow(new InvalidTokenException("Thiếu token xác thực")).when(authService).logout(any(), any());

        mockMvc.perform(withToken(logoutRequest(), TOKEN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("Thiếu token xác thực"));
    }

    // ------------------------------------------------------------------------------- a revoked token is answered with 401

    @Test
    void aRevokedToken_getsA401_onLogout_andNeverReachesTheService() throws Exception {
        mockMvc.perform(withToken(logoutRequest(), REVOKED_TOKEN))
                .andExpect(status().isUnauthorized())
                .andExpect(result -> assertEquals(REVOKED_MESSAGE, result.getResponse().getErrorMessage()));

        verifyNoInteractions(authService);
    }

    @Test
    void aRevokedToken_getsA401_onChangePassword_andNeverReachesTheService() throws Exception {
        mockMvc.perform(withToken(withJson(put(CHANGE_PASSWORD_URL).servletPath(CHANGE_PASSWORD_URL), CHANGE_PASSWORD_BODY),
                        REVOKED_TOKEN))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(authService);
    }

    @Test
    void aRevokedToken_getsA401_onEveryOtherProtectedEndpointToo() throws Exception {
        // Nothing is mapped to this URL in the slice: the filter answers before any controller is looked for.
        mockMvc.perform(withToken(get(OTHER_PROTECTED_URL).servletPath(OTHER_PROTECTED_URL), REVOKED_TOKEN))
                .andExpect(status().isUnauthorized());

        verify(userDetailsService, never()).loadApplicationUser(any());
    }

    @Test
    void aTokenThatIsNotRevoked_isNotAnswered401() throws Exception {
        mockMvc.perform(withToken(logoutRequest(), TOKEN))
                .andExpect(status().isOk());

        verify(tokenBlacklistService).isRevoked(TOKEN);
    }

    @Test
    void theBlacklistIsOnlyAskedAboutTokensThatAreValid_soAnInvalidTokenKeepsItsOldAnswer() throws Exception {
        mockMvc.perform(withToken(logoutRequest(), BAD_TOKEN))
                .andExpect(result -> assertNotEquals(401, result.getResponse().getStatus()));

        verify(tokenBlacklistService, never()).isRevoked(BAD_TOKEN);
    }

    @Test
    void thePublicAuthEndpointsNeverConsultTheBlacklist() throws Exception {
        mockMvc.perform(withToken(post(FORGOT_PASSWORD_URL).servletPath(FORGOT_PASSWORD_URL).param("email", "a@b.c"),
                        REVOKED_TOKEN))
                .andExpect(status().isOk());

        verify(tokenBlacklistService, never()).isRevoked(any());
    }

    // ------------------------------------------------------------------------------------------------------- refresh

    @Test
    void refresh_issuesNewTokens_forARefreshTokenThatWasNotRevoked() throws Exception {
        when(jwtUtil.validateToken(REFRESH_TOKEN)).thenReturn(true);
        when(jwtUtil.extractUsername(REFRESH_TOKEN)).thenReturn(USERNAME);
        when(jwtUtil.generateToken(USERNAME)).thenReturn("new.access.token");
        when(jwtUtil.generateRefreshToken(USERNAME)).thenReturn("new.refresh.token");

        mockMvc.perform(refreshRequest(REFRESH_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").value("new.access.token"))
                .andExpect(jsonPath("$.data.refreshToken").value("new.refresh.token"));

        verify(tokenBlacklistService).isRevoked(REFRESH_TOKEN);
    }

    @Test
    void refresh_returns401_forARefreshTokenThatWasRevokedAtLogout_andIssuesNothing() throws Exception {
        when(jwtUtil.validateToken(REFRESH_TOKEN)).thenReturn(true);
        when(tokenBlacklistService.isRevoked(REFRESH_TOKEN)).thenReturn(true);

        mockMvc.perform(refreshRequest(REFRESH_TOKEN))
                .andExpect(status().isUnauthorized());

        verify(jwtUtil, never()).generateToken(any());
        verify(jwtUtil, never()).generateRefreshToken(any());
    }

    @Test
    void refresh_returns401_forAnInvalidRefreshToken_withoutAskingTheBlacklist() throws Exception {
        when(jwtUtil.validateToken(REFRESH_TOKEN)).thenReturn(false);

        mockMvc.perform(refreshRequest(REFRESH_TOKEN))
                .andExpect(status().isUnauthorized());

        verify(tokenBlacklistService, never()).isRevoked(any());
    }
}
