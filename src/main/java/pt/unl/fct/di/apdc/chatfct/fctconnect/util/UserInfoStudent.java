package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

import com.google.cloud.datastore.Entity;

public class UserInfoStudent extends UserInfo {

    private final String studentNumber;

    private UserInfoStudent(Entity user, Entity student) {
        super(user);
        this.studentNumber = student.getString(DatastoreTypes.STUDENT_NUM_ATTR);
    }

    public static UserInfoStudent createUserInfoStudent(Entity user, Entity student) {
        return new UserInfoStudent(user, student);
    }

    @Override
    public String toString() {
        return "UserInfoStudent{" +
                "studentNumber='" + studentNumber + '\'' +
                '}';
    }
}