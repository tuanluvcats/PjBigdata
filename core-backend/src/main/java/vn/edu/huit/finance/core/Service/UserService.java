package vn.edu.huit.finance.core.Service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

import org.apache.coyote.BadRequestException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.huit.finance.core.Dto.UserCreateDto;
import vn.edu.huit.finance.core.Dto.UserUpdateDto;
import vn.edu.huit.finance.core.Entity.User;
import vn.edu.huit.finance.core.Repository.UserRepository;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User createUser(UserCreateDto dto) {
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setEmail(dto.getEmail());
        user.setPhone(dto.getPhone());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setRole(normalizeRole(dto.getRole()));
        user.setActive(true);

        return userRepository.save(user);
    }

    @Transactional
    public User updateUser(Long id, UserUpdateDto dto) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + id));

        if (dto.getUsername() != null && !dto.getUsername().isBlank()) user.setUsername(dto.getUsername());
        if (dto.getPhone() != null && !dto.getPhone().isBlank()) user.setPhone(dto.getPhone());
        if (dto.getEmail() != null && !dto.getEmail().isBlank()) user.setEmail(dto.getEmail());
        if (dto.getRole() != null && !dto.getRole().isBlank()) user.setRole(normalizeRole(dto.getRole()));

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

    private String normalizeRole(String role) {
        String normalized = role.trim().toUpperCase();
        return normalized.startsWith("ROLE_") ? normalized : "ROLE_" + normalized;
    }

    private String buildAdminPlaceholderPhone(String username) {
        String suffix = Integer.toUnsignedString(username.hashCode());
        return "ADMIN_" + suffix;
    }
    @org.springframework.transaction.annotation.Transactional
    public void toggleUserStatus(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng với ID: " + userId));

        if (user.getRole() != null && "ADMIN".equalsIgnoreCase(user.getRole().toString())) {
            throw new RuntimeException("Không được phép tác động hoặc thay đổi trạng thái của ADMIN hệ thống!");
        }

        user.setActive(!user.isActive());
        userRepository.save(user);
    }

}
