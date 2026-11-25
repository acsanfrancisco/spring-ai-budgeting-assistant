package acsanfrancisco.budgeting.application.usecase.transaction;

import acsanfrancisco.budgeting.application.exception.ResourceNotFoundException;
import acsanfrancisco.budgeting.application.output.MonthlySummaryOutput;
import acsanfrancisco.budgeting.domain.report.MonthlySummary;
import acsanfrancisco.budgeting.domain.transaction.TransactionRepository;
import acsanfrancisco.budgeting.domain.transaction.TransactionType;
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

@ExtendWith(MockitoExtension.class)
public class GetMonthlySummaryUseCaseTest {

    @Mock
    TransactionRepository transactionRepository;

    @Mock
    UserRepository userRepository;

    @InjectMocks
    GetMonthlySummaryUseCase useCase;

    User user;
    UserId userId;
    YearMonth period;

    @BeforeEach
    void setUp() {
        user = new User("Test User", "user@example.com", "password");
        userId = user.getId();
        period = YearMonth.of(2026, 11);
    }

    @Test
    @DisplayName("Should return positive net when income exceeds expenses")
    void shouldReturnPositiveNet() {
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        MonthlySummary summary = new MonthlySummary(new BigDecimal("500.00"), new BigDecimal("1500.00"));
        Mockito.when(transactionRepository.getMonthlySummary(period, userId, TransactionType.INCOME, TransactionType.EXPENSE))
                .thenReturn(summary);

        MonthlySummaryOutput output = useCase.execute(period, userId);

        Assertions.assertEquals(new BigDecimal("500.00"), output.expenses());
        Assertions.assertEquals(new BigDecimal("1500.00"), output.income());
        Assertions.assertEquals(new BigDecimal("1000.00"), output.net());
        Mockito.verify(transactionRepository, Mockito.times(1))
                .getMonthlySummary(period, userId, TransactionType.INCOME, TransactionType.EXPENSE);
    }

    @Test
    @DisplayName("Should return negative net when expenses exceed income")
    void shouldReturnNegativeNet() {
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        MonthlySummary summary = new MonthlySummary(new BigDecimal("1500.00"), new BigDecimal("500.00"));
        Mockito.when(transactionRepository.getMonthlySummary(period, userId, TransactionType.INCOME, TransactionType.EXPENSE))
                .thenReturn(summary);

        MonthlySummaryOutput output = useCase.execute(period, userId);

        Assertions.assertEquals(new BigDecimal("1500.00"), output.expenses());
        Assertions.assertEquals(new BigDecimal("500.00"), output.income());
        Assertions.assertEquals(new BigDecimal("-1000.00"), output.net());
        Mockito.verify(transactionRepository, Mockito.times(1))
                .getMonthlySummary(period, userId, TransactionType.INCOME, TransactionType.EXPENSE);
    }

    @Test
    @DisplayName("Should return zero net when income equals expenses")
    void shouldReturnZeroNet() {
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        MonthlySummary summary = new MonthlySummary(new BigDecimal("500.00"), new BigDecimal("500.00"));
        Mockito.when(transactionRepository.getMonthlySummary(period, userId, TransactionType.INCOME, TransactionType.EXPENSE))
                .thenReturn(summary);

        MonthlySummaryOutput output = useCase.execute(period, userId);

        Assertions.assertEquals(new BigDecimal("500.00"), output.expenses());
        Assertions.assertEquals(new BigDecimal("500.00"), output.income());
        Assertions.assertEquals(new BigDecimal("0.00"), output.net());
        Mockito.verify(transactionRepository, Mockito.times(1))
                .getMonthlySummary(period, userId, TransactionType.INCOME, TransactionType.EXPENSE);
    }

    @Test
    @DisplayName("Should return zero totals when there are no transactions")
    void shouldReturnNoTransactions() {
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        MonthlySummary summary = new MonthlySummary(new BigDecimal("0.00"), new BigDecimal("0.00"));
        Mockito.when(transactionRepository.getMonthlySummary(period, userId, TransactionType.INCOME, TransactionType.EXPENSE))
                .thenReturn(summary);

        MonthlySummaryOutput output = useCase.execute(period, userId);

        Assertions.assertEquals(new BigDecimal("0.00"), output.expenses());
        Assertions.assertEquals(new BigDecimal("0.00"), output.income());
        Assertions.assertEquals(new BigDecimal("0.00"), output.net());
        Mockito.verify(transactionRepository, Mockito.times(1))
                .getMonthlySummary(period, userId, TransactionType.INCOME, TransactionType.EXPENSE);
    }

    @Test
    @DisplayName("Should return summary with only income")
    void shouldReturnOnlyIncome() {
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        MonthlySummary summary = new MonthlySummary(new BigDecimal("0.00"), new BigDecimal("1500.00"));
        Mockito.when(transactionRepository.getMonthlySummary(period, userId, TransactionType.INCOME, TransactionType.EXPENSE))
                .thenReturn(summary);

        MonthlySummaryOutput output = useCase.execute(period, userId);

        Assertions.assertEquals(new BigDecimal("0.00"), output.expenses());
        Assertions.assertEquals(new BigDecimal("1500.00"), output.income());
        Assertions.assertEquals(new BigDecimal("1500.00"), output.net());
        Mockito.verify(transactionRepository, Mockito.times(1))
                .getMonthlySummary(period, userId, TransactionType.INCOME, TransactionType.EXPENSE);
    }

    @Test
    @DisplayName("Should return summary with only expenses")
    void shouldReturnOnlyExpenses() {
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        MonthlySummary summary = new MonthlySummary(new BigDecimal("500.00"), new BigDecimal("0.00"));
        Mockito.when(transactionRepository.getMonthlySummary(period, userId, TransactionType.INCOME, TransactionType.EXPENSE))
                .thenReturn(summary);

        MonthlySummaryOutput output = useCase.execute(period, userId);

        Assertions.assertEquals(new BigDecimal("500.00"), output.expenses());
        Assertions.assertEquals(new BigDecimal("0.00"), output.income());
        Assertions.assertEquals(new BigDecimal("-500.00"), output.net());
        Mockito.verify(transactionRepository, Mockito.times(1))
                .getMonthlySummary(period, userId, TransactionType.INCOME, TransactionType.EXPENSE);
    }

    @Test
    @DisplayName("Should reject nonexistent User")
    void shouldRejectNonexistentUser() {
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.empty());

        Assertions.assertThrows(ResourceNotFoundException.class, () -> useCase.execute(period, userId));

        Mockito.verifyNoInteractions(transactionRepository);
    }

    @Test
    @DisplayName("Should reject null period")
    void shouldRejectNullPeriod() {
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        Assertions.assertThrows(IllegalArgumentException.class, () -> useCase.execute(null, userId));

        Mockito.verifyNoInteractions(transactionRepository);
    }

}

