package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

public class TokenInfo {

    private final String tokenID;
    private final String username;
    private final String role;

    public TokenInfo(String tokenID, String username, String role) {
        this.tokenID = tokenID;
        this.username = username;
        this.role = role;
    }

    public String getTokenID() {
        return tokenID;
    }

    public String getUsername() {
        return username;
    }

    public String getRole() {
        return role;
    }

    @Override
    public String toString() {
        return "TokenInfo{" +
                "tokenID='" + tokenID + '\'' +
                ", username='" + username + '\'' +
                ", role='" + role + '\'' +
                '}';
    }
}