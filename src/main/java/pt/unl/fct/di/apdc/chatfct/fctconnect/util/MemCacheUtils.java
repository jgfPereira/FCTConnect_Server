package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

import com.google.appengine.api.memcache.*;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public final class MemCacheUtils {

    public static final String USER_ENTITY_KEY = "user-entity-%s";
    public static final String USER_LOGIN_REG_KEY = "user-loginReg-%s";
    public static final String USER_NAMESPACE = "users";
    public static final String LOGIN_REGISTRY_NAMESPACE = "loginRegs";
    private static final long EXPIRATION_TIME = 1L;
    private final AsyncMemcacheService memcache;

    private MemCacheUtils(String namespace) {
        memcache = createMemCacheService(namespace);
    }

    public static MemCacheUtils getMemCache(String namespace) {
        return new MemCacheUtils(namespace);
    }

    private static AsyncMemcacheService createMemCacheService(final String namespace) {
        final AsyncMemcacheService m = MemcacheServiceFactory.getAsyncMemcacheService(namespace);
        m.setErrorHandler(ErrorHandlers.getConsistentLogAndContinue(Level.INFO));
        return m;
    }

    private static <T> T wait(Future<T> future) {
        try {
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            return null;
        }
    }

    public Boolean put(String key, Object value) {
        final Future<Boolean> future = memcache.put(key, value,
                Expiration.byDeltaSeconds((int) TimeUnit.HOURS.toSeconds(EXPIRATION_TIME)),
                MemcacheService.SetPolicy.SET_ALWAYS);
        return wait(future);
    }

    public <T> T get(String key, Class<T> clazz) {
        final Future<Object> future = memcache.get(key);
        final Object value = wait(future);
        return clazz.isInstance(value) ? clazz.cast(value) : null;
    }

    public Boolean delete(String key) {
        final Future<Boolean> future = memcache.delete(key);
        return wait(future);
    }
}