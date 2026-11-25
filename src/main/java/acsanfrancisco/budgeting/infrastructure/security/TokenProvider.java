package acsanfrancisco.budgeting.infrastructure.security;

import acsanfrancisco.budgeting.domain.user.UserId;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class TokenProvider {

    @Value("${JWT_EXPIRATION}")
    private Long expiration;

    @Value("${JWT_KEY}")
    private String key;

    public String generateToken(Authentication authentication) {
        CustomUserDetails user = (CustomUserDetails) authentication.getPrincipal();
        return buildToken(user.getId(), user.getUsername());
    }

    public boolean isTokenValid(String token) {
        try{
            getClaimsFromToken(token);
            return true;
        }
        catch(Exception e){
            return false;
        }
    }

    public String getUsernameFromToken(String token) {
        return getClaimsFromToken(token).get("username", String.class);
    }

    private String buildToken(UserId id, String username) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);
        return Jwts.builder()
                .subject(id.toString())
                .claim("username", username)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(signingKey())
                .compact();
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(key.getBytes());
    }

    private Claims getClaimsFromToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
