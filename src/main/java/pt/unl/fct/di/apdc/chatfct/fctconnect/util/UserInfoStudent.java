package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

import com.google.cloud.datastore.Entity;

public class UserInfoStudent extends UserInfo {

    private final String studentNumber;
    private final String course;
    private final String year;
    private final String credits;
    private final String average;

    private UserInfoStudent(Entity user, Entity student) {
        super(user);
        this.studentNumber = handleNull(student.getString(DatastoreTypes.STUDENT_NUM_ATTR));
        this.course = handleNull(student.getString(DatastoreTypes.COURSE_STUDENT_ATTR));
        this.year = handleNull(student.getString(DatastoreTypes.YEAR_STUDENT_ATTR));
        this.credits = handleNull(student.getString(DatastoreTypes.CREDITS_STUDENT_ATTR));
        this.average = handleNull(student.getString(DatastoreTypes.AVERAGE_STUDENT_ATTR));
    }

    public static UserInfoStudent createUserInfoStudent(Entity user, Entity student) {
        return new UserInfoStudent(user, student);
    }

    @Override
    public String toString() {
        return "UserInfoStudent{" +
                "studentNumber='" + studentNumber + '\'' +
                ", course='" + course + '\'' +
                ", year='" + year + '\'' +
                ", credits='" + credits + '\'' +
                ", average='" + average + '\'' +
                '}';
    }
}