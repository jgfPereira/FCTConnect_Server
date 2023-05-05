package pt.unl.fct.di.apdc.adcdemo.util;

public class LoginData {
    public String username;
    public String password;

    public LoginData() {
    }

    public boolean validateData() {
        return !(this.username == null || this.password == null);
    }
}
