package acsanfrancisco.budgeting.application.usecase.budget;

import acsanfrancisco.budgeting.application.exception.ResourceNotFoundException;
import acsanfrancisco.budgeting.application.output.MonthlyBudgetLimitStatusOutput;
import acsanfrancisco.budgeting.domain.budget.BudgetLimitRepository;
import acsanfrancisco.budgeting.domain.budget.BudgetStatus;
import acsanfrancisco.budgeting.domain.report.MonthlyBudgetLimitStatus;
import acsanfrancisco.budgeting.domain.transaction.Category;
import acsanfrancisco.budgeting.domain.user.User;
import acsanfrancisco.budgeting.domain.user.UserId;
import acsanfrancisco.budgeting.domain.user.UserRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@ExtendWith(MockitoExtension.class)
public class GetBudgetLimitStatusUseCaseTest {

    @Mock
    BudgetLimitRepository budgetLimitRepository;

    @Mock
    UserRepository userRepository;

    @InjectMocks
    GetBudgetLimitStatusUseCase getBudgetLimitStatusUseCase;

    User user;
    UserId userId;
    YearMonth period;
    Category category;

    @BeforeEach
    void setUp() {
        user = new User("Test User", "user@example.com", "password");
        userId = user.getId();
        period = YearMonth.of(2026, 11);
        category = Category.GROCERIES;
    }

    @Test
    @DisplayName("Should return WITHIN_LIMIT without expenses")
    void shouldReturnStatusWithoutExpenses() {
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        MonthlyBudgetLimitStatus budgetLimitStatus = new MonthlyBudgetLimitStatus(new BigDecimal("500.00"), new BigDecimal("0.00"));
        Mockito.when(budgetLimitRepository.getBudgetLimitStatus(period, userId, category))
                .thenReturn(budgetLimitStatus);

        MonthlyBudgetLimitStatusOutput output = getBudgetLimitStatusUseCase.execute(period, userId, category);

        Assertions.assertEquals(BudgetStatus.WITHIN_LIMIT, output.status());
        Assertions.assertEquals(new BigDecimal("500.00"), output.limit());
        Assertions.assertEquals(new BigDecimal("0.00"), output.spent());
        Assertions.assertEquals(new BigDecimal("500.00"), output.remaining());
        Assertions.assertEquals(new BigDecimal("0.00"), output.percentage());
        Mockito.verify(budgetLimitRepository, Mockito.times(1)).getBudgetLimitStatus(period, userId, category);
    }

    @Test
    @DisplayName("Should return WITHIN_LIMIT below warning limit")
    void shouldReturnStatusBelowWarningThreshold() {
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        MonthlyBudgetLimitStatus budgetLimitStatus =
                new MonthlyBudgetLimitStatus(new BigDecimal("500.00"), new BigDecimal("399.99"));
        Mockito.when(budgetLimitRepository.getBudgetLimitStatus(period, userId, category))
                .thenReturn(budgetLimitStatus);

        MonthlyBudgetLimitStatusOutput output = getBudgetLimitStatusUseCase.execute(period, userId, category);

        Assertions.assertEquals(BudgetStatus.WITHIN_LIMIT, output.status());
        Assertions.assertEquals(new BigDecimal("500.00"), output.limit());
        Assertions.assertEquals(new BigDecimal("399.99"), output.spent());
        Assertions.assertEquals(new BigDecimal("100.01"), output.remaining());
        Assertions.assertEquals(new BigDecimal("80.00"), output.percentage());
        Mockito.verify(budgetLimitRepository, Mockito.times(1)).getBudgetLimitStatus(period, userId, category);
    }

    @Test
    @DisplayName("Should return NEAR_LIMIT at warning limit")
    void shouldReturnStatusAtWarningThreshold() {
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        MonthlyBudgetLimitStatus budgetLimitStatus =
                new MonthlyBudgetLimitStatus(new BigDecimal("500.00"), new BigDecimal("400.00"));
        Mockito.when(budgetLimitRepository.getBudgetLimitStatus(period, userId, category))
                .thenReturn(budgetLimitStatus);

        MonthlyBudgetLimitStatusOutput output = getBudgetLimitStatusUseCase.execute(period, userId, category);

        Assertions.assertEquals(BudgetStatus.NEAR_LIMIT, output.status());
        Assertions.assertEquals(new BigDecimal("500.00"), output.limit());
        Assertions.assertEquals(new BigDecimal("400.00"), output.spent());
        Assertions.assertEquals(new BigDecimal("100.00"), output.remaining());
        Assertions.assertEquals(new BigDecimal("80.00"), output.percentage());
        Mockito.verify(budgetLimitRepository, Mockito.times(1)).getBudgetLimitStatus(period, userId, category);
    }

    @Test
    @DisplayName("Should return AT_LIMIT at limit")
    void shouldReturnStatusAtLimit() {
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        MonthlyBudgetLimitStatus budgetLimitStatus =
                new MonthlyBudgetLimitStatus(new BigDecimal("500.00"), new BigDecimal("500.00"));
        Mockito.when(budgetLimitRepository.getBudgetLimitStatus(period, userId, category))
                .thenReturn(budgetLimitStatus);

        MonthlyBudgetLimitStatusOutput output = getBudgetLimitStatusUseCase.execute(period, userId, category);

        Assertions.assertEquals(BudgetStatus.AT_LIMIT, output.status());
        Assertions.assertEquals(new BigDecimal("500.00"), output.limit());
        Assertions.assertEquals(new BigDecimal("500.00"), output.spent());
        Assertions.assertEquals(new BigDecimal("0.00"), output.remaining());
        Assertions.assertEquals(new BigDecimal("100.00"), output.percentage());
        Mockito.verify(budgetLimitRepository, Mockito.times(1)).getBudgetLimitStatus(period, userId, category);
    }

    @Test
    @DisplayName("Should return EXCEEDED above limit")
    void shouldReturnStatusAboveLimit() {
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        MonthlyBudgetLimitStatus budgetLimitStatus =
                new MonthlyBudgetLimitStatus(new BigDecimal("500.00"), new BigDecimal("600.00"));
        Mockito.when(budgetLimitRepository.getBudgetLimitStatus(period, userId, category))
                .thenReturn(budgetLimitStatus);

        MonthlyBudgetLimitStatusOutput output = getBudgetLimitStatusUseCase.execute(period, userId, category);

        Assertions.assertEquals(BudgetStatus.EXCEEDED, output.status());
        Assertions.assertEquals(new BigDecimal("500.00"), output.limit());
        Assertions.assertEquals(new BigDecimal("600.00"), output.spent());
        Assertions.assertEquals(new BigDecimal("-100.00"), output.remaining());
        Assertions.assertEquals(new BigDecimal("120.00"), output.percentage());
        Mockito.verify(budgetLimitRepository, Mockito.times(1)).getBudgetLimitStatus(period, userId, category);
    }

    @Test
    @DisplayName("Should return NOT_CONFIGURED when no Budget Limit exists")
    void shouldReturnNotConfiguredWhenNoBudgetLimitExists() {
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        Mockito.when(budgetLimitRepository.getBudgetLimitStatus(period, userId, category))
                .thenReturn(new MonthlyBudgetLimitStatus(null, BigDecimal.ZERO));

        MonthlyBudgetLimitStatusOutput output = getBudgetLimitStatusUseCase.execute(period, userId, category);

        Assertions.assertEquals(BudgetStatus.NOT_CONFIGURED, output.status());
        Assertions.assertNull(output.limit());
        Assertions.assertEquals(BigDecimal.ZERO, output.spent());
        Assertions.assertNull(output.remaining());
        Assertions.assertNull(output.percentage());
        Mockito.verify(budgetLimitRepository, Mockito.times(1)).getBudgetLimitStatus(period, userId, category);
    }

    @Test
    @DisplayName("Should throw Resource Not Found Exception when User is not found")
    void shouldThrowResourceNotFoundExceptionWhenUserIsNotFound() {
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.empty());

        Assertions.assertThrows(ResourceNotFoundException.class, () -> {
            getBudgetLimitStatusUseCase.execute(period, userId, category);
        });

        Mockito.verifyNoInteractions(budgetLimitRepository);
    }

    @Test
    @DisplayName("Should reject request when period is null")
    void shouldRejectRequestWhenPeriodIsNull() {
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            getBudgetLimitStatusUseCase.execute(null, userId, category);
        });

        Mockito.verifyNoInteractions(budgetLimitRepository);
    }

    @Test
    @DisplayName("Should reject request when category is null")
    void shouldRejectRequestWhenCategoryIsNull() {
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            getBudgetLimitStatusUseCase.execute(period, userId, null);
        });

        Mockito.verifyNoInteractions(budgetLimitRepository);
    }

    @ParameterizedTest
    @EnumSource(value = Category.class, names = {"SALARY", "FREELANCE", "INVESTMENT", "OTHER_INCOMES"})
    @DisplayName("Should reject request when category is income")
    void shouldRejectRequestWhenCategoryIsIncome(Category incomeCategory) {
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            getBudgetLimitStatusUseCase.execute(period, userId, incomeCategory);
        });

        Mockito.verifyNoInteractions(budgetLimitRepository);
    }
}
