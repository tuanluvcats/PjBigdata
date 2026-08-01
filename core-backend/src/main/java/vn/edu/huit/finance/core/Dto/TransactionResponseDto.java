package vn.edu.huit.finance.core.Dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
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
}