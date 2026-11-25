package acsanfrancisco.budgeting.application.usecase.transaction;

import acsanfrancisco.budgeting.application.exception.ResourceNotFoundException;
import acsanfrancisco.budgeting.domain.transaction.TransactionId;
import acsanfrancisco.budgeting.domain.transaction.TransactionRepository;
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
public class DeleteTransactionUseCaseTest {

    @Mock
    TransactionRepository transactionRepository;

    @InjectMocks
    DeleteTransactionUseCase useCase;

    TransactionId transactionId;
    UserId userId;

    @BeforeEach
    void setUp() {
        transactionId = new TransactionId();
        userId = new UserId();
    }

    @Test
    @DisplayName("Should delete Transaction using its ID and User ID")
    void shouldDeleteTransaction() {
        useCase.execute(transactionId, userId);

        Mockito.verify(transactionRepository, Mockito.times(1)).delete(transactionId, userId);
    }

    @Test
    @DisplayName("Should propagate TransactionNotFound")
    void shouldPropagateTransactionNotFound() {
        ResourceNotFoundException exception = new ResourceNotFoundException("Transaction not found");
        Mockito.doThrow(exception).when(transactionRepository).delete(transactionId, userId);

        ResourceNotFoundException thrown = Assertions.assertThrows(ResourceNotFoundException.class, () -> useCase.execute(transactionId, userId));

        Assertions.assertSame(exception, thrown);
        Mockito.verify(transactionRepository, Mockito.times(1)).delete(transactionId, userId);
    }
}

