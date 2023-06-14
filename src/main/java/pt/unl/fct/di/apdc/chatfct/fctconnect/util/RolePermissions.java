package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

public final class RolePermissions {

    public static final String STUDENT_ROLE = "STUDENT";
    public static final String PROFESSOR_ROLE = "PROFESSOR";
    public static final String EMPLOYEE_ROLE = "EMPLOYEE";

    private RolePermissions() {
    }

    public static boolean canUpdate(String propertyName) {
        return !(propertyName.equals(DatastoreTypes.CREATION_DATE_ATTR)
                || propertyName.equals(DatastoreTypes.PASSWORD_ATTR)
                || propertyName.equals(DatastoreTypes.ROLE_ATTR)
                || propertyName.equals(DatastoreTypes.EMAIL_ATTR));
    }
}