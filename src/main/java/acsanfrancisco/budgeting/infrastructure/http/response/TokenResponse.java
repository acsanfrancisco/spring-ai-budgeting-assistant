package acsanfrancisco.budgeting.infrastructure.http.response;

import acsanfrancisco.budgeting.application.output.TokenOutput;

public record TokenResponse(String token) {
    public static TokenResponse from(TokenOutput token) {
        return new TokenResponse(token.token());
    }
}
