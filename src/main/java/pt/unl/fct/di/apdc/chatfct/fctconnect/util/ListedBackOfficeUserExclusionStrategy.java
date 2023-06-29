package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;

public class ListedBackOfficeUserExclusionStrategy implements ExclusionStrategy {

    private static final String PHOTO_ATTR_CONFLICT = "photo";

    @Override
    public boolean shouldSkipField(FieldAttributes f) {
        return f.getDeclaringClass().equals(ListedUser.class) && f.getName().equals(PHOTO_ATTR_CONFLICT);
    }

    @Override
    public boolean shouldSkipClass(Class<?> clazz) {
        return false;
    }
}