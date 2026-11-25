package acsanfrancisco.budgeting.application.usecase.transaction;

import acsanfrancisco.budgeting.application.exception.ResourceNotFoundException;
import acsanfrancisco.budgeting.application.input.CreateTransactionInput;
import acsanfrancisco.budgeting.application.output.TransactionOutput;
import acsanfrancisco.budgeting.domain.transaction.Category;
import acsanfrancisco.budgeting.domain.transaction.Transaction;
import acsanfrancisco.budgeting.domain.transaction.TransactionId;
import acsanfrancisco.budgeting.domain.transaction.TransactionRepository;
import acsanfrancisco.budgeting.domain.user.User;
import acsanfrancisco.budgeting.domain.user.UserId;
import acsanfrancisco.budgeting.domain.user.UserRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
public class PersistTransactionUseCaseTest {

    @Mock
    TransactionRepository transactionRepository;

    @Mock
    UserRepository userRepository;

    @InjectMocks
    PersistTransactionUseCase useCase;

    User user;
    UserId userId;
    LocalDate date;
    Transaction transaction;
    TransactionId transactionId;

    @BeforeEach
    void setUp() {
        user = new User("Test User", "user@example.com", "password");
        userId = user.getId();
        date = LocalDate.of(2026, 11, 9);
        transaction = new Transaction("Groceries", new BigDecimal("50.00"), Category.GROCERIES, user, date);
        transactionId = transaction.getId();
    }

    @Test
    @DisplayName("Should create valid transaction in category GROCERIES")
    void shouldCreateGROCERIES() {
        CreateTransactionInput input = new CreateTransactionInput("Test transaction", new BigDecimal("75.50"), Category.GROCERIES, date);
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        Mockito.when(transactionRepository.save(Mockito.any(Transaction.class), Mockito.isNull())).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionOutput output = useCase.execute(input, userId);

        var captor = ArgumentCaptor.forClass(Transaction.class);
        Mockito.verify(transactionRepository, Mockito.times(1)).save(captor.capture(), Mockito.isNull());
        Transaction saved = captor.getValue();
        Assertions.assertNotNull(saved.getId());
        Assertions.assertSame(user, saved.getUser());
        Assertions.assertEquals(input.description(), saved.getDescription());
        Assertions.assertEquals(input.amount(), saved.getAmount());
        Assertions.assertEquals(input.category(), saved.getCategory());
        Assertions.assertEquals(date, saved.getDate());
        Assertions.assertEquals(input.category().getType(), saved.getType());
        Assertions.assertEquals(TransactionOutput.from(saved), output);
    }

    @Test
    @DisplayName("Should create valid transaction in category SALARY")
    void shouldCreateSALARY() {
        CreateTransactionInput input = new CreateTransactionInput("Test transaction", new BigDecimal("75.50"), Category.SALARY, date);
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        Mockito.when(transactionRepository.save(Mockito.any(Transaction.class), Mockito.isNull())).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionOutput output = useCase.execute(input, userId);

        var captor = ArgumentCaptor.forClass(Transaction.class);
        Mockito.verify(transactionRepository, Mockito.times(1)).save(captor.capture(), Mockito.isNull());
        Transaction saved = captor.getValue();
        Assertions.assertNotNull(saved.getId());
        Assertions.assertSame(user, saved.getUser());
        Assertions.assertEquals(input.description(), saved.getDescription());
        Assertions.assertEquals(input.amount(), saved.getAmount());
        Assertions.assertEquals(input.category(), saved.getCategory());
        Assertions.assertEquals(date, saved.getDate());
        Assertions.assertEquals(input.category().getType(), saved.getType());
        Assertions.assertEquals(TransactionOutput.from(saved), output);
    }

    @Test
    @DisplayName("Should use current date when date is omitted")
    void shouldUseCurrentDateWhenOmitted() {
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        Mockito.when(transactionRepository.save(Mockito.any(Transaction.class), Mockito.isNull())).thenAnswer(invocation -> invocation.getArgument(0));
        LocalDate before = LocalDate.now();

        TransactionOutput output = useCase.execute(new CreateTransactionInput("Groceries", BigDecimal.ONE, Category.GROCERIES, null), userId);

        LocalDate after = LocalDate.now();
        Assertions.assertFalse(output.date().isBefore(before));
        Assertions.assertFalse(output.date().isAfter(after));
    }

    @Test
    @DisplayName("Should reject null input")
    void shouldRejectNullInput() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> useCase.execute(null, userId));

        Mockito.verifyNoInteractions(userRepository, transactionRepository);
    }

    @Test
    @DisplayName("Should reject null amount")
    void shouldRejectNullAmount() {
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        CreateTransactionInput input = new CreateTransactionInput("Groceries", null, Category.GROCERIES, date);

        Assertions.assertThrows(IllegalArgumentException.class, () -> useCase.execute(input, userId));

        Mockito.verifyNoInteractions(transactionRepository);
    }

    @Test
    @DisplayName("Should reject zero amount")
    void shouldRejectZeroAmount() {
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        CreateTransactionInput input = new CreateTransactionInput("Groceries", BigDecimal.ZERO, Category.GROCERIES, date);

        Assertions.assertThrows(IllegalArgumentException.class, () -> useCase.execute(input, userId));

        Mockito.verifyNoInteractions(transactionRepository);
    }

    @Test
    @DisplayName("Should reject negative amount")
    void shouldRejectNegativeAmount() {
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        CreateTransactionInput input = new CreateTransactionInput("Groceries", new BigDecimal("-0.01"), Category.GROCERIES, date);

        Assertions.assertThrows(IllegalArgumentException.class, () -> useCase.execute(input, userId));

        Mockito.verifyNoInteractions(transactionRepository);
    }

    @Test
    @DisplayName("Should reject null description")
    void shouldRejectNullDescription() {
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        CreateTransactionInput input = new CreateTransactionInput(null, BigDecimal.ONE, Category.GROCERIES, date);

        Assertions.assertThrows(IllegalArgumentException.class, () -> useCase.execute(input, userId));

        Mockito.verifyNoInteractions(transactionRepository);
    }

    @Test
    @DisplayName("Should reject blank description")
    void shouldRejectBlankDescription() {
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        CreateTransactionInput input = new CreateTransactionInput("   ", BigDecimal.ONE, Category.GROCERIES, date);

        Assertions.assertThrows(IllegalArgumentException.class, () -> useCase.execute(input, userId));

        Mockito.verifyNoInteractions(transactionRepository);
    }

    @Test
    @DisplayName("Should reject null category")
    void shouldRejectNullCategory() {
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        CreateTransactionInput input = new CreateTransactionInput("Groceries", BigDecimal.ONE, null, date);

        Assertions.assertThrows(IllegalArgumentException.class, () -> useCase.execute(input, userId));

        Mockito.verifyNoInteractions(transactionRepository);
    }

    @Test
    @DisplayName("Should reject nonexistent User")
    void shouldRejectNonexistentUser() {
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.empty());

        Assertions.assertThrows(ResourceNotFoundException.class,
                () -> useCase.execute(new CreateTransactionInput("Groceries", BigDecimal.ONE, Category.GROCERIES, date), userId));

        Mockito.verifyNoInteractions(transactionRepository);
    }

}

