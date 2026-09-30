package vn.edu.huit.finance.core.Controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import vn.edu.huit.finance.core.Dto.ApiResponse;
import vn.edu.huit.finance.core.Dto.AuthTokenResponseDto;
import vn.edu.huit.finance.core.Dto.ChangePasswordRequestDto;
import vn.edu.huit.finance.core.Dto.LogoutRequestDto;
import vn.edu.huit.finance.core.Dto.RefreshTokenRequestDto;
import vn.edu.huit.finance.core.Dto.RegisterRequestDto;
import vn.edu.huit.finance.core.Dto.ResetPasswordRequestDto;
import vn.edu.huit.finance.core.Entity.User;
import vn.edu.huit.finance.core.Exception.InvalidTokenException;
import vn.edu.huit.finance.core.Repository.UserRepository;
import vn.edu.huit.finance.core.Security.AuthPaths;
import vn.edu.huit.finance.core.Security.BearerToken;
import vn.edu.huit.finance.core.Security.JwtUtil;
import vn.edu.huit.finance.core.Service.AuditLogService;
import vn.edu.huit.finance.core.Service.AuthService;
import vn.edu.huit.finance.core.Service.LoginAttemptService;

import java.time.LocalDateTime;

@RestController
@RequestMapping(AuthPaths.BASE)
public class AuthController {
    private static final String CHANGE_PASSWORD_SUCCESS_MESSAGE = "Đổi mật khẩu thành công!";
    private static final String LOGOUT_SUCCESS_MESSAGE = "Đăng xuất thành công!";
    private static final String REFRESH_SUCCESS_MESSAGE = "Refresh token thành công";
    private static final String MSG_TOKEN_MISSING = "Thiếu token xác thực";
    private static final String MSG_ACCOUNT_NOT_FOUND = "Không tìm thấy tài khoản với email này!";

    @Autowired  private org.springframework.security.authentication.AuthenticationManager authenticationManager;
    @Autowired  private UserRepository userRepository;
    @Autowired  private PasswordEncoder passwordEncoder;
    @Autowired  private JwtUtil jwtUtil;
    @Autowired  private LoginAttemptService loginAttemptService;
    @Autowired  private AuditLogService auditLogService;
    @Autowired  private AuthService authService;
    @Autowired  private vn.edu.huit.finance.core.Service.SocialAuthService socialAuthService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequestDto request) {
        // Built from the DTO on purpose: the client must not be able to set the wallet balance, role or any other User field.
        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());

        if (user.getEmail() != null && userRepository.findByEmail(user.getEmail()).isPresent()) {
            return ResponseEntity.status(400).body(java.util.Map.of(
                    "code", 400, "message", "Email đã được đăng ký. Vui lòng dùng email khác."));
        }
        if (user.getUsername() != null && userRepository.findByUsername(user.getUsername()).isPresent()) {
            return ResponseEntity.status(400).body(java.util.Map.of(
                    "code", 400, "message", "Tên đăng nhập đã tồn tại."));
        }

        user.setRole("ROLE_USER");

        String hashedPassword = passwordEncoder.encode(request.getPassword());
        user.setPassword(hashedPassword);

        try {
            userRepository.save(user);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            return ResponseEntity.status(400).body(java.util.Map.of(
                    "code", 400, "message", "Email hoặc số điện thoại đã tồn tại."));
        }

        java.util.Map<String, Object> response = new java.util.HashMap<>();
        response.put("code", 200);
        response.put("message", "Đăng ký thành công!");
        auditLogService.logAction(user.getEmail(), "CREATE_USER", "Người dùng tự đăng ký tài khoản qua App Mobile");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody java.util.Map<String, String> loginRequest, HttpServletRequest request) {
        String email = loginRequest.get("email");
        String password = loginRequest.get("password");
        String ipAddress = clientIp(request);

        try {
            loginAttemptService.assertLoginAllowed(email, ipAddress);

            // Read BEFORE the password check: if the password is changed or reset while the check runs, the tokens carry
            // the version the checked password belonged to and are refused, instead of outliving the change.
            java.util.Optional<Integer> checkedVersion = userRepository.findByEmail(email).map(User::getCredentialsVersion);

            authenticationManager.authenticate(
                    new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(email, password)
            );
            loginAttemptService.recordSuccess(email, ipAddress);

            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException(MSG_ACCOUNT_NOT_FOUND));
            Integer credentialsVersion = checkedVersion.orElseThrow(() -> new RuntimeException(MSG_ACCOUNT_NOT_FOUND));

            String token = jwtUtil.generateToken(email, credentialsVersion);
            String refreshToken = jwtUtil.generateRefreshToken(email, credentialsVersion);

            LocalDateTime now = LocalDateTime.now();
            user.setLastLoginTime(now);
            user.setLastActionTime(now);
            userRepository.save(user);
            auditLogService.logAction(user.getEmail(), "LOGIN_SUCCESS", "Đăng nhập hợp lệ vào App Mobile", ipAddress);

            java.util.Map<String, Object> userData = new java.util.HashMap<>();
            userData.put("id", user.getId());
            userData.put("email", user.getUsername());
            userData.put("role", user.getRole());

            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("token", token);
            data.put("refreshToken", refreshToken);
            data.put("user", userData);

            java.util.Map<String, Object> response = new java.util.HashMap<>();
            response.put("code", 200);
            response.put("message", "Đăng nhập thành công");
            response.put("data", data);

            return ResponseEntity.ok(response);

        } catch (LockedException e) {
            auditLogService.logAction(email, "LOGIN_LOCKED", "Thiết bị đang bị khóa vẫn cố tình gửi lệnh đăng nhập", ipAddress);
            return ResponseEntity.status(429).body(java.util.Map.of("message", "Tài khoản hoặc IP của bạn đang bị tạm khóa 15 phút do nhập sai quá 5 lần!"));
        } catch (org.springframework.security.core.AuthenticationException e) {
            loginAttemptService.recordFailure(email, ipAddress);
            try {
                loginAttemptService.assertLoginAllowed(email, ipAddress);
            } catch (LockedException lockedException) {
                auditLogService.logAction(email, "LOGIN_LOCKED", "Hệ thống tự động kích hoạt khóa IP do phát hiện Brute-force", ipAddress);
                return ResponseEntity.status(429).body(java.util.Map.of("message", "Tài khoản hoặc IP của bạn đang bị tạm khóa 15 phút do nhập sai quá 5 lần!"));
            }
            auditLogService.logAction(email, "LOGIN_FAILED", "Nhập sai mật khẩu tài khoản", ipAddress);
            return ResponseEntity.status(401).body(java.util.Map.of("message", "Sai email hoặc mật khẩu!"));
        }
    }


    @PostMapping("/social-login")
    public ResponseEntity<?> socialLogin(@RequestBody java.util.Map<String, String> req) {
        try {
            String provider = req.get("provider");
            String token = req.get("token");
            if (provider == null || token == null) {
                return ResponseEntity.status(400)
                        .body(java.util.Map.of("message", "Thiếu provider hoặc token"));
            }

            User user = socialAuthService.authenticate(provider, token);
            LocalDateTime now = LocalDateTime.now();
            user.setLastLoginTime(now);
            user.setLastActionTime(now);
            userRepository.save(user);
            auditLogService.logAction(user.getEmail(), "LOGIN_SUCCESS", "Người dùng đăng nhập App qua: " + provider);
            String jwt = jwtUtil.generateToken(user.getEmail(), user.getCredentialsVersion());
            String refreshToken = jwtUtil.generateRefreshToken(user.getEmail(), user.getCredentialsVersion());

            java.util.Map<String, Object> userData = new java.util.HashMap<>();
            userData.put("id", user.getId());
            userData.put("email", user.getEmail());
            userData.put("role", user.getRole());

            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("token", jwt);
            data.put("refreshToken", refreshToken);
            data.put("user", userData);

            java.util.Map<String, Object> response = new java.util.HashMap<>();
            response.put("code", 200);
            response.put("message", "Đăng nhập thành công");
            response.put("data", data);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(401).body(java.util.Map.of("message", "Lỗi xác thực: " + e.getMessage()));
        }
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<String>> forgotPassword(@RequestParam String email) {
        authService.generateAndSendOtp(email);

        ApiResponse<String> response = ApiResponse.<String>builder()
                .code(200)
                .message("Mã OTP đã được gửi đến email: " + email)
                .build();
        return ResponseEntity.ok(response);
    }

    // JSON body, not query parameters: the OTP and the new password must not end up in access logs.
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<String>> resetPassword(@Valid @RequestBody ResetPasswordRequestDto request) {
        authService.verifyOtpAndResetPassword(request.getEmail(), request.getOtp(), request.getNewPassword());

        ApiResponse<String> response = ApiResponse.<String>builder()
                .code(200)
                .message("Đổi mật khẩu thành công! Vui lòng đăng nhập lại.")
                .build();
        return ResponseEntity.ok(response);
    }

    // Every session opened with the old password ends, on every device; this one continues with the token pair returned.
    @PutMapping(AuthPaths.CHANGE_PASSWORD)
    public ResponseEntity<ApiResponse<AuthTokenResponseDto>> changePassword(
            @Valid @RequestBody ChangePasswordRequestDto request,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        AuthTokenResponseDto tokens = authService.changePassword(request, bearerTokenOf(authorization));
        return ApiResponses.respond(HttpStatus.OK, CHANGE_PASSWORD_SUCCESS_MESSAGE, tokens);
    }

    // The body is optional: a client that sends its refresh token gets that one revoked too.
    @PostMapping(AuthPaths.LOGOUT)
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @Valid @RequestBody(required = false) LogoutRequestDto request) {
        authService.logout(bearerTokenOf(authorization), request == null ? null : request.getRefreshToken());

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .code(HttpStatus.OK.value())
                .message(LOGOUT_SUCCESS_MESSAGE)
                .build());
    }

    private String bearerTokenOf(String authorizationHeader) {
        return BearerToken.extract(authorizationHeader).orElseThrow(() -> new InvalidTokenException(MSG_TOKEN_MISSING));
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
    // Same answer as before ({code, message, data: {token, refreshToken}}); the refresh token sent is used up. A refused
    // token is an InvalidTokenException, i.e. 401.
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthTokenResponseDto>> refreshToken(@Valid @RequestBody RefreshTokenRequestDto request) {
        AuthTokenResponseDto tokens = authService.refresh(request.getRefreshToken());
        return ApiResponses.respond(HttpStatus.OK, REFRESH_SUCCESS_MESSAGE, tokens);
    }
}