package vn.edu.huit.finance.core.Repository;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import vn.edu.huit.finance.core.Entity.Notification;
import vn.edu.huit.finance.core.Entity.NotificationType;
import vn.edu.huit.finance.core.Entity.User;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs the real mapping and query on H2 (the project has no test PostgreSQL), so ordering, the ownership scoping and the
 * "one query" promise are checked for real. The creation time is set by JPA auditing, so the tests move it afterwards
 * with a native update, which is the only way to control it.
 */
@DataJpaTest(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.generate_statistics=true"})
class NotificationRepositoryTest {

    private static final LocalDateTime NOON = LocalDateTime.of(2026, 9, 21, 12, 0);
    private static final int WHOLE_LIST = 100;

    @Autowired private NotificationRepository notificationRepository;
    @Autowired private TestEntityManager entityManager;

    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        alice = persistUser("alice", "0900000001");
        bob = persistUser("bob", "0900000002");
    }

    private User persistUser(String username, String phone) {
        User user = new User();
        user.setUsername(username);
        user.setPassword("hash");
        user.setEmail(username + "@example.com");
        user.setPhone(phone);
        return entityManager.persistAndFlush(user);
    }

    private Notification persistNotification(User owner, NotificationType type, String message, LocalDateTime createdAt) {
        Notification notification = new Notification();
        notification.setUser(owner);
        notification.setType(type);
        notification.setMessage(message);
        notification.setBudgetId(100L);
        Notification saved = entityManager.persistAndFlush(notification);
        entityManager.getEntityManager()
                .createNativeQuery("update notifications set created_at = :createdAt where id = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", saved.getId())
                .executeUpdate();
        return saved;
    }

    private Statistics statistics() {
        return entityManager.getEntityManager().getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
    }

    private void forgetLoadedEntities() {
        entityManager.flush();
        entityManager.clear();
        statistics().clear();
    }

    private List<String> messagesOf(User owner, int size) {
        return notificationRepository.findNewestByUserId(owner.getId(), PageRequest.of(0, size)).stream()
                .map(Notification::getMessage)
                .toList();
    }

    @Test
    void findNewestByUserId_listsTheNewestFirst() {
        persistNotification(alice, NotificationType.BUDGET_WARNING, "oldest", NOON.minusDays(2));
        persistNotification(alice, NotificationType.BUDGET_EXCEEDED, "newest", NOON);
        persistNotification(alice, NotificationType.BUDGET_WARNING, "middle", NOON.minusDays(1));
        forgetLoadedEntities();

        assertEquals(List.of("newest", "middle", "oldest"), messagesOf(alice, WHOLE_LIST));
    }

    @Test
    void findNewestByUserId_putsTheLaterOneFirst_whenTwoWereRaisedInTheSameInstant() {
        persistNotification(alice, NotificationType.BUDGET_WARNING, "first", NOON);
        persistNotification(alice, NotificationType.BUDGET_EXCEEDED, "second", NOON);
        forgetLoadedEntities();

        assertEquals(List.of("second", "first"), messagesOf(alice, WHOLE_LIST));
    }

    @Test
    void findNewestByUserId_neverReturnsTheNotificationsOfAnotherUser() {
        persistNotification(alice, NotificationType.BUDGET_WARNING, "alice's", NOON);
        persistNotification(bob, NotificationType.BUDGET_WARNING, "bob's", NOON);
        forgetLoadedEntities();

        assertEquals(List.of("alice's"), messagesOf(alice, WHOLE_LIST));
        assertEquals(List.of("bob's"), messagesOf(bob, WHOLE_LIST));
    }

    @Test
    void findNewestByUserId_returnsNothingForAUserWithoutNotifications() {
        persistNotification(bob, NotificationType.BUDGET_WARNING, "bob's", NOON);
        forgetLoadedEntities();

        assertTrue(messagesOf(alice, WHOLE_LIST).isEmpty());
    }

    @Test
    void findNewestByUserId_cutsTheListToThePageSize_keepingTheNewest() {
        for (int i = 0; i < 5; i++) {
            persistNotification(alice, NotificationType.BUDGET_WARNING, "n" + i, NOON.plusMinutes(i));
        }
        forgetLoadedEntities();

        assertEquals(List.of("n4", "n3"), messagesOf(alice, 2));
    }

    @Test
    void findNewestByUserId_readsEverythingTheDtoNeedsInOneQuery() {
        for (int i = 0; i < 3; i++) {
            persistNotification(alice, NotificationType.BUDGET_WARNING, "n" + i, NOON.plusMinutes(i));
        }
        forgetLoadedEntities();

        List<Notification> found = notificationRepository.findNewestByUserId(alice.getId(), PageRequest.of(0, WHOLE_LIST));
        // What NotificationResponseDto.from reads.
        found.forEach(n -> {
            n.getId();
            n.getType();
            n.getMessage();
            n.getBudgetId();
            n.getCreatedAt();
        });

        assertEquals(3, found.size());
        assertEquals(1, statistics().getPrepareStatementCount(), "listing must not issue a query per notification");
    }

    @Test
    void theTypeIsStoredAsItsName_notItsOrdinal() {
        persistNotification(alice, NotificationType.BUDGET_EXCEEDED, "stored", NOON);
        entityManager.flush();

        Object stored = entityManager.getEntityManager()
                .createNativeQuery("select type from notifications").getSingleResult();

        assertEquals("BUDGET_EXCEEDED", stored);
    }

    @Test
    void aNotificationGetsItsCreationTimeFromJpaAuditing() {
        Notification notification = new Notification();
        notification.setUser(alice);
        notification.setType(NotificationType.BUDGET_WARNING);
        notification.setMessage("audited");

        Notification saved = entityManager.persistAndFlush(notification);

        assertNotNull(saved.getCreatedAt(), "the list is ordered by it, so it must never be null");
    }
}
