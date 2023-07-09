package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

public class UpdateCheckpointInventoryData {

    public String username;
    public String item;

    public UpdateCheckpointInventoryData() {
    }

    public boolean validateData() {
        return !(username == null || item == null);
    }

    public boolean isTokenSameUser(String usernameToken) {
        return username.equals(usernameToken);
    }
}