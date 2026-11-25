package acsanfrancisco.budgeting.application.usecase.transaction;

import acsanfrancisco.budgeting.application.exception.ResourceNotFoundException;
import acsanfrancisco.budgeting.application.input.UpdateTransactionInput;
import acsanfrancisco.budgeting.application.output.TransactionOutput;
import acsanfrancisco.budgeting.domain.transaction.Category;
import acsanfrancisco.budgeting.domain.transaction.Transaction;
import acsanfrancisco.budgeting.domain.transaction.TransactionId;
import acsanfrancisco.budgeting.domain.transaction.TransactionType;
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
public class UpdateTransactionUseCaseTest {

    @Mock
    TransactionRepository transactionRepository;

    @Mock
    UserRepository userRepository;

    @InjectMocks
    UpdateTransactionUseCase useCase;

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
    @DisplayName("Should update all editable fields and preserve identity, owner and type")
    void shouldUpdateAllFields() {
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        Mockito.when(transactionRepository.findTransactionByIdAndUserId(transactionId, userId)).thenReturn(Optional.of(transaction));
        UpdateTransactionInput input = new UpdateTransactionInput("Medicine", new BigDecimal("80.00"), Category.PHARMA, date.plusDays(1));
        Mockito.when(transactionRepository.update(Mockito.any(Transaction.class), Mockito.eq(transactionId), Mockito.eq(userId)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TransactionOutput output = useCase.execute(input, transactionId, userId);

        var captor = ArgumentCaptor.forClass(Transaction.class);
        Mockito.verify(transactionRepository, Mockito.times(1)).update(captor.capture(), Mockito.eq(transactionId), Mockito.eq(userId));
        Transaction saved = captor.getValue();
        Assertions.assertSame(transaction, saved);
        Assertions.assertEquals(transactionId, saved.getId());
        Assertions.assertSame(user, saved.getUser());
        Assertions.assertEquals(userId, saved.getUser().getId());
        Assertions.assertEquals("Medicine", saved.getDescription());
        Assertions.assertEquals(new BigDecimal("80.00"), saved.getAmount());
        Assertions.assertEquals(Category.PHARMA, saved.getCategory());
        Assertions.assertEquals(date.plusDays(1), saved.getDate());
        Assertions.assertEquals(TransactionType.EXPENSE, saved.getType());
        Assertions.assertEquals(new TransactionOutput(transactionId.id(), "Medicine", new BigDecimal("80.00"), Category.PHARMA.name(), date.plusDays(1), "EXPENSE"), output);
    }

    @Test
    @DisplayName("Should update only description and preserve omitted fields")
    void shouldUpdateDescriptionOnly() {
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        Mockito.when(transactionRepository.findTransactionByIdAndUserId(transactionId, userId)).thenReturn(Optional.of(transaction));
        UpdateTransactionInput input = new UpdateTransactionInput("Weekly groceries", null, null, null);
        Mockito.when(transactionRepository.update(Mockito.any(Transaction.class), Mockito.eq(transactionId), Mockito.eq(userId)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TransactionOutput output = useCase.execute(input, transactionId, userId);

        var captor = ArgumentCaptor.forClass(Transaction.class);
        Mockito.verify(transactionRepository, Mockito.times(1)).update(captor.capture(), Mockito.eq(transactionId), Mockito.eq(userId));
        Transaction saved = captor.getValue();
        Assertions.assertSame(transaction, saved);
        Assertions.assertEquals(transactionId, saved.getId());
        Assertions.assertSame(user, saved.getUser());
        Assertions.assertEquals(userId, saved.getUser().getId());
        Assertions.assertEquals("Weekly groceries", saved.getDescription());
        Assertions.assertEquals(new BigDecimal("50.00"), saved.getAmount());
        Assertions.assertEquals(Category.GROCERIES, saved.getCategory());
        Assertions.assertEquals(date, saved.getDate());
        Assertions.assertEquals(TransactionType.EXPENSE, saved.getType());
        Assertions.assertEquals(new TransactionOutput(transactionId.id(), "Weekly groceries", new BigDecimal("50.00"), Category.GROCERIES.name(), date, "EXPENSE"), output);
    }

    @Test
    @DisplayName("Should update only amount and preserve omitted fields")
    void shouldUpdateAmountOnly() {
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        Mockito.when(transactionRepository.findTransactionByIdAndUserId(transactionId, userId)).thenReturn(Optional.of(transaction));
        UpdateTransactionInput input = new UpdateTransactionInput(null, new BigDecimal("80.00"), null, null);
        Mockito.when(transactionRepository.update(Mockito.any(Transaction.class), Mockito.eq(transactionId), Mockito.eq(userId)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TransactionOutput output = useCase.execute(input, transactionId, userId);

        var captor = ArgumentCaptor.forClass(Transaction.class);
        Mockito.verify(transactionRepository, Mockito.times(1)).update(captor.capture(), Mockito.eq(transactionId), Mockito.eq(userId));
        Transaction saved = captor.getValue();
        Assertions.assertSame(transaction, saved);
        Assertions.assertEquals(transactionId, saved.getId());
        Assertions.assertSame(user, saved.getUser());
        Assertions.assertEquals(userId, saved.getUser().getId());
        Assertions.assertEquals("Groceries", saved.getDescription());
        Assertions.assertEquals(new BigDecimal("80.00"), saved.getAmount());
        Assertions.assertEquals(Category.GROCERIES, saved.getCategory());
        Assertions.assertEquals(date, saved.getDate());
        Assertions.assertEquals(TransactionType.EXPENSE, saved.getType());
        Assertions.assertEquals(new TransactionOutput(transactionId.id(), "Groceries", new BigDecimal("80.00"), Category.GROCERIES.name(), date, "EXPENSE"), output);
    }

    @Test
    @DisplayName("Should update only category and preserve omitted fields")
    void shouldUpdateCategoryOnly() {
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        Mockito.when(transactionRepository.findTransactionByIdAndUserId(transactionId, userId)).thenReturn(Optional.of(transaction));
        UpdateTransactionInput input = new UpdateTransactionInput(null, null, Category.PHARMA, null);
        Mockito.when(transactionRepository.update(Mockito.any(Transaction.class), Mockito.eq(transactionId), Mockito.eq(userId)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TransactionOutput output = useCase.execute(input, transactionId, userId);

        var captor = ArgumentCaptor.forClass(Transaction.class);
        Mockito.verify(transactionRepository, Mockito.times(1)).update(captor.capture(), Mockito.eq(transactionId), Mockito.eq(userId));
        Transaction saved = captor.getValue();
        Assertions.assertSame(transaction, saved);
        Assertions.assertEquals(transactionId, saved.getId());
        Assertions.assertSame(user, saved.getUser());
        Assertions.assertEquals(userId, saved.getUser().getId());
        Assertions.assertEquals("Groceries", saved.getDescription());
        Assertions.assertEquals(new BigDecimal("50.00"), saved.getAmount());
        Assertions.assertEquals(Category.PHARMA, saved.getCategory());
        Assertions.assertEquals(date, saved.getDate());
        Assertions.assertEquals(TransactionType.EXPENSE, saved.getType());
        Assertions.assertEquals(new TransactionOutput(transactionId.id(), "Groceries", new BigDecimal("50.00"), Category.PHARMA.name(), date, "EXPENSE"), output);
    }

    @Test
    @DisplayName("Should update only date and preserve omitted fields")
    void shouldUpdateDateOnly() {
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        Mockito.when(transactionRepository.findTransactionByIdAndUserId(transactionId, userId)).thenReturn(Optional.of(transaction));
        UpdateTransactionInput input = new UpdateTransactionInput(null, null, null, date.plusDays(1));
        Mockito.when(transactionRepository.update(Mockito.any(Transaction.class), Mockito.eq(transactionId), Mockito.eq(userId)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TransactionOutput output = useCase.execute(input, transactionId, userId);

        var captor = ArgumentCaptor.forClass(Transaction.class);
        Mockito.verify(transactionRepository, Mockito.times(1)).update(captor.capture(), Mockito.eq(transactionId), Mockito.eq(userId));
        Transaction saved = captor.getValue();
        Assertions.assertSame(transaction, saved);
        Assertions.assertEquals(transactionId, saved.getId());
        Assertions.assertSame(user, saved.getUser());
        Assertions.assertEquals(userId, saved.getUser().getId());
        Assertions.assertEquals("Groceries", saved.getDescription());
        Assertions.assertEquals(new BigDecimal("50.00"), saved.getAmount());
        Assertions.assertEquals(Category.GROCERIES, saved.getCategory());
        Assertions.assertEquals(date.plusDays(1), saved.getDate());
        Assertions.assertEquals(TransactionType.EXPENSE, saved.getType());
        Assertions.assertEquals(new TransactionOutput(transactionId.id(), "Groceries", new BigDecimal("50.00"), Category.GROCERIES.name(), date.plusDays(1), "EXPENSE"), output);
    }

    @Test
    @DisplayName("Should reject update when all fields are null")
    void shouldUpdateNoFields() {
        UpdateTransactionInput input = new UpdateTransactionInput(null, null, null, null);

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> useCase.execute(input, transactionId, userId));

        Mockito.verifyNoInteractions(userRepository, transactionRepository);
    }

    @Test
    @DisplayName("Should reject null input")
    void shouldRejectNullInput() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> useCase.execute(null, transactionId, userId));

        Mockito.verifyNoInteractions(userRepository, transactionRepository);
    }

    @Test
    @DisplayName("Should reject nonexistent User")
    void shouldRejectNonexistentUser() {
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.empty());
        UpdateTransactionInput input = new UpdateTransactionInput("Weekly groceries", null, null, null);

        Assertions.assertThrows(ResourceNotFoundException.class,
                () -> useCase.execute(input, transactionId, userId));

        Mockito.verifyNoInteractions(transactionRepository);
    }

    @Test
    @DisplayName("Should reject Transaction not found for the given User")
    void shouldRejectTransactionNotFoundForUser() {
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        Mockito.when(transactionRepository.findTransactionByIdAndUserId(transactionId, userId)).thenReturn(Optional.empty());
        UpdateTransactionInput input = new UpdateTransactionInput("Weekly groceries", null, null, null);

        Assertions.assertThrows(ResourceNotFoundException.class,
                () -> useCase.execute(input, transactionId, userId));

        Mockito.verify(transactionRepository, Mockito.never()).update(Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    @DisplayName("Should reject zero amount")
    void shouldRejectZeroAmount() {
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        Mockito.when(transactionRepository.findTransactionByIdAndUserId(transactionId, userId)).thenReturn(Optional.of(transaction));
        UpdateTransactionInput input = new UpdateTransactionInput(null, BigDecimal.ZERO, null, null);

        Assertions.assertThrows(IllegalArgumentException.class, () -> useCase.execute(input, transactionId, userId));

        Mockito.verify(transactionRepository, Mockito.never()).update(Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    @DisplayName("Should reject negative amount")
    void shouldRejectNegativeAmount() {
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        Mockito.when(transactionRepository.findTransactionByIdAndUserId(transactionId, userId)).thenReturn(Optional.of(transaction));
        UpdateTransactionInput input = new UpdateTransactionInput(null, new BigDecimal("-0.01"), null, null);

        Assertions.assertThrows(IllegalArgumentException.class, () -> useCase.execute(input, transactionId, userId));

        Mockito.verify(transactionRepository, Mockito.never()).update(Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    @DisplayName("Should reject blank description")
    void shouldRejectBlankDescription() {
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        Mockito.when(transactionRepository.findTransactionByIdAndUserId(transactionId, userId)).thenReturn(Optional.of(transaction));
        UpdateTransactionInput input = new UpdateTransactionInput("   ", null, null, null);

        Assertions.assertThrows(IllegalArgumentException.class, () -> useCase.execute(input, transactionId, userId));

        Mockito.verify(transactionRepository, Mockito.never()).update(Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    @DisplayName("Should reject changing expense category to income")
    void shouldRejectDifferentCategoryType() {
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        Mockito.when(transactionRepository.findTransactionByIdAndUserId(transactionId, userId)).thenReturn(Optional.of(transaction));
        UpdateTransactionInput input = new UpdateTransactionInput(null, null, Category.SALARY, null);

        Assertions.assertThrows(IllegalArgumentException.class, () -> useCase.execute(input, transactionId, userId));

        Mockito.verify(transactionRepository, Mockito.never()).update(Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    @DisplayName("Should update income category and preserve omitted fields")
    void shouldUpdateIncomeCategory() {
        transaction = new Transaction("Salary", new BigDecimal("3000.00"), Category.SALARY, user, date);
        transactionId = transaction.getId();
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        Mockito.when(transactionRepository.findTransactionByIdAndUserId(transactionId, userId)).thenReturn(Optional.of(transaction));
        UpdateTransactionInput input = new UpdateTransactionInput(null, null, Category.FREELANCE, null);
        Mockito.when(transactionRepository.update(Mockito.any(Transaction.class), Mockito.eq(transactionId), Mockito.eq(userId)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TransactionOutput output = useCase.execute(input, transactionId, userId);

        var captor = ArgumentCaptor.forClass(Transaction.class);
        Mockito.verify(transactionRepository, Mockito.times(1)).update(captor.capture(), Mockito.eq(transactionId), Mockito.eq(userId));
        Transaction saved = captor.getValue();
        Assertions.assertSame(transaction, saved);
        Assertions.assertEquals(transactionId, saved.getId());
        Assertions.assertSame(user, saved.getUser());
        Assertions.assertEquals(userId, saved.getUser().getId());
        Assertions.assertEquals("Salary", saved.getDescription());
        Assertions.assertEquals(new BigDecimal("3000.00"), saved.getAmount());
        Assertions.assertEquals(Category.FREELANCE, saved.getCategory());
        Assertions.assertEquals(date, saved.getDate());
        Assertions.assertEquals(TransactionType.INCOME, saved.getType());
        Assertions.assertEquals(new TransactionOutput(transactionId.id(), "Salary", new BigDecimal("3000.00"),
                "FREELANCE", date, "INCOME"), output);
    }

    @Test
    @DisplayName("Should reject changing income category to expense")
    void shouldRejectChangingIncomeToExpense() {
        transaction = new Transaction("Salary", new BigDecimal("3000.00"), Category.SALARY, user, date);
        transactionId = transaction.getId();
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        Mockito.when(transactionRepository.findTransactionByIdAndUserId(transactionId, userId)).thenReturn(Optional.of(transaction));
        UpdateTransactionInput input = new UpdateTransactionInput(null, null, Category.GROCERIES, null);

        Assertions.assertThrows(IllegalArgumentException.class, () -> useCase.execute(input, transactionId, userId));

        Mockito.verify(transactionRepository, Mockito.never()).update(Mockito.any(), Mockito.any(), Mockito.any());
    }
}

