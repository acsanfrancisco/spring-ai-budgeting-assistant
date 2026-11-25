package acsanfrancisco.budgeting.infrastructure.persistence.repository.user;

import acsanfrancisco.budgeting.domain.user.User;
import acsanfrancisco.budgeting.domain.user.UserId;
import acsanfrancisco.budgeting.domain.user.UserRepository;
import acsanfrancisco.budgeting.infrastructure.persistence.entity.UserEntity;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public class JpaUserRepository implements UserRepository {

    private final UserEntityRepository userEntityRepository;

    public JpaUserRepository(UserEntityRepository userEntityRepository) {
        this.userEntityRepository = userEntityRepository;
    }

    @Override
    public User save(User user) {
        UserEntity userEntity = UserEntity.from(user);
        return userEntityRepository.save(userEntity).toDomain();
    }

    @Override
    public boolean existsByEmail(String email) {
        return userEntityRepository.existsByEmail(email);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return userEntityRepository.findByEmail(email).map(UserEntity::toDomain);
    }

    @Override
    public Optional<User> findById(UserId id) {
        return userEntityRepository.findById(id.id()).map(UserEntity::toDomain);
    }

    @Override
    public boolean lockById(UserId id) {
        return userEntityRepository.lockById(id.id()).isPresent();
    }

}
