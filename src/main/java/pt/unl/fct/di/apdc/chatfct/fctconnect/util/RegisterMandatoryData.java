package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

import org.apache.commons.validator.routines.EmailValidator;

public class RegisterMandatoryData {

    public String email;
    public String password;
    public String passConf;
    public String name;
    public String role;

    public RegisterMandatoryData() {
    }

    public boolean validateData() {
        return !(this.email == null || this.password == null || this.passConf == null
                || this.name == null || this.role == null);
    }

    public boolean validateEmail() {
        return EmailValidator.getInstance().isValid(this.email);
    }

    public boolean validatePassword() {
        return PasswordUtils.validatePassword(this.password);
    }

    public boolean comparePasswords() {
        return PasswordUtils.comparePasswords(this.password, this.passConf);
    }

    public boolean validateRole() {
        this.role = this.role.toUpperCase().trim();
        return this.role.matches(RegexExp.ROLE_REGEX);
    }
}