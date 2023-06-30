package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

public class AddOnlinePlayerData {

    public String username;
    public String x;
    public String y;

    public AddOnlinePlayerData() {
    }

    public boolean validateData() {
        return !(username == null || x == null || y == null);
    }

    public boolean validateCoords() {
        return x.matches(RegexExp.REAL_NUMBER_REGEX) && y.matches(RegexExp.REAL_NUMBER_REGEX);
    }

    public boolean isTokenSameUser(String usernameToken) {
        return username.equals(usernameToken);
    }
}