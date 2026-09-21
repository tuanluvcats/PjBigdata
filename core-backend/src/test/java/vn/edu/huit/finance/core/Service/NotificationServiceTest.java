package vn.edu.huit.finance.core.Service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.huit.finance.core.Dto.NotificationResponseDto;
import vn.edu.huit.finance.core.Entity.Notification;
import vn.edu.huit.finance.core.Entity.NotificationType;
import vn.edu.huit.finance.core.Entity.User;
import vn.edu.huit.finance.core.Exception.UserNotFoundException;
import vn.edu.huit.finance.core.Repository.NotificationRepository;
import vn.edu.huit.finance.core.Security.CurrentUserProvider;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final Long ALICE_ID = 1L;
    private static final Long BOB_ID = 2L;
    private static final Long BUDGET_ID = 100L;
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 9, 21, 10, 15, 30);

    @Mock private NotificationRepository notificationRepository;
    @Mock private CurrentUserProvider currentUserProvider;
    @InjectMocks private NotificationService service;

    private final User alice = user(ALICE_ID);

    private static User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("user" + id);
        return user;
    }

    private static Notification notification(Long id, NotificationType type, String message, Long budgetId) {
        Notification notification = new Notification();
        notification.setId(id);
        notification.setType(type);
        notification.setMessage(message);
        notification.setBudgetId(budgetId);
        notification.setCreatedAt(CREATED_AT);
        return notification;
    }

    // ----------------------------------------------------------------------------------------------------- the listing

    @Test
    void getMyNotifications_listsWhatTheRepositoryReturnsForTheCaller_inTheSameOrder() {
        when(currentUserProvider.getCurrentUser()).thenReturn(alice);
        when(notificationRepository.findNewestByUserId(eq(ALICE_ID), any(Pageable.class))).thenReturn(List.of(
                notification(2L, NotificationType.BUDGET_EXCEEDED, "vượt", BUDGET_ID),
                notification(1L, NotificationType.BUDGET_WARNING, "đạt 80%", BUDGET_ID)));

        List<NotificationResponseDto> mine = service.getMyNotifications();

        assertEquals(2, mine.size());
        assertEquals(2L, mine.get(0).getId());
        assertEquals(NotificationType.BUDGET_EXCEEDED, mine.get(0).getType());
        assertEquals("vượt", mine.get(0).getMessage());
        assertEquals(BUDGET_ID, mine.get(0).getBudgetId());
        assertEquals(CREATED_AT, mine.get(0).getCreatedAt());
        assertEquals(1L, mine.get(1).getId());
    }

    @Test
    void getMyNotifications_asksOnlyForTheCaller_neverForAnotherUser() {
        when(currentUserProvider.getCurrentUser()).thenReturn(alice);
        when(notificationRepository.findNewestByUserId(eq(ALICE_ID), any(Pageable.class))).thenReturn(List.of());

        service.getMyNotifications();

        verify(notificationRepository).findNewestByUserId(eq(ALICE_ID), any(Pageable.class));
        verify(notificationRepository, never()).findNewestByUserId(eq(BOB_ID), any(Pageable.class));
        verifyNoMoreInteractions(notificationRepository);
    }

    @Test
    void getMyNotifications_isCappedToTheNewest() {
        when(currentUserProvider.getCurrentUser()).thenReturn(alice);
        when(notificationRepository.findNewestByUserId(eq(ALICE_ID), any(Pageable.class))).thenReturn(List.of());

        service.getMyNotifications();

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationRepository).findNewestByUserId(eq(ALICE_ID), page.capture());
        assertEquals(0, page.getValue().getPageNumber());
        assertEquals(NotificationService.MAX_LISTED_NOTIFICATIONS, page.getValue().getPageSize());
    }

    @Test
    void getMyNotifications_isEmptyWhenThereIsNothing() {
        when(currentUserProvider.getCurrentUser()).thenReturn(alice);
        when(notificationRepository.findNewestByUserId(eq(ALICE_ID), any(Pageable.class))).thenReturn(List.of());

        assertTrue(service.getMyNotifications().isEmpty());
    }

    @Test
    void getMyNotifications_failsAndReadsNothing_whenTheCallerCannotBeResolved() {
        when(currentUserProvider.getCurrentUser()).thenThrow(new UserNotFoundException("gone"));

        assertThrows(UserNotFoundException.class, () -> service.getMyNotifications());

        verifyNoInteractions(notificationRepository);
    }

    // ------------------------------------------------------------------------------------------------------ creating

    @Test
    void create_savesANotificationForTheGivenUser() {
        service.create(alice, NotificationType.BUDGET_WARNING, "đạt 80%", BUDGET_ID);

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(saved.capture());
        assertSame(alice, saved.getValue().getUser());
        assertEquals(NotificationType.BUDGET_WARNING, saved.getValue().getType());
        assertEquals("đạt 80%", saved.getValue().getMessage());
        assertEquals(BUDGET_ID, saved.getValue().getBudgetId());
    }

    @Test
    void create_doesNotSwallowADatabaseFailure_soTheCallersTransactionCanRollBack() {
        doThrow(new IllegalStateException("database went away")).when(notificationRepository).save(any(Notification.class));

        assertThrows(IllegalStateException.class,
                () -> service.create(alice, NotificationType.BUDGET_EXCEEDED, "vượt", BUDGET_ID));
    }

    // ---------------------------------------------------------------------------------------------- structural guards

    @Test
    void theListingIsReadOnly_andCreatingWritesInTheTransactionOfItsCaller() throws NoSuchMethodException {
        Method listing = NotificationService.class.getMethod("getMyNotifications");
        Method creating = NotificationService.class.getMethod(
                "create", User.class, NotificationType.class, String.class, Long.class);

        Transactional reading = AnnotatedElementUtils.findMergedAnnotation(listing, Transactional.class);
        Transactional writing = AnnotatedElementUtils.findMergedAnnotation(creating, Transactional.class);

        assertNotNull(reading);
        assertTrue(reading.readOnly());
        assertNotNull(writing);
        assertFalse(writing.readOnly());
    }
}
