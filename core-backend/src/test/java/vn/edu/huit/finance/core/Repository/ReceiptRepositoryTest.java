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

/** Runs the real mapping and query on H2, so the "one statement" promise of findDetailedById is measured, not assumed. */
@DataJpaTest(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.generate_statistics=true"})
class ReceiptRepositoryTest {

    @Autowired private ReceiptRepository receiptRepository;
    @Autowired private TestEntityManager entityManager;

    private Receipt carried;
    private Receipt orphan;

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

        carried = new Receipt();
        carried.setVendorName("Highlands");
        carried.setStatus("FAILED");

        Transaction transaction = new Transaction();
        transaction.setUser(alice);
        transaction.setCategory(food);
        transaction.setCategoryName(food.getName());
        transaction.setAmount(new BigDecimal("100000"));
        transaction.setExpense(true);
        transaction.setTransactionDate(LocalDateTime.of(2026, 9, 1, 12, 0));
        transaction.setReceipt(carried);
        entityManager.persist(transaction);

        orphan = new Receipt();
        orphan.setStatus("PENDING");
        entityManager.persist(orphan);
        entityManager.flush();
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
    void findDetailedByIdLoadsTheCarryingTransactionAndItsOwnerInOneStatement() {
        forgetLoadedEntities();

        Receipt found = receiptRepository.findDetailedById(carried.getId()).orElseThrow();
        String owner = found.getTransaction().getUser().getUsername();

        assertEquals("alice", owner);
        assertEquals(1, statistics().getPrepareStatementCount(), "checking the owner must not add queries");
    }

    @Test
    void aReceiptNoTransactionCarriesIsStillFoundWithoutOne() {
        forgetLoadedEntities();

        Receipt found = receiptRepository.findDetailedById(orphan.getId()).orElseThrow();

        assertNull(found.getTransaction());
        assertEquals(1, statistics().getPrepareStatementCount());
    }

    @Test
    void aMissingIdIsEmpty() {
        assertTrue(receiptRepository.findDetailedById(-1L).isEmpty());
    }

    @Test
    void controlPlainFindByIdReallyNeedsMoreStatementsSoTheCounterIsTrustworthy() {
        forgetLoadedEntities();

        Receipt found = receiptRepository.findById(carried.getId()).orElseThrow();
        found.getTransaction().getUser().getUsername();

        assertTrue(statistics().getPrepareStatementCount() > 1, "the lazy owner costs a statement of its own");
    }
}
