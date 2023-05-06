package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

public class RolePermissions {

    public static final String STUDENT_ROLE = "STUDENT";
    public static final String PROFESSOR_ROLE = "PROFESSOR";
    public static final String EMPLOYEE_ROLE = "EMPLOYEE";
    public static final String USER_ROLE = "USER";
    public static final String GBO_ROLE = "GBO";
    public static final String GA_ROLE = "GA";
    public static final String GS_ROLE = "GS";
    public static final String SU_ROLE = "SU";

    public static final String[] USER_PERMS = {};
    public static final String[] GBO_PERMS = {USER_ROLE};
    public static final String[] GA_PERMS = {USER_ROLE, GBO_ROLE};
    public static final String[] GS_PERMS = {USER_ROLE, GBO_ROLE, GA_ROLE};
    public static final String[] SU_PERMS = {USER_ROLE, GBO_ROLE, GA_ROLE, GS_ROLE, SU_ROLE};

    public static boolean canRemove(String removerRole, String removedRole) {
        return contains(removerRole, removedRole);
    }

    public static boolean canUpdate(UpdateData data, String updaterRole, String updatedRole, String attributeName, String attributeValue) {
        // the key (username) of and entity cannot change after its created
        if (attributeName.equals("creationDate") || attributeName.equals("password")) {
            return false;
        }
        if (data.isSameUser()) {
            if (updaterRole.equals(USER_ROLE)) {
                return !(attributeName.equals("email") || attributeName.equals("name") || attributeName.equals("role") || attributeName.equals("state"));
            } else //unrecognized role
                if (updaterRole.equals(GBO_ROLE) || updaterRole.equals(GA_ROLE) || updaterRole.equals(GS_ROLE)) {
                    return !(attributeName.equals("role") || attributeName.equals("state"));
                } else return updaterRole.equals(SU_ROLE);
        }
        boolean roleBasicPerms = contains(updaterRole, updatedRole);
        if (!roleBasicPerms) {
            return false;
        }
        if (updaterRole.equals(GBO_ROLE)) {
            return !attributeName.equals("role");
        } else if (updaterRole.equals(GA_ROLE)) {
            return !attributeName.equals("role");
        } else //unrecognized role
            if (updaterRole.equals(GS_ROLE)) {
                if (attributeName.equals("role")) {
                    return updatedRole.equals(USER_ROLE) && attributeValue.equals(GBO_ROLE);
                } else {
                    return true;
                }
            } else return updaterRole.equals(SU_ROLE);
    }

    private static boolean contains(String fatherRole, String childRole) {
        String[] tmp = null;
        switch (fatherRole) {
            case USER_ROLE:
                return false;
            case GBO_ROLE:
                tmp = GBO_PERMS;
                break;
            case GA_ROLE:
                tmp = GA_PERMS;
                break;
            case GS_ROLE:
                tmp = GS_PERMS;
                break;
            case SU_ROLE:
                tmp = SU_PERMS;
                break;
        }
        if (tmp == null) {
            return false;
        }
        for (String s : tmp) {
            if (s.equals(childRole)) {
                return true;
            }
        }
        return false;
    }
}
