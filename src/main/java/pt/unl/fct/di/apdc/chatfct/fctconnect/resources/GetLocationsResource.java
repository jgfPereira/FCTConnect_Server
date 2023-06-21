package pt.unl.fct.di.apdc.chatfct.fctconnect.resources;

import com.google.cloud.datastore.*;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.DatastoreTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class GetLocationsResource {

    private static final Logger LOG = Logger.getLogger(GetLocationsResource.class.getName());
    private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    private final KeyFactory locationsKeyFactory = datastore.newKeyFactory().setKind(DatastoreTypes.LOCATIONS_TYPE);

    public GetLocationsResource() {
    }

    public List<String> getLocations() {
        Key key = locationsKeyFactory.newKey(DatastoreTypes.LOCATIONS_TYPE_KEY);
        Transaction txn = datastore.newTransaction();
        try {
            Entity locationsOnDB = txn.get(key);
            final boolean checkLocationsOnDB = checkLocationsOnDB(locationsOnDB);
            if (!checkLocationsOnDB) {
                txn.rollback();
                return null;
            }
            final List<String> allLocations = getLocationsList(locationsOnDB);
            txn.commit();
            LOG.fine("Locations fetching was successful");
            LOG.severe(allLocations.toString());
            return allLocations;
        } catch (Exception e) {
            txn.rollback();
            LOG.severe(e.getLocalizedMessage());
            return null;
        } finally {
            if (txn.isActive()) {
                txn.rollback();
                return null;
            }
        }
    }

    private boolean checkLocationsOnDB(Entity locationsOnDB) {
        if (locationsOnDB == null) {
            LOG.fine("Locations does not exist");
            return false;
        }
        return true;
    }

    private List<String> getLocationsList(Entity locationsOnDB) {
        List<Value<?>> list = locationsOnDB.getList(DatastoreTypes.EVENT_ACL_ATTR);
        List<String> res = new ArrayList<>();
        for (Value<?> v : list) {
            res.add((String) v.get());
        }
        return res;
    }
}