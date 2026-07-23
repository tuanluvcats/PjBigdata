package vn.edu.huit.finance.core.Repository;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import vn.edu.huit.finance.core.Entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    List<User> findByWalletBalanceLessThan(BigDecimal amount);

    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);
    @Query(value = "SELECT CAST(u.id AS VARCHAR) as userId, " +
            "COALESCE(EXTRACT(DAY FROM (NOW() - MAX(t.transaction_date))), 999) as recency, " +
            "COUNT(t.id) as frequency, " +
            "COALESCE(SUM(t.amount), 0) as monetary " +
            "FROM users u LEFT JOIN transactions t ON u.id = t.user_id " +
            "GROUP BY u.id", nativeQuery = true)
    List<Object[]> calculateAllUsersRFM();

    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.aiLabel = :label WHERE u.id = :id")
    void updateUserAiLabel(@org.springframework.data.repository.query.Param("id") Long id, @org.springframework.data.repository.query.Param("label") String label);

    long countByAiLabel(String aiLabel);
}
