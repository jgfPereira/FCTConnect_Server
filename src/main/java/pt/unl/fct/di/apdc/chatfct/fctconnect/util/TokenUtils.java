package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import pt.unl.fct.di.apdc.chatfct.fctconnect.resources.SecretKeyResource;
import pt.unl.fct.di.apdc.chatfct.fctconnect.resources.TokenRevocationListResource;

import javax.crypto.SecretKey;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Response;
import java.util.Date;
import java.util.UUID;

public final class TokenUtils {

    public static final String AUTH_HEADER = "X-Auth-Token";
    public static final String AUTH_TYPE = "Bearer ";
    private static final int EXPIRATION_WINDOW = 7200000; // 2 hours
    private static final String TOKEN_DELIMITER = " ";
    private static final String ROLE_ATTR = "role";
    private static final SecretKey KEY;
    private static final TokenRevocationListResource TOKEN_REVOCATION_LIST;

    static {
        KEY = new SecretKeyResource().decryptSecretKey();
        TOKEN_REVOCATION_LIST = new TokenRevocationListResource();
    }

    private TokenUtils() {
    }

    public static String createToken(String username, String role) {
        Date emissionTime = new Date();
        Date expirationTime = new Date(emissionTime.getTime() + EXPIRATION_WINDOW);
        return Jwts.builder()
                .setId(UUID.randomUUID().toString())
                .setSubject(username)
                .setIssuedAt(emissionTime)
                .setExpiration(expirationTime)
                .claim(ROLE_ATTR, role)
                .signWith(KEY)
                .compact();
    }

    public static TokenInfo verifyToken(final String token) {
        Claims claims = Jwts.parserBuilder().setSigningKey(KEY).build().parseClaimsJws(token).getBody();
        final String tokenID = claims.getId();
        final String subject = claims.getSubject();
        final String role = claims.get(ROLE_ATTR, String.class);
        final Boolean isTokenRevoked = TOKEN_REVOCATION_LIST.isTokenRevoked(tokenID);
        if (isTokenRevoked == null) {
            throw new JwtException("An error occurred while checking the validity of the token");
        } else if (isTokenRevoked.equals(Boolean.TRUE)) {
            throw new JwtException("Token is revoked");
        }
        return new TokenInfo(tokenID, subject, role);
    }

    public static Response revokeToken(final String tokenID) {
        return TOKEN_REVOCATION_LIST.revokeToken(tokenID);
    }

    public static void cleanupRevokedTokens() {
        TOKEN_REVOCATION_LIST.cleanupRevokedTokens();
    }

    public static String extractTokenFromHeaders(HttpServletRequest request) {
        return request.getHeader(AUTH_HEADER).split(TOKEN_DELIMITER)[1];
    }
}