package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

import com.google.cloud.datastore.ProjectionEntity;

public class ListedUserProfessor extends ListedUser {

    private final String department;
    private final String office;

    private ListedUserProfessor(String name, String email, String role,
                                String creationDate, String photo, String department, String office) {
        super(name, email, role, creationDate, photo);
        this.department = handleNull(department);
        this.office = handleNull(office);
    }

    public static ListedUserProfessor createListedUserProfessor(ProjectionEntity entity, String department, String office) {
        final String name = entity.getString(DatastoreTypes.NAME_ATTR);
        final String email = entity.getString(DatastoreTypes.EMAIL_ATTR);
        final String role = DatastoreTypes.formatRoleType(entity.getString(DatastoreTypes.ROLE_ATTR));
        final String creationDate = creationDateToString(entity.getTimestamp(DatastoreTypes.CREATION_DATE_ATTR).toDate());
        final String photo = handleNull(entity.getString(DatastoreTypes.PHOTO_ATTR));
        return new ListedUserProfessor(name, email, role, creationDate, photo, department, office);
    }

    @Override
    public String toString() {
        return "ListedUserProfessor{" +
                "department='" + department + '\'' +
                ", office='" + office + '\'' +
                '}';
    }
}