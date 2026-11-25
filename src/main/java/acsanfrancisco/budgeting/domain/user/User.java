package acsanfrancisco.budgeting.domain.user;

import acsanfrancisco.budgeting.domain.transaction.Transaction;
import lombok.Getter;
import lombok.Setter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter
public class User {

    private final UserId id;

    private String fullName;

    private String email;

    private String password;

    @Setter
    private List<Transaction> transactions = new ArrayList<>();

    @Setter
    private Set<Role> roles = new HashSet<>();

    public User(String fullName, String email, String password) {
        this(new UserId(), fullName, email, password);
        this.roles.add(Role.ROLE_USER);
    }

    public User(UserId userId, String fullName, String email, String password) {
        if(userId == null){
            throw new IllegalArgumentException("Id must be informed");
        }
        validateFullName(fullName);
        validateEmail(email);
        validatePassword(password);

        this.id = userId;
        this.fullName = fullName;
        this.email = email;
        this.password = password;
    }

    public void setFullName(String fullName) {
        validateFullName(fullName);
        this.fullName = fullName;
    }

    public void setEmail(String email) {
        validateEmail(email);
        this.email = email;
    }

    public void setPassword(String password) {
        validatePassword(password);
        this.password = password;
    }

    private static void validateFullName(String fullName) {
        if(fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Full name must be informed");
        }

        if(fullName.length() < 6 || fullName.length() > 100){
            throw new IllegalArgumentException("Full name must be between 6 and 100 characters");
        }

    }

    private static void validateEmail(String email) {
        if(email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email must be informed");
        }
    }

    private static void validatePassword(String password) {
        if(password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password must be informed");
        }
    }
}
