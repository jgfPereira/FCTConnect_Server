package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

public class RemoveFriendData {

    public String username;
    public String removeFriend;

    public RemoveFriendData() {
    }

    public boolean validateData() {
        return !(username == null || removeFriend == null);
    }

    public boolean isTokenSameUser(String usernameToken) {
        return username.equals(usernameToken);
    }
}