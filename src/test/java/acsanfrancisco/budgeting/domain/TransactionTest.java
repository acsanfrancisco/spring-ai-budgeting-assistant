package acsanfrancisco.budgeting.domain;

import acsanfrancisco.budgeting.domain.transaction.Category;
import acsanfrancisco.budgeting.domain.transaction.Transaction;
import acsanfrancisco.budgeting.domain.transaction.TransactionId;
import acsanfrancisco.budgeting.domain.transaction.TransactionType;
import acsanfrancisco.budgeting.domain.user.User;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.stream.Stream;

public class TransactionTest {

    @ParameterizedTest
    @ValueSource(strings = {"10.005", "0.001", "1E+17"})
    void rejectsAmountsOutsideExactCentRange(String input) {
        BigDecimal value = new BigDecimal(input);
        Assertions.assertThrows(IllegalArgumentException.class, () -> new Transaction("Groceries", value, Category.GROCERIES, user, date));
    }

    @ParameterizedTest
    @ValueSource(strings = {"10.000", "99999999999999999.99"})
    void acceptsExactCents(String input) {
        BigDecimal value = new BigDecimal(input);
        Assertions.assertEquals(value.setScale(2), new Transaction("Groceries", value, Category.GROCERIES, user, date).getAmount());
    }

    static Stream<Arguments> invalidReconstructedTransactions() {
        TransactionId id = new TransactionId();
        User owner = new User("First Test User", "first@example.com", "encoded-password");
        LocalDate date = LocalDate.of(2026, 11, 1);
        return Stream.of(
                Arguments.of(null, "Groceries", BigDecimal.TEN, Category.GROCERIES, date, TransactionType.EXPENSE, owner),
                Arguments.of(id, null, BigDecimal.TEN, Category.GROCERIES, date, TransactionType.EXPENSE, owner),
                Arguments.of(id, "", BigDecimal.TEN, Category.GROCERIES, date, TransactionType.EXPENSE, owner),
                Arguments.of(id, " ", BigDecimal.TEN, Category.GROCERIES, date, TransactionType.EXPENSE, owner),
                Arguments.of(id, "Groceries", null, Category.GROCERIES, date, TransactionType.EXPENSE, owner),
                Arguments.of(id, "Groceries", BigDecimal.ZERO, Category.GROCERIES, date, TransactionType.EXPENSE, owner),
                Arguments.of(id, "Groceries", BigDecimal.ONE.negate(), Category.GROCERIES, date, TransactionType.EXPENSE, owner),
                Arguments.of(id, "Groceries", BigDecimal.TEN, null, date, TransactionType.EXPENSE, owner),
                Arguments.of(id, "Groceries", BigDecimal.TEN, Category.GROCERIES, null, TransactionType.EXPENSE, owner),
                Arguments.of(id, "Groceries", BigDecimal.TEN, Category.GROCERIES, date, null, owner),
                Arguments.of(id, "Groceries", BigDecimal.TEN, Category.GROCERIES, date, TransactionType.EXPENSE, null),
                Arguments.of(id, "Groceries", BigDecimal.TEN, Category.GROCERIES, date, TransactionType.INCOME, owner),
                Arguments.of(id, "Salary", BigDecimal.TEN, Category.SALARY, date, TransactionType.EXPENSE, owner)
        );
    }

    @ParameterizedTest
    @MethodSource("invalidReconstructedTransactions")
    @DisplayName("Should reject invalid fields when reconstructing a transaction with ID")
    void shouldRejectInvalidReconstructedTransaction(TransactionId id, String description, BigDecimal amount,
            Category category, LocalDate date, TransactionType type, User owner) {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new Transaction(id, description, amount, category, date, type, owner));
    }

    @ParameterizedTest
    @EnumSource(value = Category.class, names = {"GROCERIES", "SALARY"})
    @DisplayName("Should restore transaction with supplied identity and consistent type")
    void shouldReconstructValidTransaction(Category category) {
        TransactionId id = new TransactionId();
        Transaction transaction = new Transaction(id, "Original description", BigDecimal.TEN,
                category, date, category.getType(), user);

        Assertions.assertEquals(id, transaction.getId());
        Assertions.assertEquals("Original description", transaction.getDescription());
        Assertions.assertEquals(new BigDecimal("10.00"), transaction.getAmount());
        Assertions.assertEquals(category, transaction.getCategory());
        Assertions.assertEquals(category.getType(), transaction.getType());
        Assertions.assertEquals(date, transaction.getDate());
        Assertions.assertSame(user, transaction.getUser());
    }

    @Test
    @DisplayName("Should correct editable fields while preserving identity, owner and type")
    void shouldCorrectEditableFields() {
        Transaction transaction = new Transaction("Groceries", new BigDecimal("50.00"), Category.GROCERIES, user, date);
        TransactionId id = transaction.getId();

        transaction.setDescription("Medicine");
        transaction.setAmount(new BigDecimal("0.01"));
        transaction.setCategory(Category.PHARMA);
        transaction.setDate(date.minusDays(1));

        Assertions.assertEquals("Medicine", transaction.getDescription());
        Assertions.assertEquals(new BigDecimal("0.01"), transaction.getAmount());
        Assertions.assertEquals(Category.PHARMA, transaction.getCategory());
        Assertions.assertEquals(date.minusDays(1), transaction.getDate());
        Assertions.assertEquals(id, transaction.getId());
        Assertions.assertSame(user, transaction.getUser());
        Assertions.assertEquals(TransactionType.EXPENSE, transaction.getType());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0", "-0.01", "10.005", "1E+17"})
    @DisplayName("Should preserve amount when rejecting an invalid correction")
    void shouldPreserveAmountAfterRejectedCorrection(String amount) {
        Transaction transaction = new Transaction("Groceries", new BigDecimal("50.00"), Category.GROCERIES, user, date);
        BigDecimal replacement = amount == null ? null : new BigDecimal(amount);

        Assertions.assertThrows(IllegalArgumentException.class, () -> transaction.setAmount(replacement));
        Assertions.assertEquals(new BigDecimal("50.00"), transaction.getAmount());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("Should preserve description when rejecting a missing correction")
    void shouldPreserveDescriptionAfterRejectedCorrection(String description) {
        Transaction transaction = new Transaction("Groceries", BigDecimal.TEN, Category.GROCERIES, user, date);

        Assertions.assertThrows(IllegalArgumentException.class, () -> transaction.setDescription(description));
        Assertions.assertEquals("Groceries", transaction.getDescription());
    }

    @ParameterizedTest
    @NullSource
    @EnumSource(value = Category.class, names = {"SALARY", "FREELANCE", "INVESTMENT", "OTHER_INCOMES"})
    @DisplayName("Should preserve expense category and type when rejecting a correction")
    void shouldPreserveExpenseCategoryAfterRejectedCorrection(Category category) {
        Transaction transaction = new Transaction("Groceries", BigDecimal.TEN, Category.GROCERIES, user, date);

        Assertions.assertThrows(IllegalArgumentException.class, () -> transaction.setCategory(category));
        Assertions.assertEquals(Category.GROCERIES, transaction.getCategory());
        Assertions.assertEquals(TransactionType.EXPENSE, transaction.getType());
    }

    @Test
    @DisplayName("Should allow income recategorization and reject changing income to expense")
    void shouldPreserveIncomeTypeWhenRecategorizing() {
        Transaction transaction = new Transaction("Income", BigDecimal.TEN, Category.SALARY, user, date);
        transaction.setCategory(Category.FREELANCE);
        Assertions.assertEquals(Category.FREELANCE, transaction.getCategory());

        Assertions.assertThrows(IllegalArgumentException.class, () -> transaction.setCategory(Category.GROCERIES));
        Assertions.assertEquals(Category.FREELANCE, transaction.getCategory());
        Assertions.assertEquals(TransactionType.INCOME, transaction.getType());
    }

    @Test
    @DisplayName("Should preserve date when rejecting a null correction")
    void shouldPreserveDateAfterRejectedCorrection() {
        Transaction transaction = new Transaction("Groceries", BigDecimal.TEN, Category.GROCERIES, user, date);

        Assertions.assertThrows(IllegalArgumentException.class, () -> transaction.setDate(null));
        Assertions.assertEquals(date, transaction.getDate());
    }

    private User user;
    private LocalDate date;

    @BeforeEach
    void setUp() {
        user = new User("First Test User", "first@example.com", "encoded-password");
        date = LocalDate.of(2026, 11, 1);
    }

    @ParameterizedTest
    @EnumSource(value = Category.class, names = {"GROCERIES", "SALARY"})
    @DisplayName("Should create transaction with generated ID, supplied data and type derived from category")
    void shouldCreateTransactionAndDeriveType(Category category) {
        Transaction transaction = new Transaction("Test transaction", new BigDecimal("0.01"), category, user, date);

        Assertions.assertNotNull(transaction.getId());
        Assertions.assertNotNull(transaction.getId().id());
        Assertions.assertEquals("Test transaction", transaction.getDescription());
        Assertions.assertEquals(new BigDecimal("0.01"), transaction.getAmount());
        Assertions.assertEquals(category, transaction.getCategory());
        Assertions.assertEquals(category == Category.SALARY ? TransactionType.INCOME : TransactionType.EXPENSE,
                transaction.getType());
        Assertions.assertEquals(date, transaction.getDate());
        Assertions.assertSame(user, transaction.getUser());
    }

    @Test
    @DisplayName("Should use current date when transaction date is omitted")
    void shouldDefaultToCurrentDate() {
        LocalDate before = LocalDate.now();
        Transaction transaction = new Transaction("Groceries", BigDecimal.TEN, Category.GROCERIES, user, null);
        LocalDate after = LocalDate.now();

        Assertions.assertFalse(transaction.getDate().isBefore(before));
        Assertions.assertFalse(transaction.getDate().isAfter(after));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0.00", "-0.01"})
    @DisplayName("Should reject null, zero or negative transaction amount")
    void shouldRejectInvalidAmount(String amount) {
        BigDecimal value = amount == null ? null : new BigDecimal(amount);

        IllegalArgumentException thrown = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new Transaction("Groceries", value, Category.GROCERIES, user, date));

        Assertions.assertEquals("Amount value must be informed an greater than zero", thrown.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("Should reject null, empty or blank transaction description")
    void shouldRejectInvalidDescription(String description) {
        IllegalArgumentException thrown = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new Transaction(description, BigDecimal.TEN, Category.GROCERIES, user, date));

        Assertions.assertEquals("Must inform description", thrown.getMessage());
    }

    @Test
    @DisplayName("Should reject transaction without category")
    void shouldRejectNullCategory() {
        IllegalArgumentException thrown = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new Transaction("Groceries", BigDecimal.TEN, null, user, date));

        Assertions.assertEquals("Must inform category", thrown.getMessage());
    }

    @Test
    @DisplayName("Should reject transaction without user")
    void shouldRejectNullUser() {
        IllegalArgumentException thrown = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new Transaction("Groceries", BigDecimal.TEN, Category.GROCERIES, null, date));

        Assertions.assertEquals("Must inform user id", thrown.getMessage());
    }
}
