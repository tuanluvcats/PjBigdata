package vn.edu.huit.finance.core.Service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.coyote.BadRequestException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.huit.finance.core.Dto.UserCreateDto;
import vn.edu.huit.finance.core.Dto.UserUpdateDto;
import vn.edu.huit.finance.core.Entity.User;
import vn.edu.huit.finance.core.Repository.UserRepository;
import vn.edu.huit.finance.core.Security.Roles;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private static final String MSG_ROLE_CHANGE_FORBIDDEN = "Không được phép tự thay đổi vai trò";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User createUser(UserCreateDto dto) {
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setEmail(dto.getEmail());
        user.setPhone(dto.getPhone());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setRole(Roles.normalize(dto.getRole()));
        user.setActive(true);

        return userRepository.save(user);
    }

    /** For administrators: any field of any user, the role included. */
    @Transactional
    public User updateUser(Long id, UserUpdateDto dto) {
        return update(id, dto, true);
    }

    /**
     * For a user editing their own profile: the same fields, but the role cannot change. Asking for a different role is
     * refused (403) instead of being ignored, since it is an attempt to promote oneself.
     */
    @Transactional
    public User updateOwnProfile(Long id, UserUpdateDto dto) {
        return update(id, dto, false);
    }

    private User update(Long id, UserUpdateDto dto, boolean mayChangeRole) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + id));

        if (dto.getRole() != null && !dto.getRole().isBlank()) {
            String requestedRole = Roles.normalize(dto.getRole());
            if (mayChangeRole) {
                user.setRole(requestedRole);
            } else if (!requestedRole.equals(Roles.normalize(user.getRole()))) {
                log.warn("Role change refused, userId={}, requestedRole={}", id, requestedRole);
                throw new AccessDeniedException(MSG_ROLE_CHANGE_FORBIDDEN);
            }
        }
        if (dto.getUsername() != null && !dto.getUsername().isBlank()) user.setUsername(dto.getUsername());
        if (dto.getPhone() != null && !dto.getPhone().isBlank()) user.setPhone(dto.getPhone());
        if (dto.getEmail() != null && !dto.getEmail().isBlank()) user.setEmail(dto.getEmail());

        return userRepository.save(user);
    }

    @Transactional
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + id));
        try {
            userRepository.delete(user);
            userRepository.flush();
        } catch (Exception e) {
            user.setActive(false);
            userRepository.save(user);
            throw new RuntimeException("Tài khoản đang có giao dịch. Hệ thống đã tự động vô hiệu hóa (Khóa) thay vì xóa vĩnh viễn để bảo toàn dữ liệu.");
        }
    }

    private String buildAdminPlaceholderPhone(String username) {
        String suffix = Integer.toUnsignedString(username.hashCode());
        return "ADMIN_" + suffix;
    }
    @org.springframework.transaction.annotation.Transactional
    public void toggleUserStatus(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng với ID: " + userId));

        if (Roles.isAdmin(user.getRole())) {
            throw new RuntimeException("Không được phép tác động hoặc thay đổi trạng thái của ADMIN hệ thống!");
        }

        user.setActive(!user.isActive());
        userRepository.save(user);
    }

}
