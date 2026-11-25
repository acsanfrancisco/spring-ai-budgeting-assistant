package acsanfrancisco.budgeting.application.usecase.transaction;

import acsanfrancisco.budgeting.application.exception.ResourceNotFoundException;
import acsanfrancisco.budgeting.application.input.CreateTransactionInput;
import acsanfrancisco.budgeting.application.output.TransactionOutput;
import acsanfrancisco.budgeting.domain.transaction.Transaction;
import acsanfrancisco.budgeting.domain.transaction.TransactionRepository;
import acsanfrancisco.budgeting.domain.transaction.TransactionRequestKey;
import acsanfrancisco.budgeting.domain.user.User;
import acsanfrancisco.budgeting.domain.user.UserId;
import acsanfrancisco.budgeting.domain.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

@Service
public class PersistTransactionUseCase {

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    public PersistTransactionUseCase(TransactionRepository transactionRepository, UserRepository userRepository) {
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public TransactionOutput execute(CreateTransactionInput input, UserId userId) {
        return execute(input, userId, null);
    }

    /**
     * Saves the transaction under an idempotency key when one is given, so a repeated
     * request finds the record instead of creating another.
     */
    @Transactional
    public TransactionOutput execute(CreateTransactionInput input, UserId userId, TransactionRequestKey key) {
        if(input == null){
            throw new IllegalArgumentException("Transaction data is mandatory.");
        }
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found."));
        Transaction transaction = new Transaction(input.description(), input.amount(), input.category(), user, input.date());
        return TransactionOutput.from(transactionRepository.save(transaction, key));
    }

    @Transactional(readOnly = true)
    public Optional<TransactionOutput> findRegisteredTransaction(UserId userId, TransactionRequestKey key) {
        return transactionRepository.findByIdempotencyKey(userId, key).map(TransactionOutput::from);
    }
}
