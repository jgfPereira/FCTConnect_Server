package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.util.Date;

public final class TokenUtils {

    public static final String AUTH_HEADER = "Authorization";
    public static final String AUTH_TYPE = "Bearer ";
    private static final int EXPIRATION_WINDOW = 7200000; // 2 hours

    private TokenUtils() {
    }

    public static String createToken(String username, String role) {
        Date emissionTime = new Date();
        Date expirationTime = new Date(emissionTime.getTime() + EXPIRATION_WINDOW);
        SecretKey key = Keys.secretKeyFor(SignatureAlgorithm.HS512);
        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(emissionTime)
                .setExpiration(expirationTime)
                .claim("role", role)
                .claim("isRevoked", false)
                .signWith(key)
                .compact();
    }
}
