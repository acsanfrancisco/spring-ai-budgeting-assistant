package acsanfrancisco.budgeting.application.usecase.transaction;

import acsanfrancisco.budgeting.application.exception.ResourceNotFoundException;
import acsanfrancisco.budgeting.application.input.UpdateTransactionInput;
import acsanfrancisco.budgeting.application.output.TransactionOutput;
import acsanfrancisco.budgeting.domain.transaction.Transaction;
import acsanfrancisco.budgeting.domain.transaction.TransactionId;
import acsanfrancisco.budgeting.domain.transaction.TransactionRepository;
import acsanfrancisco.budgeting.domain.user.UserId;
import acsanfrancisco.budgeting.domain.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateTransactionUseCase {

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    public UpdateTransactionUseCase(TransactionRepository transactionRepository,  UserRepository userRepository) {
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public TransactionOutput execute(UpdateTransactionInput input, TransactionId transactionId, UserId userId) {
        if(input == null){
            throw new IllegalArgumentException("Transaction data is mandatory");
        }
        if(input.category() == null && input.date() == null && input.amount() == null && input.description() == null){
            throw new IllegalArgumentException("Must inform at least one field");
        }

        userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Transaction transaction = transactionRepository.findTransactionByIdAndUserId(transactionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));

        if(input.category() != null){
            transaction.setCategory(input.category());
        }
        if(input.amount() != null){
            transaction.setAmount(input.amount());
        }
        if(input.description() != null){
            transaction.setDescription(input.description());
        }
        if(input.date() != null){
            transaction.setDate(input.date());
        }

        Transaction updatedTransaction = transactionRepository.update(
                transaction, transactionId, userId);
        return TransactionOutput.from(updatedTransaction);
    }
}
