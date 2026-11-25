package acsanfrancisco.budgeting.domain.transaction;

import java.util.UUID;

public record TransactionRequestKey(UUID value, String messageHash) {}
