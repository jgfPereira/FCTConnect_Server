package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

import com.google.cloud.datastore.ProjectionEntity;

public class ListedUserEmployee extends ListedUser {

    private final String department;
    private final String jobTitle;

    private ListedUserEmployee(String name, String email, String role,
                               String creationDate, String photo, String department, String jobTitle) {
        super(name, email, role, creationDate, photo);
        this.department = handleNull(department);
        this.jobTitle = handleNull(jobTitle);
    }

    public static ListedUserEmployee createListedUserEmployee(ProjectionEntity entity, String department, String jobTitle) {
        final String name = entity.getString(DatastoreTypes.NAME_ATTR);
        final String email = entity.getString(DatastoreTypes.EMAIL_ATTR);
        final String role = DatastoreTypes.formatRoleType(entity.getString(DatastoreTypes.ROLE_ATTR));
        final String creationDate = creationDateToString(entity.getTimestamp(DatastoreTypes.CREATION_DATE_ATTR).toDate());
        final String photo = handleNull(entity.getString(DatastoreTypes.PHOTO_ATTR));
        return new ListedUserEmployee(name, email, role, creationDate, photo, department, jobTitle);
    }
    

    @Override
    public String toString() {
        return "ListedUserEmployee{" +
                "department='" + department + '\'' +
                ", jobTitle='" + jobTitle + '\'' +
                '}';
    }
}