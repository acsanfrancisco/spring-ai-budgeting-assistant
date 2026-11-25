package acsanfrancisco.budgeting.infrastructure.persistence.entity;

import acsanfrancisco.budgeting.domain.user.Role;
import acsanfrancisco.budgeting.domain.user.User;
import acsanfrancisco.budgeting.domain.user.UserId;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.*;

@Entity
@Table(name = "tb_user")
@Getter
@Setter
@NoArgsConstructor
public class UserEntity {

    @Id
    private UUID id;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @OneToMany(mappedBy = "user")
    private List<TransactionEntity> transactions = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "tb_user_role",
            joinColumns = @JoinColumn(name = "user_id")
    )
    @Column(name = "role", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private Set<Role> roles = new HashSet<>();

    public UserEntity(UUID id, String fullName, String email, String password) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.password = password;
    }

    public static UserEntity from(User user) {
        UserEntity entity = new UserEntity(
                user.getId().id(),
                user.getFullName(),
                user.getEmail(),
                user.getPassword());
        entity.setRoles(new HashSet<>(user.getRoles()));
        return entity;
    }

    public User toDomain() {
       User user = new User(
               new UserId(this.id),
               this.fullName,
               this.email,
               this.password);
       user.setRoles(new HashSet<>(this.roles));
       return user;
    }

    @PrePersist
    public void prePersistRole(){
        this.roles.add(Role.ROLE_USER);
    }
}
