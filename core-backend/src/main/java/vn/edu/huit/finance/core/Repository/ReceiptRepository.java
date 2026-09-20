package vn.edu.huit.finance.core.Repository;

import vn.edu.huit.finance.core.Entity.Receipt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ReceiptRepository extends JpaRepository<Receipt, Long> {
    /** One receipt with the transaction that carries it and that transaction's owner in a single query, so an ownership check adds none. */
    @Query("SELECT r FROM Receipt r " +
           "LEFT JOIN FETCH r.transaction t " +
           "LEFT JOIN FETCH t.user " +
           "WHERE r.id = :id")
    Optional<Receipt> findDetailedById(@Param("id") Long id);

    List<Receipt> findByStatus(String status);
    Page<Receipt> findAllByOrderByCreatedAtDesc(Pageable pageable);
    @org.springframework.data.jpa.repository.Query("SELECT r.status, COUNT(r) FROM Receipt r GROUP BY r.status")
    List<Object[]> getReceiptStatusStatistics();
}
