package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

public final class RegexExp {

    public static final String ZIP_CODE_REGEX = "[0-9]{4}-[0-9]{3}";
    /*
    country code: 1-3 digits
    national number: 9-10 digits
     */
    public static final String PHONE_NUM_REGEX = "^(\\+\\d{1,3}( )?)?((\\(\\d{3}\\))|\\d{3})[- .]?\\d{3}[- .]?\\d{4}$"
            + "|^(\\+\\d{1,3}( )?)?(\\d{3}[ ]?){2}\\d{3}$"
            + "|^(\\+\\d{1,3}( )?)?(\\d{3}[ ]?)(\\d{2}[ ]?){2}\\d{2}$";
    public static final String DATE_REGEX = "^((2000|2400|2800|(19|2[0-9])(0[48]|[2468][048]|[13579][26]))-02-29)$"
            + "|^(((19|2[0-9])[0-9]{2})-02-(0[1-9]|1[0-9]|2[0-8]))$"
            + "|^(((19|2[0-9])[0-9]{2})-(0[13578]|10|12)-(0[1-9]|[12][0-9]|3[01]))$"
            + "|^(((19|2[0-9])[0-9]{2})-(0[469]|11)-(0[1-9]|[12][0-9]|30))$";
    public static final String VISIBILITY_REGEX = "PUBLIC|PRIVATE";

    public static final String ROLE_REGEX = "STUDENT|PROFESSOR|EMPLOYEE";
    public static final String ROLE_STUDENT_REGEX = "STUDENT";
    public static final String ROLE_OTHER_REGEX = "PROFESSOR|EMPLOYEE";
    public static final String STUDENT_NUMBER_REGEX = "[0-9]{5}";
    public static final String PUBLIC_VISIBILITY_REGEX = "PUBLIC";
    public static final String BACK_OFFICE_ROLE_REGEX = "ADMIN|MOD";
    public static final String APPROVED_STATE_REGEX = "APPROVED";
    public static final String ROLE_PROFESSOR_REGEX = "PROFESSOR";
    public static final String WHOLE_NUMBER_REGEX = "^\\d+$";
    public static final String REAL_NUMBER_REGEX = "^(?:-(?:[1-9](?:\\d{0,2}(?:,\\d{3})+|\\d*))|(?:0|(?:[1-9](?:\\d{0,2}(?:,\\d{3})+|\\d*))))(?:.\\d+|)$";

    private RegexExp() {
    }
}