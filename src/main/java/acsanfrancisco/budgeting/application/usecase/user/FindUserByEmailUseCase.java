package acsanfrancisco.budgeting.application.usecase.user;

import acsanfrancisco.budgeting.application.exception.ResourceNotFoundException;
import acsanfrancisco.budgeting.application.output.UserOutput;
import acsanfrancisco.budgeting.domain.user.User;
import acsanfrancisco.budgeting.domain.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FindUserByEmailUseCase {

    private final UserRepository userRepository;

    public FindUserByEmailUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public UserOutput execute(String email){
        User user = userRepository.findByEmail(email)
                .orElseThrow(()-> new ResourceNotFoundException("User not found"));
        return UserOutput.from(user);
    }
}
