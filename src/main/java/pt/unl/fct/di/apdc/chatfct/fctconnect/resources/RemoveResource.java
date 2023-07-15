package pt.unl.fct.di.apdc.chatfct.fctconnect.resources;

import com.google.cloud.datastore.*;
import com.google.gson.Gson;
import io.jsonwebtoken.JwtException;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.DatastoreTypes;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.MemcacheUtils;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.TokenInfo;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.TokenUtils;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DELETE;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

@Path("/remove")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class RemoveResource {

    private static final Logger LOG = Logger.getLogger(RemoveResource.class.getName());
    private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    private final KeyFactory userKeyFactory = datastore.newKeyFactory().setKind(DatastoreTypes.USER_TYPE);
    private final MemcacheUtils memcacheUsers = MemcacheUtils.getMemcache(MemcacheUtils.USER_NAMESPACE);
    private final MemcacheUtils memcacheSpecificUsers = MemcacheUtils.getMemcache(MemcacheUtils.SPECIFIC_USERS_NAMESPACE);
    private final MemcacheUtils memcacheLoginRegs = MemcacheUtils.getMemcache(MemcacheUtils.LOGIN_REGISTRY_NAMESPACE);
    private final Gson gson = new Gson();

    public RemoveResource() {
    }

    private Entity getUserCached(String username) {
        final String key = String.format(MemcacheUtils.USER_ENTITY_KEY, username);
        return memcacheUsers.get(key, Entity.class);
    }

    private Entity getLoginRegCached(String username) {
        final String key = String.format(MemcacheUtils.USER_LOGIN_REG_KEY, username);
        return memcacheLoginRegs.get(key, Entity.class);
    }

    private boolean isCached(Entity e) {
        return e != null;
    }

    @DELETE
    public Response doRemove(@Context HttpHeaders headers, @Context HttpServletRequest request) {
        LOG.fine("User attempt to remove user");
        final String token = TokenUtils.extractTokenFromHeaders(request);
        TokenInfo tokenInfo = verifyToken(token);
        if (tokenInfo == null) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(gson.toJson("Invalid credentials")).build();
        }
        LOG.fine("Valid token. Proceeding...");
        final String username = tokenInfo.getUsername();
        final String role = tokenInfo.getRole();
        Key key = userKeyFactory.newKey(username);
        Transaction txn = datastore.newTransaction();
        try {
            Entity user;
            final Entity userCached = getUserCached(username);
            final boolean isUserCached = isCached(userCached);
            if (isUserCached) {
                user = userCached;
            } else {
                user = txn.get(key);
                final Response checkUserOnDB = checkUserOnDB(user);
                if (checkUserOnDB != null) {
                    txn.rollback();
                    return checkUserOnDB;
                }
            }
            Key specificUserKey = getSpecificUserKey(username, role);
            txn.delete(specificUserKey);
            memcacheSpecificUsers.delete(String.format(MemcacheUtils.SPECIFIC_USER_ENTITY_KEY, username));
            Key loginRegistryKey = getLoginRegistryKey(username);
            Entity loginRegistry;
            final Entity loginRegCached = getLoginRegCached(username);
            final boolean isLoginRegCached = isCached(loginRegCached);
            if (isLoginRegCached) {
                loginRegistry = loginRegCached;
                memcacheLoginRegs.delete(String.format(MemcacheUtils.USER_LOGIN_REG_KEY, username));
            } else {
                loginRegistry = txn.get(loginRegistryKey);
            }
            final boolean isLoginRegistryOnDB = checkLoginRegistryOnDB(loginRegistry);
            if (isLoginRegistryOnDB) {
                txn.delete(loginRegistryKey);
                Query<Entity> loginLogsQuery = getLoginLogsQuery(key);
                QueryResults<Entity> loginLogs = txn.run(loginLogsQuery);
                Key[] keysToRemove = removeLoginLogs(loginLogs);
                txn.delete(keysToRemove);
            }
            memcacheUsers.delete(String.format(MemcacheUtils.USER_ENTITY_KEY, username));
            txn.delete(key);
            txn.commit();
            TokenUtils.revokeToken(tokenInfo.getTokenID());
            LOG.fine("Remove done: " + username);
            return Response.ok(gson.toJson("Remove done")).build();
        } catch (Exception e) {
            txn.rollback();
            LOG.severe(e.getLocalizedMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(gson.toJson("Server Error")).build();
        } finally {
            if (txn.isActive()) {
                txn.rollback();
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(gson.toJson("Server Error")).build();
            }
        }
    }

    private TokenInfo verifyToken(final String token) {
        try {
            return TokenUtils.verifyToken(token);
        } catch (JwtException ex) {
            LOG.warning("Invalid token --> " + ex.getLocalizedMessage());
            return null;
        }
    }

    private Response checkUserOnDB(Entity usernameOnDB) {
        if (usernameOnDB == null) {
            LOG.fine("User does not exist");
            return Response.status(Response.Status.NOT_FOUND).entity(gson.toJson("Not Found - User does not exist")).build();
        }
        return null;
    }

    private Key getSpecificUserKey(String username, String role) {
        return datastore.newKeyFactory().setKind(DatastoreTypes.formatRoleType(role))
                .addAncestors(PathElement.of(DatastoreTypes.USER_TYPE, username))
                .newKey(username);
    }

    private Key getLoginRegistryKey(String username) {
        return datastore.newKeyFactory().setKind(DatastoreTypes.LOGIN_REGISTRY_TYPE)
                .addAncestors(PathElement.of(DatastoreTypes.USER_TYPE, username))
                .newKey(DatastoreTypes.LOGIN_REGISTRY_KEY);
    }

    private boolean checkLoginRegistryOnDB(Entity e) {
        return e != null;
    }

    private Query<Entity> getLoginLogsQuery(Key userKey) {
        return Query.newEntityQueryBuilder().setKind(DatastoreTypes.LOGIN_LOG_TYPE)
                .setFilter(StructuredQuery.PropertyFilter
                        .hasAncestor(userKey))
                .build();
    }

    private Key[] removeLoginLogs(QueryResults<Entity> loginLogs) {
        List<Key> keys = new ArrayList<>();
        loginLogs.forEachRemaining(log -> keys.add(log.getKey()));
        return keys.toArray(new Key[keys.size()]);
    }
}