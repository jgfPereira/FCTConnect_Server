package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

public class AcceptFriendshipRequestData {

    public String requesterUsername;

    public String username;

    public AcceptFriendshipRequestData() {
    }

    public boolean validateData() {
        return !(username == null || requesterUsername == null);
    }

    public boolean isTokenSameUser(String usernameToken) {
        return username.equals(usernameToken);
    }
}