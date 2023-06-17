package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

public final class RolePermissions {

    private RolePermissions() {
    }

    public static boolean canUpdate(String propertyName, String role) {
        final boolean baseConstraints = propertyName.equals(DatastoreTypes.CREATION_DATE_ATTR)
                || propertyName.equals(DatastoreTypes.PASSWORD_ATTR)
                || propertyName.equals(DatastoreTypes.ROLE_ATTR)
                || propertyName.equals(DatastoreTypes.EMAIL_ATTR)
                || propertyName.equals(DatastoreTypes.PHOTO_ATTR);
        if (baseConstraints) {
            return false;
        }
        final boolean isStudent = role.equals(RegexExp.ROLE_STUDENT_REGEX);
        final boolean studentProps = propertyName.equals(DatastoreTypes.COURSE_STUDENT_ATTR)
                || propertyName.equals(DatastoreTypes.STUDENT_NUM_ATTR)
                || propertyName.equals(DatastoreTypes.YEAR_STUDENT_ATTR)
                || propertyName.equals(DatastoreTypes.CREDITS_STUDENT_ATTR)
                || propertyName.equals(DatastoreTypes.AVERAGE_STUDENT_ATTR);
        if (studentProps && !isStudent) {
            return false;
        }
        final boolean isProfessor = role.equals(RegexExp.ROLE_PROFESSOR_REGEX);
        final boolean professorProps = propertyName.equals(DatastoreTypes.DEPARTMENT_ATTR)
                || propertyName.equals(DatastoreTypes.OFFICE_PROFESSOR_ATTR);
        if (professorProps && !isProfessor) {
            return false;
        }
        final boolean isEmployee = role.equals(RegexExp.ROLE_EMPLOYEE_REGEX);
        final boolean employeeProps = propertyName.equals(DatastoreTypes.DEPARTMENT_ATTR)
                || propertyName.equals(DatastoreTypes.JOB_TITLE_EMPLOYEE_ATTR);
        return !employeeProps || isEmployee;
    }
}