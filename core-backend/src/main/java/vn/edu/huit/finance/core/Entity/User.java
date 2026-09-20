package vn.edu.huit.finance.core.Entity;
import org.hibernate.envers.Audited;
import jakarta.persistence.*;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Audited
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false, unique = true)
    private String phone;

    @Column(nullable = false)
    private String role = "ROLE_USER";

    @Column(nullable = false)
    private boolean active = true;

    private LocalDateTime lastLoginTime;

    private LocalDateTime lastActionTime;
    @JsonIgnore
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @ToString.Exclude
    private List<Transaction> transactions;
    @Column(nullable = false)
    private BigDecimal walletBalance = BigDecimal.ZERO;
    @Column(name = "ai_label", length = 50)
    private String aiLabel;
}
