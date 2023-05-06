package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

public class Address {

    public String street;
    public String locale;
    public String zipCode;

    public Address() {
    }

    public boolean validateZipCode() {
        return this.zipCode == null || this.zipCode.matches(RegexExp.ZIP_CODE_REGEX);
    }
}
