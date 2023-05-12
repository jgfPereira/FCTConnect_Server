package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

public final class DatastoreTypes {

    public static final String USER_TYPE = "User";
    public static final String STUDENT_TYPE = "Student";
    public static final String PROFESSOR_TYPE = "Professor";
    public static final String EMPLOYEE_TYPE = "Employee";
    public static final String EMAIL_ATTR = "email";
    public static final String NAME_ATTR = "name";
    public static final String PASSWORD_ATTR = "password";
    public static final String CREATION_DATE_ATTR = "creationDate";
    public static final String ROLE_ATTR = "role";
    public static final String BIRTH_DATE_ATTR = "birthDate";
    public static final String PHONE_NUM_ATTR = "phoneNum";
    public static final String NIF_ATTR = "nif";
    public static final String VISIBILITY_ATTR = "visibility";
    public static final String STREET_ATTR = "street";
    public static final String LOCALE_ATTR = "locale";
    public static final String ZIP_CODE_ATTR = "zipCode";
    public static final String PHOTO_ATTR = "photo";
    public static final String STUDENT_NUM_ATTR = "studentNumber";

    private DatastoreTypes() {
    }

    public static String formatRoleType(String role) {
        StringBuilder sb = new StringBuilder(role.toLowerCase());
        sb.setCharAt(0, Character.toUpperCase(sb.charAt(0)));
        return sb.toString();
    }
}
