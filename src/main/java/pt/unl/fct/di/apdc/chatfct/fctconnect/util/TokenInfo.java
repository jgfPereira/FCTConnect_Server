package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

public class TokenInfo {

    private final String username;
    private final String role;

    public TokenInfo(String username, String role) {
        this.username = username;
        this.role = role;
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
                "username='" + username + '\'' +
                ", role='" + role + '\'' +
                '}';
    }
}