package vn.edu.huit.finance.core.Service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
import vn.edu.huit.finance.core.Entity.User;
import vn.edu.huit.finance.core.Exception.InsufficientBalanceException;
import vn.edu.huit.finance.core.Exception.InvalidCategoryException;
import vn.edu.huit.finance.core.Exception.InvalidSortFieldException;
import vn.edu.huit.finance.core.Exception.ReceiptNotFoundException;
import vn.edu.huit.finance.core.Exception.TransactionAccessDeniedException;
import vn.edu.huit.finance.core.Exception.TransactionNotFoundException;
import vn.edu.huit.finance.core.Exception.UserNotFoundException;
import vn.edu.huit.finance.core.Repository.CategoryRepository;
import vn.edu.huit.finance.core.Repository.ReceiptRepository;
import vn.edu.huit.finance.core.Repository.TransactionRepository;
import vn.edu.huit.finance.core.Repository.UserRepository;
import vn.edu.huit.finance.core.Security.CurrentUserProvider;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    private static final Long ALICE_ID = 1L;
    private static final Long BOB_ID = 2L;
    private static final Long TX_ID = 100L;
    private static final Long FOOD_ID = 10L;
    private static final BigDecimal WALLET = new BigDecimal("1000000");
    private static final BigDecimal HUNDRED_THOUSAND = new BigDecimal("100000");

    @Mock private ReceiptRepository receiptRepo;
    @Mock private TransactionRepository transactionRepo;
    @Mock private UserRepository userRepo;
    @Mock private CategoryRepository categoryRepo;
    @Mock private AuditLogService auditLogService;
    @Mock private CurrentUserProvider currentUserProvider;
    @Mock private ReceiptProcessingService receiptProcessingService;
    @Mock private BudgetAlertService budgetAlertService;
    @Mock private EntityManager entityManager;
    @InjectMocks private TransactionService service;

    private final User alice = user(ALICE_ID, WALLET);
    private final User bob = user(BOB_ID, WALLET);
    private final Category food = category(FOOD_ID, "Ăn uống", "EXPENSE");

    // ---------------------------------------------------------------------------------------------------------- fixtures

    private static User user(Long id, BigDecimal wallet) {
        User user = new User();
        user.setId(id);
        user.setUsername("user" + id);
        user.setEmail("user" + id + "@example.com");
        user.setWalletBalance(wallet);
        return user;
    }

    private static Category category(Long id, String name, String type) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        category.setType(type);
        return category;
    }

    private Transaction transaction(User owner, BigDecimal amount, boolean isExpense) {
        Transaction transaction = new Transaction();
        transaction.setId(TX_ID);
        transaction.setUser(owner);
        transaction.setAmount(amount);
        transaction.setExpense(isExpense);
        transaction.setCategory(food);
        transaction.setCategoryName(food.getName());
        transaction.setNote("Bún bò");
        transaction.setLocation("Quán A");
        transaction.setPaymentMethod("Ví chính");
        transaction.setStatus("DONE");
        transaction.setVoucherCode("SALE10");
        transaction.setTransactionDate(LocalDateTime.of(2026, 9, 1, 12, 0));
        return transaction;
    }

    private static TransactionUpdateRequestDto update() {
        return new TransactionUpdateRequestDto();
    }

    private static TransactionUpdateRequestDto updateAmount(String amount) {
        TransactionUpdateRequestDto request = update();
        request.setAmount(new BigDecimal(amount));
        return request;
    }

    private void signedInAsAlice() {
        when(currentUserProvider.getCurrentUser()).thenReturn(alice);
        // Locking the wallet reloads the row; unless a test says otherwise the database still holds what Alice holds.
        lenient().when(entityManager.find(User.class, ALICE_ID, LockModeType.PESSIMISTIC_WRITE)).thenReturn(alice);
    }

    /** Alice is signed in and owns an expense of 100,000 that the wallet already reflects. */
    private Transaction aliceHasOwnTransaction(BigDecimal amount, boolean isExpense) {
        signedInAsAlice();
        Transaction transaction = transaction(alice, amount, isExpense);
        when(transactionRepo.findDetailedById(TX_ID)).thenReturn(Optional.of(transaction));
        lenient().when(transactionRepo.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        return transaction;
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }

    private void verifyWalletNotLocked() {
        verify(entityManager, never()).detach(any());
        verify(entityManager, never()).find(eq(User.class), any(), eq(LockModeType.PESSIMISTIC_WRITE));
    }

    private void verifyNothingWasWritten() {
        verify(userRepo, never()).saveAndFlush(any());
        verify(userRepo, never()).save(any());
        verify(transactionRepo, never()).save(any());
        verify(transactionRepo, never()).delete(any());
        verifyWalletNotLocked();
    }

    // -------------------------------------------------------------------------------------- update: the wallet arithmetic

    static Stream<Arguments> walletCases() {
        // old amount, was expense, new amount (null = unchanged), new type (null = unchanged), wallet afterwards
        return Stream.of(
                Arguments.of("100000", true, "150000", null, "950000"),   // spend 50,000 more
                Arguments.of("100000", true, "40000", null, "1060000"),   // spend 60,000 less
                Arguments.of("100000", false, "250000", null, "1150000"), // earn 150,000 more
                Arguments.of("100000", false, "30000", null, "930000"),   // earn 70,000 less
                Arguments.of("100000", true, null, false, "1200000"),     // expense became income: +100,000 back, +100,000 in
                Arguments.of("100000", false, null, true, "800000"),      // income became expense
                Arguments.of("100000", true, "30000", false, "1130000"),  // expense of 100,000 became an income of 30,000
                Arguments.of("100000", false, "30000", true, "870000"),   // income of 100,000 became an expense of 30,000
                Arguments.of("100000", true, "100000.50", null, "999999.50"));
    }

    @ParameterizedTest
    @MethodSource("walletCases")
    void theWalletMovesByTheDifferenceOfWhatTheTransactionDid(
            String oldAmount, boolean wasExpense, String newAmount, Boolean newType, String walletAfter) {
        Transaction transaction = aliceHasOwnTransaction(new BigDecimal(oldAmount), wasExpense);
        TransactionUpdateRequestDto request = update();
        if (newAmount != null) request.setAmount(new BigDecimal(newAmount));
        request.setExpense(newType);

        service.updateTransaction(TX_ID, request);

        assertMoney(walletAfter, alice.getWalletBalance());
        verify(userRepo).saveAndFlush(alice);
        assertMoney(newAmount != null ? newAmount : oldAmount, transaction.getAmount());
        assertEquals(newType != null ? newType : wasExpense, transaction.isExpense());
    }

    @Test
    void sendingTheSameAmountAndTypeAgainDoesNotTouchTheWallet() {
        aliceHasOwnTransaction(HUNDRED_THOUSAND, true);
        TransactionUpdateRequestDto request = updateAmount("100000.00");
        request.setExpense(true);

        service.updateTransaction(TX_ID, request);

        assertMoney("1000000", alice.getWalletBalance());
        verify(userRepo, never()).saveAndFlush(any());
        verifyWalletNotLocked();
    }

    @Test
    void changingOnlyTheDescriptionOfATransactionDoesNotTouchTheWallet() {
        Transaction transaction = aliceHasOwnTransaction(HUNDRED_THOUSAND, true);
        TransactionUpdateRequestDto request = update();
        request.setNote("Bún chả");
        request.setPaymentMethod("Tiền mặt");
        request.setTransactionDate(LocalDateTime.of(2026, 9, 2, 8, 30));

        service.updateTransaction(TX_ID, request);

        assertMoney("1000000", alice.getWalletBalance());
        verify(userRepo, never()).saveAndFlush(any());
        verifyWalletNotLocked();
        verify(transactionRepo).save(transaction);
    }

    // ------------------------------------------------------------------------------ update: never below zero, but no more

    @Test
    void anExpenseMayUseUpTheWalletToTheLastDong() {
        alice.setWalletBalance(new BigDecimal("100000"));
        aliceHasOwnTransaction(HUNDRED_THOUSAND, true);

        service.updateTransaction(TX_ID, updateAmount("200000"));

        assertMoney("0", alice.getWalletBalance());
    }

    @Test
    void anExpenseThatTakesTheWalletBelowZeroIsRefusedAndNothingChanges() {
        alice.setWalletBalance(new BigDecimal("99999.99"));
        Transaction transaction = aliceHasOwnTransaction(HUNDRED_THOUSAND, true);

        InsufficientBalanceException refused = assertThrows(InsufficientBalanceException.class,
                () -> service.updateTransaction(TX_ID, updateAmount("200000")));

        assertEquals("GIAO DỊCH THẤT BẠI: Số dư ví không đủ!", refused.getMessage());
        assertMoney("99999.99", alice.getWalletBalance());
        assertMoney("100000", transaction.getAmount());
        verify(userRepo, never()).saveAndFlush(any());
        verify(transactionRepo, never()).save(any());
    }

    @Test
    void turningAnIncomeIntoAnExpenseTheWalletCannotCarryIsRefused() {
        alice.setWalletBalance(new BigDecimal("150000"));
        Transaction transaction = aliceHasOwnTransaction(HUNDRED_THOUSAND, false);
        TransactionUpdateRequestDto request = update();
        request.setExpense(true); // -100,000 instead of +100,000: 150,000 - 200,000 < 0

        assertThrows(InsufficientBalanceException.class, () -> service.updateTransaction(TX_ID, request));

        assertFalse(transaction.isExpense(), "the type must not change");
        assertMoney("150000", alice.getWalletBalance());
    }

    @Test
    void aWalletThatIsAlreadyNegativeStillAllowsEditsThatDoNotLowerIt() {
        alice.setWalletBalance(new BigDecimal("-5000"));
        aliceHasOwnTransaction(HUNDRED_THOUSAND, true);

        TransactionUpdateRequestDto onlyANote = update();
        onlyANote.setNote("Ghi chú mới");
        service.updateTransaction(TX_ID, onlyANote);
        assertMoney("-5000", alice.getWalletBalance());

        service.updateTransaction(TX_ID, updateAmount("60000")); // spends 40,000 less
        assertMoney("35000", alice.getWalletBalance());
    }

    @Test
    void anEditThatRaisesAnOverdrawnWalletIsAllowedEvenIfItStaysBelowZero() {
        alice.setWalletBalance(new BigDecimal("-100000"));
        aliceHasOwnTransaction(HUNDRED_THOUSAND, true);

        service.updateTransaction(TX_ID, updateAmount("60000")); // spends 40,000 less, so the wallet rises by 40,000

        assertMoney("-60000", alice.getWalletBalance());
        verify(userRepo).saveAndFlush(alice);
    }

    @Test
    void aWalletThatIsAlreadyNegativeDoesNotAllowSpendingMore() {
        alice.setWalletBalance(new BigDecimal("-5000"));
        aliceHasOwnTransaction(HUNDRED_THOUSAND, true);

        assertThrows(InsufficientBalanceException.class, () -> service.updateTransaction(TX_ID, updateAmount("120000")));

        assertMoney("-5000", alice.getWalletBalance());
    }

    // ----------------------------------------------------------------------------------- update: order of the writes

    @Test
    void theWalletIsLockedAndReloadedThenWrittenAndFlushedBeforeTheTransactionIsSaved() {
        Transaction transaction = aliceHasOwnTransaction(HUNDRED_THOUSAND, true);

        service.updateTransaction(TX_ID, updateAmount("150000"));

        InOrder order = inOrder(entityManager, userRepo, transactionRepo);
        order.verify(entityManager).detach(alice);
        order.verify(entityManager).find(User.class, ALICE_ID, LockModeType.PESSIMISTIC_WRITE);
        order.verify(userRepo).saveAndFlush(alice);
        order.verify(transactionRepo).save(transaction);
    }

    @Test
    void theBalanceUsedIsTheOneReadUnderTheLockNotTheOneReadEarlier() {
        // Another request committed +300,000 after this one first read the user: only the locked reload shows it.
        Transaction transaction = aliceHasOwnTransaction(HUNDRED_THOUSAND, true);
        User reloaded = user(ALICE_ID, new BigDecimal("1300000"));
        when(entityManager.find(User.class, ALICE_ID, LockModeType.PESSIMISTIC_WRITE)).thenReturn(reloaded);

        service.updateTransaction(TX_ID, updateAmount("150000"));

        assertMoney("1250000", reloaded.getWalletBalance());
        assertMoney("1000000", alice.getWalletBalance()); // the stale copy is never written
        verify(userRepo).saveAndFlush(reloaded);
        verify(userRepo, never()).saveAndFlush(alice);
        assertSame(reloaded, transaction.getUser());
    }

    @Test
    void theInsufficientBalanceCheckUsesTheLockedBalanceToo() {
        aliceHasOwnTransaction(HUNDRED_THOUSAND, true);
        User reloaded = user(ALICE_ID, new BigDecimal("20000")); // spent elsewhere in the meantime
        when(entityManager.find(User.class, ALICE_ID, LockModeType.PESSIMISTIC_WRITE)).thenReturn(reloaded);

        assertThrows(InsufficientBalanceException.class, () -> service.updateTransaction(TX_ID, updateAmount("150000")));

        assertMoney("20000", reloaded.getWalletBalance());
        verify(userRepo, never()).saveAndFlush(any());
    }

    @Test
    void aWalletOwnerWhoVanishedBeforeTheLockIsReportedAndNothingIsWritten() {
        Transaction transaction = aliceHasOwnTransaction(HUNDRED_THOUSAND, true);
        when(entityManager.find(User.class, ALICE_ID, LockModeType.PESSIMISTIC_WRITE)).thenReturn(null);

        assertThrows(UserNotFoundException.class, () -> service.updateTransaction(TX_ID, updateAmount("150000")));

        assertMoney("100000", transaction.getAmount());
        verify(userRepo, never()).saveAndFlush(any());
        verify(transactionRepo, never()).save(any());
    }

    @Test
    void aFailureWhileSavingTheTransactionIsNotSwallowed() {
        aliceHasOwnTransaction(HUNDRED_THOUSAND, true);
        when(transactionRepo.save(any(Transaction.class))).thenThrow(new IllegalStateException("disk full"));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.updateTransaction(TX_ID, updateAmount("150000")));

        assertEquals("disk full", error.getMessage());
    }

    // ---------------------------------------------------------------------------------------- update: who may do it

    @Test
    void aTransactionOfSomebodyElseIsRefusedWithoutAnyWriteOrLock() {
        signedInAsAlice();
        Transaction bobs = transaction(bob, HUNDRED_THOUSAND, true);
        when(transactionRepo.findDetailedById(TX_ID)).thenReturn(Optional.of(bobs));

        assertThrows(TransactionAccessDeniedException.class, () -> service.updateTransaction(TX_ID, updateAmount("999")));

        assertMoney("100000", bobs.getAmount());
        assertMoney("1000000", bob.getWalletBalance());
        assertMoney("1000000", alice.getWalletBalance());
        verifyNothingWasWritten();
        verifyNoInteractions(categoryRepo);
    }

    @Test
    void anAdministratorIsNotTheOwnerEither() {
        User admin = user(9L, WALLET);
        admin.setRole("ROLE_ADMIN");
        when(currentUserProvider.getCurrentUser()).thenReturn(admin);
        when(transactionRepo.findDetailedById(TX_ID)).thenReturn(Optional.of(transaction(alice, HUNDRED_THOUSAND, true)));

        assertThrows(TransactionAccessDeniedException.class, () -> service.updateTransaction(TX_ID, updateAmount("999")));

        verifyNothingWasWritten();
    }

    @Test
    void aTransactionThatDoesNotExistIsNotFound() {
        signedInAsAlice();
        when(transactionRepo.findDetailedById(TX_ID)).thenReturn(Optional.empty());

        assertThrows(TransactionNotFoundException.class, () -> service.updateTransaction(TX_ID, updateAmount("999")));

        verifyNothingWasWritten();
    }

    @Test
    void theOwnerIsTheSignedInUserAndNeverSomethingTheRequestSays() {
        Transaction transaction = aliceHasOwnTransaction(HUNDRED_THOUSAND, true);

        service.updateTransaction(TX_ID, updateAmount("150000"));

        assertSame(alice, transaction.getUser());
        verify(currentUserProvider).getCurrentUser();
        verify(userRepo, never()).findById(anyLong());
    }

    // ----------------------------------------------------------------------------------------------- update: the fields

    @Test
    void onlyTheFieldsOfTheRequestChange() {
        Transaction transaction = aliceHasOwnTransaction(HUNDRED_THOUSAND, true);
        LocalDateTime date = transaction.getTransactionDate();

        service.updateTransaction(TX_ID, update());

        assertEquals("Bún bò", transaction.getNote());
        assertEquals(date, transaction.getTransactionDate());
        assertEquals("Ví chính", transaction.getPaymentMethod());
        assertSame(food, transaction.getCategory());
        assertEquals("Quán A", transaction.getLocation());
        assertEquals("DONE", transaction.getStatus());
        assertEquals("SALE10", transaction.getVoucherCode());
        verifyNoInteractions(categoryRepo);
        verify(userRepo, never()).saveAndFlush(any());
    }

    @Test
    void theEditableFieldsAreAppliedAndTrimmed() {
        Transaction transaction = aliceHasOwnTransaction(HUNDRED_THOUSAND, true);
        TransactionUpdateRequestDto request = update();
        request.setNote("  Bún chả  ");
        request.setPaymentMethod("  Tiền mặt ");
        LocalDateTime newDate = LocalDateTime.of(2026, 9, 3, 19, 45);
        request.setTransactionDate(newDate);

        TransactionResponseDto response = service.updateTransaction(TX_ID, request);

        assertEquals("Bún chả", transaction.getNote());
        assertEquals("Tiền mặt", transaction.getPaymentMethod());
        assertEquals(newDate, transaction.getTransactionDate());
        assertEquals("Bún chả", response.getTitle());
        assertEquals(newDate, response.getDate());
    }

    @Test
    void aBlankNoteClearsTheNoteAndTheTitleFallsBackToTheCategory() {
        Transaction transaction = aliceHasOwnTransaction(HUNDRED_THOUSAND, true);
        TransactionUpdateRequestDto request = update();
        request.setNote("   ");

        TransactionResponseDto response = service.updateTransaction(TX_ID, request);

        assertNull(transaction.getNote());
        assertEquals("Ăn uống", response.getTitle());
    }

    @Test
    void theEditedTransactionAnswersWithTheStatusOfItsReceipt() {
        Transaction transaction = aliceHasOwnTransaction(HUNDRED_THOUSAND, true);
        transaction.setReceipt(receipt(ReceiptStatus.SUCCESS.name(), transaction));
        TransactionUpdateRequestDto request = update();
        request.setNote("Bún chả");

        TransactionResponseDto response = service.updateTransaction(TX_ID, request);

        assertEquals(ReceiptStatus.SUCCESS.name(), response.getReceiptStatus());
    }

    @Test
    void theResponseDescribesTheTransactionAfterTheChange() {
        aliceHasOwnTransaction(HUNDRED_THOUSAND, true);
        TransactionUpdateRequestDto request = updateAmount("150000");
        request.setExpense(false);

        TransactionResponseDto response = service.updateTransaction(TX_ID, request);

        assertEquals(TX_ID, response.getId());
        assertMoney("150000", response.getAmount());
        assertFalse(response.isExpense());
        assertEquals("Ăn uống", response.getCategory());
    }

    // --------------------------------------------------------------------------------------------- update: the category

    @Test
    void aCategoryGivenByIdReplacesTheOldOne() {
        Transaction transaction = aliceHasOwnTransaction(HUNDRED_THOUSAND, true);
        Category transport = category(11L, "Di chuyển", "EXPENSE");
        when(categoryRepo.findById(11L)).thenReturn(Optional.of(transport));
        TransactionUpdateRequestDto request = update();
        request.setCategoryId(11L);

        TransactionResponseDto response = service.updateTransaction(TX_ID, request);

        assertSame(transport, transaction.getCategory());
        assertEquals("Di chuyển", transaction.getCategoryName());
        assertEquals("Di chuyển", response.getCategory());
        verify(userRepo, never()).saveAndFlush(any());
    }

    @Test
    void anUnknownCategoryIdIsRefusedBeforeAnythingIsWrittenEvenIfTheAmountChangesToo() {
        Transaction transaction = aliceHasOwnTransaction(HUNDRED_THOUSAND, true);
        when(categoryRepo.findById(99L)).thenReturn(Optional.empty());
        TransactionUpdateRequestDto request = updateAmount("150000");
        request.setCategoryId(99L);

        InvalidCategoryException refused = assertThrows(InvalidCategoryException.class,
                () -> service.updateTransaction(TX_ID, request));

        assertEquals("Không tìm thấy danh mục", refused.getMessage());
        assertMoney("1000000", alice.getWalletBalance());
        assertMoney("100000", transaction.getAmount());
        verifyNothingWasWritten();
    }

    @Test
    void aCategoryGivenByNameIsReusedWhenItExists() {
        Transaction transaction = aliceHasOwnTransaction(HUNDRED_THOUSAND, true);
        Category coffee = category(12L, "Cà phê", "EXPENSE");
        when(categoryRepo.findByName("Cà phê")).thenReturn(Optional.of(coffee));
        TransactionUpdateRequestDto request = update();
        request.setCategoryName("  Cà phê ");

        service.updateTransaction(TX_ID, request);

        assertSame(coffee, transaction.getCategory());
        verify(categoryRepo, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void aNewCategoryNameCreatesACategoryTypedAfterTheTransactionAsItWillBe(boolean expenseAfterwards) {
        Transaction transaction = aliceHasOwnTransaction(HUNDRED_THOUSAND, !expenseAfterwards);
        when(categoryRepo.findByName("Freelance")).thenReturn(Optional.empty());
        when(categoryRepo.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));
        TransactionUpdateRequestDto request = update();
        request.setCategoryName("Freelance");
        request.setExpense(expenseAfterwards);

        service.updateTransaction(TX_ID, request);

        ArgumentCaptor<Category> created = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepo).save(created.capture());
        assertEquals("Freelance", created.getValue().getName());
        assertEquals(expenseAfterwards ? "EXPENSE" : "INCOME", created.getValue().getType());
        assertEquals("Freelance", transaction.getCategoryName());
    }

    @Test
    void whenBothAreGivenTheCategoryIdWins() {
        aliceHasOwnTransaction(HUNDRED_THOUSAND, true);
        when(categoryRepo.findById(FOOD_ID)).thenReturn(Optional.of(food));
        TransactionUpdateRequestDto request = update();
        request.setCategoryId(FOOD_ID);
        request.setCategoryName("Something else");

        service.updateTransaction(TX_ID, request);

        verify(categoryRepo, never()).findByName(any());
    }

    @Test
    void aBlankCategoryNameMeansNoChangeOfCategory() {
        Transaction transaction = aliceHasOwnTransaction(HUNDRED_THOUSAND, true);
        TransactionUpdateRequestDto request = update();
        request.setCategoryName("   ");

        service.updateTransaction(TX_ID, request);

        assertSame(food, transaction.getCategory());
        verifyNoInteractions(categoryRepo);
    }

    // ------------------------------------------------------------------------------------------------------------ delete

    @Test
    void deletingAnExpenseGivesTheMoneyBack() {
        Transaction transaction = aliceHasOwnTransaction(HUNDRED_THOUSAND, true);

        service.deleteTransaction(TX_ID);

        assertMoney("1100000", alice.getWalletBalance());
        InOrder order = inOrder(entityManager, userRepo, transactionRepo);
        order.verify(entityManager).detach(alice);
        order.verify(entityManager).find(User.class, ALICE_ID, LockModeType.PESSIMISTIC_WRITE);
        order.verify(userRepo).saveAndFlush(alice);
        order.verify(transactionRepo).delete(transaction);
    }

    @Test
    void deletingAnIncomeTakesItBackOutOfTheWalletEvenBelowZero() {
        alice.setWalletBalance(new BigDecimal("30000"));
        Transaction transaction = aliceHasOwnTransaction(HUNDRED_THOUSAND, false);

        service.deleteTransaction(TX_ID);

        assertMoney("-70000", alice.getWalletBalance()); // the rule of the old endpoint, kept as it was
        verify(transactionRepo).delete(transaction);
    }

    @Test
    void aTransactionOfSomebodyElseCannotBeDeleted() {
        signedInAsAlice();
        when(transactionRepo.findDetailedById(TX_ID)).thenReturn(Optional.of(transaction(bob, HUNDRED_THOUSAND, true)));

        assertThrows(TransactionAccessDeniedException.class, () -> service.deleteTransaction(TX_ID));

        assertMoney("1000000", bob.getWalletBalance());
        assertMoney("1000000", alice.getWalletBalance());
        verifyNothingWasWritten();
    }

    @Test
    void aTransactionThatDoesNotExistCannotBeDeleted() {
        signedInAsAlice();
        when(transactionRepo.findDetailedById(TX_ID)).thenReturn(Optional.empty());

        assertThrows(TransactionNotFoundException.class, () -> service.deleteTransaction(TX_ID));

        verifyNothingWasWritten();
    }

    // -------------------------------------------------------------------------------------------------- cancel a receipt

    private Receipt receipt(String status, Transaction carrier) {
        Receipt receipt = new Receipt();
        receipt.setId(7L);
        receipt.setStatus(status);
        receipt.setTransaction(carrier);
        return receipt;
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "pending"})
    void aPendingReceiptOfTheCallerIsCanceled(String status) {
        signedInAsAlice();
        Receipt receipt = receipt(status, transaction(alice, HUNDRED_THOUSAND, true));
        when(receiptRepo.findById(7L)).thenReturn(Optional.of(receipt));

        service.cancelMyReceipt(7L);

        assertEquals("CANCELED", receipt.getStatus());
        verify(receiptRepo).save(receipt);
    }

    @Test
    void aReceiptOfSomebodyElseCannotBeCanceled() {
        signedInAsAlice();
        Receipt receipt = receipt("PENDING", transaction(bob, HUNDRED_THOUSAND, true));
        when(receiptRepo.findById(7L)).thenReturn(Optional.of(receipt));

        assertThrows(TransactionAccessDeniedException.class, () -> service.cancelMyReceipt(7L));

        assertEquals("PENDING", receipt.getStatus());
        verify(receiptRepo, never()).save(any());
    }

    @Test
    void aReceiptNoTransactionCarriesHasNoOwnerAndIsRefusedInsteadOfFailingWithANullPointer() {
        signedInAsAlice();
        Receipt orphan = receipt("PENDING", null);
        when(receiptRepo.findById(7L)).thenReturn(Optional.of(orphan));

        assertThrows(TransactionAccessDeniedException.class, () -> service.cancelMyReceipt(7L));

        verify(receiptRepo, never()).save(any());
    }

    @Test
    void aReceiptThatIsNoLongerPendingCannotBeCanceled() {
        signedInAsAlice();
        Receipt receipt = receipt("SUCCESS", transaction(alice, HUNDRED_THOUSAND, true));
        when(receiptRepo.findById(7L)).thenReturn(Optional.of(receipt));

        IllegalStateException refused = assertThrows(IllegalStateException.class, () -> service.cancelMyReceipt(7L));

        assertTrue(refused.getMessage().contains("Chờ xử lý"));
        assertEquals("SUCCESS", receipt.getStatus());
        verify(receiptRepo, never()).save(any());
    }

    @Test
    void aReceiptThatDoesNotExistIsNotFound() {
        signedInAsAlice();
        when(receiptRepo.findById(7L)).thenReturn(Optional.empty());

        assertThrows(ReceiptNotFoundException.class, () -> service.cancelMyReceipt(7L));
    }

    // ------------------------------------------------------------------------------------- checkout with a scanned receipt

    private TransactionRequestDto checkout(String rawReceiptData) {
        TransactionRequestDto request = new TransactionRequestDto();
        request.setAmount(HUNDRED_THOUSAND);
        request.setLocation("Hóa đơn quét");
        request.setPaymentMethod("WALLET");
        request.setTransactionDate(LocalDateTime.of(2026, 9, 20, 9, 0));
        request.setCategoryName(food.getName());
        request.setExpense(true);
        request.setRawReceiptData(rawReceiptData);
        request.setImageUrl("https://img.example.com/1.jpg");
        return request;
    }

    @Test
    void aScannedCheckoutHandsTheAiJsonToTheReceiptServiceBeforeTheTransactionIsSaved() {
        when(userRepo.findById(ALICE_ID)).thenReturn(Optional.of(alice));
        when(categoryRepo.findByName(food.getName())).thenReturn(Optional.of(food));

        service.createTransaction(checkout("{\"seller\": {}}"), ALICE_ID);

        ArgumentCaptor<Transaction> saved = ArgumentCaptor.forClass(Transaction.class);
        InOrder inOrder = inOrder(receiptProcessingService, transactionRepo);
        inOrder.verify(receiptProcessingService)
                .attachReceipt(saved.capture(), eq("{\"seller\": {}}"), eq("https://img.example.com/1.jpg"));
        inOrder.verify(transactionRepo).save(saved.getValue());
        assertEquals(0, new BigDecimal("900000").compareTo(alice.getWalletBalance()));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void aCheckoutWithoutAiDataHasNoReceipt(String rawReceiptData) {
        when(userRepo.findById(ALICE_ID)).thenReturn(Optional.of(alice));
        when(categoryRepo.findByName(food.getName())).thenReturn(Optional.of(food));

        service.createTransaction(checkout(rawReceiptData), ALICE_ID);

        verifyNoInteractions(receiptProcessingService);
        verify(transactionRepo).save(any(Transaction.class));
    }

    @Test
    void whenTheReceiptCannotBeBuiltNothingIsSavedAndTheFailureIsNotSwallowed() {
        when(userRepo.findById(ALICE_ID)).thenReturn(Optional.of(alice));
        when(categoryRepo.findByName(food.getName())).thenReturn(Optional.of(food));
        doThrow(new IllegalStateException("cannot build")).when(receiptProcessingService).attachReceipt(any(), any(), any());

        assertThrows(IllegalStateException.class, () -> service.createTransaction(checkout("{\"seller\": {}}"), ALICE_ID));

        verify(transactionRepo, never()).save(any());
    }

    // --------------------------------------------------------------------------------------- the caller's own listings

    @Test
    void myTransactionsAreThoseOfTheCaller() {
        signedInAsAlice();
        when(transactionRepo.findByUserIdOrderByTransactionDateDesc(ALICE_ID))
                .thenReturn(List.of(transaction(alice, HUNDRED_THOUSAND, true)));

        List<TransactionResponseDto> mine = service.getMyTransactions();

        assertEquals(1, mine.size());
        assertEquals(TX_ID, mine.get(0).getId());
        verify(transactionRepo, never()).findByUserIdOrderByTransactionDateDesc(BOB_ID);
    }

    @Test
    void myTransactionsCarryTheStatusOfTheirReceiptAndNoneWhenThereIsNoReceipt() {
        signedInAsAlice();
        Transaction scanned = transaction(alice, HUNDRED_THOUSAND, true);
        scanned.setReceipt(receipt(ReceiptStatus.FAILED.name(), scanned));
        Transaction typedIn = transaction(alice, HUNDRED_THOUSAND, true);
        when(transactionRepo.findByUserIdOrderByTransactionDateDesc(ALICE_ID)).thenReturn(List.of(scanned, typedIn));

        List<TransactionResponseDto> mine = service.getMyTransactions();

        assertEquals(ReceiptStatus.FAILED.name(), mine.get(0).getReceiptStatus());
        assertNull(mine.get(1).getReceiptStatus());
    }

    @Test
    void theFilterSearchesTheCallersTransactionsAndSortsByTheGivenFieldOnly() {
        signedInAsAlice();
        when(transactionRepo.filterTransactions(eq(ALICE_ID), any(), any(), any(), any(), any(Sort.class)))
                .thenReturn(List.of());
        TransactionFilterRequestDto filter = TransactionFilterRequestDto.builder()
                .categoryId(FOOD_ID).minPrice(new BigDecimal("1")).maxPrice(new BigDecimal("9")).vendorName("Highlands")
                .sortBy("amount").sortDirection("asc").build();

        service.filterMyTransactions(filter);

        ArgumentCaptor<Sort> sort = ArgumentCaptor.forClass(Sort.class);
        verify(transactionRepo).filterTransactions(eq(ALICE_ID), eq(FOOD_ID), eq(new BigDecimal("1")),
                eq(new BigDecimal("9")), eq("Highlands"), sort.capture());
        assertEquals(Sort.by(Sort.Direction.ASC, "amount"), sort.getValue());
    }

    @ParameterizedTest
    @ValueSource(strings = {"DESC", "desc", "sideways", ""})
    void aDirectionThatIsNotAscendingSortsDescendingAsBefore(String direction) {
        signedInAsAlice();
        when(transactionRepo.filterTransactions(eq(ALICE_ID), any(), any(), any(), any(), any(Sort.class)))
                .thenReturn(List.of());

        service.filterMyTransactions(TransactionFilterRequestDto.builder()
                .sortBy("transactionDate").sortDirection(direction).build());

        ArgumentCaptor<Sort> sort = ArgumentCaptor.forClass(Sort.class);
        verify(transactionRepo).filterTransactions(eq(ALICE_ID), any(), any(), any(), any(), sort.capture());
        assertEquals(Sort.by(Sort.Direction.DESC, "transactionDate"), sort.getValue());
    }

    @ParameterizedTest
    @ValueSource(strings = {"user.password", "password", "user", "id; drop table transactions", "", "amount desc"})
    void aSortFieldThatIsNotOnTheWhitelistIsRefusedBeforeTheQueryRuns(String sortBy) {
        signedInAsAlice();

        assertThrows(InvalidSortFieldException.class, () -> service.filterMyTransactions(
                TransactionFilterRequestDto.builder().sortBy(sortBy).sortDirection("ASC").build()));

        verifyNoInteractions(transactionRepo);
    }

    @Test
    void theSortFieldMayBeWrittenInAnyCase() {
        signedInAsAlice();
        when(transactionRepo.filterTransactions(eq(ALICE_ID), any(), any(), any(), any(), any(Sort.class)))
                .thenReturn(List.of());

        service.filterMyTransactions(TransactionFilterRequestDto.builder().sortBy("TRANSACTIONDATE").sortDirection("ASC").build());

        ArgumentCaptor<Sort> sort = ArgumentCaptor.forClass(Sort.class);
        verify(transactionRepo).filterTransactions(eq(ALICE_ID), any(), any(), any(), any(), sort.capture());
        assertEquals(Sort.by(Sort.Direction.ASC, "transactionDate"), sort.getValue());
    }

    @Test
    void myPopularVendorsAreThoseOfTheCaller() {
        signedInAsAlice();
        when(transactionRepo.findPopularVendors(ALICE_ID)).thenReturn(List.of(
                new Object[]{"Highlands", 5L}, new Object[]{"Circle K", 2L}));

        List<PopularVendorResponseDto> vendors = service.getMyPopularVendors();

        assertEquals(2, vendors.size());
        assertEquals("Highlands", vendors.get(0).getVendorName());
        assertEquals(5L, vendors.get(0).getTransactionCount());
        verify(transactionRepo, never()).findPopularVendors(BOB_ID);
    }

    @Test
    void myDashboardIsBuiltFromTheCallersTransactionsOnly() {
        signedInAsAlice();
        Transaction recent = transaction(alice, HUNDRED_THOUSAND, true);
        Transaction biggest = transaction(alice, new BigDecimal("900000"), false);
        Transaction sale = transaction(alice, new BigDecimal("5000"), true);
        when(transactionRepo.findByUserId(eq(ALICE_ID), any(Pageable.class))).thenReturn(List.of(recent));
        when(transactionRepo.findTopByUserIdOrderByAmountDesc(ALICE_ID)).thenReturn(biggest);
        when(transactionRepo.findByUserIdAndVoucherCodeNotNull(ALICE_ID)).thenReturn(List.of(sale));

        AppDashboardResponseDto dashboard = service.getMyDashboard();

        assertEquals(1, dashboard.getRecentTransactions().size());
        assertMoney("900000", dashboard.getBiggestTransaction().getAmount());
        assertEquals(1, dashboard.getSaleTransactions().size());
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(transactionRepo).findByUserId(eq(ALICE_ID), page.capture());
        assertEquals(0, page.getValue().getPageNumber());
        assertEquals(5, page.getValue().getPageSize());
        assertEquals(Sort.by("transactionDate").descending(), page.getValue().getSort());
    }

    @Test
    void everyTransactionOfTheDashboardCarriesTheStatusOfItsReceipt() {
        signedInAsAlice();
        Transaction recent = transaction(alice, HUNDRED_THOUSAND, true);
        recent.setReceipt(receipt(ReceiptStatus.PENDING.name(), recent));
        Transaction biggest = transaction(alice, new BigDecimal("900000"), false);
        biggest.setReceipt(receipt(ReceiptStatus.SUCCESS.name(), biggest));
        Transaction sale = transaction(alice, new BigDecimal("5000"), true);
        when(transactionRepo.findByUserId(eq(ALICE_ID), any(Pageable.class))).thenReturn(List.of(recent));
        when(transactionRepo.findTopByUserIdOrderByAmountDesc(ALICE_ID)).thenReturn(biggest);
        when(transactionRepo.findByUserIdAndVoucherCodeNotNull(ALICE_ID)).thenReturn(List.of(sale));

        AppDashboardResponseDto dashboard = service.getMyDashboard();

        assertEquals(ReceiptStatus.PENDING.name(), dashboard.getRecentTransactions().get(0).getReceiptStatus());
        assertEquals(ReceiptStatus.SUCCESS.name(), dashboard.getBiggestTransaction().getReceiptStatus());
        assertNull(dashboard.getSaleTransactions().get(0).getReceiptStatus());
    }

    @Test
    void aUserWithoutTransactionsHasNoBiggestOne() {
        signedInAsAlice();
        when(transactionRepo.findByUserId(eq(ALICE_ID), any(Pageable.class))).thenReturn(List.of());
        when(transactionRepo.findTopByUserIdOrderByAmountDesc(ALICE_ID)).thenReturn(null);
        when(transactionRepo.findByUserIdAndVoucherCodeNotNull(ALICE_ID)).thenReturn(List.of());

        AppDashboardResponseDto dashboard = service.getMyDashboard();

        assertNull(dashboard.getBiggestTransaction());
        assertTrue(dashboard.getRecentTransactions().isEmpty());
    }

    // ------------------------------------------------------------------------------------- the administrators' lookups

    @Test
    void anAdministratorReadsThePopularVendorsOfTheUserTheyNameAndNeedsNoCurrentUserForIt() {
        when(userRepo.existsById(BOB_ID)).thenReturn(true);
        when(transactionRepo.findPopularVendors(BOB_ID)).thenReturn(List.<Object[]>of(new Object[]{"Highlands", 3L}));

        List<PopularVendorResponseDto> vendors = service.getPopularVendorsOf(BOB_ID);

        assertEquals("Highlands", vendors.get(0).getVendorName());
        verifyNoInteractions(currentUserProvider);
    }

    @Test
    void anAdministratorReadsTheDashboardOfTheUserTheyName() {
        when(userRepo.existsById(BOB_ID)).thenReturn(true);
        when(transactionRepo.findByUserId(eq(BOB_ID), any(Pageable.class))).thenReturn(List.of());
        when(transactionRepo.findTopByUserIdOrderByAmountDesc(BOB_ID)).thenReturn(null);
        when(transactionRepo.findByUserIdAndVoucherCodeNotNull(BOB_ID)).thenReturn(List.of());

        assertNotNull(service.getDashboardOf(BOB_ID));

        verifyNoInteractions(currentUserProvider);
    }

    @Test
    void anAdministratorAskingAboutAnUnknownUserGetsNotFoundAndNoQueryRuns() {
        when(userRepo.existsById(404L)).thenReturn(false);

        assertThrows(UserNotFoundException.class, () -> service.getPopularVendorsOf(404L));
        assertThrows(UserNotFoundException.class, () -> service.getDashboardOf(404L));

        verifyNoInteractions(transactionRepo);
    }

    // ------------------------------------------------------- checkout, unchanged apart from where the category comes from

    private TransactionRequestDto checkout(boolean isExpense, String amount) {
        TransactionRequestDto request = new TransactionRequestDto();
        request.setAmount(new BigDecimal(amount));
        request.setExpense(isExpense);
        request.setLocation("Quán A");
        request.setPaymentMethod("Ví chính");
        request.setTransactionDate(LocalDateTime.of(2026, 9, 1, 12, 0));
        return request;
    }

    private void checkoutAsAlice() {
        signedInAsAlice();
        when(userRepo.findById(ALICE_ID)).thenReturn(Optional.of(alice));
    }

    @Test
    void checkoutOfAnExpenseTakesTheAmountOutOfTheCallersWalletOnly() {
        checkoutAsAlice();
        when(categoryRepo.findById(FOOD_ID)).thenReturn(Optional.of(food));
        TransactionRequestDto request = checkout(true, "150000");
        request.setCategoryId(FOOD_ID);

        Map<String, Object> result = service.createMyTransaction(request);

        assertMoney("850000", alice.getWalletBalance());
        assertMoney("850000", (BigDecimal) result.get("remainingBalance"));
        assertEquals("", result.get("warning"));
        ArgumentCaptor<Transaction> saved = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepo).save(saved.capture());
        assertSame(alice, saved.getValue().getUser());
        assertSame(food, saved.getValue().getCategory());
        verify(userRepo, never()).findById(BOB_ID);
    }

    @Test
    void checkoutOfAnIncomeAddsToTheWallet() {
        checkoutAsAlice();
        when(categoryRepo.findById(FOOD_ID)).thenReturn(Optional.of(food));
        TransactionRequestDto request = checkout(false, "150000");
        request.setCategoryId(FOOD_ID);

        service.createMyTransaction(request);

        assertMoney("1150000", alice.getWalletBalance());
    }

    @Test
    void checkoutWarnsWhenTheWalletRunsLow() {
        alice.setWalletBalance(new BigDecimal("120000"));
        checkoutAsAlice();
        when(categoryRepo.findById(FOOD_ID)).thenReturn(Optional.of(food));
        TransactionRequestDto request = checkout(true, "100000");
        request.setCategoryId(FOOD_ID);

        Map<String, Object> result = service.createMyTransaction(request);

        assertTrue(((String) result.get("warning")).startsWith("CẢNH BÁO"));
    }

    @Test
    void checkoutOfAnExpenseTheWalletCannotCarryFailsWithTheSameMessageAsBefore() {
        alice.setWalletBalance(new BigDecimal("50000"));
        checkoutAsAlice();
        when(categoryRepo.findById(FOOD_ID)).thenReturn(Optional.of(food));
        TransactionRequestDto request = checkout(true, "50000.01");
        request.setCategoryId(FOOD_ID);

        RuntimeException refused = assertThrows(RuntimeException.class, () -> service.createMyTransaction(request));

        assertEquals("GIAO DỊCH THẤT BẠI: Số dư ví không đủ!", refused.getMessage());
        assertMoney("50000", alice.getWalletBalance());
        verify(transactionRepo, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void checkoutWithANewCategoryNameCreatesTheCategoryTypedAfterTheTransaction(boolean isExpense) {
        checkoutAsAlice();
        when(categoryRepo.findByName("Freelance")).thenReturn(Optional.empty());
        when(categoryRepo.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));
        TransactionRequestDto request = checkout(isExpense, "10000");
        request.setCategoryName(" Freelance ");

        service.createMyTransaction(request);

        ArgumentCaptor<Category> created = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepo).save(created.capture());
        assertEquals("Freelance", created.getValue().getName());
        assertEquals(isExpense ? "EXPENSE" : "INCOME", created.getValue().getType());
    }

    @Test
    void checkoutWithAnUnknownCategoryIdOrNoCategoryFailsWithTheSameMessagesAsBefore() {
        checkoutAsAlice();
        when(categoryRepo.findById(99L)).thenReturn(Optional.empty());
        TransactionRequestDto unknown = checkout(true, "10000");
        unknown.setCategoryId(99L);

        assertEquals("Không tìm thấy danh mục",
                assertThrows(RuntimeException.class, () -> service.createMyTransaction(unknown)).getMessage());
        assertEquals("Thiếu danh mục (categoryId hoặc categoryName)",
                assertThrows(RuntimeException.class, () -> service.createMyTransaction(checkout(true, "10000"))).getMessage());
        assertMoney("1000000", alice.getWalletBalance());
    }

    // ------------------------------------------------------------------------------------------- budget notifications

    @Test
    void aNewTransactionIsHandedToTheBudgetAlertRightAfterItIsSaved() {
        checkoutAsAlice();
        when(categoryRepo.findById(FOOD_ID)).thenReturn(Optional.of(food));
        TransactionRequestDto request = checkout(true, "150000");
        request.setCategoryId(FOOD_ID);

        service.createMyTransaction(request);

        ArgumentCaptor<Transaction> saved = ArgumentCaptor.forClass(Transaction.class);
        InOrder inOrder = inOrder(transactionRepo, budgetAlertService);
        inOrder.verify(transactionRepo).save(saved.capture());
        inOrder.verify(budgetAlertService).notifyAfterCreate(saved.getValue());
    }

    @Test
    void aCheckoutTheWalletCannotCarryRaisesNoBudgetAlert() {
        alice.setWalletBalance(new BigDecimal("50000"));
        checkoutAsAlice();
        when(categoryRepo.findById(FOOD_ID)).thenReturn(Optional.of(food));
        TransactionRequestDto request = checkout(true, "50000.01");
        request.setCategoryId(FOOD_ID);

        assertThrows(RuntimeException.class, () -> service.createMyTransaction(request));

        verifyNoInteractions(budgetAlertService);
    }

    @Test
    void anEditedTransactionIsHandedToTheBudgetAlertWithWhatItCountedForBeforeTheEdit() {
        Transaction transaction = aliceHasOwnTransaction(HUNDRED_THOUSAND, true);
        LocalDateTime dateBefore = transaction.getTransactionDate();
        Category transport = category(20L, "Di chuyển", "EXPENSE");
        when(categoryRepo.findById(20L)).thenReturn(Optional.of(transport));
        TransactionUpdateRequestDto request = updateAmount("250000");
        request.setCategoryId(20L);
        request.setTransactionDate(LocalDateTime.of(2026, 9, 5, 8, 0));

        service.updateTransaction(TX_ID, request);

        ArgumentCaptor<BudgetAlertService.SpendingSnapshot> before = ArgumentCaptor.forClass(BudgetAlertService.SpendingSnapshot.class);
        verify(budgetAlertService).notifyAfterUpdate(same(transaction), before.capture());
        // What the transaction counted for until this edit (100,000 on food), not what it counts for now.
        assertEquals(new BudgetAlertService.SpendingSnapshot(HUNDRED_THOUSAND, true, dateBefore.toLocalDate(), FOOD_ID),
                before.getValue());
        assertMoney("250000", transaction.getAmount());
        assertSame(transport, transaction.getCategory());
    }

    @Test
    void theBudgetAlertOfAnEditRunsAfterTheTransactionIsSaved() {
        Transaction transaction = aliceHasOwnTransaction(HUNDRED_THOUSAND, true);

        service.updateTransaction(TX_ID, updateAmount("150000"));

        InOrder inOrder = inOrder(transactionRepo, budgetAlertService);
        inOrder.verify(transactionRepo).save(transaction);
        inOrder.verify(budgetAlertService).notifyAfterUpdate(same(transaction), any());
    }

    @Test
    void anEditTheWalletCannotAffordRaisesNoBudgetAlert() {
        alice.setWalletBalance(new BigDecimal("99999.99"));
        aliceHasOwnTransaction(HUNDRED_THOUSAND, true);

        assertThrows(InsufficientBalanceException.class, () -> service.updateTransaction(TX_ID, updateAmount("200000")));

        verifyNoInteractions(budgetAlertService);
    }

    // ------------------------------------------------------------------------------------------------ structural guards

    @Test
    void everyMethodThatChangesMoneyRunsInAWritableTransaction() throws NoSuchMethodException {
        List<Method> writers = List.of(
                TransactionService.class.getMethod("updateTransaction", Long.class, TransactionUpdateRequestDto.class),
                TransactionService.class.getMethod("deleteTransaction", Long.class),
                TransactionService.class.getMethod("cancelMyReceipt", Long.class),
                TransactionService.class.getMethod("createMyTransaction", TransactionRequestDto.class),
                TransactionService.class.getMethod("createTransaction", TransactionRequestDto.class, Long.class));

        for (Method method : writers) {
            Transactional transactional = AnnotatedElementUtils.findMergedAnnotation(method, Transactional.class);
            assertNotNull(transactional, method.getName() + " must be @Transactional");
            assertFalse(transactional.readOnly(), method.getName() + " writes, so it cannot be read-only");
        }
    }

    @Test
    void theQueriesRunInReadOnlyTransactions() throws NoSuchMethodException {
        List<Method> readers = List.of(
                TransactionService.class.getMethod("getMyTransactions"),
                TransactionService.class.getMethod("filterMyTransactions", TransactionFilterRequestDto.class),
                TransactionService.class.getMethod("getMyPopularVendors"),
                TransactionService.class.getMethod("getMyDashboard"),
                TransactionService.class.getMethod("getPopularVendorsOf", Long.class),
                TransactionService.class.getMethod("getDashboardOf", Long.class));

        for (Method method : readers) {
            Transactional transactional = AnnotatedElementUtils.findMergedAnnotation(method, Transactional.class);
            assertNotNull(transactional, method.getName() + " must be @Transactional");
            assertTrue(transactional.readOnly(), method.getName() + " only reads");
        }
    }

    @Test
    void onlyTheTrustedEntryPointsTakeAUserId() {
        Set<String> takingAUserId = Arrays.stream(TransactionService.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> Arrays.stream(method.getParameters()).anyMatch(parameter -> parameter.getName().equals("userId")))
                .map(Method::getName)
                .collect(Collectors.toSet());

        // The payment webhook has no SecurityContext, and the two administrator lookups are guarded by their controller.
        assertEquals(Set.of("createTransaction", "getPopularVendorsOf", "getDashboardOf"), takingAUserId);
    }
}
