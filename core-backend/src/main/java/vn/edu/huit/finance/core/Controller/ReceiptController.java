package vn.edu.huit.finance.core.Controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.edu.huit.finance.core.Dto.ApiResponse;
import vn.edu.huit.finance.core.Entity.Receipt;
import vn.edu.huit.finance.core.Repository.ReceiptRepository;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api")
public class ReceiptController {

    @Autowired
    private ReceiptRepository receiptRepository;

    @PostMapping("/receipts/feedback/{receiptId}")
    public ResponseEntity<ApiResponse<String>> submitReceiptFeedback(
            @PathVariable Long receiptId,
            @RequestParam BigDecimal manualAmount,
            @RequestParam(defaultValue = "1") Long userId) {

        Receipt receipt = receiptRepository.findById(receiptId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy hóa đơn"));

        if (!"FAILED".equalsIgnoreCase(receipt.getStatus())) {
            throw new RuntimeException("Chỉ có thể gửi yêu cầu xem xét cho các hóa đơn bị lỗi (FAILED)");
        }

        receipt.setExtractedTotal(manualAmount);
        receipt.setStatus("WAITING_FOR_REVIEW");

        receiptRepository.save(receipt);

        ApiResponse<String> response = ApiResponse.<String>builder()
                .code(200)
                .message("Đã gửi báo cáo cho Admin duyệt lại hóa đơn!")
                .build();
        return ResponseEntity.ok(response);
    }
}
