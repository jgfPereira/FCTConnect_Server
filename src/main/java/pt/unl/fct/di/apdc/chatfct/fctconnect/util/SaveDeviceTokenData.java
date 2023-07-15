package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

public class SaveDeviceTokenData {

    public String username;
    public String deviceToken;

    public SaveDeviceTokenData() {
    }

    public boolean validateData() {
        return !(username == null || deviceToken == null);
    }

    public boolean isTokenSameUser(String usernameToken) {
        return username.equals(usernameToken);
    }
}