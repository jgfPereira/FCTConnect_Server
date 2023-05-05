package pt.unl.fct.di.apdc.adcdemo.util;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.HttpHeaders;
import java.util.UUID;

public class AuthToken {

    public static final long EXPIRATION_TIME = 300000; // 5min test //1000 * 60 * 60 * 2; // 2h
    private static final String AUTH_TYPE = "Bearer";
    public String username;
    public String tokenID;
    public long creationDate;
    public long expirationDate;
    public boolean isRevoked;

    public AuthToken() {
    }

    public AuthToken(String username) {
        this.username = username;
        this.tokenID = UUID.randomUUID().toString();
        this.creationDate = System.currentTimeMillis();
        this.expirationDate = this.creationDate + AuthToken.EXPIRATION_TIME;
        this.isRevoked = false;
    }

    public AuthToken(String username, String tokenID, long creationDate, long expirationDate, boolean isRevoked) {
        this.username = username;
        this.tokenID = tokenID;
        this.creationDate = creationDate;
        this.expirationDate = expirationDate;
        this.isRevoked = isRevoked;
    }

    public static boolean isValid(long expDate, boolean isRevoked) {
        return System.currentTimeMillis() <= expDate && !isRevoked;
    }

    public static String getAuthHeader(HttpServletRequest request) {
        final String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null) {
            return null;
        }
        final String[] split = header.split(" ");
        if (split.length != 2) {
            return null;
        }
        return split[0].equals(AUTH_TYPE) ? split[1] : null;
    }
}