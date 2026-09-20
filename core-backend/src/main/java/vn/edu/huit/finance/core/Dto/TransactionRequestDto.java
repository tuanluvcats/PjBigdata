package vn.edu.huit.finance.core.Dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class TransactionRequestDto {

    @NotNull(message = "Số tiền không được để trống")
    @Positive(message = "Số tiền giao dịch phải lớn hơn 0")
    private BigDecimal amount;

    private String note;

    @NotBlank(message = "Địa chỉ giao dịch không được để trống")
    private String location;

    @NotBlank(message = "Vui lòng chọn nguồn tiền")
    private String paymentMethod;

    @NotNull(message = "Ngày giao dịch không được để trống")
    private LocalDateTime transactionDate;

    private Long categoryId;

    private String categoryName;

    private Long receiptId;
    @JsonProperty("isExpense")
    private boolean isExpense;
    private Long campaignId;
    private String rawReceiptData;
    private String imageUrl;
    private List<String> additionalImages;
}
