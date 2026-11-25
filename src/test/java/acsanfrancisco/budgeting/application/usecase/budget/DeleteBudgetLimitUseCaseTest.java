package acsanfrancisco.budgeting.application.usecase.budget;

import acsanfrancisco.budgeting.application.exception.ResourceNotFoundException;
import acsanfrancisco.budgeting.domain.budget.BudgetLimitId;
import acsanfrancisco.budgeting.domain.budget.BudgetLimitRepository;
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

@ExtendWith(MockitoExtension.class)
public class DeleteBudgetLimitUseCaseTest {

    @Mock
    BudgetLimitRepository budgetLimitRepository;

    @InjectMocks
    DeleteBudgetLimitUseCase deleteBudgetLimitUseCase;

    BudgetLimitId budgetLimitId;
    UserId userId;

    @BeforeEach
    void setUp() {
        budgetLimitId = new BudgetLimitId();
        userId = new UserId();
    }

    @Test
    @DisplayName("Should delete Budget Limit using its ID and User ID")
    void shouldDeleteBudgetLimit() {
        deleteBudgetLimitUseCase.execute(budgetLimitId, userId);

        Mockito.verify(budgetLimitRepository, Mockito.times(1)).delete(budgetLimitId, userId);
    }

    @Test
    @DisplayName("Should propagate Resource Not Found Exception when Budget Limit is not found")
    void shouldThrowResourceNotFoundExceptionWhenBudgetLimitIsNotFound() {
        ResourceNotFoundException exception = new ResourceNotFoundException("Budget limit not found");
        Mockito.doThrow(exception).when(budgetLimitRepository).delete(budgetLimitId, userId);

        ResourceNotFoundException thrown = Assertions.assertThrows(ResourceNotFoundException.class, () -> {
            deleteBudgetLimitUseCase.execute(budgetLimitId, userId);
        });

        Assertions.assertSame(exception, thrown);
        Mockito.verify(budgetLimitRepository, Mockito.times(1)).delete(budgetLimitId, userId);
    }
}
