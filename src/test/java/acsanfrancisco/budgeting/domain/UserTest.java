package acsanfrancisco.budgeting.domain;

import acsanfrancisco.budgeting.domain.user.Role;
import acsanfrancisco.budgeting.domain.user.User;
import acsanfrancisco.budgeting.domain.user.UserId;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.Set;
import java.util.stream.Stream;

public class UserTest {

    static Stream<Arguments> invalidReconstructedUsers() {
        UserId id = new UserId();
        return Stream.of(
                Arguments.of(null, "First Test User", "first@example.com", "encoded-password"),
                Arguments.of(id, null, "first@example.com", "encoded-password"),
                Arguments.of(id, "", "first@example.com", "encoded-password"),
                Arguments.of(id, "      ", "first@example.com", "encoded-password"),
                Arguments.of(id, "a".repeat(5), "first@example.com", "encoded-password"),
                Arguments.of(id, "a".repeat(101), "first@example.com", "encoded-password"),
                Arguments.of(id, "First Test User", null, "encoded-password"),
                Arguments.of(id, "First Test User", "", "encoded-password"),
                Arguments.of(id, "First Test User", " ", "encoded-password"),
                Arguments.of(id, "First Test User", "first@example.com", null),
                Arguments.of(id, "First Test User", "first@example.com", ""),
                Arguments.of(id, "First Test User", "first@example.com", " ")
        );
    }

    @ParameterizedTest
    @MethodSource("invalidReconstructedUsers")
    @DisplayName("Should reject invalid fields when reconstructing user with ID")
    void shouldRejectInvalidReconstructedUser(UserId id, String name, String email, String password) {
        Assertions.assertThrows(IllegalArgumentException.class, () -> new User(id, name, email, password));
    }

    @ParameterizedTest
    @ValueSource(ints = {6, 100})
    @DisplayName("Should reconstruct user with supplied identity and valid name boundaries")
    void shouldReconstructValidUser(int length) {
        UserId id = new UserId();
        User user = new User(id, "a".repeat(length), "first@example.com", "encoded-password");

        Assertions.assertEquals(id, user.getId());
        Assertions.assertEquals("a".repeat(length), user.getFullName());
        Assertions.assertEquals("first@example.com", user.getEmail());
        Assertions.assertEquals("encoded-password", user.getPassword());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"      ", "short"})
    @DisplayName("Should preserve name after rejecting an invalid replacement")
    void shouldPreserveNameAfterRejectedReplacement(String name) {
        User user = new User("First Test User", "first@example.com", "encoded-password");

        Assertions.assertThrows(IllegalArgumentException.class, () -> user.setFullName(name));
        Assertions.assertEquals("First Test User", user.getFullName());
    }

    @Test
    @DisplayName("Should preserve name after rejecting a replacement longer than 100 characters")
    void shouldPreserveNameAfterRejectedLongReplacement() {
        User user = new User("First Test User", "first@example.com", "encoded-password");

        Assertions.assertThrows(IllegalArgumentException.class, () -> user.setFullName("a".repeat(101)));
        Assertions.assertEquals("First Test User", user.getFullName());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("Should preserve email after rejecting a missing replacement")
    void shouldPreserveEmailAfterRejectedReplacement(String email) {
        User user = new User("First Test User", "first@example.com", "encoded-password");

        Assertions.assertThrows(IllegalArgumentException.class, () -> user.setEmail(email));
        Assertions.assertEquals("first@example.com", user.getEmail());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("Should preserve password after rejecting a missing replacement")
    void shouldPreservePasswordAfterRejectedReplacement(String password) {
        User user = new User("First Test User", "first@example.com", "encoded-password");

        Assertions.assertThrows(IllegalArgumentException.class, () -> user.setPassword(password));
        Assertions.assertEquals("encoded-password", user.getPassword());
    }

    @ParameterizedTest
    @ValueSource(ints = {6, 100})
    @DisplayName("Should update valid user fields and preserve identity and roles")
    void shouldUpdateValidFields(int nameLength) {
        User user = new User("First Test User", "first@example.com", "encoded-password");
        UserId id = user.getId();

        user.setFullName("a".repeat(nameLength));
        user.setEmail("updated@example.com");
        user.setPassword("updated-encoded-password");

        Assertions.assertEquals("a".repeat(nameLength), user.getFullName());
        Assertions.assertEquals("updated@example.com", user.getEmail());
        Assertions.assertEquals("updated-encoded-password", user.getPassword());
        Assertions.assertEquals(id, user.getId());
        Assertions.assertEquals(Set.of(Role.ROLE_USER), user.getRoles());
    }

    @Test
    @DisplayName("Should create user with generated ID, supplied fields and default user role")
    void shouldCreateUserWithDefaultRole() {
        User user = new User("First Test User", "first@example.com", "encoded-password");

        Assertions.assertNotNull(user.getId());
        Assertions.assertNotNull(user.getId().id());
        Assertions.assertEquals("First Test User", user.getFullName());
        Assertions.assertEquals("first@example.com", user.getEmail());
        Assertions.assertEquals("encoded-password", user.getPassword());
        Assertions.assertEquals(Set.of(Role.ROLE_USER), user.getRoles());
        Assertions.assertTrue(user.getTransactions().isEmpty());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"      "})
    @DisplayName("Should reject null, empty or blank full name")
    void shouldRejectMissingFullName(String name) {
        IllegalArgumentException thrown = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new User(name, "first@example.com", "encoded-password"));

        Assertions.assertEquals("Full name must be informed", thrown.getMessage());
    }

    @ParameterizedTest
    @ValueSource(ints = {5, 101})
    @DisplayName("Should reject full name shorter than 6 or longer than 100 characters")
    void shouldRejectInvalidFullNameLength(int length) {
        IllegalArgumentException thrown = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new User("a".repeat(length), "first@example.com", "encoded-password"));

        Assertions.assertEquals("Full name must be between 6 and 100 characters", thrown.getMessage());
    }

    @ParameterizedTest
    @ValueSource(ints = {6, 100})
    @DisplayName("Should accept full name at minimum and maximum allowed lengths")
    void shouldAcceptFullNameLengthBoundaries(int length) {
        User user = new User("a".repeat(length), "first@example.com", "encoded-password");

        Assertions.assertEquals("a".repeat(length), user.getFullName());
        Assertions.assertEquals(Set.of(Role.ROLE_USER), user.getRoles());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("Should reject null, empty or blank email")
    void shouldRejectMissingEmail(String email) {
        IllegalArgumentException thrown = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new User("First Test User", email, "encoded-password"));

        Assertions.assertEquals("Email must be informed", thrown.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("Should reject null, empty or blank password")
    void shouldRejectMissingPassword(String password) {
        IllegalArgumentException thrown = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new User("First Test User", "first@example.com", password));

        Assertions.assertEquals("Password must be informed", thrown.getMessage());
    }
}
