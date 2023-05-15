package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

public class NewPasswordData {

    public String password;
    public String newPassword;
    public String newPasswordConf;

    public NewPasswordData() {
    }

    public boolean validateData() {
        return !(this.password == null || this.newPassword == null || this.newPasswordConf == null);
    }

    public boolean validatePassword() {
        return PasswordUtils.validatePassword(this.newPassword);
    }

    public boolean comparePasswords() {
        return PasswordUtils.comparePasswords(this.newPassword, this.newPasswordConf);
    }

    public boolean isPasswordSameAsOld() {
        return PasswordUtils.comparePasswords(password, newPassword);
    }
}