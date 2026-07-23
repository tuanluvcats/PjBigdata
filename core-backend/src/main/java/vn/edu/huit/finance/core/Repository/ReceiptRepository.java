package vn.edu.huit.finance.core.Repository;

import vn.edu.huit.finance.core.Entity.Receipt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReceiptRepository extends JpaRepository<Receipt, Long> {
    List<Receipt> findByStatus(String status);
    Page<Receipt> findAllByOrderByCreatedAtDesc(Pageable pageable);
    @org.springframework.data.jpa.repository.Query("SELECT r.status, COUNT(r) FROM Receipt r GROUP BY r.status")
    List<Object[]> getReceiptStatusStatistics();
}
