package vn.edu.huit.finance.core.Controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import vn.edu.huit.finance.core.Dto.ApiResponse;
import vn.edu.huit.finance.core.Dto.UserResponseDto;
import vn.edu.huit.finance.core.Dto.UserUpdateDto;
import vn.edu.huit.finance.core.Entity.User;
import vn.edu.huit.finance.core.Repository.UserRepository;
import vn.edu.huit.finance.core.Service.AuditLogService;
import vn.edu.huit.finance.core.Service.UserService;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;
    
    @Autowired
    private AuditLogService auditLogService;

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new RuntimeException("Chưa đăng nhập");
        }

        String identifier = auth.getName();

        try {
            Long userId = Long.parseLong(identifier);
            return userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy user ID: " + userId));
        } catch (NumberFormatException e) {
            return userRepository.findByUsername(identifier)
                    .orElseGet(() -> userRepository.findByEmail(identifier)
                            .orElseThrow(() -> new RuntimeException("Không tìm thấy user: " + identifier)));
        }
    }

    // Hàm tiện ích map Entity sang DTO
    private UserResponseDto mapToDto(User u) {
        return UserResponseDto.builder()
                .id(u.getId())
                .username(u.getUsername())
                .email(u.getEmail())
                .phone(u.getPhone())
                .role(u.getRole())
                .walletBalance(u.getWalletBalance())
                .build();
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponseDto>> me() {
        User u = currentUser();
        ApiResponse<UserResponseDto> response = ApiResponse.<UserResponseDto>builder()
                .code(200)
                .message("OK")
                .data(mapToDto(u))
                .build();
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponseDto>> updateProfile(
            @PathVariable Long id,
            @RequestBody UserUpdateDto dto) {

        User current = currentUser();
        if (!current.getId().equals(id)) {
            throw new RuntimeException("Lỗi: Bạn không có quyền chỉnh sửa tài khoản của người khác!");
        }
        User updatedUser = userService.updateUser(id, dto);
        try {
            auditLogService.logAction(updatedUser.getEmail(), "UPDATE_PROFILE", "Người dùng tự cập nhật thông tin cá nhân trên App");
        } catch (Exception e) {
            System.out.println("Lỗi ghi log: " + e.getMessage());
        }

        ApiResponse<UserResponseDto> response = ApiResponse.<UserResponseDto>builder()
                .code(200)
                .message("Cập nhật thông tin cá nhân thành công!")
                .data(mapToDto(updatedUser))
                .build();
        return ResponseEntity.ok(response);
    }
}