package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

import org.apache.commons.validator.routines.EmailValidator;

public class RegisterBasicData {

    public static final String DEFAULT_ROLE = "UNASSIGNED";
    public String username;
    public String email;
    public String name;
    public String password;
    public String passConf;
    public String birthDate;
    public String phoneNum;
    public String nif;
    public String visibility;
    public String street;
    public String locale;
    public String zipCode;
    public String photo;

    public RegisterBasicData() {
    }

    public boolean validateData() {
        return !(this.username == null || this.password == null || this.passConf == null || this.email == null
                || this.name == null);
    }

    public boolean validatePasswordConstraints() {
        return PasswordUtils.validatePassword(this.password);
    }

    public boolean validatePasswords() {
        return PasswordUtils.comparePasswords(this.password, this.passConf);
    }

    public boolean validateZipCode() {
        return this.zipCode == null || this.zipCode.matches(RegexExp.ZIP_CODE_REGEX);
    }

    public boolean validateNif() {
        return this.nif == null || this.nif.matches(RegexExp.NIF_REGEX);
    }

    public boolean validatePhoneNum() {
        return this.phoneNum == null || this.phoneNum.matches("");
    }

    //email validation based on RFC 822 standard
    public boolean validateEmail() {
        return EmailValidator.getInstance().isValid(this.email);
    }
}
