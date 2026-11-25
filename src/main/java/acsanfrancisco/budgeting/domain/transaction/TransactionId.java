package acsanfrancisco.budgeting.domain.transaction;

import java.util.UUID;

public record TransactionId(UUID id) {
    public TransactionId {
        if(id == null) {
            throw new IllegalArgumentException("Id must not be null");
        }
    }
    public TransactionId() {
        this(UUID.randomUUID());
    }
}
