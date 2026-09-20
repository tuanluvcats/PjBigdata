package vn.edu.huit.finance.core.Dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/** The data of the app's home page for one user. */
@Data
@Builder
public class AppDashboardResponseDto {
    private List<TransactionResponseDto> recentTransactions;
    /** Null while the user has no transaction. */
    private TransactionResponseDto biggestTransaction;
    private List<TransactionResponseDto> saleTransactions;
}
