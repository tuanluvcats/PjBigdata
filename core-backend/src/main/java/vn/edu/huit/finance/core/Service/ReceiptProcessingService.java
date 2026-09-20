package vn.edu.huit.finance.core.Service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.huit.finance.core.Dto.ExtractedReceiptDto;
import vn.edu.huit.finance.core.Entity.Receipt;
import vn.edu.huit.finance.core.Entity.ReceiptStatus;
import vn.edu.huit.finance.core.Entity.Transaction;
import vn.edu.huit.finance.core.Entity.User;
import vn.edu.huit.finance.core.Exception.ReceiptNotFoundException;
import vn.edu.huit.finance.core.Exception.TransactionAccessDeniedException;
import vn.edu.huit.finance.core.Repository.ReceiptRepository;
import vn.edu.huit.finance.core.Security.CurrentUserProvider;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * What happens to a receipt after the AI has read it. A receipt is owned through the transaction that carries it (the
 * entity has no owner of its own), so every rule here works from that link:
 * <ul>
 *   <li>{@link #attachReceipt} builds the receipt of a transaction that is being created; the receipt is persisted by
 *       the cascade of that transaction, in the same database transaction as the wallet, so all of them commit or
 *       none does;</li>
 *   <li>{@link #submitFeedback} lets the owner correct the amount of a receipt the AI failed on;</li>
 *   <li>{@link #approveReceipt} verifies a receipt, and only one that a transaction carries.</li>
 * </ul>
 */
@Slf4j
@Service
public class ReceiptProcessingService {

    private static final String MSG_RECEIPT_NOT_FOUND = "Không tìm thấy hóa đơn";
    private static final String MSG_RECEIPT_FORBIDDEN = "Bạn không có quyền thao tác trên hóa đơn này";
    private static final String MSG_FEEDBACK_NOT_FAILED = "Chỉ có thể gửi yêu cầu xem xét cho các hóa đơn bị lỗi (FAILED)";
    private static final String MSG_AMOUNT_INVALID = "Số tiền điều chỉnh phải lớn hơn 0";
    private static final String MSG_RECEIPT_UNLINKED = "Hóa đơn chưa gắn với giao dịch nào nên không thể xác nhận";
    private static final int MONEY_SCALE = 2;

    @Autowired
    private ReceiptRepository receiptRepo;
    @Autowired
    private CurrentUserProvider currentUserProvider;
    @Autowired
    private OcrService ocrService;

    /**
     * Builds the receipt of {@code transaction} from the AI's JSON and links both ways. Meant to be called while the
     * transaction is being built inside {@code TransactionService.createTransaction}, which saves it (and, by cascade,
     * the receipt) after moving the wallet. Fallbacks for what the AI did not deliver: no timestamp keeps the date the
     * client sent, no address keeps the client's location, no seller leaves the vendor empty, and no total marks the
     * receipt FAILED (the AI could not read it) so that its owner can send the amount through {@link #submitFeedback}.
     */
    public void attachReceipt(Transaction transaction, String rawReceiptData, String imageUrl) {
        ExtractedReceiptDto extracted = ocrService.extract(rawReceiptData);

        Receipt receipt = new Receipt();
        receipt.setVendorName(extracted.getSeller());
        receipt.setExtractedTotal(extracted.getTotalCost());
        receipt.setStatus((extracted.getTotalCost() != null ? ReceiptStatus.SUCCESS : ReceiptStatus.FAILED).name());
        if (imageUrl != null && !imageUrl.isBlank()) {
            receipt.setImageUrl(imageUrl.trim());
        }
        if (extracted.getTimestamp() != null) {
            receipt.setExtractedDate(extracted.getTimestamp().toLocalDate());
            transaction.setTransactionDate(extracted.getTimestamp());
        }
        if (extracted.getAddress() != null) {
            transaction.setLocation(extracted.getAddress());
        }

        receipt.setTransaction(transaction);
        transaction.setReceipt(receipt);
        log.info("Receipt attached to a new transaction, status={}", receipt.getStatus());
    }

    /**
     * The owner of a FAILED receipt sends the amount the AI could not read; the receipt then waits for an administrator.
     * Anybody else, and anybody at all for a receipt no transaction carries, is refused with a 403.
     */
    @Transactional
    public void submitFeedback(Long receiptId, BigDecimal manualAmount) {
        BigDecimal amount = manualAmount == null ? null : manualAmount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        if (amount == null || amount.signum() <= 0 || amount.compareTo(OcrService.MAX_TOTAL) > 0) {
            throw new IllegalArgumentException(MSG_AMOUNT_INVALID);
        }

        User caller = currentUserProvider.getCurrentUser();
        Receipt receipt = findOwnedReceipt(receiptId, caller.getId());
        if (!ReceiptStatus.FAILED.matches(receipt.getStatus())) {
            throw new IllegalStateException(MSG_FEEDBACK_NOT_FAILED);
        }

        receipt.setExtractedTotal(amount);
        receipt.setStatus(ReceiptStatus.WAITING_FOR_REVIEW.name());
        receiptRepo.save(receipt);
        log.info("Receipt feedback received, userId={}, receiptId={}", caller.getId(), receiptId);
    }

    /** For administrators only (their controller has to check that): a receipt is verified only when a transaction carries it. */
    @Transactional
    public void approveReceipt(Long receiptId) {
        Receipt receipt = receiptRepo.findDetailedById(receiptId).orElseThrow(() -> {
            log.warn("Receipt not found for approval, receiptId={}", receiptId);
            return new ReceiptNotFoundException(MSG_RECEIPT_NOT_FOUND);
        });
        if (receipt.getTransaction() == null) {
            log.warn("Receipt approval refused, no transaction is linked, receiptId={}", receiptId);
            throw new IllegalStateException(MSG_RECEIPT_UNLINKED);
        }

        receipt.setStatus(ReceiptStatus.SUCCESS.name());
        receiptRepo.save(receipt);
        log.info("Receipt approved, receiptId={}, transactionId={}", receiptId, receipt.getTransaction().getId());
    }

    /** A receipt that does not exist is a 404, one that exists but is somebody else's (or nobody's) a 403. */
    private Receipt findOwnedReceipt(Long receiptId, Long userId) {
        Receipt receipt = receiptRepo.findDetailedById(receiptId).orElseThrow(() -> {
            log.warn("Receipt not found, userId={}, receiptId={}", userId, receiptId);
            return new ReceiptNotFoundException(MSG_RECEIPT_NOT_FOUND);
        });
        Transaction carrier = receipt.getTransaction();
        if (carrier == null || !carrier.getUser().getId().equals(userId)) {
            log.warn("Receipt access denied, userId={}, receiptId={}", userId, receiptId);
            throw new TransactionAccessDeniedException(MSG_RECEIPT_FORBIDDEN);
        }
        return receipt;
    }
}
