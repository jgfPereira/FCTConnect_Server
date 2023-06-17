package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

import com.google.cloud.datastore.ProjectionEntity;

public class ListedUserStudent extends ListedUser {

    private final String course;
    private final String year;

    private ListedUserStudent(String name, String email, String role,
                              String creationDate, String photo, String course, String year) {
        super(name, email, role, creationDate, photo);
        this.course = handleNull(course);
        this.year = handleNull(year);
    }

    public static ListedUserStudent createListedUserStudent(ProjectionEntity entity, String course, String year) {
        final String name = entity.getString(DatastoreTypes.NAME_ATTR);
        final String email = entity.getString(DatastoreTypes.EMAIL_ATTR);
        final String role = DatastoreTypes.formatRoleType(entity.getString(DatastoreTypes.ROLE_ATTR));
        final String creationDate = creationDateToString(entity.getTimestamp(DatastoreTypes.CREATION_DATE_ATTR).toDate());
        final String photo = handleNull(entity.getString(DatastoreTypes.PHOTO_ATTR));
        return new ListedUserStudent(name, email, role, creationDate, photo, course, year);
    }

    @Override
    public String toString() {
        return "ListedUserStudent{" +
                "course='" + course + '\'' +
                ", year='" + year + '\'' +
                '}';
    }
}