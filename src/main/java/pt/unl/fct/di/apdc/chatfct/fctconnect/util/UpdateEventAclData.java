package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

public class UpdateEventAclData {

    public String id;
    public String tag;

    public UpdateEventAclData() {
    }

    public boolean validateData() {
        return !(id == null || tag == null);
    }

    public boolean validateAclTag() {
        tag = tag.toUpperCase().trim();
        return tag.matches(RegexExp.ROLE_REGEX);
    }
}