package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

public class ApproveBackOfficeUserData {

    public String username;

    public ApproveBackOfficeUserData() {
    }

    public boolean validateData() {
        return username != null;
    }
}