package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

public class RemoveData {

    public String removedUsername;

    public RemoveData() {
    }

    public boolean validateData() {
        return this.removedUsername != null;
    }

    public boolean isSameUser(String username) {
        return username.equals(this.removedUsername);
    }
}