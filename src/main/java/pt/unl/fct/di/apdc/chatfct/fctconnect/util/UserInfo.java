package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

import com.google.cloud.Timestamp;
import com.google.cloud.datastore.Entity;

import java.time.ZoneId;
import java.util.Date;

public class UserInfo {
    private static final String NULL_VALUE = "null";
    private static final String DEFAULT_TIME_ZONE = "UTC";
    private final String username;
    private final String birthDate;
    private final String creationDate;
    private final String email;
    private final String locale;
    private final String name;
    private final String nif;
    private final String phoneNum;
    private final String role;
    private final String street;
    private final String visibility;
    private final String zipCode;

    protected UserInfo(Entity user) {
        this.username = user.getKey().getName();
        this.birthDate = handleNullDate(user.getTimestamp(DatastoreTypes.BIRTH_DATE_ATTR));
        this.creationDate = dateToString(user.getTimestamp(DatastoreTypes.CREATION_DATE_ATTR).toDate());
        this.email = user.getString(DatastoreTypes.EMAIL_ATTR);
        this.locale = handleNull(user.getString(DatastoreTypes.LOCALE_ATTR));
        this.name = user.getString(DatastoreTypes.NAME_ATTR);
        this.nif = handleNull(user.getString(DatastoreTypes.NIF_ATTR));
        this.phoneNum = handleNull(user.getString(DatastoreTypes.PHONE_NUM_ATTR));
        this.role = DatastoreTypes.formatRoleType(user.getString(DatastoreTypes.ROLE_ATTR));
        this.street = handleNull(user.getString(DatastoreTypes.STREET_ATTR));
        this.visibility = user.getString(DatastoreTypes.VISIBILITY_ATTR);
        this.zipCode = handleNull(user.getString(DatastoreTypes.ZIP_CODE_ATTR));
    }

    public static UserInfo createUserInfo(Entity user) {
        return new UserInfo(user);
    }

    private static String dateToString(Date date) {
        return date.toInstant().atZone(ZoneId.of(DEFAULT_TIME_ZONE))
                .toLocalDateTime().format(LocalDateTimeAdapter.FORMATTER);
    }

    public static String handleNull(String str) {
        return str == null ? NULL_VALUE : str;
    }

    private String handleNullDate(Timestamp timestamp) {
        return timestamp == null ? NULL_VALUE : dateToString(timestamp.toDate());
    }

    @Override
    public String toString() {
        return "UserInfo{" +
                "username='" + username + '\'' +
                ", birthDate='" + birthDate + '\'' +
                ", creationDate='" + creationDate + '\'' +
                ", email='" + email + '\'' +
                ", locale='" + locale + '\'' +
                ", name='" + name + '\'' +
                ", nif='" + nif + '\'' +
                ", phoneNum='" + phoneNum + '\'' +
                ", role='" + role + '\'' +
                ", street='" + street + '\'' +
                ", visibility='" + visibility + '\'' +
                ", zipCode='" + zipCode + '\'' +
                '}';
    }
}