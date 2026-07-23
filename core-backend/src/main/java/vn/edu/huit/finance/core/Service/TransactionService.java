package vn.edu.huit.finance.core.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.huit.finance.core.Dto.TransactionRequestDto;
import vn.edu.huit.finance.core.Entity.Category;
import vn.edu.huit.finance.core.Entity.Receipt;
import vn.edu.huit.finance.core.Entity.Transaction;
import vn.edu.huit.finance.core.Entity.User;
import vn.edu.huit.finance.core.Repository.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TransactionService {
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

    @Transactional
    public Map<String, Object> createTransaction(TransactionRequestDto request, Long userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy User"));

        Category category;
        if (request.getCategoryId() != null) {
            category = categoryRepo.findById(request.getCategoryId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy danh mục"));
        } else if (request.getCategoryName() != null && !request.getCategoryName().isBlank()) {
            String catName = request.getCategoryName().trim();
            category = categoryRepo.findByName(catName).orElseGet(() -> {
                Category c = new Category();
                c.setName(catName);
                c.setType(request.isExpense() ? "EXPENSE" : "INCOME");
                return categoryRepo.save(c);
            });
        } else {
            throw new RuntimeException("Thiếu danh mục (categoryId hoặc categoryName)");
        }

        BigDecimal finalAmount = request.getAmount();

        if (request.isExpense()) {
            if (user.getWalletBalance().compareTo(finalAmount) < 0) {
                throw new RuntimeException("GIAO DỊCH THẤT BẠI: Số dư ví không đủ!");
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

        if (request.getRawReceiptData() != null && !request.getRawReceiptData().isEmpty()) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(request.getRawReceiptData());
                JsonNode sellerNode = root.path("seller");

                if (!sellerNode.isMissingNode()) {
                    Receipt receipt = new Receipt();
                    receipt.setVendorName(sellerNode.path("seller_name").asText(null));

                    String totalStr = sellerNode.path("total_cost_number").asText(null);
                    if (totalStr != null && !totalStr.isEmpty() && !totalStr.equals("null")) {
                        receipt.setExtractedTotal(new BigDecimal(totalStr));
                    }

                    String rawTimestamp = sellerNode.path("timestamp").asText(null);
                    if (rawTimestamp != null && !rawTimestamp.isBlank()) {

                        Pattern newPattern = Pattern.compile("(\\d{4})[/.-](\\d{2})[/.-](\\d{2})[\\sT]+(\\d{2})[:.](\\d{2})");
                        Matcher newMatcher = newPattern.matcher(rawTimestamp);

                        if (newMatcher.find()) {
                            try {
                                int year = Integer.parseInt(newMatcher.group(1));
                                int month = Integer.parseInt(newMatcher.group(2));
                                int day = Integer.parseInt(newMatcher.group(3));
                                int hour = Integer.parseInt(newMatcher.group(4));
                                int minute = Integer.parseInt(newMatcher.group(5));

                                LocalDateTime extractedDate = LocalDateTime.of(year, month, day, hour, minute);
                                transaction.setTransactionDate(extractedDate);
                                receipt.setExtractedDate(extractedDate.toLocalDate());
                            } catch (Exception ignored) {
                            }
                        } else {
                            Pattern datePattern = Pattern.compile("(\\d{2})[/.-](\\d{2})[/.-](\\d{4})");
                            Matcher dateMatcher = datePattern.matcher(rawTimestamp);

                            if (dateMatcher.find()) {
                                try {
                                    int day = Integer.parseInt(dateMatcher.group(1));
                                    int month = Integer.parseInt(dateMatcher.group(2));
                                    int year = Integer.parseInt(dateMatcher.group(3));

                                    int hour = 0;
                                    int minute = 0;
                                    String timePart = rawTimestamp.substring(dateMatcher.end()).trim();

                                    Pattern timePattern = Pattern.compile("(\\d{2})[:.](\\d{2})");
                                    Matcher timeMatcher = timePattern.matcher(timePart);
                                    if (timeMatcher.find()) {
                                        hour = Integer.parseInt(timeMatcher.group(1));
                                        minute = Integer.parseInt(timeMatcher.group(2));
                                    } else {
                                        String prevPart = rawTimestamp.substring(0, dateMatcher.start()).trim();
                                        Matcher prevTimeMatcher = timePattern.matcher(prevPart);
                                        if (prevTimeMatcher.find()) {
                                            hour = Integer.parseInt(prevTimeMatcher.group(1));
                                            minute = Integer.parseInt(prevTimeMatcher.group(2));
                                        }
                                    }

                                    LocalDateTime extractedDate = LocalDateTime.of(year, month, day, hour, minute);
                                    transaction.setTransactionDate(extractedDate);
                                    receipt.setExtractedDate(extractedDate.toLocalDate());

                                } catch (Exception ignored) {
                                }
                            }
                        }
                    }

                    receipt.setStatus("SUCCESS");

                    if (request.getImageUrl() != null && !request.getImageUrl().isEmpty()) {
                        receipt.setImageUrl(request.getImageUrl());
                    }

                    receipt.setTransaction(transaction);
                    transaction.setReceipt(receipt);
                }
            } catch (Exception e) {
                System.out.println(" Lỗi parse JSON Receipt từ AI: " + e.getMessage());
            }
        }

        transactionRepo.save(transaction);

        try {
            if (request.getRawReceiptData() != null && !request.getRawReceiptData().isEmpty()) {
                auditLogService.logAction(user.getEmail(), "CREATE_RECEIPT", "Quét hóa đơn AI & Lưu giao dịch: " + finalAmount + " VNĐ");
            } else {
                auditLogService.logAction(user.getEmail(), "CREATE_TRANSACTION", "Thêm giao dịch thủ công: " + finalAmount + " VNĐ");
            }
        } catch (Exception e) {
        }

        String warningMessage = "";
        if (user.getWalletBalance().compareTo(new BigDecimal("50000")) < 0) {
            warningMessage = "CẢNH BÁO: Số dư ví của bạn sắp cạn kiệt (Dưới 50,000 VNĐ)!";
        }

        Map<String, Object> responseData = new HashMap<>();
        responseData.put("transactionId", transaction.getId());
        responseData.put("finalAmount", finalAmount);
        responseData.put("remainingBalance", user.getWalletBalance());
        responseData.put("warning", warningMessage);

        return responseData;
    }
    @Transactional
    public void cancelReceipt(Long receiptId, Long userId) {
        Receipt receipt = receiptRepo.findById(receiptId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy hóa đơn"));

        if (!receipt.getTransaction().getUser().getId().equals(userId)) {
            throw new RuntimeException("Bạn không có quyền hủy hóa đơn này");
        }

        if (!"PENDING".equalsIgnoreCase(receipt.getStatus())) {
            throw new RuntimeException("Chỉ có thể hủy hóa đơn đang ở trạng thái Chờ xử lý");
        }

        receipt.setStatus("CANCELED");
        receiptRepo.save(receipt);
    }
}