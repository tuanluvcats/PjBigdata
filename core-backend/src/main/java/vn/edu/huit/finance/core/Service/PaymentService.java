package vn.edu.huit.finance.core.Service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentService {

    @Autowired private PaymentOrderRepository orderRepo;
    @Autowired private UserRepository userRepo;
    @Autowired private PaymentGatewayRegistry gateways;
    @Autowired private TransactionService transactionService;

    @Value("${payment.secret}")
    private String secret;

    private static final String DEFAULT_PROVIDER = "MOCK";

    @Transactional
    public Map<String, Object> createOrder(CreatePaymentRequestDto req, Long userId) {
        PaymentType type;
        try {
            type = PaymentType.valueOf(req.getType().trim().toUpperCase());
        } catch (Exception e) {
            throw new RuntimeException("type khong hop le (DEPOSIT|WITHDRAW)");
        }

        String provider = (req.getProvider() == null || req.getProvider().isBlank())
                ? DEFAULT_PROVIDER : req.getProvider().trim().toUpperCase();
        PaymentGateway gateway = gateways.get(provider);

        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("Khong tim thay user"));

        if (type == PaymentType.WITHDRAW && user.getWalletBalance().compareTo(req.getAmount()) < 0) {
            throw new RuntimeException("So du vi khong du de rut");
        }

        String orderCode = "PAY" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String sign = HmacUtil.hmacSha256(orderCode + "|" + req.getAmount().toPlainString(), secret);

        PaymentOrder order = PaymentOrder.builder()
                .orderCode(orderCode)
                .userId(userId)
                .amount(req.getAmount())
                .type(type)
                .status(PaymentStatus.PENDING)
                .provider(provider)
                .description(req.getDescription())
                .signature(sign)
                .build();
        orderRepo.save(order);

        String payUrl = gateway.buildPaymentUrl(order);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderCode", orderCode);
        data.put("amount", order.getAmount());
        data.put("type", type.name());
        data.put("status", order.getStatus().name());
        data.put("provider", provider);
        data.put("payUrl", payUrl);
        return data;
    }

    @Transactional
    public void handleWebhook(PaymentWebhookDto dto) {
        String raw = dto.getOrderCode() + "|" + dto.getGatewayTxnId() + "|"
                + dto.getResultCode() + "|" + dto.getAmount().toPlainString();
        if (!HmacUtil.verify(raw, secret, dto.getSignature())) {
            throw new RuntimeException("Sai chu ky webhook (HMAC khong khop)");
        }

        PaymentOrder order = orderRepo.findByOrderCode(dto.getOrderCode())
                .orElseThrow(() -> new RuntimeException("Khong tim thay don: " + dto.getOrderCode()));

        if (order.getStatus() != PaymentStatus.PENDING) {
            return;
        }

        if (order.getAmount().compareTo(dto.getAmount()) != 0) {
            order.setGatewayTxnId(dto.getGatewayTxnId());
            order.setStatus(PaymentStatus.FAILED);
            orderRepo.save(order);
            return;
        }

        order.setGatewayTxnId(dto.getGatewayTxnId());

        boolean success = "00".equals(dto.getResultCode());
        if (success) {
            createSyncTransaction(order);
            order.setStatus(PaymentStatus.SUCCESS);
        } else {
            order.setStatus(PaymentStatus.FAILED);
        }
        orderRepo.save(order);
    }

    private void createSyncTransaction(PaymentOrder order) {
        boolean isWithdraw = order.getType() == PaymentType.WITHDRAW;
        TransactionRequestDto dto = new TransactionRequestDto();
        dto.setAmount(order.getAmount());
        dto.setExpense(isWithdraw);
        dto.setCategoryName(isWithdraw ? "Rút ví điện tử" : "Nạp ví điện tử");
        dto.setLocation("Ví " + order.getProvider());
        dto.setPaymentMethod(order.getProvider());
        dto.setTransactionDate(LocalDateTime.now());
        dto.setNote((isWithdraw ? "Rút về ví " : "Nạp từ ví ") + order.getProvider()
                + " (" + order.getOrderCode() + ")");
        transactionService.createTransaction(dto, order.getUserId());
    }

    public Map<String, Object> getStatus(String orderCode, Long userId) {
        PaymentOrder o = orderRepo.findByOrderCode(orderCode)
                .orElseThrow(() -> new RuntimeException("Khong tim thay don"));
        if (!o.getUserId().equals(userId)) {
            throw new RuntimeException("Don khong thuoc ve ban");
        }
        return toMap(o);
    }

    public List<Map<String, Object>> history(Long userId) {
        return orderRepo.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toMap).toList();
    }

    @Transactional
    public Map<String, Object> cancel(String orderCode, Long userId) {
        PaymentOrder o = orderRepo.findByOrderCode(orderCode)
                .orElseThrow(() -> new RuntimeException("Khong tim thay don"));
        if (!o.getUserId().equals(userId)) throw new RuntimeException("Don khong thuoc ve ban");
        if (o.getStatus() == PaymentStatus.PENDING) {
            o.setStatus(PaymentStatus.CANCELLED);
            orderRepo.save(o);
        }
        return toMap(o);
    }

    private Map<String, Object> toMap(PaymentOrder o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orderCode", o.getOrderCode());
        m.put("amount", o.getAmount());
        m.put("type", o.getType().name());
        m.put("status", o.getStatus().name());
        m.put("provider", o.getProvider());
        m.put("description", o.getDescription());
        m.put("gatewayTxnId", o.getGatewayTxnId());
        LocalDateTime c = o.getCreatedAt();
        m.put("createdAt", c != null ? c.toString() : null);
        return m;
    }
}
