package vn.edu.huit.finance.core.Security;

/** Auth API paths shared by the controller, the security filter chain and {@link JwtAuthFilter}. */
public final class AuthPaths {

    public static final String BASE = "/api/auth";
    public static final String CHANGE_PASSWORD = "/change-password";

    /** The only endpoint under {@link #BASE} that needs a JWT; every other one is public. */
    public static final String CHANGE_PASSWORD_URL = BASE + CHANGE_PASSWORD;

    private AuthPaths() {
    }
}
