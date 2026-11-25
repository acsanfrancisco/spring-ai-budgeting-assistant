package acsanfrancisco.budgeting.domain.user;

import java.util.Optional;

public interface UserRepository {

    User save(User user);

    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);

    Optional<User> findById(UserId id);

    boolean lockById(UserId id);
}
