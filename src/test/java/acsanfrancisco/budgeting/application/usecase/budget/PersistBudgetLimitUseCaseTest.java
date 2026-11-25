package acsanfrancisco.budgeting.application.usecase.budget;

import acsanfrancisco.budgeting.application.exception.ResourceAlreadyExistsException;
import acsanfrancisco.budgeting.application.exception.ResourceNotFoundException;
import acsanfrancisco.budgeting.application.input.CreateBudgetLimitInput;
import acsanfrancisco.budgeting.application.output.BudgetLimitOutput;
import acsanfrancisco.budgeting.domain.budget.BudgetLimit;
import acsanfrancisco.budgeting.domain.budget.BudgetLimitRepository;
import acsanfrancisco.budgeting.domain.transaction.Category;
import acsanfrancisco.budgeting.domain.user.User;
import acsanfrancisco.budgeting.domain.user.UserId;
import acsanfrancisco.budgeting.domain.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mockito;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
public class PersistBudgetLimitUseCaseTest {

    @Mock
    BudgetLimitRepository budgetLimitRepository;

    @Mock
    UserRepository userRepository;

    @InjectMocks
    PersistBudgetLimitUseCase useCase;

    User user;
    UserId userId;
    YearMonth period;
    CreateBudgetLimitInput validInput;

    @BeforeEach
    void setUp() {
        user = new User("Test User", "user@example.com", "password");
        userId = user.getId();
        period = YearMonth.of(2026, 11);
        validInput = new CreateBudgetLimitInput(Category.GROCERIES, period, new BigDecimal("500.00"));
    }
    @Test
    @DisplayName("Should create Budget Limit when input is valid")
    void shouldCreateBudgetLimitWithValidData() {
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        Mockito.when(budgetLimitRepository.save(Mockito.any(BudgetLimit.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BudgetLimitOutput output = useCase.execute(validInput, userId);

        var captor = ArgumentCaptor.forClass(BudgetLimit.class);
        Mockito.verify(budgetLimitRepository, Mockito.times(1)).save(captor.capture());
        BudgetLimit saved = captor.getValue();
        Assertions.assertAll(
                () -> Assertions.assertNotNull(saved.getId()),
                () -> Assertions.assertSame(user, saved.getUser()),
                () -> Assertions.assertEquals(validInput.category(), saved.getCategory()),
                () -> Assertions.assertEquals(period, saved.getPeriod()),
                () -> Assertions.assertEquals(validInput.limitAmount(), saved.getLimitAmount()),
                () -> Assertions.assertEquals(saved.getId().id(), output.id()),
                () -> Assertions.assertEquals("GROCERIES", output.category()),
                () -> Assertions.assertEquals("2026-11", output.period()),
                () -> Assertions.assertEquals(validInput.limitAmount(), output.limitAmount()));
    }

    @Test
    @DisplayName("Should reject null input")
    void shouldRejectNullInput() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> useCase.execute(null, userId));

        Mockito.verifyNoInteractions(userRepository, budgetLimitRepository);
    }

    @Test
    @DisplayName("Should reject nonexistent User")
    void shouldRejectNonexistentUser() {
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.empty());

        Assertions.assertThrows(ResourceNotFoundException.class, () -> useCase.execute(validInput, userId));

        Mockito.verify(budgetLimitRepository, Mockito.never()).save(Mockito.any(BudgetLimit.class));
    }

    @Test
    @DisplayName("Should reject duplicate Budget Limit for User, category and period")
    void shouldRejectDuplicateLimitForUserCategoryAndPeriod() {
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        Mockito.when(budgetLimitRepository.existsByUserIdAndCategoryAndPeriod(userId, Category.GROCERIES, period))
                .thenReturn(true);

        Assertions.assertThrows(ResourceAlreadyExistsException.class, () -> useCase.execute(validInput, userId));

        Mockito.verify(budgetLimitRepository, Mockito.never()).save(Mockito.any(BudgetLimit.class));
    }

    @Test
    @DisplayName("Should reject Income category")
    void shouldRejectIncomeCategory() {
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        CreateBudgetLimitInput input = new CreateBudgetLimitInput(Category.SALARY, period, validInput.limitAmount());

        Assertions.assertThrows(IllegalArgumentException.class, () -> useCase.execute(input, userId));

        Mockito.verifyNoInteractions(budgetLimitRepository);
    }

    @Test
    @DisplayName("Should reject null category")
    void shouldRejectNullCategory() {
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        CreateBudgetLimitInput input = new CreateBudgetLimitInput(null, period, validInput.limitAmount());

        Assertions.assertThrows(IllegalArgumentException.class, () -> useCase.execute(input, userId));

        Mockito.verifyNoInteractions(budgetLimitRepository);
    }

    @Test
    @DisplayName("Should reject null period")
    void shouldRejectNullPeriod() {
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        CreateBudgetLimitInput input = new CreateBudgetLimitInput(Category.GROCERIES, null, validInput.limitAmount());

        Assertions.assertThrows(IllegalArgumentException.class, () -> useCase.execute(input, userId));

        Mockito.verifyNoInteractions(budgetLimitRepository);
    }

    @Test
    @DisplayName("Should reject null amount")
    void shouldRejectNullAmount() {
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        CreateBudgetLimitInput input = new CreateBudgetLimitInput(Category.GROCERIES, period, null);

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            useCase.execute(input, userId);
        });

        Mockito.verifyNoInteractions(budgetLimitRepository);
    }

    @Test
    @DisplayName("Should reject zero amount")
    void shouldRejectZeroAmount() {
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        CreateBudgetLimitInput input = new CreateBudgetLimitInput(Category.GROCERIES, period, BigDecimal.ZERO);

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            useCase.execute(input, userId);
        });

        Mockito.verifyNoInteractions(budgetLimitRepository);
    }

    @Test
    @DisplayName("Should reject negative amount")
    void shouldRejectNegativeAmount() {
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        CreateBudgetLimitInput input = new CreateBudgetLimitInput(Category.GROCERIES, period, new BigDecimal("-0.01"));

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            useCase.execute(input, userId);
        });

        Mockito.verifyNoInteractions(budgetLimitRepository);
    }
}
