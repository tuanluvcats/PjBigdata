package vn.edu.huit.finance.core.Controller;

import jakarta.persistence.EntityManager;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import vn.edu.huit.finance.core.Dto.AdminDashboardSummaryDto;
import vn.edu.huit.finance.core.Dto.ApiResponse;
import vn.edu.huit.finance.core.Dto.MonthlyTransactionCountDto;
import vn.edu.huit.finance.core.Dto.ReceiptStatusCountDto;
import vn.edu.huit.finance.core.Dto.UserCreateDto;
import vn.edu.huit.finance.core.Dto.UserUpdateDto;
import vn.edu.huit.finance.core.Entity.*;
import vn.edu.huit.finance.core.Repository.*;
import vn.edu.huit.finance.core.Service.AdminStatisticsService;
import vn.edu.huit.finance.core.Service.AuditLogService;
import vn.edu.huit.finance.core.Service.BackupService;
import vn.edu.huit.finance.core.Service.LoginAttemptService;
import vn.edu.huit.finance.core.Service.ReceiptProcessingService;
import vn.edu.huit.finance.core.Service.TransactionService;
import vn.edu.huit.finance.core.Service.UserService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Controller
@RequestMapping("/admin")
public class AdminController {
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int TOP_SPENDERS_PAGE = 0;
    private static final int TOP_SPENDERS_LIMIT = 5;
    private static final int CHART_MONTHS = 6;
    private static final String CHART_LABEL_PREFIX = "Tháng ";

    @Autowired private UserRepository userRepo;
    @Autowired private AdminStatisticsService adminStatisticsService;
    @Autowired private ReceiptProcessingService receiptProcessingService;
    @Autowired private CategoryRepository categoryRepo;
    @Autowired private CampaignRepository campaignRepo;
    @Autowired private ReceiptRepository receiptRepo;
    @Autowired private BackupService backupService;
    @Autowired private UserService userService;
    @Autowired private TransactionService transactionService;
    @Autowired private EntityManager entityManager;
    @Autowired private SubscriptionPlanRepository subscriptionPlanRepository;
    @Autowired private ActionLogRepository actionLogRepository;
    @Autowired private AuditLogService auditLogService;
    @Autowired private LoginAttemptService loginAttemptService;
    @Autowired private AppReviewRepository appReviewRepository;
    /**
     * The aggregates come from AdminStatisticsService, cached in Redis as DTOs (never this Model, never an entity) and
     * keyed by the month, threshold, page and limit asked for here; the customer segments below are read live.
     */
    @GetMapping("/dashboard")
    public String showDashboard(Model model) {
        YearMonth currentMonth = YearMonth.now();
        AdminDashboardSummaryDto summary = adminStatisticsService.getDashboardSummary(
                currentMonth, TransactionService.LOW_BALANCE_THRESHOLD);

        model.addAttribute("totalVolume", formatCurrencyWithoutDecimal(summary.getTotalVolume()));
        model.addAttribute("totalUsers", summary.getTotalUsers());
        model.addAttribute("lowBalanceUsers", summary.getLowBalanceUsers());
        model.addAttribute("totalTransactionsThisMonth", summary.getTransactionsInPeriod());

        model.addAttribute("topSpenders", adminStatisticsService.getTopSpenders(TOP_SPENDERS_PAGE, TOP_SPENDERS_LIMIT));

        List<MonthlyTransactionCountDto> chart = adminStatisticsService.getMonthlyTransactionCounts(currentMonth, CHART_MONTHS);
        model.addAttribute("chartLabels", chart.stream()
                .map(month -> CHART_LABEL_PREFIX + month.getMonth().getMonthValue())
                .toList());
        model.addAttribute("chartData", chart.stream().map(MonthlyTransactionCountDto::getTransactionCount).toList());

        List<String> aiLabels = new java.util.ArrayList<>();
        List<Long> aiData = new java.util.ArrayList<>();

        for (ReceiptStatusCountDto receiptStatus : summary.getReceiptStatuses()) {
            String status = receiptStatus.getStatus();

            if ("SUCCESS".equals(status)) status = "Thành công";
            else if ("FAILED".equals(status)) status = "Lỗi AI";
            else status = "Đang xử lý";

            aiLabels.add(status);
            aiData.add(receiptStatus.getCount());
        }

        model.addAttribute("aiLabels", aiLabels);
        model.addAttribute("aiData", aiData);
        model.addAttribute("vipCount", userRepo.countByAiLabel("VIP"));
        model.addAttribute("activeCount", userRepo.countByAiLabel("ACTIVE"));
        model.addAttribute("atRiskCount", userRepo.countByAiLabel("AT-RISK"));
        model.addAttribute("churnCount", userRepo.countByAiLabel("CHURN"));
        Page<User> segmentedUsers = userRepo.findAll(PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "id")));
        model.addAttribute("segmentedUsers", segmentedUsers.getContent());
        return "admin/dashboard";
    }


    @GetMapping("/login")
    public String showLoginPage() {
        return "admin/login";
    }


    @GetMapping("/categories")
    public String showCategories(Model model) {
        List<Category> categories = categoryRepo.findAll();
        model.addAttribute("categories", categories);
        return "admin/categories";
    }

    @GetMapping("/receipts")
    public String showReceipts(
            @RequestParam(defaultValue = "0") int receiptPage,
            @RequestParam(defaultValue = "0") int userPage,
            @RequestParam(defaultValue = "10") int size,
            Model model) {
        addUserAndReceiptPages(
                model,
                PageRequest.of(Math.max(userPage, 0), normalizePageSize(size), Sort.by(Sort.Direction.DESC, "id")),
                PageRequest.of(Math.max(receiptPage, 0), normalizePageSize(size)));
        return "admin/receipts";
    }

    @GetMapping("/users")
    public String showUsers(
            @RequestParam(defaultValue = "0") int userPage,
            @RequestParam(defaultValue = "10") int size,
            Model model) {
        Page<User> usersPage = userRepo.findAll(PageRequest.of(Math.max(userPage, 0), normalizePageSize(size), Sort.by(Sort.Direction.DESC, "id")));
        model.addAttribute("users", usersPage.getContent());
        model.addAttribute("usersPage", usersPage);
        model.addAttribute("pageSize", size);
        return "admin/users";
    }

    @PostMapping("/receipts/approve/{id}")
    public String approveReceipt(@PathVariable Long id) {
        transactionService.approveReceipt(id);
        return "redirect:/admin/receipts";
    }

    @PostMapping("/receipts/reject/{id}")
    public String rejectReceipt(@PathVariable Long id) {
        receiptProcessingService.rejectReceipt(id);
        return "redirect:/admin/receipts";
    }




    @GetMapping("/subscriptions")
    public String viewSubscriptions(Model model) {
        List<SubscriptionPlan> plans = subscriptionPlanRepository.findAll();
        model.addAttribute("plans", plans);
        return "admin/subscriptions";
    }
    @PostMapping("/subscriptions/add")
    public String addSubscription(
            @RequestParam String planName,
            @RequestParam Double price,
            @RequestParam Integer durationDays,
            @RequestParam Integer maxAiScans,
            @RequestParam(defaultValue = "false") boolean isWcmSpadeEnabled) {

        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setPlanName(planName);
        plan.setPrice(price);
        plan.setDurationDays(durationDays);
        plan.setMaxAiScans(maxAiScans);
        plan.setWcmSpadeEnabled(isWcmSpadeEnabled);
        plan.setActive(true);

        subscriptionPlanRepository.save(plan);

        return "redirect:/admin/subscriptions";
    }

    @PostMapping("/ajax/users")
    @ResponseBody
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createUserAjax(@Valid @RequestBody UserCreateDto dto) {
        try {
            User user = userService.createUser(dto);
            String adminEmail = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
            auditLogService.logAction(adminEmail, "CREATE_USER", "Admin tạo tài khoản mới: " + user.getEmail());

            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<Map<String, Object>>builder()
                    .code(201)
                    .message("Tạo tài khoản thành công")
                    .data(toUserResponse(user))
                    .build());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.<Map<String, Object>>builder()
                    .code(400)
                    .message(e.getMessage())
                    .build());
        }
    }

    @PutMapping("/ajax/users/{id}")
    @ResponseBody
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateUserAjax(
            @PathVariable Long id,
            @Valid @RequestBody UserUpdateDto dto) {
        try {
            User user = userService.updateUser(id, dto);
            String adminEmail = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
            auditLogService.logAction(adminEmail, "UPDATE_USER", "Admin sửa thông tin tài khoản: " + user.getEmail());

            return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                    .code(200)
                    .message("Cập nhật thông tin thành công")
                    .data(toUserResponse(user))
                    .build());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.<Map<String, Object>>builder()
                    .code(400)
                    .message(e.getMessage())
                    .build());
        }
    }

    @DeleteMapping("/ajax/users/{id}")
    @ResponseBody
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteUserAjax(@PathVariable Long id) {
        try {
            userService.deleteUser(id);
            String adminEmail = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
            auditLogService.logAction(adminEmail, "DELETE_USER", "Xóa vĩnh viễn tài khoản ID: #" + id);

            return ResponseEntity.ok(ApiResponse.<Void>builder()
                    .code(200)
                    .message("Xóa tài khoản thành công")
                    .build());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.<Void>builder()
                    .code(400)
                    .message(e.getMessage())
                    .build());
        }
    }

    @PutMapping("/ajax/users/{id}/toggle-status")
    @ResponseBody
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> toggleUserStatusAjax(@PathVariable Long id) {
        try {
            userService.toggleUserStatus(id);
            String adminEmail = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
            auditLogService.logAction(adminEmail, "TOGGLE_USER", "Khóa / Mở khóa tài khoản ID: #" + id);

            return ResponseEntity.ok(ApiResponse.<Void>builder()
                    .code(200)
                    .message("Cập nhật trạng thái thành công")
                    .build());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.<Void>builder()
                    .code(400)
                    .message(e.getMessage())
                    .build());
        }
    }

    @GetMapping("/backup")
    public String showBackupPage() {
        return "admin/backup";
    }


    @PostMapping("/backup/execute")
    @ResponseBody
    public Map<String, Object> executeBackup(@RequestParam("type") String type) {
        Map<String, Object> response = new HashMap<>();

        boolean isSuccess = backupService.backupDatabase(type);

        if (isSuccess) {
            response.put("success", true);
            response.put("message", "Đã sao lưu toàn bộ dữ liệu thành công!");
        } else {
            response.put("success", false);
            response.put("message", "Lỗi sao lưu! Kiểm tra lại đường dẫn PostgreSQL.");
        }
        return response;
    }


    @PostMapping("/backup/restore")
    @ResponseBody
    public Map<String, Object> executeRestore(@RequestParam(value = "type", defaultValue = "FULL") String type) {
        Map<String, Object> response = new HashMap<>();

        boolean isSuccess = backupService.restoreLatest(type);

        if (isSuccess) {
            response.put("success", true);
            response.put("message", "Đã phục hồi từ bản backup " + type + " mới nhất!");
        } else {
            response.put("success", false);
            response.put("message", "Lỗi phục hồi! Không tìm thấy file backup " + type + " hoặc pg_restore lỗi.");
        }
        return response;
    }

    @PostMapping("/ajax/subscriptions")
    @ResponseBody
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> createSubscriptionAjax(@RequestBody SubscriptionPlan plan) {
        plan.setActive(true);
        subscriptionPlanRepository.save(plan);
        logAdminAction("CREATE_PLAN", "Tạo gói hội viên mới: " + plan.getPlanName());
        return ResponseEntity.ok(ApiResponse.<Void>builder().code(201).message("Thành công").build());
    }

    @PutMapping("/ajax/subscriptions/{id}")
    @ResponseBody
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> updateSubscriptionAjax(@PathVariable Long id, @RequestBody SubscriptionPlan dto) {
        SubscriptionPlan plan = subscriptionPlanRepository.findById(id).orElseThrow();
        plan.setPlanName(dto.getPlanName());
        plan.setPrice(dto.getPrice());
        plan.setDurationDays(dto.getDurationDays());
        plan.setMaxAiScans(dto.getMaxAiScans());
        plan.setWcmSpadeEnabled(dto.isWcmSpadeEnabled());
        subscriptionPlanRepository.save(plan);

        logAdminAction("UPDATE_PLAN", "Cập nhật thông tin gói: " + plan.getPlanName());
        return ResponseEntity.ok(ApiResponse.<Void>builder().code(200).message("Thành công").build());
    }

    @PutMapping("/ajax/subscriptions/{id}/toggle")
    @ResponseBody
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> toggleSubscriptionAjax(@PathVariable Long id) {
        SubscriptionPlan plan = subscriptionPlanRepository.findById(id).orElseThrow();
        plan.setActive(!plan.isActive());
        subscriptionPlanRepository.save(plan);

        logAdminAction("TOGGLE_PLAN", (plan.isActive() ? "Mở bán" : "Tạm ẩn") + " gói: " + plan.getPlanName());
        return ResponseEntity.ok(ApiResponse.<Void>builder().code(200).build());
    }

    @DeleteMapping("/ajax/subscriptions/{id}")
    @ResponseBody
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteSubscriptionAjax(@PathVariable Long id) {
        SubscriptionPlan plan = subscriptionPlanRepository.findById(id).orElseThrow();
        subscriptionPlanRepository.delete(plan);

        logAdminAction("DELETE_PLAN", "Xóa vĩnh viễn gói: " + plan.getPlanName());
        return ResponseEntity.ok(ApiResponse.<Void>builder().code(200).build());
    }

    @GetMapping("/auditlog")
    public String showAuditLog(Model model) {
        List<ActionLog> systemLogs = actionLogRepository.findTop200ByActionNotContainingOrderByTimestampDesc("LOGIN");

        List<ActionLog> loginAttempts = actionLogRepository.findTop200ByActionContainingOrderByTimestampDesc("LOGIN");

        List<Map<String, Object>> auditSummary = actionLogRepository.getAuditSummary();

        model.addAttribute("systemLogs", systemLogs);
        model.addAttribute("loginAttempts", loginAttempts);
        model.addAttribute("auditSummary", auditSummary);

        return "admin/auditlog";
    }

    private void addUserAndReceiptPages(Model model, Pageable userPageable, Pageable receiptPageable) {
        Page<User> usersPage = userRepo.findAll(userPageable);
        Page<Receipt> receiptsPage = receiptRepo.findAllByOrderByCreatedAtDesc(receiptPageable);

        model.addAttribute("users", usersPage.getContent());
        model.addAttribute("usersPage", usersPage);
        model.addAttribute("receipts", receiptsPage.getContent());
        model.addAttribute("receiptsPage", receiptsPage);
        model.addAttribute("pageSize", userPageable.getPageSize());
    }

    private int normalizePageSize(int size) {
        return size > 0 ? size : DEFAULT_PAGE_SIZE;
    }

    private String formatCurrencyWithoutDecimal(BigDecimal amount) {
        BigDecimal safeAmount = amount != null ? amount : BigDecimal.ZERO;
        return safeAmount.setScale(0, RoundingMode.DOWN).toPlainString();
    }

    private Map<String, Object> toUserResponse(User user) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", user.getId());
        response.put("username", user.getUsername());
        response.put("email", user.getEmail());
        response.put("role", user.getRole());
        response.put("isActive", user.isActive());
        return response;
    }
    private void logAdminAction(String action, String description) {
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        String email = (auth != null && auth.getName() != null) ? auth.getName() : "System Admin";

        ActionLog log = new ActionLog();
        log.setUserEmail(email);
        log.setAction(action);
        log.setDescription(description);
        log.setTimestamp(LocalDateTime.now());
        actionLogRepository.save(log);
    }

    @PostMapping("/ajax/categories")
    @ResponseBody
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> addCategoryAjax(@RequestParam String name, @RequestParam String type) {
        Category category = new Category();
        category.setName(name);
        category.setType(type);
        categoryRepo.save(category);

        logAdminAction("CREATE_CATEGORY", "Thêm danh mục mới: " + name + " (" + type + ")");
        return ResponseEntity.ok(ApiResponse.<Void>builder().code(201).message("Thêm thành công").build());
    }

    @PutMapping("/ajax/categories/{id}")
    @ResponseBody
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> updateCategoryAjax(@PathVariable Long id, @RequestParam String name) {
        Category category = categoryRepo.findById(id).orElseThrow(() -> new RuntimeException("Không tìm thấy danh mục"));
        String oldName = category.getName();
        category.setName(name);
        categoryRepo.save(category);

        logAdminAction("UPDATE_CATEGORY", "Sửa danh mục: " + oldName + " ➡ " + name);
        return ResponseEntity.ok(ApiResponse.<Void>builder().code(200).message("Cập nhật thành công").build());
    }

    @DeleteMapping("/ajax/categories/{id}")
    @ResponseBody
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteCategoryAjax(@PathVariable Long id) {
        Category category = categoryRepo.findById(id).orElseThrow(() -> new RuntimeException("Không tìm thấy danh mục"));
        try {
            categoryRepo.deleteById(id);
            categoryRepo.flush();

            logAdminAction("DELETE_CATEGORY", "Xóa danh mục: " + category.getName());
            return ResponseEntity.ok(ApiResponse.<Void>builder().code(200).message("Xóa thành công").build());
        } catch (Exception e) {
            throw new RuntimeException("Không thể xóa danh mục [" + category.getName() + "] vì đang có giao dịch sử dụng nó!");
        }
    }
    @PostMapping("/ajax/security/unlock")
    @ResponseBody
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> unlockUserOrIp(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String ipAddress) {
        try {
            loginAttemptService.resetAttempts(email, ipAddress);

            String adminEmail = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
            auditLogService.logAction(adminEmail, "SECURITY_UNLOCK", "Admin can thiệp gỡ khóa sớm cho Email: " + email + " / IP: " + ipAddress, ipAddress);

            return ResponseEntity.ok(ApiResponse.<Void>builder()
                    .code(200)
                    .message("Đã gỡ khóa giải ban thành công!")
                    .build());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.<Void>builder()
                    .code(400)
                    .message("Gỡ khóa thất bại: " + e.getMessage())
                    .build());
        }

    }

    @GetMapping("/reviews")
    public String showReviews(Model model) {
        List<AppReview> reviews = appReviewRepository.findAllByOrderByCreatedAtDesc();
        model.addAttribute("reviews", reviews);
        return "admin/reviews";
    }

    @PostMapping("/ajax/reviews/{id}/approve")
    @ResponseBody
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> approveReviewAjax(@PathVariable Long id) {
        try {
            AppReview review = appReviewRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy đánh giá"));
            review.setStatus("APPROVED");
            appReviewRepository.save(review);

            logAdminAction("APPROVE_REVIEW", "Duyệt hiển thị đánh giá của user: " + review.getUserEmail());

            return ResponseEntity.ok(ApiResponse.<Void>builder()
                    .code(200)
                    .message("Đã duyệt đánh giá thành công!")
                    .build());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.<Void>builder().code(400).message(e.getMessage()).build());
        }
    }

    @DeleteMapping("/ajax/reviews/{id}")
    @ResponseBody
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteReviewAjax(@PathVariable Long id) {
        try {
            AppReview review = appReviewRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy đánh giá"));
            appReviewRepository.delete(review);

            logAdminAction("DELETE_REVIEW", "Xóa đánh giá vi phạm của: " + review.getUserEmail());

            return ResponseEntity.ok(ApiResponse.<Void>builder()
                    .code(200)
                    .message("Đã xóa đánh giá thành công!")
                    .build());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.<Void>builder().code(400).message(e.getMessage()).build());
        }
    }
    @PostMapping("/ajax/segmentation/run")
    @ResponseBody
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> runAiSegmentation() {
        try {

            List<Object[]> rfmData = userRepo.calculateAllUsersRFM();
            List<Map<String, Object>> userRFMs = new java.util.ArrayList<>();

            for (Object[] row : rfmData) {
                Map<String, Object> rfm = new HashMap<>();
                rfm.put("user_id", row[0].toString());
                rfm.put("recency", ((Number) row[1]).intValue());
                rfm.put("frequency", ((Number) row[2]).intValue());
                rfm.put("monetary", ((Number) row[3]).doubleValue());
                userRFMs.add(rfm);
            }


            Map<String, Object> requestBody = Map.of("users", userRFMs);
            org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    "http://localhost:8000/admin/segment", requestBody, Map.class
            );


            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<Map<String, String>> segments = (List<Map<String, String>>) response.getBody().get("segments");
                for (Map<String, String> seg : segments) {
                    userRepo.updateUserAiLabel(Long.parseLong(seg.get("user_id")), seg.get("label"));
                }

                String adminEmail = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
                auditLogService.logAction(adminEmail, "AI_SEGMENT", "Chạy phân lớp khách hàng (RFM + K-Means) thành công");

                return ResponseEntity.ok(ApiResponse.<Void>builder().code(200).message("Phân lớp hoàn tất").build());
            }
            throw new RuntimeException("Python AI Service không trả về kết quả.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.<Void>builder().code(400).message(e.getMessage()).build());
        }
    }
}