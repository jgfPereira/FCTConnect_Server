package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

import com.google.cloud.datastore.Entity;

public class UserInfoProfessor extends UserInfo {

    private final String department;
    private final String office;

    private UserInfoProfessor(Entity user, Entity professor) {
        super(user);
        this.department = handleNull(professor.getString(DatastoreTypes.DEPARTMENT_ATTR));
        this.office = handleNull(professor.getString(DatastoreTypes.OFFICE_PROFESSOR_ATTR));
    }

    public static UserInfoProfessor createUserInfoProfessor(Entity user, Entity professor) {
        return new UserInfoProfessor(user, professor);
    }

    @Override
    public String toString() {
        return "UserInfoProfessor{" +
                "department='" + department + '\'' +
                ", office='" + office + '\'' +
                '}';
    }
}