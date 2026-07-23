package vn.edu.huit.finance.core.Entity;
import org.hibernate.envers.Audited;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
@Audited
@Entity
@Table(name = "receipts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Receipt extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String vendorName;

    private BigDecimal extractedTotal;

    private LocalDate extractedDate;

    @Column(name = "image_url", length = 2048)
    private String imageUrl;

    @Column(nullable = false)
    private String status = "PENDING";

    @OneToOne(mappedBy = "receipt")
    private Transaction transaction;
}
