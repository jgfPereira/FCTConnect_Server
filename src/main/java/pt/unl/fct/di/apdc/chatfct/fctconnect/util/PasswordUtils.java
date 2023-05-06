package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

import org.apache.commons.codec.digest.DigestUtils;
import org.passay.*;

import java.util.ArrayList;
import java.util.List;

public final class PasswordUtils {

    private static final PasswordValidator passValidator;

    static {
        List<Rule> passRules = new ArrayList<>();
        passRules.add(new LengthRule(8, 20));
        passRules.add(new CharacterRule(EnglishCharacterData.UpperCase, 1));
        passRules.add(new CharacterRule(EnglishCharacterData.LowerCase, 1));
        passRules.add(new CharacterRule(EnglishCharacterData.Digit, 1));
        passRules.add(new CharacterRule(EnglishCharacterData.Special, 1));
        passRules.add(new WhitespaceRule());
        passValidator = new PasswordValidator(passRules);
    }

    private PasswordUtils() {
    }

    public static String hashPass(String pass) {
        return DigestUtils.sha3_512Hex(pass);
    }

    public static boolean validatePassword(String password) {
        PasswordData passData = new PasswordData(password);
        return passValidator.validate(passData).isValid();
    }

    public static boolean comparePasswords(String password, String passConf) {
        return password.equals(passConf);
    }
}
