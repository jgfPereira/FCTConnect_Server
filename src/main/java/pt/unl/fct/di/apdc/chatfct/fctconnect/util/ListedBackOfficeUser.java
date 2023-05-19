package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

import com.google.cloud.datastore.Entity;

public class ListedBackOfficeUser extends ListedUser {

    private final String username;
    private final String state;

    private ListedBackOfficeUser(Entity user) {
        super(user);
        username = user.getKey().getName();
        state = user.getString(DatastoreTypes.STATE_ATTR);
    }

    public static ListedBackOfficeUser createListedBackOfficeUser(Entity user) {
        return new ListedBackOfficeUser(user);
    }

    @Override
    public String toString() {
        return "ListedBackOfficeUser{" +
                "username='" + username + '\'' +
                ", state='" + state + '\'' +
                '}';
    }
}