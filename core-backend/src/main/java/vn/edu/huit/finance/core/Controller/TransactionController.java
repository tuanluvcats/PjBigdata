package vn.edu.huit.finance.core.Controller;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.edu.huit.finance.core.Dto.ApiResponse;
import vn.edu.huit.finance.core.Dto.TransactionRequestDto;
import vn.edu.huit.finance.core.Dto.TransactionResponseDto;
import vn.edu.huit.finance.core.Entity.Transaction;
import vn.edu.huit.finance.core.Repository.TransactionRepository;
import vn.edu.huit.finance.core.Service.TransactionService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {
    @Autowired
    private TransactionService transactionService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private vn.edu.huit.finance.core.Repository.UserRepository userRepository;

    private Long currentUserId() {
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new RuntimeException("Chưa đăng nhập");
        }
        String identifier = auth.getName();
        try {
            Long userId = Long.parseLong(identifier);
            return userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy user ID: " + userId))
                    .getId();
        } catch (NumberFormatException e) {
            return userRepository.findByUsername(identifier)
                    .orElseGet(() -> userRepository.findByEmail(identifier)
                            .orElseThrow(() -> new RuntimeException("Không tìm thấy user: " + identifier)))
                    .getId();
        }
    }

    // Hàm tiện ích để chuyển Entity sang DTO
    private TransactionResponseDto mapToDto(Transaction t) {
        String note = t.getNote();
        String title = (note != null && !note.isBlank()) ? note : t.getCategoryName();
        String imgUrl = (t.getReceipt() != null) ? t.getReceipt().getImageUrl() : null;

        return TransactionResponseDto.builder()
                .id(t.getId())
                .title(title)
                .amount(t.getAmount())
                .date(t.getTransactionDate())
                .category(t.getCategoryName())
                .isExpense(t.isExpense())
                .imageUrl(imgUrl)
                .build();
    }

    @GetMapping("/mine")
    public ResponseEntity<ApiResponse<List<TransactionResponseDto>>> myTransactions() {
        Long userId = currentUserId();
        List<Transaction> list = transactionRepository.findByUserIdOrderByTransactionDateDesc(userId);

        // Sử dụng Stream API thay cho vòng lặp for cồng kềnh
        List<TransactionResponseDto> data = list.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());

        ApiResponse<List<TransactionResponseDto>> response = ApiResponse.<List<TransactionResponseDto>>builder()
                .code(200)
                .message("Lấy giao dịch thành công")
                .data(data)
                .build();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/checkout")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createTransaction(
            @Valid @RequestBody TransactionRequestDto request) {
        try {
            Long userId = currentUserId();
            Map<String, Object> result = transactionService.createTransaction(request, userId);

            ApiResponse<Map<String, Object>> response = ApiResponse.<Map<String, Object>>builder()
                    .code(200)
                    .message("Lưu giao dịch thành công!")
                    .data(result)
                    .build();
            return ResponseEntity.ok(response);
            
        } catch (RuntimeException e) {
            ApiResponse<Map<String, Object>> errorResponse = ApiResponse.<Map<String, Object>>builder()
                    .code(400)
                    .message(e.getMessage())
                    .build();
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @GetMapping("/filter")
    public ResponseEntity<ApiResponse<List<TransactionResponseDto>>> filterTransactions(
            @RequestParam(defaultValue = "1") Long userId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String vendorName,
            @RequestParam(required = false) LocalDate startDate, @RequestParam(required = false) LocalDate endDate,
            @RequestParam(defaultValue = "transactionDate") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {

        Sort.Direction direction = sortDir.equalsIgnoreCase("ASC") ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort = Sort.by(direction, sortBy);

        List<Transaction> list = transactionRepository.filterTransactions(userId, categoryId, minPrice, maxPrice, vendorName, sort);
        
        List<TransactionResponseDto> data = list.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());

        ApiResponse<List<TransactionResponseDto>> response = ApiResponse.<List<TransactionResponseDto>>builder()
                .code(200)
                .message("Lấy dữ liệu thành công")
                .data(data)
                .build();
        return ResponseEntity.ok(response);
    }

    @PutMapping("/cancel/{receiptId}")
    public ResponseEntity<ApiResponse<Void>> cancelTransaction(
            @PathVariable Long receiptId,
            @RequestParam(defaultValue = "1") Long userId) {

        transactionService.cancelReceipt(receiptId, userId);

        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .code(200)
                .message("Đã hủy hóa đơn thành công!")
                .build();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/popular-vendors")
    public ResponseEntity<ApiResponse<List<Object[]>>> getPopularVendors(@RequestParam(defaultValue = "1") Long userId) {
        List<Object[]> popularVendors = transactionRepository.findPopularVendors(userId);

        ApiResponse<List<Object[]>> response = ApiResponse.<List<Object[]>>builder()
                .code(200)
                .message("Lấy danh sách độ phổ biến thành công")
                .data(popularVendors)
                .build();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/app-dashboard")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> getAppDashboardData(@RequestParam(defaultValue = "1") Long userId) {
        java.util.Map<String, Object> dashboardData = new java.util.HashMap<>();

        org.springframework.data.domain.Pageable top5 = org.springframework.data.domain.PageRequest.of(0, 5, org.springframework.data.domain.Sort.by("transactionDate").descending());
        
        // Map Entities sang DTOs trước khi nhét vào Dashboard
        List<TransactionResponseDto> recentTx = transactionRepository.findByUserId(userId, top5)
                .stream().map(this::mapToDto).collect(Collectors.toList());

        Transaction biggestEntity = transactionRepository.findTopByUserIdOrderByAmountDesc(userId);
        TransactionResponseDto biggestTx = biggestEntity != null ? mapToDto(biggestEntity) : null;

        List<TransactionResponseDto> saleTx = transactionRepository.findByUserIdAndVoucherCodeNotNull(userId)
                .stream().map(this::mapToDto).collect(Collectors.toList());

        dashboardData.put("recentTransactions", recentTx);
        dashboardData.put("biggestTransaction", biggestTx);
        dashboardData.put("saleTransactions", saleTx);

        ApiResponse<java.util.Map<String, Object>> response = ApiResponse.<java.util.Map<String, Object>>builder()
                .code(200)
                .message("Load trang chủ App thành công")
                .data(dashboardData)
                .build();
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteTransaction(@PathVariable Long id) {
        Long userId = currentUserId();
        Transaction tx = transactionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy giao dịch"));

        if (!tx.getUser().getId().equals(userId)) {
            throw new RuntimeException("Bạn không có quyền xóa giao dịch này");
        }

        vn.edu.huit.finance.core.Entity.User user = tx.getUser();
        if (tx.isExpense()) {
            user.setWalletBalance(user.getWalletBalance().add(tx.getAmount()));
        } else {
            user.setWalletBalance(user.getWalletBalance().subtract(tx.getAmount()));
        }
        userRepository.save(user);
        transactionRepository.delete(tx);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .code(200)
                .message("Đã xóa giao dịch và hoàn tiền thành công!")
                .build());
    }
}