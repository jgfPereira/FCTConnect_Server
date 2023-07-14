package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

public class SendFriendshipRequestData {

    public String username;
    public String requesterUsername;

    public SendFriendshipRequestData() {
    }

    public boolean validateData() {
        return !(username == null || requesterUsername == null);
    }

    public boolean isTokenSameUser(String requesterToken) {
        return requesterUsername.equals(requesterToken);
    }
}