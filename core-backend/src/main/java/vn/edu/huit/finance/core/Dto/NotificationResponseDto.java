package vn.edu.huit.finance.core.Dto;

import lombok.Builder;
import lombok.Data;
import vn.edu.huit.finance.core.Entity.Notification;
import vn.edu.huit.finance.core.Entity.NotificationType;

import java.time.LocalDateTime;

@Data
@Builder
public class NotificationResponseDto {
    private Long id;
    private NotificationType type;
    private String message;
    /** The budget that raised the notification; it may have been deleted since. */
    private Long budgetId;
    private LocalDateTime createdAt;

    public static NotificationResponseDto from(Notification notification) {
        return NotificationResponseDto.builder()
                .id(notification.getId())
                .type(notification.getType())
                .message(notification.getMessage())
                .budgetId(notification.getBudgetId())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
