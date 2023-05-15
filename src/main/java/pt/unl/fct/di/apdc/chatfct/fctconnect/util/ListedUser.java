package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

import com.google.cloud.Timestamp;
import com.google.cloud.datastore.ProjectionEntity;

public class ListedUser {

    private final String name;
    private final String email;
    private final String role;
    private final Timestamp creationDate;

    private ListedUser(String name, String email, String role, Timestamp creationDate) {
        this.name = name;
        this.email = email;
        this.role = role;
        this.creationDate = creationDate;
    }

    public static ListedUser createListedUser(ProjectionEntity entity) {
        final String name = entity.getString(DatastoreTypes.NAME_ATTR);
        final String email = entity.getString(DatastoreTypes.EMAIL_ATTR);
        final String role = DatastoreTypes.formatRoleType(entity.getString(DatastoreTypes.ROLE_ATTR));
        final Timestamp creationDate = entity.getTimestamp(DatastoreTypes.CREATION_DATE_ATTR);
        return new ListedUser(name, email, role, creationDate);
    }

    @Override
    public String toString() {
        return "ListedUser{" +
                "name='" + name + '\'' +
                ", email='" + email + '\'' +
                ", role='" + role + '\'' +
                ", creationDate='" + creationDate + '\'' +
                '}';
    }
}