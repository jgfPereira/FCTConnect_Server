package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

import com.google.cloud.datastore.Entity;
import com.google.cloud.datastore.Value;

import java.util.ArrayList;
import java.util.List;

public class ListedBackOfficeEvent extends ListedEvent {

    private final String id;
    private final List<String> acl;

    private ListedBackOfficeEvent(Entity event) {
        super(event);
        id = event.getKey().getName();
        acl = getAcl(event);
    }

    public static ListedBackOfficeEvent createListedBackOfficeEvent(Entity event) {
        return new ListedBackOfficeEvent(event);
    }

    private List<String> getAcl(Entity event) {
        List<Value<?>> list = event.getList(DatastoreTypes.EVENT_ACL_ATTR);
        List<String> res = new ArrayList<>();
        for (Value<?> v : list) {
            res.add((String) v.get());
        }
        return res;
    }

    @Override
    public String toString() {
        return "ListedBackOfficeEvent{" +
                "id='" + id + '\'' +
                ", acl=" + acl +
                '}';
    }
}