package acsanfrancisco.budgeting.application.usecase.transaction;

import acsanfrancisco.budgeting.domain.transaction.TransactionId;
import acsanfrancisco.budgeting.domain.transaction.TransactionRepository;
import acsanfrancisco.budgeting.domain.user.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeleteTransactionUseCase {

    private final TransactionRepository transactionRepository;

    public DeleteTransactionUseCase(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public void execute(TransactionId transactionId, UserId userId) {
        transactionRepository.delete(transactionId, userId);
    }
}
