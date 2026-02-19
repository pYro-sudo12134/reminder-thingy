package by.losik.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.UUID;

@Singleton
public class PasswordResetTokenService {
    private static final Logger log = LoggerFactory.getLogger(PasswordResetTokenService.class);

    private final String secretKey;
    private final long expirationTime;
    private final String issuer;

    @Inject
    public PasswordResetTokenService(@Named("secret.key")String secretKey,
                                     @Named("token.expiration")long expirationTime,
                                     @Named("issuer")String issuer) {
        this.secretKey = secretKey;
        this.expirationTime = expirationTime;
        this.issuer = issuer;
    }

    public String generateToken(String username) {
        Algorithm algorithm = Algorithm.HMAC256(secretKey);

        String token = JWT.create()
                .withIssuer(issuer)
                .withSubject(username)
                .withJWTId(UUID.randomUUID().toString())
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + expirationTime))
                .withClaim("type", "password_reset")
                .sign(algorithm);

        log.debug("Generated password reset token for user: {}", username);
        return token;
    }

    public String validateToken(String token) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(secretKey);

            DecodedJWT jwt = JWT.require(algorithm)
                    .withIssuer(issuer)
                    .withClaim("type", "password_reset")
                    .build()
                    .verify(token);

            String username = jwt.getSubject();
            Date expiresAt = jwt.getExpiresAt();

            log.debug("Token validated for user: {}, expires: {}", username, expiresAt);
            return username;

        } catch (JWTVerificationException e) {
            log.warn("Invalid password reset token: {}", e.getMessage());
            return null;
        }
    }
}