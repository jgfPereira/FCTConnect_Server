package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

public class BackOfficeGetUserInfoData {

    public String username;

    public BackOfficeGetUserInfoData() {
    }

    public boolean validateData() {
        return username != null;
    }
}