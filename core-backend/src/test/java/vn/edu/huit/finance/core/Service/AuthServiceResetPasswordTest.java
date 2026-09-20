package vn.edu.huit.finance.core.Service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.huit.finance.core.Entity.User;
import vn.edu.huit.finance.core.Exception.InvalidOtpException;
import vn.edu.huit.finance.core.Repository.UserRepository;

import java.lang.reflect.Method;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Regression tests for the OTP reset-password flow: what reaches the database, how long an OTP lives, and how many
 * wrong guesses it survives.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceResetPasswordTest {

    private static final String EMAIL = "alice@example.com";
    private static final String OLD_PASSWORD = "OldPass123";
    private static final String NEW_PASSWORD = "NewPass456";
    private static final String OTHER_PASSWORD = "OtherPass789";
    // Never a valid OTP (they are 6 digits), so a wrong guess cannot collide with the random one.
    private static final String WRONG_OTP = "wrong-otp";

    // The requirements under test, spelled out here instead of read from the service.
    private static final Duration OTP_VALIDITY = Duration.ofMinutes(5);
    private static final int MAX_FAILED_ATTEMPTS = 3;
    private static final int PARALLEL_ROUNDS = 25;
    private static final int OTP_BOUND = 1_000_000;
    private static final String OTP_EXPIRED_MESSAGE =
            "Mã OTP không tồn tại hoặc đã hết hạn. Vui lòng yêu cầu gửi lại mã mới.";
    private static final String TWO_ATTEMPTS_LEFT_MESSAGE = "Mã OTP không chính xác. Bạn còn 2 lần thử.";
    private static final String ONE_ATTEMPT_LEFT_MESSAGE = "Mã OTP không chính xác. Bạn còn 1 lần thử.";
    private static final String ATTEMPTS_EXHAUSTED_MESSAGE =
            "Bạn đã nhập sai OTP quá số lần cho phép. Vui lòng yêu cầu gửi lại mã mới.";

    private static final Instant ISSUED_AT = Instant.parse("2026-01-01T10:00:00Z");
    // "$2a$" + 2-digit cost + "$" + 53 characters of salt and hash = a 60-character BCrypt string.
    private static final Pattern BCRYPT_HASH = Pattern.compile("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");
    private static final Pattern OTP_IN_MAIL = Pattern.compile("\\d{6}");

    @Mock private UserRepository userRepository;
    @Mock private JavaMailSender mailSender;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private AuthService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new AuthService();
        ReflectionTestUtils.setField(service, "userRepository", userRepository);
        ReflectionTestUtils.setField(service, "passwordEncoder", passwordEncoder);
        ReflectionTestUtils.setField(service, "mailSender", mailSender);
        timeIs(ISSUED_AT);

        user = new User();
        user.setId(1L);
        user.setEmail(EMAIL);
        user.setPassword(passwordEncoder.encode(OLD_PASSWORD));
    }

    private void timeIs(Instant now) {
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(now, ZoneOffset.UTC));
    }

    /** Makes the service draw these numbers in order, so a test knows (and can tell apart) its OTPs. */
    private void drawing(int... draws) {
        AtomicInteger next = new AtomicInteger();
        ReflectionTestUtils.setField(service, "secureRandom", new SecureRandom() {
            @Override
            public int nextInt(int bound) {
                return draws[next.getAndIncrement()];
            }
        });
    }

    /** Requests an OTP like a client would and reads it back from the newest mail the service sent. */
    private String requestOtp() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        service.generateAndSendOtp(EMAIL);

        ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, atLeastOnce()).send(mail.capture());
        Matcher otp = OTP_IN_MAIL.matcher(mail.getValue().getText());
        assertTrue(otp.find(), "the mail should contain the OTP");
        return otp.group();
    }

    private InvalidOtpException rejectionOf(String otp) {
        return assertThrows(InvalidOtpException.class,
                () -> service.verifyOtpAndResetPassword(EMAIL, otp, NEW_PASSWORD));
    }

    private User savedUser() {
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        return saved.getValue();
    }

    @Test
    void verifyOtpAndResetPassword_savesBcryptHash_neverThePlaintext() {
        String otp = requestOtp();

        service.verifyOtpAndResetPassword(EMAIL, otp, NEW_PASSWORD);

        String stored = savedUser().getPassword();
        assertNotEquals(NEW_PASSWORD, stored, "the plaintext password must never reach the database");
        assertTrue(BCRYPT_HASH.matcher(stored).matches(), "expected a BCrypt hash but was: " + stored);
        assertTrue(passwordEncoder.matches(NEW_PASSWORD, stored));
        assertFalse(passwordEncoder.matches(OLD_PASSWORD, stored));
    }

    @Test
    void verifyOtpAndResetPassword_countsDownTheAttempts_andKeepsPassword_whenOtpIsWrong() {
        requestOtp();
        String hashBefore = user.getPassword();

        assertEquals(TWO_ATTEMPTS_LEFT_MESSAGE, rejectionOf(WRONG_OTP).getMessage());
        assertEquals(ONE_ATTEMPT_LEFT_MESSAGE, rejectionOf(WRONG_OTP).getMessage());

        assertEquals(hashBefore, user.getPassword());
        verify(userRepository, never()).save(any());
    }

    @Test
    void verifyOtpAndResetPassword_stillAcceptsTheOtp_afterTwoWrongAttempts() {
        String otp = requestOtp();
        rejectionOf(WRONG_OTP);
        rejectionOf(WRONG_OTP);

        service.verifyOtpAndResetPassword(EMAIL, otp, NEW_PASSWORD);

        assertTrue(passwordEncoder.matches(NEW_PASSWORD, savedUser().getPassword()));
    }

    @Test
    void verifyOtpAndResetPassword_dropsTheOtpOnTheThirdWrongAttempt_soEvenTheRightCodeIsRefusedAfterwards() {
        String otp = requestOtp();
        rejectionOf(WRONG_OTP);
        rejectionOf(WRONG_OTP);

        assertEquals(ATTEMPTS_EXHAUSTED_MESSAGE, rejectionOf(WRONG_OTP).getMessage());
        assertEquals(OTP_EXPIRED_MESSAGE, rejectionOf(otp).getMessage());

        verify(userRepository, never()).save(any());
    }

    @Test
    void generateAndSendOtp_issuesAWorkingOtp_afterTheOldOneWasLockedOut() {
        requestOtp();
        rejectionOf(WRONG_OTP);
        rejectionOf(WRONG_OTP);
        rejectionOf(WRONG_OTP);

        String freshOtp = requestOtp();
        service.verifyOtpAndResetPassword(EMAIL, freshOtp, NEW_PASSWORD);

        assertTrue(passwordEncoder.matches(NEW_PASSWORD, savedUser().getPassword()));
    }

    @Test
    void generateAndSendOtp_replacesThePendingOtp_andRestartsTheAttempts() {
        drawing(111_111, 222_222);
        String first = requestOtp();
        rejectionOf(WRONG_OTP);
        rejectionOf(WRONG_OTP);
        String second = requestOtp();

        // The old code is now just a wrong guess, and the counter is back at zero (2 left, not exhausted).
        assertEquals(TWO_ATTEMPTS_LEFT_MESSAGE, rejectionOf(first).getMessage());
        service.verifyOtpAndResetPassword(EMAIL, second, NEW_PASSWORD);

        assertTrue(passwordEncoder.matches(NEW_PASSWORD, savedUser().getPassword()));
    }

    @Test
    void verifyOtpAndResetPassword_acceptsTheOtp_untilTheExpiryInstant() {
        String otp = requestOtp();
        timeIs(ISSUED_AT.plus(OTP_VALIDITY));

        service.verifyOtpAndResetPassword(EMAIL, otp, NEW_PASSWORD);

        assertTrue(passwordEncoder.matches(NEW_PASSWORD, savedUser().getPassword()));
    }

    @Test
    void verifyOtpAndResetPassword_rejectsAnExpiredOtp_evenWithTheRightCode_andDropsIt() {
        String otp = requestOtp();
        timeIs(ISSUED_AT.plus(OTP_VALIDITY).plusSeconds(1));

        assertEquals(OTP_EXPIRED_MESSAGE, rejectionOf(otp).getMessage());

        // It was dropped, not just refused: turning the clock back into the validity window does not revive it.
        timeIs(ISSUED_AT);
        assertEquals(OTP_EXPIRED_MESSAGE, rejectionOf(otp).getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void verifyOtpAndResetPassword_rejectsAnOtpThatWasNeverRequested() {
        assertEquals(OTP_EXPIRED_MESSAGE, rejectionOf("123456").getMessage());

        verifyNoInteractions(userRepository);
    }

    @Test
    void verifyOtpAndResetPassword_rejectsReplayOfTheSameOtp() {
        String otp = requestOtp();
        service.verifyOtpAndResetPassword(EMAIL, otp, NEW_PASSWORD);
        String storedAfterReset = user.getPassword();

        InvalidOtpException thrown = assertThrows(InvalidOtpException.class,
                () -> service.verifyOtpAndResetPassword(EMAIL, otp, OTHER_PASSWORD));

        assertEquals(OTP_EXPIRED_MESSAGE, thrown.getMessage());
        assertEquals(storedAfterReset, user.getPassword());
        verify(userRepository, times(1)).save(any());
    }

    @Test
    void verifyOtpAndResetPassword_allowsOnlyThreeWrongAttempts_evenWhenTheGuessesArriveInParallel() throws Exception {
        int guesses = 40;
        ExecutorService pool = Executors.newFixedThreadPool(guesses);
        try {
            // Repeated: whether a race shows up is a matter of timing, so one round proves little.
            for (int round = 0; round < PARALLEL_ROUNDS; round++) {
                String otp = requestOtp();
                CountDownLatch startGate = new CountDownLatch(1);
                List<Future<String>> outcomes = new ArrayList<>();
                for (int i = 0; i < guesses; i++) {
                    String wrongOtp = WRONG_OTP + "-" + i;
                    outcomes.add(pool.submit(() -> {
                        startGate.await();
                        return rejectionOf(wrongOtp).getMessage();
                    }));
                }
                startGate.countDown();
                List<String> messages = new ArrayList<>();
                for (Future<String> outcome : outcomes) {
                    messages.add(outcome.get(10, TimeUnit.SECONDS));
                }

                // Exactly three guesses were ever weighed; every later one found nothing pending.
                assertEquals(1, Collections.frequency(messages, TWO_ATTEMPTS_LEFT_MESSAGE), "round " + round);
                assertEquals(1, Collections.frequency(messages, ONE_ATTEMPT_LEFT_MESSAGE), "round " + round);
                assertEquals(1, Collections.frequency(messages, ATTEMPTS_EXHAUSTED_MESSAGE), "round " + round);
                assertEquals(guesses - MAX_FAILED_ATTEMPTS,
                        Collections.frequency(messages, OTP_EXPIRED_MESSAGE), "round " + round);
                assertEquals(OTP_EXPIRED_MESSAGE, rejectionOf(otp).getMessage(), "round " + round);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void otpSource_isSecureRandomByDefault() {
        // java.util.Random is predictable; the injected stand-ins in the tests below would hide a regression to it.
        assertInstanceOf(SecureRandom.class, ReflectionTestUtils.getField(new AuthService(), "secureRandom"));
    }

    @ParameterizedTest
    @CsvSource({"0,000000", "42,000042", "999999,999999"})
    void generateAndSendOtp_mailsASixDigitCode_drawnFromTheWholeSecureRandomRange(int draw, String expectedOtp) {
        AtomicInteger requestedBound = new AtomicInteger();
        ReflectionTestUtils.setField(service, "secureRandom", new SecureRandom() {
            @Override
            public int nextInt(int bound) {
                requestedBound.set(bound);
                return draw;
            }
        });

        assertEquals(expectedOtp, requestOtp());
        assertEquals(OTP_BOUND, requestedBound.get());
    }

    @Test
    void verifyOtpAndResetPassword_runsInsideATransaction() throws NoSuchMethodException {
        // Mockito bypasses Spring's proxy, so the transaction boundary can only be checked declaratively.
        Method method = AuthService.class.getMethod(
                "verifyOtpAndResetPassword", String.class, String.class, String.class);

        assertNotNull(AnnotatedElementUtils.findMergedAnnotation(method, Transactional.class),
                "read-modify-write of the user must be atomic");
    }
}
