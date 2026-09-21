package vn.edu.huit.finance.core.Entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.envers.Audited;

/** A message for one user, created by the server (today: a budget reached 80% or 100%). The creation time is in BaseEntity. */
@Audited
@Entity
@Table(name = "notifications", indexes = @Index(name = "idx_notifications_user_id", columnList = "user_id"))
@Getter
@Setter
@NoArgsConstructor
public class Notification extends BaseEntity {

    public static final int MESSAGE_MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    /** Ready to show, in Vietnamese; the figures in it are the ones at the moment the notification was raised. */
    @Column(nullable = false, length = MESSAGE_MAX_LENGTH)
    private String message;

    /**
     * The budget behind a budget notification. A plain id and not a relation, so deleting a budget never has to touch
     * the notifications it raised; the app may find that the budget is gone.
     */
    @Column(name = "budget_id")
    private Long budgetId;
}
