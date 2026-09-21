package vn.edu.huit.finance.core.Service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.huit.finance.core.Entity.Receipt;
import vn.edu.huit.finance.core.Entity.Transaction;
import vn.edu.huit.finance.core.Entity.User;
import vn.edu.huit.finance.core.Exception.ReceiptNotFoundException;
import vn.edu.huit.finance.core.Exception.TransactionAccessDeniedException;
import vn.edu.huit.finance.core.Repository.ReceiptRepository;
import vn.edu.huit.finance.core.Security.CurrentUserProvider;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReceiptProcessingServiceTest {

    private static final Long ALICE_ID = 1L;
    private static final Long BOB_ID = 2L;
    private static final Long RECEIPT_ID = 7L;
    private static final LocalDateTime CLIENT_DATE = LocalDateTime.of(2026, 9, 20, 9, 0);
    private static final String CLIENT_LOCATION = "Hóa đơn quét";

    @Mock private ReceiptRepository receiptRepo;
    @Mock private CurrentUserProvider currentUserProvider;
    @Spy private OcrService ocrService = new OcrService();
    @InjectMocks private ReceiptProcessingService service;

    private final User alice = user(ALICE_ID);
    private final User bob = user(BOB_ID);

    // ---------------------------------------------------------------------------------------------------------- fixtures

    private static User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("user" + id);
        return user;
    }

    private static Transaction transactionOf(User owner) {
        Transaction transaction = new Transaction();
        transaction.setId(100L);
        transaction.setUser(owner);
        transaction.setAmount(new BigDecimal("200000"));
        transaction.setTransactionDate(CLIENT_DATE);
        transaction.setLocation(CLIENT_LOCATION);
        return transaction;
    }

    private static Receipt receipt(String status, Transaction carrier) {
        Receipt receipt = new Receipt();
        receipt.setId(RECEIPT_ID);
        receipt.setStatus(status);
        receipt.setExtractedTotal(new BigDecimal("1.00"));
        receipt.setTransaction(carrier);
        return receipt;
    }

    private void signedInAsAlice() {
        when(currentUserProvider.getCurrentUser()).thenReturn(alice);
    }

    private void repositoryHolds(Receipt receipt) {
        when(receiptRepo.findDetailedById(RECEIPT_ID)).thenReturn(Optional.of(receipt));
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertNotNull(actual, "expected " + expected + " but was null");
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }

    // ------------------------------------------------------------------------------------------------------- feedback

    @Test
    void theOwnerOfAFailedReceiptSendsTheAmountAndTheReceiptWaitsForReview() {
        signedInAsAlice();
        Receipt failed = receipt("FAILED", transactionOf(alice));
        repositoryHolds(failed);

        service.submitFeedback(RECEIPT_ID, new BigDecimal("150000"));

        assertEquals("WAITING_FOR_REVIEW", failed.getStatus());
        assertMoney("150000", failed.getExtractedTotal());
        verify(receiptRepo).save(failed);
        verify(currentUserProvider).getCurrentUser();
    }

    @Test
    void theStatusIsMatchedIgnoringCaseBecauseOlderRowsWereWrittenByHand() {
        signedInAsAlice();
        Receipt failed = receipt("failed", transactionOf(alice));
        repositoryHolds(failed);

        service.submitFeedback(RECEIPT_ID, new BigDecimal("150000"));

        assertEquals("WAITING_FOR_REVIEW", failed.getStatus());
    }

    @Test
    void aReceiptOfSomebodyElseIsRefusedAndNothingIsWritten() {
        signedInAsAlice();
        Receipt bobs = receipt("FAILED", transactionOf(bob));
        repositoryHolds(bobs);

        assertThrows(TransactionAccessDeniedException.class, () -> service.submitFeedback(RECEIPT_ID, new BigDecimal("150000")));

        assertEquals("FAILED", bobs.getStatus());
        assertMoney("1", bobs.getExtractedTotal());
        verify(receiptRepo, never()).save(any());
    }

    @Test
    void aReceiptNoTransactionCarriesHasNoOwnerAndIsRefusedForEverybody() {
        signedInAsAlice();
        Receipt orphan = receipt("FAILED", null);
        repositoryHolds(orphan);

        assertThrows(TransactionAccessDeniedException.class, () -> service.submitFeedback(RECEIPT_ID, new BigDecimal("150000")));

        assertEquals("FAILED", orphan.getStatus());
        verify(receiptRepo, never()).save(any());
    }

    @Test
    void ownershipIsCheckedBeforeTheStatusSoTheStateOfSomebodyElsesReceiptIsNotRevealed() {
        signedInAsAlice();
        repositoryHolds(receipt("SUCCESS", transactionOf(bob)));

        assertThrows(TransactionAccessDeniedException.class, () -> service.submitFeedback(RECEIPT_ID, new BigDecimal("150000")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "SUCCESS", "WAITING_FOR_REVIEW", "CANCELED"})
    void onlyAFailedReceiptTakesFeedback(String status) {
        signedInAsAlice();
        Receipt receipt = receipt(status, transactionOf(alice));
        repositoryHolds(receipt);

        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> service.submitFeedback(RECEIPT_ID, new BigDecimal("150000")));

        assertTrue(refused.getMessage().contains("FAILED"));
        assertEquals(status, receipt.getStatus());
        verify(receiptRepo, never()).save(any());
    }

    @Test
    void aReceiptThatDoesNotExistIsNotFound() {
        signedInAsAlice();
        when(receiptRepo.findDetailedById(RECEIPT_ID)).thenReturn(Optional.empty());

        assertThrows(ReceiptNotFoundException.class, () -> service.submitFeedback(RECEIPT_ID, new BigDecimal("150000")));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0", "-5", "0.004", "1000000000001"})
    void anAmountThatIsNotPositiveOrPlausibleIsRejectedBeforeAnythingIsLoaded(String amount) {
        BigDecimal manualAmount = amount == null ? null : new BigDecimal(amount);

        assertThrows(IllegalArgumentException.class, () -> service.submitFeedback(RECEIPT_ID, manualAmount));

        verifyNoInteractions(receiptRepo, currentUserProvider);
    }

    @Test
    void noPublicMethodTakesAUserIdBecauseTheCallerAlwaysComesFromTheSecurityContext() {
        Set<String> takingAUserId = Arrays.stream(ReceiptProcessingService.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> Arrays.stream(method.getParameters()).anyMatch(parameter -> parameter.getName().equals("userId")))
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertEquals(Set.of(), takingAUserId);
    }

    @Test
    void feedbackRunsInsideADatabaseTransaction() throws NoSuchMethodException {
        Method feedback = ReceiptProcessingService.class.getMethod("submitFeedback", Long.class, BigDecimal.class);

        assertTrue(AnnotatedElementUtils.hasAnnotation(feedback, Transactional.class));
    }

    // ---------------------------------------------------------------------------------------- linking a scanned receipt

    private static final String FULL_SCAN = "{\"seller\": {\"seller_name\": \"Bách Hóa Xanh\", \"address\": \"12 Nguyễn Trãi, Quận 1\","
            + " \"timestamp\": \"27/05/2026 10:30\", \"total_cost\": \"245.000\", \"total_cost_number\": 245000}}";

    @Test
    void aFullyReadReceiptIsLinkedBothWaysToItsTransactionAndVerified() {
        Transaction transaction = transactionOf(alice);

        service.attachReceipt(transaction, FULL_SCAN, "  https://img.example.com/1.jpg ");

        Receipt receipt = transaction.getReceipt();
        assertNotNull(receipt);
        assertSame(transaction, receipt.getTransaction());
        assertEquals("SUCCESS", receipt.getStatus());
        assertEquals("Bách Hóa Xanh", receipt.getVendorName());
        assertMoney("245000", receipt.getExtractedTotal());
        assertEquals(LocalDate.of(2026, 5, 27), receipt.getExtractedDate());
        assertEquals("https://img.example.com/1.jpg", receipt.getImageUrl());
        assertEquals(LocalDateTime.of(2026, 5, 27, 10, 30), transaction.getTransactionDate());
        assertEquals("12 Nguyễn Trãi, Quận 1", transaction.getLocation());
    }

    @Test
    void whenTheAiReadNoTotalTheReceiptFailsAndKeepsAwaitingTheAmountOfItsOwner() {
        Transaction transaction = transactionOf(alice);

        service.attachReceipt(transaction, "{\"seller\": {\"seller_name\": \"Highlands\", \"total_cost\": \"0\"}}", null);

        Receipt receipt = transaction.getReceipt();
        assertEquals("FAILED", receipt.getStatus());
        assertNull(receipt.getExtractedTotal());
        assertEquals("Highlands", receipt.getVendorName());
        assertSame(transaction, receipt.getTransaction());
    }

    @Test
    void whatTheAiDidNotDeliverKeepsTheValuesTheClientSent() {
        Transaction transaction = transactionOf(alice);

        service.attachReceipt(transaction, "{\"seller\": {\"seller_name\": \"Highlands\", \"total_cost_number\": 90000}}", null);

        Receipt receipt = transaction.getReceipt();
        assertEquals("SUCCESS", receipt.getStatus());
        assertNull(receipt.getExtractedDate());
        assertNull(receipt.getImageUrl());
        assertEquals(CLIENT_DATE, transaction.getTransactionDate());
        assertEquals(CLIENT_LOCATION, transaction.getLocation());
    }

    @Test
    void aTimestampThatIsNotARealDateKeepsTheDateOfTheClient() {
        Transaction transaction = transactionOf(alice);

        service.attachReceipt(transaction, "{\"seller\": {\"timestamp\": \"32/13/2026 10:30\", \"total_cost_number\": 90000}}", null);

        assertEquals(CLIENT_DATE, transaction.getTransactionDate());
        assertNull(transaction.getReceipt().getExtractedDate());
    }

    @ParameterizedTest
    @ValueSource(strings = {"not json", "{\"raw_text\": \"noise\"}", "[]"})
    void dataTheAiCouldNotStructureStillKeepsTheImageAsAFailedReceiptOfTheTransaction(String raw) {
        Transaction transaction = transactionOf(alice);

        service.attachReceipt(transaction, raw, "https://img.example.com/2.jpg");

        Receipt receipt = transaction.getReceipt();
        assertEquals("FAILED", receipt.getStatus());
        assertEquals("https://img.example.com/2.jpg", receipt.getImageUrl());
        assertSame(transaction, receipt.getTransaction());
        assertEquals(CLIENT_DATE, transaction.getTransactionDate());
        assertEquals(CLIENT_LOCATION, transaction.getLocation());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void aBlankImageUrlIsNotStored(String imageUrl) {
        Transaction transaction = transactionOf(alice);

        service.attachReceipt(transaction, FULL_SCAN, imageUrl);

        assertNull(transaction.getReceipt().getImageUrl());
    }
}
