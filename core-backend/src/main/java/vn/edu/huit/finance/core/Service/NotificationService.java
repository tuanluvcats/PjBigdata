package vn.edu.huit.finance.core.Service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.huit.finance.core.Dto.NotificationResponseDto;
import vn.edu.huit.finance.core.Entity.Notification;
import vn.edu.huit.finance.core.Entity.NotificationType;
import vn.edu.huit.finance.core.Entity.User;
import vn.edu.huit.finance.core.Repository.NotificationRepository;
import vn.edu.huit.finance.core.Security.CurrentUserProvider;

import java.util.List;

/**
 * Notifications of the authenticated caller. Reading takes no user id: the owner is resolved from the SecurityContext.
 * {@link #create} is for server-side callers (BudgetAlertService) and joins the transaction of whoever calls it.
 */
@Slf4j
@Service
public class NotificationService {

    /** The app shows the newest ones and the table only ever grows, so one response never carries more than this. */
    static final int MAX_LISTED_NOTIFICATIONS = 100;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private CurrentUserProvider currentUserProvider;

    @Transactional(readOnly = true)
    public List<NotificationResponseDto> getMyNotifications() {
        Long userId = currentUserProvider.getCurrentUser().getId();
        return notificationRepository.findNewestByUserId(userId, PageRequest.of(0, MAX_LISTED_NOTIFICATIONS)).stream()
                .map(NotificationResponseDto::from)
                .toList();
    }

    @Transactional
    public void create(User user, NotificationType type, String message, Long budgetId) {
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setType(type);
        notification.setMessage(message);
        notification.setBudgetId(budgetId);
        notificationRepository.save(notification);

        log.info("Notification created, userId={}, type={}, budgetId={}", user.getId(), type, budgetId);
    }
}
