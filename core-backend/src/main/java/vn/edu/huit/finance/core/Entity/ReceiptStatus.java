package vn.edu.huit.finance.core.Entity;

/** Lifecycle of a receipt; {@code Receipt.status} stores the name of one of these. */
public enum ReceiptStatus {
    /** Scanned, not confirmed yet. */
    PENDING,
    /** Confirmed: the receipt is linked to the transaction that moved the wallet. */
    SUCCESS,
    /** Rejected by an administrator. */
    FAILED,
    /** The owner sent a corrected amount and waits for an administrator. */
    WAITING_FOR_REVIEW,
    /** Withdrawn by the owner before it was confirmed. */
    CANCELED;

    /** Case-insensitive, because older rows were written by hand ("pending", "Failed"). */
    public boolean matches(String status) {
        return name().equalsIgnoreCase(status);
    }
}
