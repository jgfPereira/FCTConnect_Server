package pt.unl.fct.di.apdc.chatfct.fctconnect.resources;

import com.google.cloud.datastore.*;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.DatastoreTypes;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.MemcacheUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class GetLocationsResource {

    private static final Logger LOG = Logger.getLogger(GetLocationsResource.class.getName());
    private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    private final KeyFactory locationsKeyFactory = datastore.newKeyFactory().setKind(DatastoreTypes.LOCATIONS_TYPE);
    private final MemcacheUtils memcacheLocations = MemcacheUtils.getMemcache(MemcacheUtils.LOCATIONS_NAMESPACE);

    public GetLocationsResource() {
    }

    private Entity getLocationsCached(String locsKey) {
        final String key = String.format(MemcacheUtils.LOCATIONS_ENTITY_KEY, locsKey);
        return memcacheLocations.get(key, Entity.class);
    }

    private boolean isCached(Entity e) {
        return e != null;
    }

    public List<String> getLocations() {
        Key key = locationsKeyFactory.newKey(DatastoreTypes.LOCATIONS_TYPE_KEY);
        Transaction txn = datastore.newTransaction();
        try {
            Entity locationsOnDB;
            final Entity locationsCached = getLocationsCached(DatastoreTypes.LOCATIONS_TYPE_KEY);
            final boolean isLocationsCached = isCached(locationsCached);
            if (isLocationsCached) {
                locationsOnDB = locationsCached;
            } else {
                locationsOnDB = txn.get(key);
                final boolean checkLocationsOnDB = checkLocationsOnDB(locationsOnDB);
                if (!checkLocationsOnDB) {
                    txn.rollback();
                    return null;
                }
                memcacheLocations.put(String.format(MemcacheUtils.LOCATIONS_ENTITY_KEY, DatastoreTypes.LOCATIONS_TYPE_KEY), locationsOnDB);
            }
            final List<String> allLocations = getLocationsList(locationsOnDB);
            txn.commit();
            LOG.fine("Locations fetching was successful");
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
        List<Value<?>> list = locationsOnDB.getList(DatastoreTypes.LOCATIONS_PLACES_ATTR);
        List<String> res = new ArrayList<>();
        for (Value<?> v : list) {
            res.add((String) v.get());
        }
        return res;
    }
}