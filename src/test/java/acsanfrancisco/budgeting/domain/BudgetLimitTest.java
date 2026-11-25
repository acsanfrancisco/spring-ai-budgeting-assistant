package acsanfrancisco.budgeting.domain;

import acsanfrancisco.budgeting.domain.budget.BudgetLimit;
import acsanfrancisco.budgeting.domain.budget.BudgetLimitId;
import acsanfrancisco.budgeting.domain.transaction.Category;
import acsanfrancisco.budgeting.domain.user.User;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.stream.Stream;

public class BudgetLimitTest {

    @ParameterizedTest
    @ValueSource(strings = {"10.005", "0.001", "1E+17"})
    void rejectsAmountsOutsideExactCentRange(String input) {
        BigDecimal value = new BigDecimal(input);
        Assertions.assertThrows(IllegalArgumentException.class, () -> new BudgetLimit(user, Category.GROCERIES, period, value));
    }

    @ParameterizedTest
    @ValueSource(strings = {"10.000", "99999999999999999.99"})
    void acceptsExactCents(String input) {
        BigDecimal value = new BigDecimal(input);
        Assertions.assertEquals(value.setScale(2), new BudgetLimit(user, Category.GROCERIES, period, value).getLimitAmount());
    }

    static Stream<Arguments> invalidReconstructedLimits() {
        BudgetLimitId id = new BudgetLimitId();
        User owner = new User("First Test User", "first@example.com", "encoded-password");
        YearMonth period = YearMonth.of(2026, 11);
        return Stream.of(
                Arguments.of(null, owner, Category.GROCERIES, period, BigDecimal.TEN),
                Arguments.of(id, null, Category.GROCERIES, period, BigDecimal.TEN),
                Arguments.of(id, owner, null, period, BigDecimal.TEN),
                Arguments.of(id, owner, Category.SALARY, period, BigDecimal.TEN),
                Arguments.of(id, owner, Category.FREELANCE, period, BigDecimal.TEN),
                Arguments.of(id, owner, Category.INVESTMENT, period, BigDecimal.TEN),
                Arguments.of(id, owner, Category.OTHER_INCOMES, period, BigDecimal.TEN),
                Arguments.of(id, owner, Category.GROCERIES, null, BigDecimal.TEN),
                Arguments.of(id, owner, Category.GROCERIES, period, null),
                Arguments.of(id, owner, Category.GROCERIES, period, BigDecimal.ZERO),
                Arguments.of(id, owner, Category.GROCERIES, period, BigDecimal.ONE.negate())
        );
    }

    @ParameterizedTest
    @MethodSource("invalidReconstructedLimits")
    @DisplayName("Should reject invalid fields when reconstructing a limit with ID")
    void shouldRejectInvalidReconstructedLimit(BudgetLimitId id, User owner, Category category,
            YearMonth period, BigDecimal amount) {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new BudgetLimit(id, owner, category, period, amount));
    }

    @Test
    @DisplayName("Should restore budget limit with supplied identity and fields")
    void shouldReconstructValidLimit() {
        BudgetLimitId id = new BudgetLimitId();
        BudgetLimit limit = new BudgetLimit(id, user, Category.GROCERIES, period, BigDecimal.TEN);

        Assertions.assertEquals(id, limit.getId());
        Assertions.assertSame(user, limit.getUser());
        Assertions.assertEquals(Category.GROCERIES, limit.getCategory());
        Assertions.assertEquals(period, limit.getPeriod());
        Assertions.assertEquals(new BigDecimal("10.00"), limit.getLimitAmount());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0", "-0.01", "10.005", "1E+17"})
    @DisplayName("Should preserve limit amount when rejecting an invalid replacement")
    void shouldPreserveLimitAfterRejectedReplacement(String amount) {
        BudgetLimit limit = new BudgetLimit(user, Category.GROCERIES, period, new BigDecimal("500.00"));
        BigDecimal replacement = amount == null ? null : new BigDecimal(amount);

        Assertions.assertThrows(IllegalArgumentException.class, () -> limit.setLimitAmount(replacement));
        Assertions.assertEquals(new BigDecimal("500.00"), limit.getLimitAmount());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.01", "750.00"})
    @DisplayName("Should replace limit amount without changing identity, owner, category or period")
    void shouldReplaceLimitAmount(String amount) {
        BudgetLimit limit = new BudgetLimit(user, Category.GROCERIES, period, new BigDecimal("500.00"));
        BudgetLimitId id = limit.getId();

        limit.setLimitAmount(new BigDecimal(amount));

        Assertions.assertEquals(new BigDecimal(amount), limit.getLimitAmount());
        Assertions.assertEquals(id, limit.getId());
        Assertions.assertSame(user, limit.getUser());
        Assertions.assertEquals(Category.GROCERIES, limit.getCategory());
        Assertions.assertEquals(period, limit.getPeriod());
    }

    private User user;
    private YearMonth period;

    @BeforeEach
    void setUp() {
        user = new User("First Test User", "first@example.com", "encoded-password");
        period = YearMonth.of(2026, 11);
    }

    @Test
    @DisplayName("Should create budget limit with generated ID, expense category and positive amount")
    void shouldCreateBudgetLimit() {
        BudgetLimit limit = new BudgetLimit(user, Category.GROCERIES, period, new BigDecimal("0.01"));

        Assertions.assertNotNull(limit.getId());
        Assertions.assertNotNull(limit.getId().id());
        Assertions.assertSame(user, limit.getUser());
        Assertions.assertEquals(Category.GROCERIES, limit.getCategory());
        Assertions.assertEquals(period, limit.getPeriod());
        Assertions.assertEquals(new BigDecimal("0.01"), limit.getLimitAmount());
    }

    @Test
    @DisplayName("Should reject budget limit without user")
    void shouldRejectNullUser() {
        IllegalArgumentException thrown = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new BudgetLimit(null, Category.GROCERIES, period, BigDecimal.TEN));

        Assertions.assertEquals("User must be informed", thrown.getMessage());
    }

    @Test
    @DisplayName("Should reject budget limit without category")
    void shouldRejectNullCategory() {
        IllegalArgumentException thrown = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new BudgetLimit(user, null, period, BigDecimal.TEN));

        Assertions.assertEquals("Category must be informed", thrown.getMessage());
    }

    @ParameterizedTest
    @EnumSource(value = Category.class, names = {"SALARY", "FREELANCE", "INVESTMENT", "OTHER_INCOMES"})
    @DisplayName("Should reject income categories for budget limits")
    void shouldRejectIncomeCategory(Category category) {
        IllegalArgumentException thrown = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new BudgetLimit(user, category, period, BigDecimal.TEN));

        Assertions.assertEquals("Category type must be EXPENSE", thrown.getMessage());
    }

    @Test
    @DisplayName("Should reject budget limit without period")
    void shouldRejectNullPeriod() {
        IllegalArgumentException thrown = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new BudgetLimit(user, Category.GROCERIES, null, BigDecimal.TEN));

        Assertions.assertEquals("Period must be informed", thrown.getMessage());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0.00", "-0.01"})
    @DisplayName("Should reject null, zero or negative budget limit amount")
    void shouldRejectInvalidAmount(String amount) {
        BigDecimal value = amount == null ? null : new BigDecimal(amount);

        IllegalArgumentException thrown = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new BudgetLimit(user, Category.GROCERIES, period, value));

        Assertions.assertEquals("Amount value must be informed an greater than zero", thrown.getMessage());
    }
}
