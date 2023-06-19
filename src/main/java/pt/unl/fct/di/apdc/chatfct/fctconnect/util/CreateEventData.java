package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CreateEventData {

    private static final int ACL_REST_START_INDEX = 1;
    public String id;
    public String name;
    public String coordX;
    public String coordY;
    public String description;
    public String startDate;
    public String endDate;
    public String[] acl;

    public CreateEventData() {
    }

    public boolean validateData() {
        return !(id == null || name == null || coordX == null || coordY == null
                || description == null || startDate == null || endDate == null
                || acl == null || acl.length == 0);
    }

    public void removeDuplicatesAndFormat() {
        List<String> res = new ArrayList<>();
        for (String tag : acl) {
            tag = tag.toUpperCase().trim();
            if (!res.contains(tag)) {
                res.add(tag);
            }
        }
        acl = res.toArray(new String[res.size()]);
    }

    public String[] getAclRest() {
        return Arrays.copyOfRange(acl, ACL_REST_START_INDEX, acl.length);
    }

    public String getAclFirst() {
        return acl[0];
    }
}