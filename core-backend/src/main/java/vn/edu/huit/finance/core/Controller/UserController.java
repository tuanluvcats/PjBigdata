package vn.edu.huit.finance.core.Controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import vn.edu.huit.finance.core.Dto.ApiResponse;
import vn.edu.huit.finance.core.Dto.UserUpdateDto;
import vn.edu.huit.finance.core.Entity.User;
import vn.edu.huit.finance.core.Repository.UserRepository;
import vn.edu.huit.finance.core.Service.AuditLogService;
import vn.edu.huit.finance.core.Service.UserService;

import java.util.HashMap;
import java.util.Map;

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

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Map<String, Object>>> me() {
        User u = currentUser();
        Map<String, Object> data = new HashMap<>();
        data.put("id", u.getId());
        data.put("email", u.getEmail());
        data.put("username", u.getUsername());
        data.put("phone", u.getPhone());
        data.put("role", u.getRole());
        data.put("walletBalance", u.getWalletBalance());

        ApiResponse<Map<String, Object>> response = ApiResponse.<Map<String, Object>>builder()
                .code(200)
                .message("OK")
                .data(data)
                .build();
        return ResponseEntity.ok(response);
    }
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateProfile(
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


        Map<String, Object> data = new HashMap<>();
        data.put("id", updatedUser.getId());
        data.put("username", updatedUser.getUsername());
        data.put("phone", updatedUser.getPhone());

        ApiResponse<Map<String, Object>> response = ApiResponse.<Map<String, Object>>builder()
                .code(200)
                .message("Cập nhật thông tin cá nhân thành công!")
                .data(data)
                .build();
        return ResponseEntity.ok(response);
    }
}