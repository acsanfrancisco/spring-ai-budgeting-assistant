package acsanfrancisco.budgeting.application.usecase.transaction;

import acsanfrancisco.budgeting.application.output.TransactionOutput;
import acsanfrancisco.budgeting.domain.pagination.PageQuery;
import acsanfrancisco.budgeting.domain.pagination.PageResult;
import acsanfrancisco.budgeting.domain.transaction.Category;
import acsanfrancisco.budgeting.domain.transaction.Transaction;
import acsanfrancisco.budgeting.domain.transaction.TransactionRepository;
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
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@ExtendWith(MockitoExtension.class)
public class FindTransactionsByCategoryAndPeriodUseCaseTest {

    @Mock
    TransactionRepository transactionRepository;

    @InjectMocks
    FindTransactionsByCategoryAndPeriodUseCase useCase;

    UserId userId;
    Category category;
    YearMonth period;
    Transaction transaction;

    @BeforeEach
    void setUp() {
        User user = new User("Test User", "user@example.com", "password");
        userId = user.getId();
        category = Category.GROCERIES;
        period = YearMonth.of(2026, 11);
        transaction = new Transaction("Weekly groceries", new BigDecimal("75.50"), category, user, LocalDate.of(2026, 11, 9));
    }

    @Test
    @DisplayName("Should map transactions in order and preserve pagination")
    void shouldReturnTransactionsAndPreservePagination() {
        PageQuery pageQuery = new PageQuery(2, 5);
        Transaction second = new Transaction("More groceries", new BigDecimal("20.00"), category, transaction.getUser(), LocalDate.of(2026, 11, 10));
        Mockito.when(transactionRepository.findByCategoryAndPeriod(category, userId, period.atDay(1), period.atEndOfMonth(), pageQuery))
                .thenReturn(new PageResult<>(List.of(transaction, second), 2, 5, 12));

        PageResult<TransactionOutput> output = useCase.execute(category, userId, period.atDay(1), period.atEndOfMonth(), pageQuery);

        Assertions.assertEquals(List.of(
                new TransactionOutput(transaction.getId().id(), "Weekly groceries", new BigDecimal("75.50"), "GROCERIES", LocalDate.of(2026, 11, 9), "EXPENSE"),
                new TransactionOutput(second.getId().id(), "More groceries", new BigDecimal("20.00"), "GROCERIES", LocalDate.of(2026, 11, 10), "EXPENSE")), output.content());
        Assertions.assertEquals(2, output.page());
        Assertions.assertEquals(5, output.size());
        Assertions.assertEquals(12L, output.totalElements());
        Mockito.verify(transactionRepository, Mockito.times(1)).findByCategoryAndPeriod(category, userId, period.atDay(1), period.atEndOfMonth(), pageQuery);
    }

    @Test
    @DisplayName("Should return empty page when no transactions match")
    void shouldReturnNoTransactions() {
        PageQuery pageQuery = new PageQuery(0, 5);
        Mockito.when(transactionRepository.findByCategoryAndPeriod(category, userId, period.atDay(1), period.atEndOfMonth(), pageQuery))
                .thenReturn(new PageResult<>(List.of(), 0, 5, 0));

        PageResult<TransactionOutput> output = useCase.execute(category, userId, period.atDay(1), period.atEndOfMonth(), pageQuery);

        Assertions.assertTrue(output.content().isEmpty());
        Assertions.assertEquals(0, output.page());
        Assertions.assertEquals(5, output.size());
        Assertions.assertEquals(0L, output.totalElements());
        Mockito.verify(transactionRepository, Mockito.times(1)).findByCategoryAndPeriod(category, userId, period.atDay(1), period.atEndOfMonth(), pageQuery);
    }

    @Test
    @DisplayName("Should preserve total elements when requested page is empty")
    void shouldReturnPageBeyondResults() {
        PageQuery pageQuery = new PageQuery(3, 5);
        Mockito.when(transactionRepository.findByCategoryAndPeriod(category, userId, period.atDay(1), period.atEndOfMonth(), pageQuery))
                .thenReturn(new PageResult<>(List.of(), 3, 5, 12));

        PageResult<TransactionOutput> output = useCase.execute(category, userId, period.atDay(1), period.atEndOfMonth(), pageQuery);

        Assertions.assertTrue(output.content().isEmpty());
        Assertions.assertEquals(3, output.page());
        Assertions.assertEquals(5, output.size());
        Assertions.assertEquals(12L, output.totalElements());
        Mockito.verify(transactionRepository, Mockito.times(1)).findByCategoryAndPeriod(category, userId, period.atDay(1), period.atEndOfMonth(), pageQuery);
    }

    @Test
    @DisplayName("Should forward null period to search category history")
    void shouldFindTransactionsWithoutPeriod() {
        PageQuery pageQuery = new PageQuery(0, 20);
        Mockito.when(transactionRepository.findByCategoryAndPeriod(category, userId, null, null, pageQuery))
                .thenReturn(new PageResult<>(List.of(transaction), 0, 20, 1));

        PageResult<TransactionOutput> output = useCase.execute(category, userId, null, null, pageQuery);

        Assertions.assertEquals(1, output.content().size());
        Assertions.assertEquals(transaction.getId().id(), output.content().getFirst().id());
        Mockito.verify(transactionRepository, Mockito.times(1)).findByCategoryAndPeriod(category, userId, null, null, pageQuery);
    }

}
