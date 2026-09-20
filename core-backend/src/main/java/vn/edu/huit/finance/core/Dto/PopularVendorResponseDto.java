package vn.edu.huit.finance.core.Dto;

import lombok.Builder;
import lombok.Data;

/** A vendor of the scanned receipts and how many transactions carry it. */
@Data
@Builder
public class PopularVendorResponseDto {
    private String vendorName;
    private long transactionCount;
}
