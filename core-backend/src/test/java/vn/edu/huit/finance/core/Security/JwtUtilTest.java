package vn.edu.huit.finance.core.Security;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The token format that token revocation relies on. No Spring context: JwtUtil has no dependencies. */
class JwtUtilTest {

    private static final String SUBJECT = "alice@example.com";
    private static final int TOKENS = 50;

    private final JwtUtil jwtUtil = new JwtUtil();

    @Test
    void tokensOfTheSameUserIssuedInTheSameInstantAreStillDifferent_soRevokingOneNeverRevokesTheNextLogin() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < TOKENS; i++) {
            tokens.add(jwtUtil.generateToken(SUBJECT));
        }

        // Without a unique id, everything issued in one second to one user would be the very same string.
        assertEquals(TOKENS, tokens.size());
    }

    @Test
    void refreshTokensAreUniqueToo() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < TOKENS; i++) {
            tokens.add(jwtUtil.generateRefreshToken(SUBJECT));
        }

        assertEquals(TOKENS, tokens.size());
    }

    @Test
    void extractExpiration_ofAnAccessToken_isAboutFifteenMinutesAhead() {
        String token = jwtUtil.generateToken(SUBJECT);

        long millisAhead = jwtUtil.extractExpiration(token).getTime() - System.currentTimeMillis();

        // The exp claim has whole-second precision, so it may be up to a second short of the full lifetime.
        assertTrue(millisAhead > Duration.ofMinutes(14).toMillis() && millisAhead <= Duration.ofMinutes(15).toMillis(),
                "expires in " + millisAhead + " ms");
    }

    @Test
    void extractExpiration_ofARefreshToken_isAboutThirtyDaysAhead() {
        String token = jwtUtil.generateRefreshToken(SUBJECT);

        Date expiration = jwtUtil.extractExpiration(token);

        long millisAhead = expiration.getTime() - System.currentTimeMillis();
        assertTrue(millisAhead > Duration.ofDays(29).toMillis() && millisAhead <= Duration.ofDays(30).toMillis(),
                "expires in " + millisAhead + " ms");
    }

    @Test
    void extractExpiration_throwsForATokenThatIsNotValid() {
        assertThrows(JwtException.class, () -> jwtUtil.extractExpiration("not.a.jwt"));
    }

    @Test
    void extractUsername_stillReturnsTheSubject() {
        assertEquals(SUBJECT, jwtUtil.extractUsername(jwtUtil.generateToken(SUBJECT)));
    }

    @Test
    void validateToken_acceptsATokenItIssued_andRejectsATamperedOne() {
        String token = jwtUtil.generateToken(SUBJECT);

        assertTrue(jwtUtil.validateToken(token));
        assertFalse(jwtUtil.validateToken(token + "x"));
        assertFalse(jwtUtil.validateToken("not.a.jwt"));
    }
}
