package vn.edu.huit.finance.core.Dto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import vn.edu.huit.finance.core.Entity.Receipt;
import vn.edu.huit.finance.core.Entity.ReceiptStatus;
import vn.edu.huit.finance.core.Entity.Transaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class TransactionResponseDtoTest {

    private static Transaction transaction(String note) {
        Transaction transaction = new Transaction();
        transaction.setId(100L);
        transaction.setAmount(new BigDecimal("150000"));
        transaction.setExpense(true);
        transaction.setCategoryName("Ăn uống");
        transaction.setNote(note);
        transaction.setTransactionDate(LocalDateTime.of(2026, 9, 1, 12, 0));
        return transaction;
    }

    @Test
    void everyFieldOfTheTransactionIsCarriedOver() {
        TransactionResponseDto dto = TransactionResponseDto.from(transaction("Bún bò"));

        assertEquals(100L, dto.getId());
        assertEquals(new BigDecimal("150000"), dto.getAmount());
        assertTrue(dto.isExpense());
        assertEquals("Ăn uống", dto.getCategory());
        assertEquals(LocalDateTime.of(2026, 9, 1, 12, 0), dto.getDate());
    }

    @Test
    void anIncomeStaysAnIncome() {
        Transaction income = transaction("Lương");
        income.setExpense(false);

        assertFalse(TransactionResponseDto.from(income).isExpense());
    }

    @Test
    void theTitleIsTheNoteWhenThereIsOne() {
        assertEquals("Bún bò", TransactionResponseDto.from(transaction("Bún bò")).getTitle());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t"})
    void withoutARealNoteTheTitleIsTheNameOfTheCategory(String note) {
        assertEquals("Ăn uống", TransactionResponseDto.from(transaction(note)).getTitle());
    }

    @Test
    void theImageComesFromTheReceiptAndIsMissingWithoutOne() {
        Transaction withReceipt = transaction("Bún bò");
        Receipt receipt = new Receipt();
        receipt.setImageUrl("https://img.example.com/1.jpg");
        withReceipt.setReceipt(receipt);

        assertEquals("https://img.example.com/1.jpg", TransactionResponseDto.from(withReceipt).getImageUrl());
        assertNull(TransactionResponseDto.from(transaction("Bún bò")).getImageUrl());
    }

    @ParameterizedTest
    @EnumSource(ReceiptStatus.class)
    void theReceiptStatusIsTheNameOfTheStatusOfTheReceipt(ReceiptStatus status) {
        Transaction withReceipt = transaction("Bún bò");
        Receipt receipt = new Receipt();
        receipt.setStatus(status.name());
        withReceipt.setReceipt(receipt);

        assertEquals(status.name(), TransactionResponseDto.from(withReceipt).getReceiptStatus());
    }

    @Test
    void withoutAReceiptThereIsNoReceiptStatus() {
        assertNull(TransactionResponseDto.from(transaction("Bún bò")).getReceiptStatus());
    }
}
