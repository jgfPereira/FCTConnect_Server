package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

public class UpdateEntry {

    public final String propertyName;
    public final String newValue;

    public UpdateEntry(String propertyName, String newValue) {
        this.propertyName = propertyName;
        this.newValue = newValue;
    }
}