package vn.edu.huit.finance.core.Service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.huit.finance.core.Dto.ChangePasswordRequestDto;
import vn.edu.huit.finance.core.Entity.User;
import vn.edu.huit.finance.core.Exception.InvalidOtpException;
import vn.edu.huit.finance.core.Exception.InvalidPasswordException;
import vn.edu.huit.finance.core.Repository.UserRepository;
import vn.edu.huit.finance.core.Security.CurrentUserProvider;

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

    private static final String AUDIT_ACTION_CHANGE_PASSWORD = "CHANGE_PASSWORD";
    private static final String AUDIT_DESC_CHANGE_PASSWORD = "Người dùng đổi mật khẩu thành công";
    private static final String MSG_OLD_PASSWORD_INCORRECT = "Mật khẩu cũ không chính xác";
    private static final String MSG_NEW_PASSWORD_SAME_AS_OLD = "Mật khẩu mới phải khác mật khẩu cũ";

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

    @Transactional
    public void verifyOtpAndResetPassword(String email, String otp, String newPassword) {
        // Consumed before the database work: if that fails the user requests a new OTP, but the code is never replayable.
        consumeOtp(email, otp);

        User user = userRepository.findByEmail(email).orElseThrow();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        log.info("Password reset via OTP, userId={}", user.getId());
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

    /** Changes the password of the authenticated caller; the user comes from the SecurityContext, never from the request. */
    @Transactional
    public void changePassword(ChangePasswordRequestDto request) {
        User user = currentUserProvider.getCurrentUser();

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            log.warn("Change password rejected: wrong old password, userId={}", user.getId());
            throw new InvalidPasswordException(MSG_OLD_PASSWORD_INCORRECT);
        }
        if (request.getOldPassword().equals(request.getNewPassword())) {
            log.warn("Change password rejected: new password equals old password, userId={}", user.getId());
            throw new InvalidPasswordException(MSG_NEW_PASSWORD_SAME_AS_OLD);
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        auditLogService.logAction(user.getEmail(), AUDIT_ACTION_CHANGE_PASSWORD, AUDIT_DESC_CHANGE_PASSWORD);
        log.info("Password changed, userId={}", user.getId());
    }
}
