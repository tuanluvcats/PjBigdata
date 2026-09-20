package vn.edu.huit.finance.core.Dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import vn.edu.huit.finance.core.Entity.Transaction;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class TransactionResponseDto {
    private Long id;
    private String title;
    private BigDecimal amount;
    private LocalDateTime date;
    private String category;
    @JsonProperty("isExpense") 
    private boolean isExpense;   
    private String imageUrl;

    /** The title is the note when there is one, otherwise the name of the category. */
    public static TransactionResponseDto from(Transaction transaction) {
        String note = transaction.getNote();
        String title = (note != null && !note.isBlank()) ? note : transaction.getCategoryName();
        String imageUrl = (transaction.getReceipt() != null) ? transaction.getReceipt().getImageUrl() : null;

        return TransactionResponseDto.builder()
                .id(transaction.getId())
                .title(title)
                .amount(transaction.getAmount())
                .date(transaction.getTransactionDate())
                .category(transaction.getCategoryName())
                .isExpense(transaction.isExpense())
                .imageUrl(imageUrl)
                .build();
    }
}