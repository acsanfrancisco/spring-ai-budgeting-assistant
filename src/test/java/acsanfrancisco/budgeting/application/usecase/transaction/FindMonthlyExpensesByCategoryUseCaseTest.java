package acsanfrancisco.budgeting.application.usecase.transaction;

import acsanfrancisco.budgeting.application.exception.ResourceNotFoundException;
import acsanfrancisco.budgeting.application.output.MonthlyExpensesOutput;
import acsanfrancisco.budgeting.domain.report.MonthlyExpenses;
import acsanfrancisco.budgeting.domain.transaction.Category;
import acsanfrancisco.budgeting.domain.transaction.TransactionRepository;
import acsanfrancisco.budgeting.domain.user.User;
import acsanfrancisco.budgeting.domain.user.UserId;
import acsanfrancisco.budgeting.domain.user.UserRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
public class FindMonthlyExpensesByCategoryUseCaseTest {

    @Mock
    TransactionRepository transactionRepository;

    @Mock
    UserRepository userRepository;

    @InjectMocks
    FindMonthlyExpensesByCategoryUseCase useCase;

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
    @DisplayName("Should return monthly expenses for category and period")
    void shouldReturnMonthlyExpenses() {
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        Mockito.when(transactionRepository.findMonthlyExpensesByCategory(period, userId, category))
                .thenReturn(new MonthlyExpenses(category, new BigDecimal("500.75")));

        MonthlyExpensesOutput output = useCase.execute(period, category, userId);

        Assertions.assertEquals(category, output.category());
        Assertions.assertEquals(new BigDecimal("500.75"), output.total());
        Mockito.verify(transactionRepository, Mockito.times(1)).findMonthlyExpensesByCategory(period, userId, category);
    }

    @Test
    @DisplayName("Should return zero total when category has no expenses")
    void shouldReturnZeroTotalWithoutExpenses() {
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        Mockito.when(transactionRepository.findMonthlyExpensesByCategory(period, userId, category))
                .thenReturn(new MonthlyExpenses(category, new BigDecimal("0.00")));

        MonthlyExpensesOutput output = useCase.execute(period, category, userId);

        Assertions.assertEquals(category, output.category());
        Assertions.assertEquals(new BigDecimal("0.00"), output.total());
        Mockito.verify(transactionRepository, Mockito.times(1)).findMonthlyExpensesByCategory(period, userId, category);
    }

    @Test
    @DisplayName("Should reject nonexistent User")
    void shouldRejectNonexistentUser() {
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.empty());

        Assertions.assertThrows(ResourceNotFoundException.class, () -> useCase.execute(period, category, userId));

        Mockito.verifyNoInteractions(transactionRepository);
    }

    @Test
    @DisplayName("Should reject null period")
    void shouldRejectNullPeriod() {
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        Assertions.assertThrows(IllegalArgumentException.class, () -> useCase.execute(null, category, userId));

        Mockito.verifyNoInteractions(transactionRepository);
    }

    @Test
    @DisplayName("Should reject null category")
    void shouldRejectNullCategory() {
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        Assertions.assertThrows(IllegalArgumentException.class, () -> useCase.execute(period, null, userId));

        Mockito.verifyNoInteractions(transactionRepository);
    }

    @ParameterizedTest
    @EnumSource(value = Category.class, names = {"SALARY", "FREELANCE", "INVESTMENT", "OTHER_INCOMES"})
    @DisplayName("Should reject income category")
    void shouldRejectIncomeCategory(Category incomeCategory) {
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> useCase.execute(period, incomeCategory, userId));

        Mockito.verifyNoInteractions(transactionRepository);
    }

}

