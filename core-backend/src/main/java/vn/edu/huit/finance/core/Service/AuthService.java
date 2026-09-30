package vn.edu.huit.finance.core.Service;

import io.jsonwebtoken.JwtException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.huit.finance.core.Dto.AuthTokenResponseDto;
import vn.edu.huit.finance.core.Dto.ChangePasswordRequestDto;
import vn.edu.huit.finance.core.Entity.User;
import vn.edu.huit.finance.core.Exception.CredentialsChangeConflictException;
import vn.edu.huit.finance.core.Exception.InvalidOtpException;
import vn.edu.huit.finance.core.Exception.InvalidPasswordException;
import vn.edu.huit.finance.core.Exception.InvalidTokenException;
import vn.edu.huit.finance.core.Exception.UserNotFoundException;
import vn.edu.huit.finance.core.Repository.UserRepository;
import vn.edu.huit.finance.core.Security.CurrentUserProvider;
import vn.edu.huit.finance.core.Security.CustomUserDetailsService;
import vn.edu.huit.finance.core.Security.JwtUtil;
import vn.edu.huit.finance.core.Security.TokenClaims;
import vn.edu.huit.finance.core.Security.TokenType;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class AuthService {

    // The password is written by a bulk UPDATE, which Envers never sees: these action_logs rows are its audit trail.
    // Descriptions are fixed texts: never a password, hash, OTP or token.
    private static final String AUDIT_ACTION_CHANGE_PASSWORD = "PASSWORD_CHANGED";
    private static final String AUDIT_DESC_CHANGE_PASSWORD = "Người dùng đổi mật khẩu thành công";
    private static final String AUDIT_ACTION_RESET_PASSWORD = "PASSWORD_RESET";
    private static final String AUDIT_DESC_RESET_PASSWORD = "Người dùng đặt lại mật khẩu bằng mã OTP";
    private static final String AUDIT_ACTION_LOGOUT = "LOGOUT";
    private static final String AUDIT_DESC_LOGOUT = "Người dùng đăng xuất";
    private static final String MSG_OLD_PASSWORD_INCORRECT = "Mật khẩu cũ không chính xác";
    private static final String MSG_NEW_PASSWORD_SAME_AS_OLD = "Mật khẩu mới phải khác mật khẩu cũ";
    // One answer for every refused refresh, so a caller cannot tell which check its token failed.
    private static final String MSG_REFRESH_TOKEN_INVALID = "Refresh token không hợp lệ hoặc đã hết hạn";
    private static final String MSG_USER_NOT_FOUND = "Không tìm thấy người dùng";
    private static final String MSG_CREDENTIALS_CHANGED_MEANWHILE =
            "Mật khẩu vừa được thay đổi bởi một yêu cầu khác. Vui lòng thử lại.";
    // The one row of the user: anything else means the guarded UPDATE did not apply.
    private static final int ROWS_OF_ONE_USER = 1;

    private static final int OTP_LENGTH = 6;
    private static final String OTP_FORMAT = "%0" + OTP_LENGTH + "d";
    // Exclusive upper bound of the drawn number: every OTP from 000000 to 999999 is possible.
    private static final int OTP_BOUND = (int) Math.pow(10, OTP_LENGTH);
    private static final Duration OTP_VALIDITY = Duration.ofMinutes(5);
    private static final int OTP_MAX_FAILED_ATTEMPTS = 3;
    private static final String OTP_MAIL_SUBJECT = "Mã xác thực Quên mật khẩu (Finance App)";
    private static final String OTP_MAIL_TEXT =
            "Mã OTP của bạn là: %s. Mã có hiệu lực trong %d phút. Vui lòng không chia sẻ mã này cho bất kỳ ai.";
    private static final String MSG_OTP_EXPIRED = "Mã OTP không tồn tại hoặc đã hết hạn. Vui lòng yêu cầu gửi lại mã mới.";
    private static final String MSG_OTP_WRONG = "Mã OTP không chính xác. Bạn còn %d lần thử.";
    private static final String MSG_OTP_ATTEMPTS_EXHAUSTED =
            "Bạn đã nhập sai OTP quá số lần cho phép. Vui lòng yêu cầu gửi lại mã mới.";

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CurrentUserProvider currentUserProvider;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private TokenBlacklistService tokenBlacklistService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @PersistenceContext
    private EntityManager entityManager;

    /** The pending OTP of one email. Immutable: a wrong attempt swaps it for a copy with one more failed attempt. */
    private record OtpRecord(String code, LocalDateTime expiryTime, int attempts) {

        OtpRecord(String code, LocalDateTime expiryTime) {
            this(code, expiryTime, 0);
        }

        boolean isExpiredAt(LocalDateTime now) {
            return now.isAfter(expiryTime);
        }

        OtpRecord withFailedAttempt() {
            return new OtpRecord(code, expiryTime, attempts + 1);
        }
    }

    // Concurrent, because the attempt limit is only worth something if parallel guesses cannot slip past it.
    private final Map<String, OtpRecord> otpStorage = new ConcurrentHashMap<>();
    // Not injected: these defaults are what production uses; tests replace them to control time and the draw.
    private Clock clock = Clock.systemUTC();
    private SecureRandom secureRandom = new SecureRandom();

    public void generateAndSendOtp(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Email không tồn tại trong hệ thống"));

        String otp = String.format(OTP_FORMAT, secureRandom.nextInt(OTP_BOUND));
        // A new OTP replaces the pending one, so the old code stops working and the attempt counter starts over.
        otpStorage.put(email, new OtpRecord(otp, LocalDateTime.now(clock).plus(OTP_VALIDITY)));

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject(OTP_MAIL_SUBJECT);
        message.setText(String.format(OTP_MAIL_TEXT, otp, OTP_VALIDITY.toMinutes()));
        mailSender.send(message);
    }

    /**
     * Sets the new password and ends every session of the account, on every device (credentials version + 1): whoever
     * knew the old password is locked out. No tokens are handed out, the client signs in again with the new password.
     * The PASSWORD_RESET audit row is written in the same transaction, once the password is: all three commit together
     * or not at all.
     */
    @Transactional
    public void verifyOtpAndResetPassword(String email, String otp, String newPassword) {
        // Consumed before the database work: if that fails the user requests a new OTP, but the code is never replayable.
        consumeOtp(email, otp);

        User user = lockUser(userRepository.findByEmail(email).orElseThrow());
        replacePasswordAndEndAllSessions(user, newPassword);
        auditLogService.logAction(user.getEmail(), AUDIT_ACTION_RESET_PASSWORD, AUDIT_DESC_RESET_PASSWORD);

        log.info("Password reset via OTP, userId={}, credentialsVersion={}", user.getId(), user.getCredentialsVersion());
    }

    /**
     * Checks the submitted code against the pending OTP of {@code email} in one atomic step per email. The OTP is
     * consumed on success and dropped once it has expired or used up its failed attempts; in both cases a new one has
     * to be requested.
     */
    private void consumeOtp(String email, String submittedCode) {
        LocalDateTime now = LocalDateTime.now(clock);
        // Message to reject with; the default also covers "nothing pending", and null means the code was accepted.
        AtomicReference<String> rejection = new AtomicReference<>(MSG_OTP_EXPIRED);

        otpStorage.computeIfPresent(email, (key, pending) -> {
            if (pending.isExpiredAt(now)) {
                return null;
            }
            if (pending.code().equals(submittedCode)) {
                rejection.set(null);
                return null;
            }
            OtpRecord failed = pending.withFailedAttempt();
            if (failed.attempts() >= OTP_MAX_FAILED_ATTEMPTS) {
                rejection.set(MSG_OTP_ATTEMPTS_EXHAUSTED);
                return null;
            }
            rejection.set(String.format(MSG_OTP_WRONG, OTP_MAX_FAILED_ATTEMPTS - failed.attempts()));
            return failed;
        });

        if (rejection.get() != null) {
            log.warn("Password reset rejected: {}", rejection.get());
            throw new InvalidOtpException(rejection.get());
        }
    }

    /**
     * Changes the password of the authenticated caller; the user comes from the SecurityContext, never from the request.
     * The row is locked and read again first, so two changes at the same time queue up and the second one is checked
     * against the password the first one set. The credentials version goes up in the same transaction, which ends every
     * session opened with the old password on every device (the access token of this request also goes on the
     * blacklist), and the caller gets a new token pair so that this device stays signed in. The PASSWORD_CHANGED audit
     * row joins the same transaction: if it cannot be written, the new password is rolled back with it.
     */
    @Transactional
    public AuthTokenResponseDto changePassword(ChangePasswordRequestDto request, String accessToken) {
        User user = lockUser(currentUserProvider.getCurrentUser());

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            log.warn("Change password rejected: wrong old password, userId={}", user.getId());
            throw new InvalidPasswordException(MSG_OLD_PASSWORD_INCORRECT);
        }
        if (request.getOldPassword().equals(request.getNewPassword())) {
            log.warn("Change password rejected: new password equals old password, userId={}", user.getId());
            throw new InvalidPasswordException(MSG_NEW_PASSWORD_SAME_AS_OLD);
        }

        replacePasswordAndEndAllSessions(user, request.getNewPassword());
        tokenBlacklistService.revoke(accessToken);

        auditLogService.logAction(user.getEmail(), AUDIT_ACTION_CHANGE_PASSWORD, AUDIT_DESC_CHANGE_PASSWORD);
        log.info("Password changed, userId={}, credentialsVersion={}", user.getId(), user.getCredentialsVersion());
        return issueTokens(user);
    }

    /**
     * Ends the session of the authenticated caller: the access token that authorised the call is revoked and, when the
     * client sends it, so is the refresh token (otherwise the app could simply sign in again with it). All or nothing.
     */
    @Transactional
    public void logout(String accessToken, String refreshToken) {
        User user = currentUserProvider.getCurrentUser();

        tokenBlacklistService.revoke(accessToken);
        if (refreshToken != null && !refreshToken.isBlank()) {
            tokenBlacklistService.revokeIfValid(refreshToken);
        }

        auditLogService.logAction(user.getEmail(), AUDIT_ACTION_LOGOUT, AUDIT_DESC_LOGOUT);
        log.info("User logged out, userId={}", user.getId());
    }

    /**
     * Trades a refresh token for a new pair and revokes it in the same transaction (rotation), so a refresh token works
     * once. Only a signed, unexpired token of type REFRESH that carries a credentials version gets past the first check;
     * an access token is refused here like any other unusable token. The user's row is locked BEFORE the blacklist is
     * read: two refreshes with the same token queue up on that lock, on one instance or several, and the second one reads
     * the blacklist only after the first has committed (then it gets 401) or rolled back (then it goes ahead). A locked
     * account, and a token issued for an older credentials version (the password changed since), are refused too.
     * Nothing is issued unless the revocation has been written.
     */
    @Transactional
    public AuthTokenResponseDto refresh(String refreshToken) {
        TokenClaims claims = verifiedRefreshToken(refreshToken);
        User user = lockUser(ownerOf(claims.subject()));

        if (CustomUserDetailsService.isLocked(user)) {
            throw refreshRefused("account locked", user.getId());
        }
        if (tokenBlacklistService.isRevoked(refreshToken)) {
            throw refreshRefused("token already used or revoked", user.getId());
        }
        if (!user.hasCredentialsVersion(claims.credentialsVersion())) {
            throw refreshRefused("token issued before the last password change", user.getId());
        }
        tokenBlacklistService.revoke(refreshToken);

        log.info("Tokens refreshed, userId={}", user.getId());
        return issueTokens(user);
    }

    /** What a usable refresh token says. Anything else (missing, garbage, expired, an access token...) is refused. */
    private TokenClaims verifiedRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw refreshRefused("no token", null);
        }
        try {
            return jwtUtil.parse(refreshToken, TokenType.REFRESH);
        } catch (JwtException | IllegalArgumentException e) {
            throw refreshRefused("unusable token (" + e.getClass().getSimpleName() + ")", null);
        }
    }

    /** The account a verified token was issued to. A token whose account is gone is refused like any other. */
    private User ownerOf(String subject) {
        try {
            return userDetailsService.loadApplicationUser(subject);
        } catch (UsernameNotFoundException e) {
            throw refreshRefused("unknown subject", null);
        }
    }

    private InvalidTokenException refreshRefused(String reason, Long userId) {
        log.warn("Refresh refused: {}, userId={}", reason, userId);
        return new InvalidTokenException(MSG_REFRESH_TOKEN_INVALID);
    }

    /**
     * Locks the user's row until the end of the transaction and returns it as the database has it now. The copy read
     * earlier in this transaction is detached first: locking it in place would keep the password and the credentials
     * version it was read with, and whatever another request committed in between would be missed. Same idiom as the
     * wallet lock of TransactionService (a refresh would cascade to every transaction of the user).
     */
    private User lockUser(User user) {
        entityManager.detach(user);
        User locked = entityManager.find(User.class, user.getId(), LockModeType.PESSIMISTIC_WRITE);
        if (locked == null) {
            throw new UserNotFoundException(MSG_USER_NOT_FOUND);
        }
        return locked;
    }

    /**
     * Stores the new password and ends every session opened so far, on every device: the hash and credentials version
     * + 1 go into the row in ONE UPDATE, so they commit or roll back together. The UPDATE is guarded by the version the
     * caller locked. Under the lock it always matches; should it not, nothing has been written and the change is refused
     * instead of being applied on top of one it did not see. Neither column is updatable through the entity, which only
     * mirrors the new values so that the tokens issued next carry the new version.
     */
    private void replacePasswordAndEndAllSessions(User lockedUser, String newPassword) {
        String passwordHash = passwordEncoder.encode(newPassword);
        Integer lockedVersion = lockedUser.getCredentialsVersion();

        int updated = userRepository.updatePasswordAndEndSessions(lockedUser.getId(), passwordHash, lockedVersion);
        if (updated != ROWS_OF_ONE_USER) {
            log.error("Credentials left unchanged: the guarded UPDATE matched {} rows, userId={}, expectedVersion={}",
                    updated, lockedUser.getId(), lockedVersion);
            throw new CredentialsChangeConflictException(MSG_CREDENTIALS_CHANGED_MEANWHILE);
        }
        lockedUser.setPassword(passwordHash);
        lockedUser.setCredentialsVersion(lockedVersion + 1);
    }

    /** A new token pair carrying the credentials version the account has right now. */
    private AuthTokenResponseDto issueTokens(User user) {
        return AuthTokenResponseDto.builder()
                .token(jwtUtil.generateToken(user.getEmail(), user.getCredentialsVersion()))
                .refreshToken(jwtUtil.generateRefreshToken(user.getEmail(), user.getCredentialsVersion()))
                .build();
    }
}
