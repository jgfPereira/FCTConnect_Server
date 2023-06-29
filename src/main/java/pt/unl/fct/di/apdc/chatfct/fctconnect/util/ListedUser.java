package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

import com.google.cloud.datastore.Entity;
import com.google.cloud.datastore.ProjectionEntity;

import java.time.ZoneId;
import java.util.Date;

public class ListedUser {

    private static final String DEFAULT_TIME_ZONE = "UTC";
    private static final String NULL_VALUE = "null";
    private final String name;
    private final String email;
    private final String role;
    private final String photo;
    private final String creationDate;

    protected ListedUser(String name, String email, String role, String creationDate, String photo) {
        this.name = name;
        this.email = email;
        this.role = role;
        this.creationDate = creationDate;
        this.photo = handleNull(photo);
    }

    protected ListedUser(Entity user) {
        name = user.getString(DatastoreTypes.NAME_ATTR);
        email = user.getString(DatastoreTypes.EMAIL_ATTR);
        role = DatastoreTypes.formatRoleType(user.getString(DatastoreTypes.ROLE_ATTR));
        creationDate = creationDateToString(user.getTimestamp(DatastoreTypes.CREATION_DATE_ATTR).toDate());
        photo = handleNull(null);
    }

    public static ListedUser createListedUser(ProjectionEntity entity) {
        final String name = entity.getString(DatastoreTypes.NAME_ATTR);
        final String email = entity.getString(DatastoreTypes.EMAIL_ATTR);
        final String role = DatastoreTypes.formatRoleType(entity.getString(DatastoreTypes.ROLE_ATTR));
        final String creationDate = creationDateToString(entity.getTimestamp(DatastoreTypes.CREATION_DATE_ATTR).toDate());
        final String photo = handleNull(entity.getString(DatastoreTypes.PHOTO_ATTR));
        return new ListedUser(name, email, role, creationDate, photo);
    }

    protected static String creationDateToString(Date date) {
        return date.toInstant().atZone(ZoneId.of(DEFAULT_TIME_ZONE))
                .toLocalDateTime().format(LocalDateTimeAdapter.FORMATTER);
    }

    public static String handleNull(String str) {
        return str == null ? NULL_VALUE : str;
    }

    @Override
    public String toString() {
        return "ListedUser{" +
                "name='" + name + '\'' +
                ", email='" + email + '\'' +
                ", role='" + role + '\'' +
                ", photo='" + photo + '\'' +
                ", creationDate='" + creationDate + '\'' +
                '}';
    }
}