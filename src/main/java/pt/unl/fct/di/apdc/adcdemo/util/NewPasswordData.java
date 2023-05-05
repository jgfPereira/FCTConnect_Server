package pt.unl.fct.di.apdc.adcdemo.util;

import org.passay.*;

import java.util.ArrayList;
import java.util.List;

public class NewPasswordData {

    public String username;
    public String passChangerUsername;
    public String password;
    public String newPassword;
    public String newPasswordConf;

    public NewPasswordData() {
    }

    public boolean validateData() {
        return !(this.username == null || this.passChangerUsername == null || this.password == null || this.newPassword == null || this.newPasswordConf == null);
    }

    public boolean validatePasswords() {
        return this.newPassword.equals(this.newPasswordConf);
    }

    public boolean validateChanger() {
        return this.username.equals(this.passChangerUsername);
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
        PasswordData passData = new PasswordData(this.newPassword);
        RuleResult res = passValidator.validate(passData);
        return res.isValid();
    }
}
