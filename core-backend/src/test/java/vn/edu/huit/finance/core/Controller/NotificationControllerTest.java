package vn.edu.huit.finance.core.Controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.edu.huit.finance.core.Config.SecurityConfig;
import vn.edu.huit.finance.core.Dto.NotificationResponseDto;
import vn.edu.huit.finance.core.Entity.NotificationType;
import vn.edu.huit.finance.core.Entity.User;
import vn.edu.huit.finance.core.Exception.GlobalExceptionHandler;
import vn.edu.huit.finance.core.Exception.UserNotFoundException;
import vn.edu.huit.finance.core.Repository.UserRepository;
import vn.edu.huit.finance.core.Security.CustomUserDetailsService;
import vn.edu.huit.finance.core.Security.JwtAuthFilter;
import vn.edu.huit.finance.core.Security.JwtUtil;
import vn.edu.huit.finance.core.Service.LoginAttemptService;
import vn.edu.huit.finance.core.Service.NotificationService;
import vn.edu.huit.finance.core.Service.TokenBlacklistService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the real security filter chain + JwtAuthFilter + GlobalExceptionHandler around GET /api/notifications.
 * Explicit context classes keep the app class (@EnableJpaAuditing) out of the slice.
 */
@WebMvcTest(NotificationController.class)
@ContextConfiguration(classes = {
        NotificationController.class, GlobalExceptionHandler.class, SecurityConfig.class, JwtAuthFilter.class})
class NotificationControllerTest {

    private static final String URL = "/api/notifications";
    private static final String TOKEN = "valid.jwt.token";
    private static final String BAD_TOKEN = "invalid.jwt.token";
    private static final String REVOKED_TOKEN = "revoked.jwt.token";
    private static final String USERNAME = "alice";

    @Autowired private MockMvc mockMvc;

    @MockBean private NotificationService notificationService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private CustomUserDetailsService userDetailsService;
    @MockBean private UserRepository userRepository;
    @MockBean private LoginAttemptService loginAttemptService;
    @MockBean private TokenBlacklistService tokenBlacklistService;

    @BeforeEach
    void stubJwtForAlice() {
        when(jwtUtil.extractUsername(TOKEN)).thenReturn(USERNAME);
        when(jwtUtil.validateToken(TOKEN)).thenReturn(true);
        when(jwtUtil.extractUsername(BAD_TOKEN)).thenReturn(USERNAME);
        when(jwtUtil.validateToken(BAD_TOKEN)).thenReturn(false);
        when(jwtUtil.extractUsername(REVOKED_TOKEN)).thenReturn(USERNAME);
        when(jwtUtil.validateToken(REVOKED_TOKEN)).thenReturn(true);
        when(tokenBlacklistService.isRevoked(REVOKED_TOKEN)).thenReturn(true);

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

    private static MockHttpServletRequestBuilder signedIn(MockHttpServletRequestBuilder builder, String token) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private static NotificationResponseDto sampleNotification() {
        return NotificationResponseDto.builder()
                .id(7L)
                .type(NotificationType.BUDGET_WARNING)
                .message("Bạn đã chi 1.600.000 / 2.000.000 VNĐ, đạt 80% ngân sách Ăn uống.")
                .budgetId(100L)
                .createdAt(LocalDateTime.of(2026, 9, 21, 10, 15, 30))
                .build();
    }

    @Test
    void returns200WithTheCallersNotificationsInTheEnvelope() throws Exception {
        when(notificationService.getMyNotifications()).thenReturn(List.of(sampleNotification()));

        mockMvc.perform(signedIn(get(URL), TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("Lấy danh sách thông báo thành công"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(7))
                .andExpect(jsonPath("$.data[0].type").value("BUDGET_WARNING"))
                .andExpect(jsonPath("$.data[0].message").value("Bạn đã chi 1.600.000 / 2.000.000 VNĐ, đạt 80% ngân sách Ăn uống."))
                .andExpect(jsonPath("$.data[0].budgetId").value(100))
                .andExpect(jsonPath("$.data[0].createdAt").value("2026-09-21T10:15:30"));
    }

    @Test
    void returnsAnEmptyListWhenThereAreNoNotifications() throws Exception {
        when(notificationService.getMyNotifications()).thenReturn(List.of());

        mockMvc.perform(signedIn(get(URL), TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void theServiceRunsAsTheJwtUser_ignoringAUserIdInTheQuery() throws Exception {
        AtomicReference<String> callerSeenByService = new AtomicReference<>();
        when(notificationService.getMyNotifications()).thenAnswer(invocation -> {
            callerSeenByService.set(SecurityContextHolder.getContext().getAuthentication().getName());
            return List.of();
        });

        mockMvc.perform(signedIn(get(URL).param("userId", "999"), TOKEN))
                .andExpect(status().isOk());

        assertEquals(USERNAME, callerSeenByService.get());
        verify(notificationService, times(1)).getMyNotifications();
    }

    @Test
    void rejectsARequestWithoutAToken_andNeverReachesTheService() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().is4xxClientError());

        verifyNoInteractions(notificationService);
    }

    @Test
    void rejectsARequestWithAnInvalidToken_andNeverReachesTheService() throws Exception {
        mockMvc.perform(signedIn(get(URL), BAD_TOKEN))
                .andExpect(status().is4xxClientError());

        verifyNoInteractions(notificationService);
    }

    @Test
    void aRevokedTokenGetsA401_andNeverReachesTheService() throws Exception {
        mockMvc.perform(signedIn(get(URL), REVOKED_TOKEN))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(notificationService);
    }

    @Test
    void mapsUserNotFoundException_to404() throws Exception {
        when(notificationService.getMyNotifications()).thenThrow(new UserNotFoundException("Không tìm thấy người dùng đang đăng nhập"));

        mockMvc.perform(signedIn(get(URL), TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("Không tìm thấy người dùng đang đăng nhập"));
    }
}
