package vn.edu.huit.finance.core.Security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.edu.huit.finance.core.Entity.User;
import vn.edu.huit.finance.core.Repository.UserRepository;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private CustomUserDetailsService userDetailsService;
    @Autowired
    private UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestPath = request.getServletPath();
        // Public auth endpoints skip JWT parsing; change-password needs it to identify the caller.
        if (requestPath.startsWith("/api/auth/") && !AuthPaths.CHANGE_PASSWORD_URL.equals(requestPath)) {
            filterChain.doFilter(request, response);
            return; 
        }
        
        String authHeader = request.getHeader("Authorization");
        String token = null;
        String username = null;

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
            try {
                username = jwtUtil.extractUsername(token);
            } catch (Exception e) {
                System.out.println("Báo động: Token không hợp lệ hoặc đã hết hạn!");
            }
        }

        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            if (jwtUtil.validateToken(token)) {
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