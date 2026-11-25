package acsanfrancisco.budgeting.application.usecase.user;

import acsanfrancisco.budgeting.application.exception.ResourceAlreadyExistsException;
import acsanfrancisco.budgeting.application.input.CreateUserInput;
import acsanfrancisco.budgeting.application.output.UserOutput;
import acsanfrancisco.budgeting.domain.user.User;
import acsanfrancisco.budgeting.domain.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersistUserUseCase {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public PersistUserUseCase(UserRepository userRepository,  PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserOutput execute (CreateUserInput input) {
        if(input == null){
            throw new IllegalArgumentException("User data is mandatory");
        }
        if(input.password() == null || input.password().length() < 6 || input.password().length() > 30) {
            throw new IllegalArgumentException("Invalid password");
        }
        if(userRepository.existsByEmail(input.email())){
            throw new ResourceAlreadyExistsException("Email already exists");
        }
        User user = userRepository.save(new User(input.fullName(), input.email(), passwordEncoder.encode(input.password())));
        return UserOutput.from(user);
    }
}
