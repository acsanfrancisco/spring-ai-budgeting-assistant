package acsanfrancisco.budgeting.application.usecase.user;

import acsanfrancisco.budgeting.application.exception.PasswordMismatchException;
import acsanfrancisco.budgeting.application.exception.ResourceNotFoundException;
import acsanfrancisco.budgeting.application.input.ChangePasswordInput;
import acsanfrancisco.budgeting.domain.user.User;
import acsanfrancisco.budgeting.domain.user.UserId;
import acsanfrancisco.budgeting.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChangeUserPasswordUseCase {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void execute(ChangePasswordInput input, UserId userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if(!input.newPassword().equals(input.confirmNewPassword())) {
            throw new PasswordMismatchException("The new passwords do not match");
        }
        if(!passwordEncoder.matches(input.currentPassword(), user.getPassword())) {
            throw new PasswordMismatchException("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(input.newPassword()));
        userRepository.save(user);
    }
}
