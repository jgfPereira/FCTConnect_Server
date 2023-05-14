package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

import java.util.Objects;

public class UpdateEntry {

    public final String propertyName;
    public final String newValue;

    public UpdateEntry(String propertyName, String newValue) {
        this.propertyName = propertyName;
        this.newValue = newValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UpdateEntry that = (UpdateEntry) o;
        return Objects.equals(propertyName, that.propertyName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(propertyName);
    }
}