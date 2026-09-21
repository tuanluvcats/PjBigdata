package vn.edu.huit.finance.core.Repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.edu.huit.finance.core.Entity.Notification;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // n.user.id compares the user_id column, no join, and the DTO reads nothing lazy: the whole list is one query.
    // The id breaks ties between notifications raised in the same instant.
    @Query("select n from Notification n where n.user.id = :userId order by n.createdAt desc, n.id desc")
    List<Notification> findNewestByUserId(@Param("userId") Long userId, Pageable pageable);
}
