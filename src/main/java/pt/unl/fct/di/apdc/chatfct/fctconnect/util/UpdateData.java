package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

import java.util.ArrayList;
import java.util.List;

public class UpdateData {
    public String updatedUsername;
    public UpdateEntry[] updateEntries;


    public UpdateData() {
    }

    public boolean validateData() {
        return !(this.updatedUsername == null || this.updateEntries == null || this.updateEntries.length == 0);
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

    public void formatVisibilityProperty() {
        for (int i = 0; i < updateEntries.length; i++) {
            UpdateEntry entry = updateEntries[i];
            if (entry.propertyName.equals(DatastoreTypes.VISIBILITY_ATTR)) {
                updateEntries[i] = new UpdateEntry(entry.propertyName, entry.newValue.toUpperCase().trim());
            }
        }
    }

    public boolean isSameUser(String username) {
        return username.equals(this.updatedUsername);
    }
}