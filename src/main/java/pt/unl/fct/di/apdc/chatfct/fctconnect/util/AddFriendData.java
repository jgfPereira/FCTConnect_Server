package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

public class AddFriendData {

    public String username;
    public String friendUsername;

    public AddFriendData() {
    }

    public boolean validateData() {
        return !(username == null || friendUsername == null);
    }

    public boolean isTokenSameUser(String usernameToken) {
        return username.equals(usernameToken);
    }
}