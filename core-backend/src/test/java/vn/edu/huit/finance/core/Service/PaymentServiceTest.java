package vn.edu.huit.finance.core.Service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import vn.edu.huit.finance.core.Dto.CreatePaymentRequestDto;
import vn.edu.huit.finance.core.Dto.PaymentWebhookDto;
import vn.edu.huit.finance.core.Dto.TransactionRequestDto;
import vn.edu.huit.finance.core.Entity.*;
import vn.edu.huit.finance.core.Repository.PaymentOrderRepository;
import vn.edu.huit.finance.core.Repository.UserRepository;
import vn.edu.huit.finance.core.Service.Payment.PaymentGateway;
import vn.edu.huit.finance.core.Service.Payment.PaymentGatewayRegistry;
import vn.edu.huit.finance.core.Util.HmacUtil;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final String SECRET = "unit_secret";
    private static final Long USER_ID = 1L;

    @Mock private PaymentOrderRepository orderRepo;
    @Mock private UserRepository userRepo;
    @Mock private PaymentGatewayRegistry gateways;
    @Mock private PaymentGateway mockGateway;
    @Mock private TransactionService transactionService;

    private PaymentService service;

    @BeforeEach
    void setUp() {
        service = new PaymentService();
        ReflectionTestUtils.setField(service, "orderRepo", orderRepo);
        ReflectionTestUtils.setField(service, "userRepo", userRepo);
        ReflectionTestUtils.setField(service, "gateways", gateways);
        ReflectionTestUtils.setField(service, "transactionService", transactionService);
        ReflectionTestUtils.setField(service, "secret", SECRET);
    }

    private User user(String balance) {
        User u = new User();
        u.setId(USER_ID);
        u.setWalletBalance(new BigDecimal(balance));
        return u;
    }

    private CreatePaymentRequestDto req(String type, String amount, String provider) {
        CreatePaymentRequestDto r = new CreatePaymentRequestDto();
        r.setType(type);
        r.setAmount(new BigDecimal(amount));
        r.setProvider(provider);
        return r;
    }

    // ---------- createOrder ----------

    @Test
    void createOrder_deposit_ok_returnsPendingAndPayUrl() {
        when(gateways.get("MOCK")).thenReturn(mockGateway);
        when(mockGateway.buildPaymentUrl(any())).thenReturn("http://pay");
        when(userRepo.findById(USER_ID)).thenReturn(Optional.of(user("100000")));

        Map<String, Object> data = service.createOrder(req("DEPOSIT", "50000", "MOCK"), USER_ID);

        assertEquals("PENDING", data.get("status"));
        assertEquals("MOCK", data.get("provider"));
        assertEquals("http://pay", data.get("payUrl"));
        assertNotNull(data.get("orderCode"));

        ArgumentCaptor<PaymentOrder> cap = ArgumentCaptor.forClass(PaymentOrder.class);
        verify(orderRepo).save(cap.capture());
        assertEquals(PaymentStatus.PENDING, cap.getValue().getStatus());
        assertEquals(PaymentType.DEPOSIT, cap.getValue().getType());
    }

    @Test
    void createOrder_defaultProviderMock_whenNull() {
        when(gateways.get("MOCK")).thenReturn(mockGateway);
        when(mockGateway.buildPaymentUrl(any())).thenReturn("http://pay");
        when(userRepo.findById(USER_ID)).thenReturn(Optional.of(user("100000")));

        Map<String, Object> data = service.createOrder(req("DEPOSIT", "50000", null), USER_ID);
        assertEquals("MOCK", data.get("provider"));
    }

    @Test
    void createOrder_withdraw_insufficientBalance_throws() {
        when(gateways.get("MOCK")).thenReturn(mockGateway);
        when(userRepo.findById(USER_ID)).thenReturn(Optional.of(user("10000")));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.createOrder(req("WITHDRAW", "50000", "MOCK"), USER_ID));
        assertTrue(ex.getMessage().contains("khong du"));
        verify(orderRepo, never()).save(any());
    }

    @Test
    void createOrder_invalidType_throws() {
        assertThrows(RuntimeException.class,
                () -> service.createOrder(req("FOO", "1000", "MOCK"), USER_ID));
        verify(orderRepo, never()).save(any());
    }

    @Test
    void createOrder_invalidProvider_throws() {
        when(gateways.get("XYZ")).thenThrow(new RuntimeException("Cong thanh toan khong ho tro: XYZ"));
        assertThrows(RuntimeException.class,
                () -> service.createOrder(req("DEPOSIT", "1000", "XYZ"), USER_ID));
        verify(orderRepo, never()).save(any());
    }

    // ---------- handleWebhook ----------

    private PaymentOrder pendingOrder(PaymentType type, String amount) {
        return PaymentOrder.builder()
                .orderCode("PAY123").userId(USER_ID)
                .amount(new BigDecimal(amount)).type(type)
                .status(PaymentStatus.PENDING).provider("MOCK")
                .build();
    }

    private PaymentWebhookDto webhook(String orderCode, String txn, String resultCode, String amount, boolean validSig) {
        PaymentWebhookDto d = new PaymentWebhookDto();
        d.setOrderCode(orderCode);
        d.setGatewayTxnId(txn);
        d.setResultCode(resultCode);
        d.setAmount(new BigDecimal(amount));
        String raw = orderCode + "|" + txn + "|" + resultCode + "|" + new BigDecimal(amount).toPlainString();
        d.setSignature(validSig ? HmacUtil.hmacSha256(raw, SECRET) : "badsig");
        return d;
    }

    @Test
    void webhook_success_deposit_createsIncomeTransaction() {
        PaymentOrder order = pendingOrder(PaymentType.DEPOSIT, "50000");
        when(orderRepo.findByOrderCode("PAY123")).thenReturn(Optional.of(order));

        service.handleWebhook(webhook("PAY123", "TXN1", "00", "50000", true));

        assertEquals(PaymentStatus.SUCCESS, order.getStatus());
        ArgumentCaptor<TransactionRequestDto> cap =
                ArgumentCaptor.forClass(TransactionRequestDto.class);
        verify(transactionService).createTransaction(cap.capture(), org.mockito.ArgumentMatchers.eq(USER_ID));
        assertFalse(cap.getValue().isExpense()); // DEPOSIT -> thu nhap
        assertEquals(0, cap.getValue().getAmount().compareTo(new BigDecimal("50000")));
    }

    @Test
    void webhook_success_withdraw_createsExpenseTransaction() {
        PaymentOrder order = pendingOrder(PaymentType.WITHDRAW, "30000");
        when(orderRepo.findByOrderCode("PAY123")).thenReturn(Optional.of(order));

        service.handleWebhook(webhook("PAY123", "TXN1", "00", "30000", true));

        assertEquals(PaymentStatus.SUCCESS, order.getStatus());
        ArgumentCaptor<TransactionRequestDto> cap =
                ArgumentCaptor.forClass(TransactionRequestDto.class);
        verify(transactionService).createTransaction(cap.capture(), org.mockito.ArgumentMatchers.eq(USER_ID));
        assertTrue(cap.getValue().isExpense()); // WITHDRAW -> chi
    }

    @Test
    void webhook_failResultCode_marksFailed_noTransaction() {
        PaymentOrder order = pendingOrder(PaymentType.DEPOSIT, "50000");
        when(orderRepo.findByOrderCode("PAY123")).thenReturn(Optional.of(order));

        service.handleWebhook(webhook("PAY123", "TXN1", "99", "50000", true));

        assertEquals(PaymentStatus.FAILED, order.getStatus());
        verify(transactionService, never()).createTransaction(any(), any());
    }

    @Test
    void webhook_badSignature_throws() {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.handleWebhook(webhook("PAY123", "TXN1", "00", "50000", false)));
        assertTrue(ex.getMessage().contains("chu ky"));
        verify(orderRepo, never()).findByOrderCode(any());
    }

    @Test
    void webhook_idempotent_alreadySuccess_skips() {
        PaymentOrder order = pendingOrder(PaymentType.DEPOSIT, "50000");
        order.setStatus(PaymentStatus.SUCCESS); // da xu ly truoc do
        when(orderRepo.findByOrderCode("PAY123")).thenReturn(Optional.of(order));

        service.handleWebhook(webhook("PAY123", "TXN1", "00", "50000", true));

        verify(transactionService, never()).createTransaction(any(), any()); // khong tao giao dich lan 2
        verify(orderRepo, never()).save(any());
    }

    @Test
    void webhook_amountMismatch_marksFailed_noThrow_noCredit() {
        // KHONG throw: method @Transactional, throw se rollback save(FAILED) -> ket lai PENDING.
        PaymentOrder order = pendingOrder(PaymentType.DEPOSIT, "50000");
        when(orderRepo.findByOrderCode("PAY123")).thenReturn(Optional.of(order));

        service.handleWebhook(webhook("PAY123", "TXN1", "00", "999", true));

        assertEquals(PaymentStatus.FAILED, order.getStatus());
        verify(transactionService, never()).createTransaction(any(), any()); // khong tao giao dich
        verify(orderRepo).save(order);          // co luu FAILED
    }

    // ---------- getStatus / cancel ----------

    @Test
    void getStatus_wrongUser_throws() {
        PaymentOrder order = pendingOrder(PaymentType.DEPOSIT, "50000");
        when(orderRepo.findByOrderCode("PAY123")).thenReturn(Optional.of(order));
        assertThrows(RuntimeException.class, () -> service.getStatus("PAY123", 999L));
    }

    @Test
    void cancel_pending_setsCancelled() {
        PaymentOrder order = pendingOrder(PaymentType.DEPOSIT, "50000");
        when(orderRepo.findByOrderCode("PAY123")).thenReturn(Optional.of(order));
        Map<String, Object> data = service.cancel("PAY123", USER_ID);
        assertEquals("CANCELLED", data.get("status"));
    }

    @Test
    void cancel_nonPending_unchanged() {
        PaymentOrder order = pendingOrder(PaymentType.DEPOSIT, "50000");
        order.setStatus(PaymentStatus.SUCCESS);
        when(orderRepo.findByOrderCode("PAY123")).thenReturn(Optional.of(order));
        Map<String, Object> data = service.cancel("PAY123", USER_ID);
        assertEquals("SUCCESS", data.get("status"));
    }
}
