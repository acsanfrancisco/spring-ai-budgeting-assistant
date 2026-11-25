package acsanfrancisco.budgeting.infrastructure.security;

import acsanfrancisco.budgeting.domain.user.Role;
import acsanfrancisco.budgeting.domain.user.UserId;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class CustomUserDetails implements UserDetails {

    private UserId userId;

    private String email;

    private String password;

    private Set<Role> roles = new HashSet<>();

    public CustomUserDetails(UserId userId, String email, String password, Set<Role> roles) {
        this.userId = userId;
        this.email = email;
        this.password = password;
        this.roles = roles;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority(role.name())).toList();
    }

    @Override
    public @Nullable String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    public UserId getId(){
        return userId;
    }
}
