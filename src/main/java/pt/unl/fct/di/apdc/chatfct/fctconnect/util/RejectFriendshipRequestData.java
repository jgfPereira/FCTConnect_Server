package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

public class RejectFriendshipRequestData {

    public String requesterUsername;

    public String username;

    public RejectFriendshipRequestData() {
    }

    public boolean validateData() {
        return !(username == null || requesterUsername == null);
    }

    public boolean isTokenSameUser(String usernameToken) {
        return username.equals(usernameToken);
    }
}