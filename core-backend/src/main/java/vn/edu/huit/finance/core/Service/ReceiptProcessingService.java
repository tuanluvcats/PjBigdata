package vn.edu.huit.finance.core.Service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
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
import vn.edu.huit.finance.core.Service.Cache.StatisticsChange;
import vn.edu.huit.finance.core.Service.Cache.StatisticsChangedEvent;

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
 *   <li>{@link #rejectReceipt} lets an administrator reject one.</li>
 * </ul>
 * Approving a receipt moves the wallet, so it lives next to the wallet lock, in {@code TransactionService.approveReceipt}
 * (which this class cannot call: {@code TransactionService} already depends on it).
 */
@Slf4j
@Service
public class ReceiptProcessingService {

    private static final String MSG_RECEIPT_NOT_FOUND = "Không tìm thấy hóa đơn";
    private static final String MSG_RECEIPT_FORBIDDEN = "Bạn không có quyền thao tác trên hóa đơn này";
    private static final String MSG_FEEDBACK_NOT_FAILED = "Chỉ có thể gửi yêu cầu xem xét cho các hóa đơn bị lỗi (FAILED)";
    private static final String MSG_AMOUNT_INVALID = "Số tiền điều chỉnh phải lớn hơn 0";
    private static final int MONEY_SCALE = 2;

    @Autowired
    private ReceiptRepository receiptRepo;
    @Autowired
    private CurrentUserProvider currentUserProvider;
    @Autowired
    private OcrService ocrService;
    @Autowired
    private ApplicationEventPublisher eventPublisher;

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
        eventPublisher.publishEvent(StatisticsChangedEvent.of(StatisticsChange.RECEIPT_STATUS, caller.getId()));
        log.info("Receipt feedback received, userId={}, receiptId={}", caller.getId(), receiptId);
    }

    /**
     * For administrators only (the caller of this method has to check that): rejects a receipt (FAILED), after which its
     * owner may send the right amount through {@link #submitFeedback}. Neither the transaction nor the wallet changes.
     */
    @Transactional
    public void rejectReceipt(Long receiptId) {
        Receipt receipt = receiptRepo.findDetailedById(receiptId).orElseThrow(() -> {
            log.warn("Receipt not found for rejection, receiptId={}", receiptId);
            return new ReceiptNotFoundException(MSG_RECEIPT_NOT_FOUND);
        });
        receipt.setStatus(ReceiptStatus.FAILED.name());
        receiptRepo.save(receipt);

        // A receipt no transaction carries belongs to nobody: only the admin summary counts it.
        Transaction carrier = receipt.getTransaction();
        Long ownerId = carrier != null ? carrier.getUser().getId() : null;
        eventPublisher.publishEvent(StatisticsChangedEvent.of(StatisticsChange.RECEIPT_STATUS, ownerId));
        log.info("Receipt rejected, receiptId={}, ownerId={}", receiptId, ownerId);
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
