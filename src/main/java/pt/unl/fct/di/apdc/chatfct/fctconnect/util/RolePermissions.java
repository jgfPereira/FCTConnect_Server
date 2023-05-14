package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

public final class RolePermissions {

    public static final String STUDENT_ROLE = "STUDENT";
    public static final String PROFESSOR_ROLE = "PROFESSOR";
    public static final String EMPLOYEE_ROLE = "EMPLOYEE";
    public static final String USER_ROLE = "USER";
    public static final String GBO_ROLE = "GBO";
    public static final String GA_ROLE = "GA";
    public static final String GS_ROLE = "GS";
    public static final String SU_ROLE = "SU";

    private RolePermissions() {
    }

    public static boolean canUpdate(UpdateData data, String username, String propertyName) {
        final boolean isPropertyUpdatable = !(propertyName.equals(DatastoreTypes.CREATION_DATE_ATTR)
                || propertyName.equals(DatastoreTypes.PASSWORD_ATTR)
                || propertyName.equals(DatastoreTypes.ROLE_ATTR)
                || propertyName.equals(DatastoreTypes.EMAIL_ATTR)
                || propertyName.equals(DatastoreTypes.STUDENT_NUM_ATTR));
        return isPropertyUpdatable && data.isSameUser(username);
    }
}