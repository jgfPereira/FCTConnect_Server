package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

public class GetUserInfoData {

    public String username;

    public GetUserInfoData() {
    }

    public boolean validateData() {
        return username != null;
    }
}