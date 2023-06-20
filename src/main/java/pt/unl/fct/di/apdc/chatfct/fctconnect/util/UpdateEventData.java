package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

import java.util.ArrayList;
import java.util.List;

public class UpdateEventData {

    public String id;
    public UpdateEntry[] updateEntries;

    public UpdateEventData() {
    }

    public boolean validateData() {
        return !(id == null || this.updateEntries == null || this.updateEntries.length == 0);
    }

    public void removeDuplicates() {
        List<UpdateEntry> res = new ArrayList<>();
        for (UpdateEntry entry : updateEntries) {
            if (!res.contains(entry)) {
                res.add(entry);
            }
        }
        updateEntries = res.toArray(new UpdateEntry[res.size()]);
    }
}