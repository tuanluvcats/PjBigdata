package vn.edu.huit.finance.core.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.huit.finance.core.Dto.AppDashboardResponseDto;
import vn.edu.huit.finance.core.Dto.PopularVendorResponseDto;
import vn.edu.huit.finance.core.Dto.TransactionFilterRequestDto;
import vn.edu.huit.finance.core.Dto.TransactionRequestDto;
import vn.edu.huit.finance.core.Dto.TransactionResponseDto;
import vn.edu.huit.finance.core.Dto.TransactionUpdateRequestDto;
import vn.edu.huit.finance.core.Entity.Category;
import vn.edu.huit.finance.core.Entity.Receipt;
import vn.edu.huit.finance.core.Entity.ReceiptStatus;
import vn.edu.huit.finance.core.Entity.Transaction;
import vn.edu.huit.finance.core.Entity.TransactionSortField;
import vn.edu.huit.finance.core.Entity.User;
import vn.edu.huit.finance.core.Exception.InsufficientBalanceException;
import vn.edu.huit.finance.core.Exception.InvalidCategoryException;
import vn.edu.huit.finance.core.Exception.ReceiptNotFoundException;
import vn.edu.huit.finance.core.Exception.TransactionAccessDeniedException;
import vn.edu.huit.finance.core.Exception.TransactionNotFoundException;
import vn.edu.huit.finance.core.Exception.UserNotFoundException;
import vn.edu.huit.finance.core.Repository.*;
import vn.edu.huit.finance.core.Security.CurrentUserProvider;
import vn.edu.huit.finance.core.Service.Cache.StatisticsChange;
import vn.edu.huit.finance.core.Service.Cache.StatisticsChangedEvent;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Transactions and the wallet they move. Every method that changes a transaction changes User.walletBalance in the same
 * database transaction, so a failure anywhere leaves both untouched; the budget notifications that a new, edited or
 * reconciled expense raises (BudgetAlertService) are written in that same transaction. Methods without a user id take the
 * owner from the SecurityContext (through CurrentUserProvider) and scope every lookup to that owner; the ones that do take
 * an id (createTransaction for the payment webhook, the *Of methods for administrators) trust their caller to have checked
 * it, and so does approveReceipt, which is for administrators too. Every write also names what it changed for the cached
 * statistics (StatisticsChangedEvent), and the stale entries are evicted once its transaction has committed.
 */
@Slf4j
@Service
public class TransactionService {

    private static final String CATEGORY_TYPE_EXPENSE = "EXPENSE";
    private static final String CATEGORY_TYPE_INCOME = "INCOME";
    private static final String RECEIPT_STATUS_PENDING = "PENDING";
    private static final String RECEIPT_STATUS_CANCELED = "CANCELED";
    /** Below this a wallet runs low: a checkout warns its owner, and the admin dashboard lists the user. */
    public static final BigDecimal LOW_BALANCE_THRESHOLD = new BigDecimal("50000");

    private static final String KEY_TRANSACTION_ID = "transactionId";
    private static final String KEY_FINAL_AMOUNT = "finalAmount";
    private static final String KEY_REMAINING_BALANCE = "remainingBalance";
    private static final String KEY_WARNING = "warning";

    private static final String MSG_LOW_BALANCE = "CẢNH BÁO: Số dư ví của bạn sắp cạn kiệt (Dưới 50,000 VNĐ)!";
    private static final String MSG_INSUFFICIENT_BALANCE = "GIAO DỊCH THẤT BẠI: Số dư ví không đủ!";
    private static final String MSG_CATEGORY_NOT_FOUND = "Không tìm thấy danh mục";
    private static final String MSG_CATEGORY_REQUIRED = "Thiếu danh mục (categoryId hoặc categoryName)";
    private static final String MSG_TRANSACTION_NOT_FOUND = "Không tìm thấy giao dịch";
    private static final String MSG_TRANSACTION_FORBIDDEN = "Bạn không có quyền thao tác trên giao dịch này";
    private static final String MSG_RECEIPT_NOT_FOUND = "Không tìm thấy hóa đơn";
    private static final String MSG_RECEIPT_FORBIDDEN = "Bạn không có quyền hủy hóa đơn này";
    private static final String MSG_RECEIPT_NOT_PENDING = "Chỉ có thể hủy hóa đơn đang ở trạng thái Chờ xử lý";
    private static final String MSG_RECEIPT_UNLINKED = "Hóa đơn chưa gắn với giao dịch nào nên không thể xác nhận";
    private static final String MSG_RECEIPT_CANCELED = "Hóa đơn đã bị hủy nên không thể xác nhận";
    private static final String MSG_RECEIPT_NO_TOTAL = "Hóa đơn chưa có tổng tiền hợp lệ nên không thể đối soát";
    private static final String MSG_USER_NOT_FOUND = "Không tìm thấy người dùng";

    @Autowired
    private ReceiptRepository receiptRepo;
    @Autowired
    private TransactionRepository transactionRepo;
    @Autowired
    private UserRepository userRepo;
    @Autowired
    private CategoryRepository categoryRepo;
    @Autowired
    private vn.edu.huit.finance.core.Service.AuditLogService auditLogService;
    @Autowired
    private CurrentUserProvider currentUserProvider;
    @Autowired
    private ReceiptProcessingService receiptProcessingService;
    @Autowired
    private BudgetAlertService budgetAlertService;
    @Autowired
    private UserStatisticsService userStatisticsService;
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Records one transaction and moves the owner's wallet by its amount, all or nothing. A request that carries a client
     * id is idempotent: the first one with a given id (per user) creates the transaction, and every later one is answered
     * with that same transaction, without the wallet, the budget alerts, the receipt or the audit log being touched again,
     * whatever the rest of its body says. The wallet row is locked before the id is looked up, so two requests that arrive
     * together (a double tap, a retry while the first attempt is still running) run one after the other and the second
     * one finds what the first one committed; the unique key on user and client id backs that up in the database.
     */
    @Transactional
    public Map<String, Object> createTransaction(TransactionRequestDto request, Long userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy User"));

        String clientId = normalizeClientId(request.getClientId());
        if (clientId != null) {
            user = lockWallet(user);
            Optional<Transaction> original = transactionRepo.findByUserIdAndClientId(userId, clientId);
            if (original.isPresent()) {
                log.info("Duplicate checkout answered with the original transaction, userId={}, transactionId={}",
                        userId, original.get().getId());
                return checkoutResponse(original.get(), user);
            }
        }

        Category category = resolveCategory(request.getCategoryId(), request.getCategoryName(), request.isExpense());

        BigDecimal finalAmount = request.getAmount();

        if (request.isExpense()) {
            if (user.getWalletBalance().compareTo(finalAmount) < 0) {
                throw new RuntimeException(MSG_INSUFFICIENT_BALANCE);
            }
            user.setWalletBalance(user.getWalletBalance().subtract(finalAmount));
        } else {
            user.setWalletBalance(user.getWalletBalance().add(finalAmount));
        }
        userRepo.save(user);

        Transaction transaction = new Transaction();
        transaction.setAmount(finalAmount);
        transaction.setNote(request.getNote());
        transaction.setLocation(request.getLocation());
        transaction.setPaymentMethod(request.getPaymentMethod());

        transaction.setTransactionDate(request.getTransactionDate());
        transaction.setExpense(request.isExpense());
        transaction.setRawReceiptData(request.getRawReceiptData());
        transaction.setClientId(clientId);

        transaction.setUser(user);
        transaction.setCategory(category);
        transaction.setCategoryName(category.getName());

        if (request.getAdditionalImages() != null && !request.getAdditionalImages().isEmpty()) {
            try {
                String imagesJson = new ObjectMapper().writeValueAsString(request.getAdditionalImages());
                transaction.setAdditionalImages(imagesJson);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Lỗi xử lý hình ảnh đính kèm");
            }
        }

        if (request.getRawReceiptData() != null && !request.getRawReceiptData().isBlank()) {
            receiptProcessingService.attachReceipt(transaction, request.getRawReceiptData(), request.getImageUrl());
        }

        transactionRepo.save(transaction);
        budgetAlertService.notifyAfterCreate(transaction);
        eventPublisher.publishEvent(StatisticsChangedEvent.of(StatisticsChange.TRANSACTION_TOTALS, userId));

        try {
            if (request.getRawReceiptData() != null && !request.getRawReceiptData().isEmpty()) {
                auditLogService.logAction(user.getEmail(), "CREATE_RECEIPT", "Quét hóa đơn AI & Lưu giao dịch: " + finalAmount + " VNĐ");
            } else {
                auditLogService.logAction(user.getEmail(), "CREATE_TRANSACTION", "Thêm giao dịch thủ công: " + finalAmount + " VNĐ");
            }
        } catch (Exception e) {
        }

        return checkoutResponse(transaction, user);
    }

    /** Same as {@link #createTransaction} for the signed-in caller. Transactional itself, because it calls that method directly. */
    @Transactional
    public Map<String, Object> createMyTransaction(TransactionRequestDto request) {
        return createTransaction(request, currentUserProvider.getCurrentUser().getId());
    }

    /**
     * Changes the given fields of one of the caller's transactions and moves the wallet by the difference, all or nothing:
     * the wallet is written and flushed first and the transaction after it, so a failure of the second write is undone
     * by the rollback together with the first. The wallet row is locked while it is recalculated, so two edits made at
     * the same time cannot overwrite each other's balance.
     */
    @Transactional
    public TransactionResponseDto updateTransaction(Long transactionId, TransactionUpdateRequestDto request) {
        User caller = currentUserProvider.getCurrentUser();
        Transaction transaction = findOwnedTransaction(transactionId, caller.getId());
        // Taken before anything changes: the budget alert needs to know what this transaction counted for until now.
        BudgetAlertService.SpendingSnapshot spendingBefore = BudgetAlertService.SpendingSnapshot.of(transaction);

        BigDecimal newAmount = request.getAmount() != null ? request.getAmount() : transaction.getAmount();
        boolean newExpense = request.getExpense() != null ? request.getExpense() : transaction.isExpense();
        // Looked up before anything is written, so a request that is going to be rejected has not touched the wallet.
        Category newCategory = hasCategory(request)
                ? resolveCategory(request.getCategoryId(), request.getCategoryName(), newExpense)
                : null;

        BigDecimal walletChange = signedAmount(newAmount, newExpense)
                .subtract(signedAmount(transaction.getAmount(), transaction.isExpense()));
        boolean dateChanges = request.getTransactionDate() != null
                && !request.getTransactionDate().equals(transaction.getTransactionDate());
        if (walletChange.signum() != 0) {
            User owner = lockWallet(caller);
            transaction.setUser(owner);
            moveWallet(owner, walletChange);
        }

        transaction.setAmount(newAmount);
        transaction.setExpense(newExpense);
        if (newCategory != null) {
            transaction.setCategory(newCategory);
            transaction.setCategoryName(newCategory.getName());
        }
        if (request.getNote() != null) {
            transaction.setNote(request.getNote().isBlank() ? null : request.getNote().trim());
        }
        if (request.getTransactionDate() != null) {
            transaction.setTransactionDate(request.getTransactionDate());
        }
        if (request.getPaymentMethod() != null) {
            transaction.setPaymentMethod(request.getPaymentMethod().trim());
        }
        Transaction saved = transactionRepo.save(transaction);
        budgetAlertService.notifyAfterUpdate(saved, spendingBefore);
        // Amount and direction move the wallet and every total, the date moves the monthly counts; anything else only
        // shows on the owner's own dashboard.
        StatisticsChange change = walletChange.signum() != 0 || dateChanges
                ? StatisticsChange.TRANSACTION_TOTALS
                : StatisticsChange.TRANSACTION_DETAILS;
        eventPublisher.publishEvent(StatisticsChangedEvent.of(change, caller.getId()));

        log.info("Transaction updated, userId={}, transactionId={}, walletChange={}", caller.getId(), transactionId, walletChange);
        return TransactionResponseDto.from(saved);
    }

    /** Deletes one of the caller's transactions and gives its effect back to the wallet, all or nothing. */
    @Transactional
    public void deleteTransaction(Long transactionId) {
        User caller = currentUserProvider.getCurrentUser();
        Transaction transaction = findOwnedTransaction(transactionId, caller.getId());

        BigDecimal undo = signedAmount(transaction.getAmount(), transaction.isExpense()).negate();
        User owner = lockWallet(caller);
        writeWallet(owner, owner.getWalletBalance().add(undo));
        transactionRepo.delete(transaction);
        eventPublisher.publishEvent(StatisticsChangedEvent.of(StatisticsChange.TRANSACTION_TOTALS, caller.getId()));

        log.info("Transaction deleted, userId={}, transactionId={}, walletChange={}", caller.getId(), transactionId, undo);
    }

    @Transactional
    public void cancelMyReceipt(Long receiptId) {
        Long userId = currentUserProvider.getCurrentUser().getId();
        Receipt receipt = receiptRepo.findById(receiptId).orElseThrow(() -> {
            log.warn("Receipt not found, userId={}, receiptId={}", userId, receiptId);
            return new ReceiptNotFoundException(MSG_RECEIPT_NOT_FOUND);
        });

        // A receipt that no transaction carries yet has no owner, so nobody may cancel it through here.
        Transaction owningTransaction = receipt.getTransaction();
        if (owningTransaction == null || !owningTransaction.getUser().getId().equals(userId)) {
            log.warn("Receipt access denied, userId={}, receiptId={}", userId, receiptId);
            throw new TransactionAccessDeniedException(MSG_RECEIPT_FORBIDDEN);
        }
        if (!RECEIPT_STATUS_PENDING.equalsIgnoreCase(receipt.getStatus())) {
            throw new IllegalStateException(MSG_RECEIPT_NOT_PENDING);
        }

        receipt.setStatus(RECEIPT_STATUS_CANCELED);
        receiptRepo.save(receipt);
        eventPublisher.publishEvent(StatisticsChangedEvent.of(StatisticsChange.RECEIPT_STATUS, userId));
        log.info("Receipt canceled, userId={}, receiptId={}", userId, receiptId);
    }

    /**
     * For administrators only (the caller of this method has to check that): verifies a receipt and makes the transaction
     * that carries it agree with it. The final total of the receipt (what the AI read, or what its owner sent through
     * feedback) becomes the amount of the transaction and the owner's wallet moves by the difference, all or nothing: the
     * wallet is locked and written first and the rows after it, so a failure of any write is undone together with the
     * others. The transaction and the receipt are read again once the wallet is locked, so two requests that approve the
     * same receipt at the same time move the wallet once, and approving a receipt again changes nothing.
     */
    @Transactional
    public void approveReceipt(Long receiptId) {
        Receipt receipt = receiptRepo.findDetailedById(receiptId).orElseThrow(() -> {
            log.warn("Receipt not found for approval, receiptId={}", receiptId);
            return new ReceiptNotFoundException(MSG_RECEIPT_NOT_FOUND);
        });
        Transaction transaction = receipt.getTransaction();
        if (transaction == null) {
            log.warn("Receipt approval refused, no transaction is linked, receiptId={}", receiptId);
            throw new IllegalStateException(MSG_RECEIPT_UNLINKED);
        }

        User owner = lockWallet(transaction.getUser());
        // What another request committed while this one waited for the lock (its own approval, a cancellation) counts:
        // the refresh also reloads the receipt, which the transaction cascades to.
        entityManager.refresh(transaction);
        BigDecimal finalAmount = approvableTotal(receipt);

        BigDecimal previousAmount = transaction.getAmount();
        BudgetAlertService.SpendingSnapshot spendingBefore = BudgetAlertService.SpendingSnapshot.of(transaction);
        BigDecimal walletChange = signedAmount(finalAmount, transaction.isExpense())
                .subtract(signedAmount(previousAmount, transaction.isExpense()));

        transaction.setUser(owner);
        if (walletChange.signum() != 0) {
            moveWallet(owner, walletChange);
        }
        transaction.setAmount(finalAmount);
        receipt.setStatus(ReceiptStatus.SUCCESS.name());
        Transaction saved = transactionRepo.save(transaction);
        receiptRepo.save(receipt);
        if (walletChange.signum() != 0) {
            budgetAlertService.notifyAfterUpdate(saved, spendingBefore);
        }
        eventPublisher.publishEvent(StatisticsChangedEvent.of(walletChange.signum() != 0
                ? StatisticsChange.TRANSACTION_TOTALS
                : StatisticsChange.RECEIPT_STATUS, owner.getId()));

        log.info("Receipt approved, receiptId={}, transactionId={}, userId={}, previousAmount={}, finalAmount={}, walletChange={}",
                receiptId, saved.getId(), owner.getId(), previousAmount, finalAmount, walletChange);
    }

    @Transactional(readOnly = true)
    public List<TransactionResponseDto> getMyTransactions() {
        Long userId = currentUserProvider.getCurrentUser().getId();
        return transactionRepo.findByUserIdOrderByTransactionDateDesc(userId).stream()
                .map(TransactionResponseDto::from)
                .toList();
    }

    /** Searches the caller's own transactions; the sort field must be one of {@link TransactionSortField}. */
    @Transactional(readOnly = true)
    public List<TransactionResponseDto> filterMyTransactions(TransactionFilterRequestDto filter) {
        Long userId = currentUserProvider.getCurrentUser().getId();
        Sort.Direction direction = Sort.Direction.fromOptionalString(filter.getSortDirection()).orElse(Sort.Direction.DESC);
        Sort sort = Sort.by(direction, TransactionSortField.fromProperty(filter.getSortBy()).getProperty());

        return transactionRepo.filterTransactions(userId, filter.getCategoryId(), filter.getMinPrice(),
                        filter.getMaxPrice(), filter.getVendorName(), sort).stream()
                .map(TransactionResponseDto::from)
                .toList();
    }

    /** Cached per user in Redis: the id is the caller's, from the SecurityContext, so nobody reads another user's entry. */
    @Transactional(readOnly = true)
    public List<PopularVendorResponseDto> getMyPopularVendors() {
        return userStatisticsService.popularVendorsOf(currentUserProvider.getCurrentUser().getId());
    }

    /** Cached per user in Redis: the id is the caller's, from the SecurityContext, so nobody reads another user's entry. */
    @Transactional(readOnly = true)
    public AppDashboardResponseDto getMyDashboard() {
        return userStatisticsService.dashboardOf(currentUserProvider.getCurrentUser().getId());
    }

    /**
     * For administrators only (the caller of this method has to check that): the statistics of any user. The user is
     * looked up first, so an unknown or deleted user is a 404 even while an entry of theirs is still cached.
     */
    @Transactional(readOnly = true)
    public List<PopularVendorResponseDto> getPopularVendorsOf(Long userId) {
        requireExistingUser(userId);
        return userStatisticsService.popularVendorsOf(userId);
    }

    /** For administrators only (the caller of this method has to check that): the home page data of any user. */
    @Transactional(readOnly = true)
    public AppDashboardResponseDto getDashboardOf(Long userId) {
        requireExistingUser(userId);
        return userStatisticsService.dashboardOf(userId);
    }

    private void requireExistingUser(Long userId) {
        if (!userRepo.existsById(userId)) {
            log.warn("User not found for a statistics request, userId={}", userId);
            throw new UserNotFoundException(MSG_USER_NOT_FOUND);
        }
    }

    /** A transaction that does not exist is a 404, one that exists but is somebody else's a 403. */
    private Transaction findOwnedTransaction(Long transactionId, Long userId) {
        Transaction transaction = transactionRepo.findDetailedById(transactionId).orElseThrow(() -> {
            log.warn("Transaction not found, userId={}, transactionId={}", userId, transactionId);
            return new TransactionNotFoundException(MSG_TRANSACTION_NOT_FOUND);
        });
        if (!transaction.getUser().getId().equals(userId)) {
            log.warn("Transaction access denied, userId={}, transactionId={}", userId, transactionId);
            throw new TransactionAccessDeniedException(MSG_TRANSACTION_FORBIDDEN);
        }
        return transaction;
    }

    /** The total a receipt is approved with: the one it holds now, positive and plausible. A canceled receipt is not approved at all. */
    private BigDecimal approvableTotal(Receipt receipt) {
        if (ReceiptStatus.CANCELED.matches(receipt.getStatus())) {
            log.warn("Receipt approval refused, the receipt is canceled, receiptId={}", receipt.getId());
            throw new IllegalStateException(MSG_RECEIPT_CANCELED);
        }
        BigDecimal total = OcrService.validTotal(receipt.getExtractedTotal());
        // A total that rounds to zero (0.004) passes the rule above but is no total either.
        if (total == null || total.signum() <= 0) {
            log.warn("Receipt approval refused, the receipt has no usable total, receiptId={}", receipt.getId());
            throw new IllegalStateException(MSG_RECEIPT_NO_TOTAL);
        }
        return total;
    }

    /** What a checkout answers: the transaction, and the wallet as it is now. A repeated checkout gets the same shape. */
    private static Map<String, Object> checkoutResponse(Transaction transaction, User owner) {
        boolean lowBalance = owner.getWalletBalance().compareTo(LOW_BALANCE_THRESHOLD) < 0;

        Map<String, Object> responseData = new HashMap<>();
        responseData.put(KEY_TRANSACTION_ID, transaction.getId());
        responseData.put(KEY_FINAL_AMOUNT, transaction.getAmount());
        responseData.put(KEY_REMAINING_BALANCE, owner.getWalletBalance());
        responseData.put(KEY_WARNING, lowBalance ? MSG_LOW_BALANCE : "");
        return responseData;
    }

    /** The client id as it is stored and looked up: trimmed, and null when the request sent none or only blanks. */
    private static String normalizeClientId(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return null;
        }
        return clientId.trim();
    }

    private static boolean hasCategory(TransactionUpdateRequestDto request) {
        return request.getCategoryId() != null
                || (request.getCategoryName() != null && !request.getCategoryName().isBlank());
    }

    /** By id, or by name; a name that does not exist yet creates the category, typed after the transaction. */
    private Category resolveCategory(Long categoryId, String categoryName, boolean isExpense) {
        if (categoryId != null) {
            return categoryRepo.findById(categoryId)
                    .orElseThrow(() -> new InvalidCategoryException(MSG_CATEGORY_NOT_FOUND));
        }
        if (categoryName != null && !categoryName.isBlank()) {
            String name = categoryName.trim();
            return categoryRepo.findByName(name).orElseGet(() -> {
                Category created = new Category();
                created.setName(name);
                created.setType(isExpense ? CATEGORY_TYPE_EXPENSE : CATEGORY_TYPE_INCOME);
                return categoryRepo.save(created);
            });
        }
        throw new InvalidCategoryException(MSG_CATEGORY_REQUIRED);
    }

    /** What a transaction does to the wallet: an income adds its amount, an expense takes it away. */
    private static BigDecimal signedAmount(BigDecimal amount, boolean isExpense) {
        return isExpense ? amount.negate() : amount;
    }

    /**
     * Locks the wallet row until the end of the transaction and returns it as it is in the database right now. The copy
     * read earlier in the request is forgotten first, because a plain lock would leave it in the persistence context
     * with the balance it had when it was read, and whatever another request committed in between would be lost.
     * (A refresh is not an option: User.transactions cascades ALL, so it would load every transaction of the user.)
     */
    private User lockWallet(User user) {
        entityManager.detach(user);
        User locked = entityManager.find(User.class, user.getId(), LockModeType.PESSIMISTIC_WRITE);
        if (locked == null) {
            throw new UserNotFoundException(MSG_USER_NOT_FOUND);
        }
        return locked;
    }

    /** Adds [change] to the wallet and writes it now. A change that lowers the balance may not take it below zero. */
    private void moveWallet(User owner, BigDecimal change) {
        BigDecimal newBalance = owner.getWalletBalance().add(change);
        if (change.signum() < 0 && newBalance.signum() < 0) {
            log.warn("Wallet change rejected: insufficient balance, userId={}, balance={}, change={}",
                    owner.getId(), owner.getWalletBalance(), change);
            throw new InsufficientBalanceException(MSG_INSUFFICIENT_BALANCE);
        }
        writeWallet(owner, newBalance);
    }

    private void writeWallet(User owner, BigDecimal newBalance) {
        owner.setWalletBalance(newBalance);
        userRepo.saveAndFlush(owner);
    }
}