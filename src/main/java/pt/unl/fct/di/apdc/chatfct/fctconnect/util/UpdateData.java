package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

public class UpdateData {
    public String updatedUsername;
    public UpdateEntry[] updateEntries;


    public UpdateData() {
    }

    public boolean validateData() {
        return !(this.updatedUsername == null || this.updateEntries == null || this.updateEntries.length == 0);
    }

    public boolean isSameUser(String username) {
        return username.equals(this.updatedUsername);
    }
}