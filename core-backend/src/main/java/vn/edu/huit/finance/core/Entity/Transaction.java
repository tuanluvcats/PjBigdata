package vn.edu.huit.finance.core.Entity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.envers.Audited;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@Audited
@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Transaction extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private BigDecimal amount;

    private String note;

    @Column(name = "transaction_date", nullable = false)
    private LocalDateTime transactionDate;

    @Column(name = "is_expense", nullable = false)
    private boolean isExpense;

    @Column(name = "category_name")
    private String categoryName;

    @Column(name = "status")
    private String status;

    @Column(name = "voucher_code")
    private String voucherCode;

    @Column(columnDefinition = "TEXT")
    private String additionalImages;

    private String location;
    private String paymentMethod;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    @JsonIgnore
    private Category category;

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)    
    @JoinColumn(name = "receipt_id", referencedColumnName = "id")
    @JsonIgnore
    private Receipt receipt;
    @Column(name = "raw_receipt_data", columnDefinition = "TEXT")
    private String rawReceiptData;

}
