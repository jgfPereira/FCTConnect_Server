package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

import com.google.cloud.datastore.Entity;

public class UserInfoEmployee extends UserInfo {

    private final String department;
    private final String jobTitle;

    private UserInfoEmployee(Entity user, Entity employee) {
        super(user);
        this.department = handleNull(employee.getString(DatastoreTypes.DEPARTMENT_ATTR));
        this.jobTitle = handleNull(employee.getString(DatastoreTypes.JOB_TITLE_EMPLOYEE_ATTR));
    }

    public static UserInfoEmployee createUserInfoEmployee(Entity user, Entity employee) {
        return new UserInfoEmployee(user, employee);
    }

    @Override
    public String toString() {
        return "UserInfoEmployee{" +
                "department='" + department + '\'' +
                ", jobTitle='" + jobTitle + '\'' +
                '}';
    }
}