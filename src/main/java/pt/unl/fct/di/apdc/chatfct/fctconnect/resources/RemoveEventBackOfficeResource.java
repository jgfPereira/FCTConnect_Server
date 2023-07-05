package pt.unl.fct.di.apdc.chatfct.fctconnect.resources;

import com.google.cloud.datastore.*;
import com.google.gson.Gson;
import io.jsonwebtoken.JwtException;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.*;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DELETE;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.logging.Logger;

@Path("/removeevent")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class RemoveEventBackOfficeResource {

    private static final Logger LOG = Logger.getLogger(RemoveEventBackOfficeResource.class.getName());
    private static final String ID_PATH_PARAM = "id";
    private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    private final KeyFactory backOfficeUserKeyFactory = datastore.newKeyFactory().setKind(DatastoreTypes.BACK_OFFICE_USER_TYPE);
    private final KeyFactory eventKeyFactory = datastore.newKeyFactory().setKind(DatastoreTypes.EVENT_TYPE);
    private final MemcacheUtils memcacheBackOfficeUsers = MemcacheUtils.getMemcache(MemcacheUtils.BACK_OFFICE_USER_NAMESPACE);
    private final MemcacheUtils memcacheEvents = MemcacheUtils.getMemcache(MemcacheUtils.EVENTS_NAMESPACE);
    private final Gson gson = new Gson();

    public RemoveEventBackOfficeResource() {
    }

    private Entity getBackOfficeUserCached(String username) {
        final String key = String.format(MemcacheUtils.BACK_OFFICE_USER_ENTITY_KEY, username);
        return memcacheBackOfficeUsers.get(key, Entity.class);
    }

    private Entity getEventCached(String id) {
        final String key = String.format(MemcacheUtils.EVENT_ENTITY_KEY, id);
        return memcacheEvents.get(key, Entity.class);
    }

    private boolean isCached(Entity e) {
        return e != null;
    }

    @DELETE
    @Path("/{id}")
    public Response doRemoveEvent(@PathParam(ID_PATH_PARAM) String id, @Context HttpHeaders headers, @Context HttpServletRequest request) {
        LOG.fine("Backoffice user attempt to remove event");
        final String token = TokenUtils.extractTokenFromHeaders(request);
        TokenInfo tokenInfo = verifyToken(token);
        if (tokenInfo == null) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(gson.toJson("Invalid credentials")).build();
        }
        LOG.fine("Valid token. Proceeding...");
        final String username = tokenInfo.getUsername();
        final String role = tokenInfo.getRole();
        final Response checkId = checkData(id);
        if (checkId != null) {
            return checkId;
        }
        Key backOfficeUserKey = backOfficeUserKeyFactory.newKey(username);
        Key eventKey = eventKeyFactory.newKey(id);
        Transaction txn = datastore.newTransaction();
        try {
            Entity backOfficeUserOnDB;
            final Entity userCached = getBackOfficeUserCached(username);
            final boolean isUserCached = isCached(userCached);
            if (isUserCached) {
                backOfficeUserOnDB = userCached;
            } else {
                backOfficeUserOnDB = txn.get(backOfficeUserKey);
                final Response checkBackOfficeUserOnDB = checkBackOfficeUserOnDB(backOfficeUserOnDB);
                if (checkBackOfficeUserOnDB != null) {
                    txn.rollback();
                    return checkBackOfficeUserOnDB;
                }
                memcacheBackOfficeUsers.put(String.format(MemcacheUtils.BACK_OFFICE_USER_ENTITY_KEY, username), backOfficeUserOnDB);
            }
            final Response canRemoveEvent = canRemoveEvent(role);
            if (canRemoveEvent != null) {
                txn.rollback();
                return canRemoveEvent;
            }
            Entity eventOnDB;
            final Entity eventCached = getEventCached(id);
            final boolean isEventCached = isCached(eventCached);
            if (isEventCached) {
                eventOnDB = eventCached;
            } else {
                eventOnDB = txn.get(eventKey);
                final Response checkEventOnDB = checkEventOnDB(eventOnDB);
                if (checkEventOnDB != null) {
                    txn.rollback();
                    return checkEventOnDB;
                }
            }
            memcacheEvents.delete(String.format(MemcacheUtils.EVENT_ENTITY_KEY, id));
            txn.delete(eventKey);
            txn.commit();
            LOG.info("Event was removed - " + id);
            return Response.ok(gson.toJson("Event was removed - " + id)).build();
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

    private Response checkData(String id) {
        if (id == null || id.isBlank()) {
            LOG.fine("Invalid data: id is invalid");
            return Response.status(Response.Status.BAD_REQUEST).entity(gson.toJson("Bad Request - invalid data")).build();
        }
        return null;
    }

    private Response checkBackOfficeUserOnDB(Entity backOfficeUserOnDB) {
        if (backOfficeUserOnDB == null) {
            LOG.fine("Backoffice user does not exist");
            return Response.status(Response.Status.NOT_FOUND).entity(gson.toJson("Not Found - Backoffice user does not exist")).build();
        }
        return null;
    }

    private Response canRemoveEvent(String role) {
        final boolean canRemoveEvent = BackOfficeRolePermissions.canRemoveEvent(role);
        if (!canRemoveEvent) {
            LOG.fine("Dont have permission to remove event");
            return Response.status(Response.Status.FORBIDDEN).entity(gson.toJson("Forbidden - Dont have permission to remove event")).build();
        }
        return null;
    }

    private Response checkEventOnDB(Entity eventOnDB) {
        if (eventOnDB == null) {
            LOG.fine("Event does not exist");
            return Response.status(Response.Status.NOT_FOUND).entity(gson.toJson("Not Found - Event does not exist")).build();
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