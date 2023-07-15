package pt.unl.fct.di.apdc.chatfct.fctconnect.resources;

import com.google.cloud.datastore.*;
import com.google.gson.Gson;
import io.jsonwebtoken.JwtException;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.*;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DELETE;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.logging.Logger;

@Path("/removedevicetoken")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class RemoveDeviceTokenResource {
    private static final Logger LOG = Logger.getLogger(RemoveDeviceTokenResource.class.getName());
    private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    private final KeyFactory userKeyFactory = datastore.newKeyFactory().setKind(DatastoreTypes.USER_TYPE);
    private final MemcacheUtils memcacheUsers = MemcacheUtils.getMemcache(MemcacheUtils.USER_NAMESPACE);
    private final Gson gson = new Gson();

    public RemoveDeviceTokenResource() {
    }

    private Entity getUserCached(String username) {
        final String key = String.format(MemcacheUtils.USER_ENTITY_KEY, username);
        return memcacheUsers.get(key, Entity.class);
    }

    private boolean isCached(Entity e) {
        return e != null;
    }

    @DELETE
    public Response doRemoveDeviceToken(@Context HttpHeaders headers, @Context HttpServletRequest request) {
        LOG.fine("User attempt to remove device token");
        final String token = TokenUtils.extractTokenFromHeaders(request);
        TokenInfo tokenInfo = verifyToken(token);
        if (tokenInfo == null) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(gson.toJson("Invalid credentials")).build();
        }
        LOG.fine("Valid token. Proceeding...");
        final String username = tokenInfo.getUsername();
        Key usernameKey = userKeyFactory.newKey(username);
        Transaction txn = datastore.newTransaction();
        try {
            Entity userOnDB;
            final Entity userCached = getUserCached(username);
            final boolean isUserCached = isCached(userCached);
            if (isUserCached) {
                userOnDB = userCached;
            } else {
                userOnDB = txn.get(usernameKey);
                Response checkUserOnDB = checkUserOnDB(userOnDB);
                if (checkUserOnDB != null) {
                    txn.rollback();
                    return checkUserOnDB;
                }
                memcacheUsers.put(String.format(MemcacheUtils.USER_ENTITY_KEY, username), userOnDB);
            }
            final Response deleteDeviceTokenDBRequest = RestClientUtils.deleteDeviceToken(username);
            if (deleteDeviceTokenDBRequest.getStatus() == Response.Status.OK.getStatusCode()) {
                txn.commit();
                LOG.fine("Device token was removed");
            } else {
                txn.rollback();
                LOG.fine("Server Error");
            }
            return deleteDeviceTokenDBRequest;
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

    private Response checkUserOnDB(Entity userOnDB) {
        if (userOnDB == null) {
            LOG.fine("User does not exist");
            return Response.status(Response.Status.NOT_FOUND).entity(gson.toJson("Not Found - User does not exist")).build();
        }
        return null;
    }

    private TokenInfo verifyToken(final String token) {
        try {
            return TokenUtils.verifyToken(token);
        } catch (JwtException ex) {
            LOG.warning("Invalid token --> " + ex.getLocalizedMessage());
            return null;
        }
    }
}