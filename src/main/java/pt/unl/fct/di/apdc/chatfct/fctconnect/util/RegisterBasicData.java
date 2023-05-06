package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

import org.apache.commons.validator.routines.EmailValidator;

public class RegisterBasicData {

    public static final String DEFAULT_ROLE = "UNASSIGNED";
    public static final String DEFAULT_VISIBILITY = "PRIVATE";
    public String username;
    public String email;
    public String name;
    public String password;
    public String passConf;
    public String birthDate;
    public String phoneNum;
    public String nif;
    public String visibility;
    public Address address;
    public String photo;
    public String role;

    public RegisterBasicData() {
    }

    public boolean validateData() {
        return !(this.username == null || this.password == null || this.passConf == null || this.email == null
                || this.name == null);
    }

    public boolean validatePassword() {
        return PasswordUtils.validatePassword(this.password);
    }

    public boolean comparePasswords() {
        return PasswordUtils.comparePasswords(this.password, this.passConf);
    }

    public boolean validateZipCode() {
        return this.address == null || this.address.validateZipCode();
    }

    public boolean validateNif() {
        return this.nif == null || this.nif.matches(RegexExp.NIF_REGEX);
    }

    public boolean validatePhoneNum() {
        return this.phoneNum == null || this.phoneNum.matches(RegexExp.PHONE_NUM_REGEX);
    }

    public boolean validateBirthDate() {
        return this.birthDate == null || this.birthDate.matches(RegexExp.DATE_REGEX);
    }

    public boolean validateVisibility() {
        if (this.visibility == null) {
            return true;
        } else {
            this.visibility = this.visibility.toUpperCase().trim();
            return this.visibility.matches(RegexExp.VISIBILITY_REGEX);
        }
    }

    public boolean validateRole() {
        if (this.role == null) {
            return false;
        } else {
            this.role = this.role.toUpperCase().trim();
            return this.role.matches(RegexExp.ROLE_REGEX);
        }
    }

    //email validation based on RFC 822 standard
    public boolean validateEmail() {
        return EmailValidator.getInstance().isValid(this.email);
    }
}