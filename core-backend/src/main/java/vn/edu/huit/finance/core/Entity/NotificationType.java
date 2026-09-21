package vn.edu.huit.finance.core.Entity;

import lombok.Getter;

/**
 * What a notification is about. The constants are declared in ascending order of {@link #thresholdPercent}:
 * BudgetAlertService relies on a higher ordinal meaning a higher level of budget use.
 */
@Getter
public enum NotificationType {
    /** Spending has reached 80% of a budget. */
    BUDGET_WARNING(80),
    /** Spending has reached, or passed, 100% of a budget. */
    BUDGET_EXCEEDED(100);

    /** The share of a budget that has to be spent for this notification to be raised. */
    private final int thresholdPercent;

    NotificationType(int thresholdPercent) {
        this.thresholdPercent = thresholdPercent;
    }
}
