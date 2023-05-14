package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import pt.unl.fct.di.apdc.chatfct.fctconnect.resources.SecretKeyResource;

import javax.crypto.SecretKey;
import javax.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.logging.Logger;

public final class TokenUtils {

    public static final String AUTH_HEADER = "X-Auth-Token";
    public static final String AUTH_TYPE = "Bearer ";
    private static final Logger LOG = Logger.getLogger(TokenUtils.class.getName());
    private static final int EXPIRATION_WINDOW = 7200000; // 2 hours
    private static final String TOKEN_DELIMITER = " ";
    private static final String ROLE_ATTR = "role";
    private static final String IS_REVOKED_ATTR = "isRevoked";
    private static final SecretKey KEY;

    static {
        KEY = new SecretKeyResource().decryptSecretKey();
    }

    private TokenUtils() {
    }

    public static String createToken(String username, String role) {
        Date emissionTime = new Date();
        Date expirationTime = new Date(emissionTime.getTime() + EXPIRATION_WINDOW);
        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(emissionTime)
                .setExpiration(expirationTime)
                .claim(ROLE_ATTR, role)
                .claim(IS_REVOKED_ATTR, false)
                .signWith(KEY)
                .compact();
    }

    public static TokenInfo verifyToken(final String token) {
        Claims claims = Jwts.parserBuilder().setSigningKey(KEY).build().parseClaimsJws(token).getBody();
        final String subject = claims.getSubject();
        final String role = claims.get(ROLE_ATTR, String.class);
        final boolean isRevoked = claims.get(IS_REVOKED_ATTR, Boolean.class);
        if (isRevoked) {
            throw new JwtException("Token has been revoked");
        } else {
            return new TokenInfo(subject, role);
        }
    }

    public static String extractTokenFromHeaders(HttpServletRequest request) {
        return request.getHeader(AUTH_HEADER).split(TOKEN_DELIMITER)[1];
    }
}