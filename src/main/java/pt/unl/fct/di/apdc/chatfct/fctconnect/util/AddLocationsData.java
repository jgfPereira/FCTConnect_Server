package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AddLocationsData {

    private static final int LOCATIONS_REST_START_INDEX = 1;
    public String[] locations;

    public AddLocationsData() {
    }

    public boolean validateData() {
        return !(locations == null || locations.length == 0);
    }

    public void removeDuplicatesAndFormat() {
        List<String> res = new ArrayList<>();
        for (String loc : locations) {
            loc = loc.toUpperCase().trim();
            if (!res.contains(loc)) {
                res.add(loc);
            }
        }
        locations = res.toArray(new String[res.size()]);
    }

    public String[] getLocationsRest() {
        return Arrays.copyOfRange(locations, LOCATIONS_REST_START_INDEX, locations.length);
    }

    public String getLocationsFirst() {
        return locations[0];
    }
}