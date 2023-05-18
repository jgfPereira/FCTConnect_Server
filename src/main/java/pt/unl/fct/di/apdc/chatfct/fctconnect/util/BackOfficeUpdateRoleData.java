package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

public class BackOfficeUpdateRoleData {

    public String username;

    public BackOfficeUpdateRoleData() {
    }

    public boolean validateData() {
        return username != null;
    }
}