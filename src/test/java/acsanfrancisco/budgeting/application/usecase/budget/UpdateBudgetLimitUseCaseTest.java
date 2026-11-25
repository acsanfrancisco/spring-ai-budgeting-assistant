package acsanfrancisco.budgeting.application.usecase.budget;

import acsanfrancisco.budgeting.application.exception.ResourceNotFoundException;
import acsanfrancisco.budgeting.application.output.BudgetLimitOutput;
import acsanfrancisco.budgeting.domain.budget.BudgetLimit;
import acsanfrancisco.budgeting.domain.budget.BudgetLimitId;
import acsanfrancisco.budgeting.domain.budget.BudgetLimitRepository;
import acsanfrancisco.budgeting.domain.transaction.Category;
import acsanfrancisco.budgeting.domain.user.User;
import acsanfrancisco.budgeting.domain.user.UserId;
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

@ExtendWith(MockitoExtension.class)
public class UpdateBudgetLimitUseCaseTest {

    @Mock
    BudgetLimitRepository budgetLimitRepository;

    @InjectMocks
    UpdateBudgetLimitUseCase updateBudgetLimitUseCase;

    User user;
    UserId userId;
    BudgetLimit budgetLimit;
    BudgetLimitId budgetLimitId;
    BigDecimal limitAmount;

    @BeforeEach
    void setUp() {
        user = new User("Test User", "user@example.com", "password");
        userId = user.getId();
        budgetLimit = new BudgetLimit(user, Category.GROCERIES, YearMonth.of(2026, 11), new BigDecimal("500.00"));
        budgetLimitId = budgetLimit.getId();
        limitAmount = new BigDecimal("750.00");
    }

    @Test
    @DisplayName("Should update Budget Limit when amount is positive")
    void shouldUpdateBudgetLimitWhenAmountIsPositive() {
        Mockito.when(budgetLimitRepository.findBudgetLimitByIdAndUserId(budgetLimitId, userId))
                .thenReturn(Optional.of(budgetLimit));
        Mockito.when(budgetLimitRepository.save(budgetLimit)).thenAnswer(invocation -> invocation.getArgument(0));

        BudgetLimitOutput updatedBudgetLimit =
                updateBudgetLimitUseCase.execute(limitAmount, budgetLimitId, userId);

        Assertions.assertEquals(budgetLimitId.id(), updatedBudgetLimit.id());
        Assertions.assertEquals("GROCERIES", updatedBudgetLimit.category());
        Assertions.assertEquals("2026-11", updatedBudgetLimit.period());
        Assertions.assertEquals(limitAmount, updatedBudgetLimit.limitAmount());
        Mockito.verify(budgetLimitRepository, Mockito.times(1))
                .save(budgetLimit);
        Assertions.assertEquals(limitAmount, budgetLimit.getLimitAmount());
        Assertions.assertEquals(budgetLimitId, budgetLimit.getId());
        Assertions.assertSame(user, budgetLimit.getUser());
        Assertions.assertEquals(userId, budgetLimit.getUser().getId());
        Assertions.assertEquals(Category.GROCERIES, budgetLimit.getCategory());
        Assertions.assertEquals(YearMonth.of(2026, 11), budgetLimit.getPeriod());
    }

    @Test
    @DisplayName("Should not update Budget Limit when amount is null")
    void shouldNotUpdateBudgetLimitWhenAmountIsNull() {
        Mockito.when(budgetLimitRepository.findBudgetLimitByIdAndUserId(budgetLimitId, userId))
                .thenReturn(Optional.of(budgetLimit));
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            updateBudgetLimitUseCase.execute(null, budgetLimitId, userId);
        });

        Mockito.verify(budgetLimitRepository, Mockito.never()).save(Mockito.any());
        Assertions.assertEquals(new BigDecimal("500.00"), budgetLimit.getLimitAmount());
    }

    @Test
    @DisplayName("Should not update Budget Limit when amount is zero")
    void shouldNotUpdateBudgetLimitWhenAmountIsZero() {
        Mockito.when(budgetLimitRepository.findBudgetLimitByIdAndUserId(budgetLimitId, userId))
                .thenReturn(Optional.of(budgetLimit));
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            updateBudgetLimitUseCase.execute(BigDecimal.ZERO, budgetLimitId, userId);
        });

        Mockito.verify(budgetLimitRepository, Mockito.never()).save(Mockito.any());
        Assertions.assertEquals(new BigDecimal("500.00"), budgetLimit.getLimitAmount());
    }

    @Test
    @DisplayName("Should not update Budget Limit when amount is negative")
    void shouldNotUpdateBudgetLimitWhenAmountIsNegative() {
        Mockito.when(budgetLimitRepository.findBudgetLimitByIdAndUserId(budgetLimitId, userId))
                .thenReturn(Optional.of(budgetLimit));
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            updateBudgetLimitUseCase.execute(new BigDecimal("-0.01"), budgetLimitId, userId);
        });

        Mockito.verify(budgetLimitRepository, Mockito.never()).save(Mockito.any());
        Assertions.assertEquals(new BigDecimal("500.00"), budgetLimit.getLimitAmount());
    }

    @Test
    @DisplayName("Should propagate Resource Not Found Exception when Budget Limit is not found")
    void shouldThrowResourceNotFoundExceptionWhenBudgetLimitIsNotFound() {
        Mockito.when(budgetLimitRepository.findBudgetLimitByIdAndUserId(budgetLimitId, userId))
                .thenReturn(Optional.empty());

        ResourceNotFoundException thrown = Assertions.assertThrows(ResourceNotFoundException.class, () -> {
            updateBudgetLimitUseCase.execute(limitAmount, budgetLimitId, userId);
        });

        Assertions.assertEquals("Budget limit not found", thrown.getMessage());
        Mockito.verify(budgetLimitRepository, Mockito.times(1))
                .findBudgetLimitByIdAndUserId(budgetLimitId, userId);
        Mockito.verify(budgetLimitRepository, Mockito.never()).save(Mockito.any());
    }
}
