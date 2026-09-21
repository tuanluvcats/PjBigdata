package vn.edu.huit.finance.core.Security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.edu.huit.finance.core.Entity.User;
import vn.edu.huit.finance.core.Repository.UserRepository;
import vn.edu.huit.finance.core.Service.TokenBlacklistService;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String MSG_TOKEN_REVOKED = "Phiên đăng nhập đã bị thu hồi. Vui lòng đăng nhập lại.";

    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private CustomUserDetailsService userDetailsService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TokenBlacklistService tokenBlacklistService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestPath = request.getServletPath();
        // Public auth endpoints skip JWT parsing; change-password and logout need it to identify the caller.
        if (AuthPaths.isPublic(requestPath)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = BearerToken.extract(request.getHeader(HttpHeaders.AUTHORIZATION)).orElse(null);
        String username = null;

        if (token != null) {
            try {
                username = jwtUtil.extractUsername(token);
            } catch (Exception e) {
                System.out.println("Báo động: Token không hợp lệ hoặc đã hết hạn!");
            }
        }

        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            if (jwtUtil.validateToken(token)) {
                // Signed and not expired, but revoked by a logout or a password change: 401, not the 403 of an unknown token.
                if (tokenBlacklistService.isRevoked(token)) {
                    log.warn("Rejected a revoked token, username={}", username);
                    response.sendError(HttpStatus.UNAUTHORIZED.value(), MSG_TOKEN_REVOKED);
                    return;
                }
                try {
                    User user = userDetailsService.loadApplicationUser(username);
                    if (!user.isActive() || "ROLE_LOCKED".equals(user.getRole())) {
                        response.sendError(HttpStatus.FORBIDDEN.value(), "Account is locked");
                        return;
                    }

                    org.springframework.security.core.userdetails.UserDetails userDetails =
                            userDetailsService.loadUserByUsername(username);

                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    
                    // 🔥 FIX CHÍ MẠNG: Chỉ lưu Database nếu lần tương tác cuối đã quá 5 phút
                    LocalDateTime now = LocalDateTime.now();
                    if (user.getLastActionTime() == null || Duration.between(user.getLastActionTime(), now).toMinutes() > 5) {
                        user.setLastActionTime(now);
                        userRepository.save(user);
                    }
                    
                } catch (AuthenticationException e) {
                    response.sendError(HttpStatus.UNAUTHORIZED.value(), e.getMessage());
                    return;
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}