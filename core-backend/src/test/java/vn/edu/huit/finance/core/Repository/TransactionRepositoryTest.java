package vn.edu.huit.finance.core.Repository;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import vn.edu.huit.finance.core.Entity.Category;
import vn.edu.huit.finance.core.Entity.Receipt;
import vn.edu.huit.finance.core.Entity.Transaction;
import vn.edu.huit.finance.core.Entity.User;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/** Runs the real mapping and queries on H2, so the "one statement" promise of findDetailedById is measured, not assumed. */
@DataJpaTest(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.generate_statistics=true"})
class TransactionRepositoryTest {

    @Autowired private TransactionRepository transactionRepository;
    @Autowired private TestEntityManager entityManager;

    private Transaction withReceipt;
    private Transaction withoutReceipt;

    @BeforeEach
    void setUp() {
        User alice = new User();
        alice.setUsername("alice");
        alice.setPassword("hash");
        alice.setEmail("alice@example.com");
        alice.setPhone("0900000001");
        entityManager.persist(alice);

        Category food = new Category();
        food.setName("Ăn uống");
        food.setType("EXPENSE");
        entityManager.persist(food);

        Receipt receipt = new Receipt();
        receipt.setVendorName("Highlands");
        receipt.setImageUrl("https://img.example.com/1.jpg");

        withReceipt = newTransaction(alice, food);
        withReceipt.setReceipt(receipt);
        withoutReceipt = newTransaction(alice, food);
        entityManager.persist(withReceipt);
        entityManager.persist(withoutReceipt);
        entityManager.flush();
    }

    private static Transaction newTransaction(User owner, Category category) {
        Transaction transaction = new Transaction();
        transaction.setUser(owner);
        transaction.setCategory(category);
        transaction.setCategoryName(category.getName());
        transaction.setAmount(new BigDecimal("100000"));
        transaction.setExpense(true);
        transaction.setTransactionDate(LocalDateTime.of(2026, 9, 1, 12, 0));
        return transaction;
    }

    private Statistics statistics() {
        return entityManager.getEntityManager().getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
    }

    private void forgetLoadedEntities() {
        entityManager.flush();
        entityManager.clear();
        statistics().clear();
    }

    @Test
    void findDetailedByIdLoadsTheOwnerTheCategoryAndTheReceiptInOneStatement() {
        forgetLoadedEntities();

        Transaction found = transactionRepository.findDetailedById(withReceipt.getId()).orElseThrow();
        String owner = found.getUser().getUsername();
        String category = found.getCategory().getName();
        String image = found.getReceipt().getImageUrl();

        assertEquals("alice", owner);
        assertEquals("Ăn uống", category);
        assertEquals("https://img.example.com/1.jpg", image);
        assertEquals(1, statistics().getPrepareStatementCount(), "reading the associations must not add queries");
    }

    @Test
    void aTransactionWithoutAReceiptIsStillFound() {
        forgetLoadedEntities();

        Transaction found = transactionRepository.findDetailedById(withoutReceipt.getId()).orElseThrow();

        assertNull(found.getReceipt());
        assertEquals(1, statistics().getPrepareStatementCount());
    }

    @Test
    void aMissingIdIsEmpty() {
        assertTrue(transactionRepository.findDetailedById(-1L).isEmpty());
    }

    @Test
    void controlPlainFindByIdReallyNeedsMoreStatementsSoTheCounterIsTrustworthy() {
        forgetLoadedEntities();

        Transaction found = transactionRepository.findById(withReceipt.getId()).orElseThrow();
        found.getUser().getUsername();
        found.getCategory().getName();
        found.getReceipt().getImageUrl();

        assertTrue(statistics().getPrepareStatementCount() > 1, "lazy associations cost one statement each");
    }
}
