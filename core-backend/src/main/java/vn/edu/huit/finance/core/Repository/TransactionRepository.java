package vn.edu.huit.finance.core.Repository;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.huit.finance.core.Entity.Transaction;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    @Query("SELECT t FROM Transaction t LEFT JOIN t.receipt r WHERE t.user.id = :userId " +
            "AND (:categoryId IS NULL OR t.category.id = :categoryId) " +
            "AND (:minPrice IS NULL OR t.amount >= :minPrice) " +
            "AND (:maxPrice IS NULL OR t.amount <= :maxPrice) " +
            "AND (:vendorName IS NULL OR LOWER(r.vendorName) LIKE LOWER(CONCAT('%', :vendorName, '%')))")
    List<Transaction> filterTransactions(
            @Param("userId") Long userId,
            @Param("categoryId") Long categoryId,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("vendorName") String vendorName,
            Sort sort
    );
    @Query("SELECT SUM(t.amount) FROM Transaction t")
    BigDecimal sumTotalVolume();
    @Query("SELECT t.receipt.vendorName, COUNT(t.id) as freq " +
            "FROM Transaction t " +
            "WHERE t.user.id = :userId AND t.receipt.vendorName IS NOT NULL " +
            "GROUP BY t.receipt.vendorName " +
            "ORDER BY freq DESC")
    List<Object[]> findPopularVendors(@org.springframework.data.repository.query.Param("userId") Long userId);
    @Query("SELECT t FROM Transaction t LEFT JOIN t.receipt r WHERE t.user.id = :userId " +
            "AND (:categoryId IS NULL OR t.category.id = :categoryId) " +
            "AND (:minPrice IS NULL OR t.amount >= :minPrice) " +
            "AND (:maxPrice IS NULL OR t.amount <= :maxPrice) " +
            "AND (:vendorName IS NULL OR LOWER(r.vendorName) LIKE LOWER(CONCAT('%', :vendorName, '%'))) " +
            "AND (CAST(:startDate AS date) IS NULL OR t.transactionDate >= :startDate) " +
            "AND (CAST(:endDate AS date) IS NULL OR t.transactionDate <= :endDate)")
    List<Transaction> filterTransactions(
            @Param("userId") Long userId,
            @Param("categoryId") Long categoryId,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("vendorName") String vendorName,
            @Param("startDate") java.time.LocalDate startDate,
            @Param("endDate") java.time.LocalDate endDate,
            org.springframework.data.domain.Sort sort
    );
    Transaction findTopByUserIdOrderByAmountDesc(Long userId);
    List<Transaction> findByUserIdAndVoucherCodeNotNull(Long userId);
    long countByTransactionDateBetween(java.time.LocalDateTime startDate, java.time.LocalDateTime endDate);
    List<Transaction> findByUserId(Long userId, org.springframework.data.domain.Pageable pageable);


    List<Transaction> findByUserIdOrderByTransactionDateDesc(Long userId);
    @Query("SELECT t.user.username, SUM(t.amount) as totalSpent FROM Transaction t WHERE t.isExpense = true GROUP BY t.user.id, t.user.username ORDER BY totalSpent DESC")
    List<Object[]> findTopSpenders(org.springframework.data.domain.Pageable pageable);
}
