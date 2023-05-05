package pt.unl.fct.di.apdc.adcdemo.util;

import org.apache.commons.validator.routines.EmailValidator;
import org.passay.*;

import java.util.ArrayList;
import java.util.List;

public class RegisterData {

    public static final String DEFAULT_ROLE = "USER";
    public static final String DEFAULT_STATE = "INACTIVE";

    public String username;
    public String password;
    public String passConf;
    public String email;
    public String name;
    public String visibility;
    public String homePhoneNum;
    public String phoneNum;
    public String occupation;
    public String placeOfWork;
    public String nif;
    public String street;
    public String locale;
    public String zipCode;
    public String photo;

    public RegisterData() {
    }

    public boolean validateData() {
        return !(this.username == null || this.password == null || this.passConf == null || this.email == null
                || this.name == null);
    }

    public boolean validatePasswords() {
        return this.password.equals(this.passConf);
    }

    public boolean validatePasswordConstraints() {
        List<Rule> passRules = new ArrayList<>();
        passRules.add(new LengthRule(8, 20));
        passRules.add(new CharacterRule(EnglishCharacterData.UpperCase, 1));
        passRules.add(new CharacterRule(EnglishCharacterData.LowerCase, 1));
        passRules.add(new CharacterRule(EnglishCharacterData.Digit, 1));
        passRules.add(new CharacterRule(EnglishCharacterData.Special, 1));
        passRules.add(new WhitespaceRule());

        PasswordValidator passValidator = new PasswordValidator(passRules);
        PasswordData passData = new PasswordData(this.password);
        RuleResult res = passValidator.validate(passData);
        return res.isValid();
    }

    public boolean validateZipCode() {
        return this.zipCode == null || this.zipCode.matches("[0-9]{4}-[0-9]{3}");
    }

    //email validation based on RFC 822 standard
    public boolean validateEmail() {
        return EmailValidator.getInstance().isValid(this.email);
    }
}
