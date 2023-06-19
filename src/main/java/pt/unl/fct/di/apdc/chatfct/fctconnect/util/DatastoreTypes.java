package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

public final class DatastoreTypes {

    public static final String PROJECT_ID = "fctconnect23";
    public static final String BUCKET_NAME = "fctconnect23.appspot.com";
    public static final String PHOTO_NAME_FMT = "photo_%s";
    public static final String USER_TYPE = "User";
    public static final String STUDENT_TYPE = "Student";
    public static final String PROFESSOR_TYPE = "Professor";
    public static final String EMPLOYEE_TYPE = "Employee";
    public static final String LOGIN_REGISTRY_TYPE = "LoginRegistry";
    public static final String LOGIN_LOG_TYPE = "LoginLog";
    public static final String SECRET_KEY_TYPE = "SecretKey";
    public static final String TOKEN_REVOKED_TYPE = "TokenRevoked";
    public static final String BACK_OFFICE_USER_TYPE = "BackOfficeUser";
    public static final String LOGIN_REGISTRY_KEY = "loginReg";
    public static final String SECRET_KEY_KEY = "secretKey";
    public static final String EMAIL_ATTR = "email";
    public static final String NAME_ATTR = "name";
    public static final String PASSWORD_ATTR = "password";
    public static final String CREATION_DATE_ATTR = "creationDate";
    public static final String ROLE_ATTR = "role";
    public static final String BIRTH_DATE_ATTR = "birthDate";
    public static final String PHONE_NUM_ATTR = "phoneNum";
    public static final String VISIBILITY_ATTR = "visibility";
    public static final String STREET_ATTR = "street";
    public static final String LOCALE_ATTR = "locale";
    public static final String ZIP_CODE_ATTR = "zipCode";
    public static final String PHOTO_ATTR = "photo";
    public static final String STUDENT_NUM_ATTR = "studentNumber";
    public static final String SUCCESS_LOGINS_ATTR = "successLogins";
    public static final String FAIL_LOGINS_ATTR = "failLogins";
    public static final String FIRST_LOGIN_ATTR = "firstLogin";
    public static final String LAST_LOGIN_ATTR = "lastLogin";
    public static final String LAST_ATTEMPT_ATTR = "lastAttempt";
    public static final String LOGIN_IP_ATTR = "loginIP";
    public static final String LOGIN_HOST_ATTR = "loginHost";
    public static final String LOGIN_COUNTRY_ATTR = "loginCountry";
    public static final String LOGIN_CITY_ATTR = "loginCity";
    public static final String LOGIN_TIME_ATTR = "loginTime";
    public static final String LOGIN_COORDS_ATTR = "loginCoords";
    public static final String SECRET_KEY_ATTR = "secretKey";
    public static final String AES_KEY_ATTR = "aesKey";
    public static final String INIT_VECTOR_ATTR = "initVector";
    public static final String DEFAULT_VISIBILITY = "PUBLIC";
    public static final String TOKEN_REVOCATION_DATE_ATTR = "revocationDate";
    public static final String STATE_ATTR = "state";
    public static final String DEFAULT_STATE = "UNAPPROVED";
    public static final String APPROVED_STATE = "APPROVED";
    public static final String COURSE_STUDENT_ATTR = "course";
    public static final String YEAR_STUDENT_ATTR = "year";
    public static final String CREDITS_STUDENT_ATTR = "credits";
    public static final String AVERAGE_STUDENT_ATTR = "average";
    public static final String DEPARTMENT_ATTR = "department";
    public static final String OFFICE_PROFESSOR_ATTR = "office";
    public static final String JOB_TITLE_EMPLOYEE_ATTR = "jobTitle";
    public static final String ACCOUNT_CONFIRMATION_TYPE = "AccountConfirmation";
    public static final String USERNAME_ACCOUNT_CONF = "username";
    public static final String CREATION_DATE_ACCOUNT_CONF = "creationDate";
    public static final String USER_STATUS_ATTR = "status";
    public static final String DEFAULT_STATUS = "UNCONFIRMED";
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    public static final String EVENT_TYPE = "Event";
    public static final String EVENT_NAME_ATTR = "name";
    public static final String EVENT_DESCRIPTION_ATTR = "description";
    public static final String EVENT_ACL_ATTR = "acl";
    public static final String EVENT_START_DATE_ATTR = "startDate";
    public static final String EVENT_END_DATE_ATTR = "endDate";
    public static final String EVENT_LOCATION_ATTR = "location";
    public static final String LOCATIONS_TYPE = "Locations";
    public static final String LOCATIONS_TYPE_KEY = "uniPlaces";

    private DatastoreTypes() {
    }

    public static String formatRoleType(String role) {
        StringBuilder sb = new StringBuilder(role.toLowerCase());
        sb.setCharAt(0, Character.toUpperCase(sb.charAt(0)));
        return sb.toString();
    }
}