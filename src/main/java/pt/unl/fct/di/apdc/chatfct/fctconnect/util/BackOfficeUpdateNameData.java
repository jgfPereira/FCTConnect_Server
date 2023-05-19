package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

public class BackOfficeUpdateNameData {

    public String name;

    public BackOfficeUpdateNameData() {
    }

    public boolean validateData() {
        return name != null;
    }
}