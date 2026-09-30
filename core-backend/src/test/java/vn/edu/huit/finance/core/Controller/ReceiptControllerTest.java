package vn.edu.huit.finance.core.Controller;

import io.jsonwebtoken.MalformedJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.RequestParam;
import vn.edu.huit.finance.core.Config.SecurityConfig;
import vn.edu.huit.finance.core.Entity.User;
import vn.edu.huit.finance.core.Exception.GlobalExceptionHandler;
import vn.edu.huit.finance.core.Exception.ReceiptNotFoundException;
import vn.edu.huit.finance.core.Exception.TransactionAccessDeniedException;
import vn.edu.huit.finance.core.Repository.UserRepository;
import vn.edu.huit.finance.core.Security.CustomUserDetailsService;
import vn.edu.huit.finance.core.Security.JwtAuthFilter;
import vn.edu.huit.finance.core.Security.JwtUtil;
import vn.edu.huit.finance.core.Security.TokenClaims;
import vn.edu.huit.finance.core.Security.TokenType;
import vn.edu.huit.finance.core.Service.LoginAttemptService;
import vn.edu.huit.finance.core.Service.TokenBlacklistService;
import vn.edu.huit.finance.core.Service.ReceiptProcessingService;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /api/receipts/feedback/{receiptId} through the real security filter chain, JwtAuthFilter and GlobalExceptionHandler.
 * The service is a mock: these tests are about whom the endpoint acts for and which status each refusal is answered with;
 * the ownership rule itself is covered in ReceiptProcessingServiceTest.
 */
@WebMvcTest(ReceiptController.class)
@ContextConfiguration(classes = {
        ReceiptController.class, GlobalExceptionHandler.class, SecurityConfig.class, JwtAuthFilter.class})
class ReceiptControllerTest {

    private static final String URL = "/api/receipts/feedback/7";
    private static final String TOKEN = "valid.jwt.token";
    private static final String BAD_TOKEN = "invalid.jwt.token";
    private static final String USERNAME = "alice";

    @Autowired private MockMvc mockMvc;

    @MockBean private ReceiptProcessingService receiptProcessingService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private CustomUserDetailsService userDetailsService;
    @MockBean private UserRepository userRepository;
    @MockBean private LoginAttemptService loginAttemptService;
    @MockBean private TokenBlacklistService tokenBlacklistService;

    @BeforeEach
    void stubJwtForAlice() {
        when(jwtUtil.parse(TOKEN, TokenType.ACCESS)).thenReturn(new TokenClaims(USERNAME, 0));
        when(jwtUtil.parse(BAD_TOKEN, TokenType.ACCESS)).thenThrow(new MalformedJwtException("not a valid token"));

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

    private static MockHttpServletRequestBuilder signedInPost(String urlWithQuery) {
        return request(HttpMethod.POST, urlWithQuery).header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN);
    }

    // ------------------------------------------------------------------------------------------------ authentication

    @Test
    void withoutATokenNothingReachesTheService() throws Exception {
        mockMvc.perform(request(HttpMethod.POST, URL + "?manualAmount=150000")).andExpect(status().is4xxClientError());

        verifyNoInteractions(receiptProcessingService);
    }

    @Test
    void withAnInvalidTokenNothingReachesTheService() throws Exception {
        mockMvc.perform(request(HttpMethod.POST, URL + "?manualAmount=150000")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + BAD_TOKEN))
                .andExpect(status().is4xxClientError());

        verifyNoInteractions(receiptProcessingService);
    }

    // ------------------------------------------------------------------------------------------------------ the happy path

    @Test
    void feedbackActsForTheCallerOnlyAndIgnoresAUserIdInTheQuery() throws Exception {
        mockMvc.perform(signedInPost(URL + "?manualAmount=150000&userId=999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("Đã gửi báo cáo cho Admin duyệt lại hóa đơn!"));

        verify(receiptProcessingService).submitFeedback(7L, new BigDecimal("150000"));
        verifyNoMoreInteractions(receiptProcessingService);
    }

    // ------------------------------------------------------------------------------------------------------- refusals

    @Test
    void aReceiptOfSomebodyElseIsAnswered403() throws Exception {
        doThrow(new TransactionAccessDeniedException("Bạn không có quyền thao tác trên hóa đơn này"))
                .when(receiptProcessingService).submitFeedback(any(), any());

        mockMvc.perform(signedInPost(URL + "?manualAmount=150000"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("Bạn không có quyền thao tác trên hóa đơn này"));
    }

    static Stream<Arguments> otherRefusals() {
        return Stream.of(
                Arguments.of(new ReceiptNotFoundException("Không tìm thấy hóa đơn"), 404, "Không tìm thấy hóa đơn"),
                Arguments.of(new IllegalStateException("Chỉ có thể gửi yêu cầu xem xét cho các hóa đơn bị lỗi (FAILED)"), 400,
                        "Chỉ có thể gửi yêu cầu xem xét cho các hóa đơn bị lỗi (FAILED)"),
                Arguments.of(new IllegalArgumentException("Số tiền điều chỉnh phải lớn hơn 0"), 400,
                        "Số tiền điều chỉnh phải lớn hơn 0"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("otherRefusals")
    void theOtherRefusalsOfTheServiceKeepTheirStatus(RuntimeException refusal, int status, String message) throws Exception {
        doThrow(refusal).when(receiptProcessingService).submitFeedback(any(), any());

        mockMvc.perform(signedInPost(URL + "?manualAmount=150000"))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.message").value(message));
    }

    @Test
    void theAmountIsRequired() throws Exception {
        mockMvc.perform(signedInPost(URL))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Thiếu tham số bắt buộc: manualAmount"));

        verifyNoInteractions(receiptProcessingService);
    }

    @Test
    void anAmountThatIsNotANumberNeverReachesTheService() throws Exception {
        mockMvc.perform(signedInPost(URL + "?manualAmount=abc")).andExpect(status().isBadRequest());

        verifyNoInteractions(receiptProcessingService);
    }

    // ------------------------------------------------------------------------------------------------ structural guard

    @Test
    void noHandlerOfTheReceiptControllerReadsAUserIdFromTheRequest() {
        for (Method method : ReceiptController.class.getDeclaredMethods()) {
            for (Parameter parameter : method.getParameters()) {
                assertFalse(parameter.getName().equalsIgnoreCase("userId"),
                        method.getName() + " must not take a userId: " + parameter);
                RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
                if (requestParam != null) {
                    assertFalse(Arrays.asList(requestParam.name(), requestParam.value()).contains("userId"),
                            method.getName() + " must not bind the request parameter userId");
                }
            }
        }
    }
}
