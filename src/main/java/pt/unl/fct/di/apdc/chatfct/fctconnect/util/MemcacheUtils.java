package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

import com.google.appengine.api.memcache.ErrorHandlers;
import com.google.appengine.api.memcache.Expiration;
import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.MemcacheServiceFactory;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public final class MemcacheUtils {

    public static final String USER_ENTITY_KEY = "user-entity-%s";
    public static final String USER_LOGIN_REG_KEY = "user-loginReg-%s";
    public static final String USER_NAMESPACE = "users";
    public static final String LOGIN_REGISTRY_NAMESPACE = "loginRegs";
    public static final String BACK_OFFICE_USER_NAMESPACE = "backofficeUsers";
    public static final String BACK_OFFICE_USER_ENTITY_KEY = "bo-user-entity-%s";
    public static final String LOCATIONS_NAMESPACE = "locations";
    public static final String LOCATIONS_ENTITY_KEY = "locations-entity-%s";
    public static final String EVENTS_NAMESPACE = "events";
    public static final String EVENT_ENTITY_KEY = "event-entity-%s";
    private static final long EXPIRATION_TIME = 1L;
    private final MemcacheService memcache;

    private MemcacheUtils(String namespace) {
        memcache = createMemcacheService(namespace);
    }

    public static MemcacheUtils getMemcache(String namespace) {
        return new MemcacheUtils(namespace);
    }

    private static MemcacheService createMemcacheService(final String namespace) {
        final MemcacheService m = MemcacheServiceFactory.getMemcacheService(namespace);
        m.setErrorHandler(ErrorHandlers.getConsistentLogAndContinue(Level.INFO));
        return m;
    }

    public Boolean put(String key, Object value) {
        return memcache.put(key, value,
                Expiration.byDeltaSeconds((int) TimeUnit.HOURS.toSeconds(EXPIRATION_TIME)),
                MemcacheService.SetPolicy.SET_ALWAYS);
    }

    public <T> T get(String key, Class<T> clazz) {
        final Object value = memcache.get(key);
        return clazz.isInstance(value) ? clazz.cast(value) : null;
    }

    public Boolean delete(String key) {
        return memcache.delete(key);
    }
}