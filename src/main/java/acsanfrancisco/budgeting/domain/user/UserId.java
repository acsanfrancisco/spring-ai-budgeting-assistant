package acsanfrancisco.budgeting.domain.user;

import java.util.UUID;

public record UserId(UUID id) {
    public UserId {
        if(id == null){
            throw new IllegalArgumentException("Id must not be null");
        }
    }
    public UserId() {
        this(UUID.randomUUID());
    }
}
