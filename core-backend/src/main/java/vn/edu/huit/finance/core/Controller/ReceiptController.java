package vn.edu.huit.finance.core.Controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.edu.huit.finance.core.Dto.ApiResponse;
import vn.edu.huit.finance.core.Service.ReceiptProcessingService;

import java.math.BigDecimal;

/**
 * The signed-in user's own receipts. No endpoint here reads a user id from the request: the caller is resolved from the
 * SecurityContext inside ReceiptProcessingService, which answers 403 for a receipt that belongs to somebody else.
 */
@RestController
@RequestMapping("/api/receipts")
public class ReceiptController {

    private static final String MSG_FEEDBACK_SENT = "Đã gửi báo cáo cho Admin duyệt lại hóa đơn!";

    @Autowired
    private ReceiptProcessingService receiptProcessingService;

    @PostMapping("/feedback/{receiptId}")
    public ResponseEntity<ApiResponse<Void>> submitReceiptFeedback(
            @PathVariable Long receiptId,
            @RequestParam BigDecimal manualAmount) {
        receiptProcessingService.submitFeedback(receiptId, manualAmount);
        return ApiResponses.respond(HttpStatus.OK, MSG_FEEDBACK_SENT, null);
    }
}
