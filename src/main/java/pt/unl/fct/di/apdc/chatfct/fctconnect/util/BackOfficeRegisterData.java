package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

public class BackOfficeRegisterData extends RegisterMandatoryData {

    public BackOfficeRegisterData() {
    }

    @Override
    public boolean validateRole() {
        this.role = this.role.toUpperCase().trim();
        return this.role.matches(RegexExp.BACK_OFFICE_ROLE_REGEX);
    }
}