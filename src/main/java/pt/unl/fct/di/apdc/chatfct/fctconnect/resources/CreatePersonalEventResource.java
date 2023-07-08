package pt.unl.fct.di.apdc.chatfct.fctconnect.resources;

import com.google.cloud.Timestamp;
import com.google.cloud.datastore.*;
import com.google.gson.Gson;
import io.jsonwebtoken.JwtException;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.*;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.UUID;
import java.util.logging.Logger;

@Path("/createpersonalevent")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class CreatePersonalEventResource {

    private static final Logger LOG = Logger.getLogger(CreatePersonalEventResource.class.getName());
    private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    private final KeyFactory userKeyFactory = datastore.newKeyFactory().setKind(DatastoreTypes.USER_TYPE);
    private final MemcacheUtils memcacheUsers = MemcacheUtils.getMemcache(MemcacheUtils.USER_NAMESPACE);
    private final MemcacheUtils memcachePersonalEvents = MemcacheUtils.getMemcache(MemcacheUtils.PERSONAL_EVENTS_NAMESPACE);
    private final Gson gson = new Gson();

    public CreatePersonalEventResource() {
    }

    private Entity getUserCached(String username) {
        final String key = String.format(MemcacheUtils.USER_ENTITY_KEY, username);
        return memcacheUsers.get(key, Entity.class);
    }

    private boolean isCached(Entity e) {
        return e != null;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response doCreatePersonalEvent(CreatePersonalEventData data, @Context HttpHeaders headers, @Context HttpServletRequest request) {
        LOG.fine("User attempt to create personal event");
        final String token = TokenUtils.extractTokenFromHeaders(request);
        TokenInfo tokenInfo = verifyToken(token);
        if (tokenInfo == null) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(gson.toJson("Invalid credentials")).build();
        }
        LOG.fine("Valid token. Proceeding...");
        final String username = tokenInfo.getUsername();
        final Response checkData = checkData(data);
        if (checkData != null) {
            return checkData;
        }
        final String id = generatePersonalEventId();
        Key userKey = userKeyFactory.newKey(username);
        Key personalEventKey = createPersonalEventKey(username, id);
        Transaction txn = datastore.newTransaction();
        try {
            Entity userOnDB;
            final Entity userCached = getUserCached(username);
            final boolean isUserCached = isCached(userCached);
            if (isUserCached) {
                userOnDB = userCached;
            } else {
                userOnDB = txn.get(userKey);
                final Response checkUserOnDB = checkUserOnDB(userOnDB);
                if (checkUserOnDB != null) {
                    txn.rollback();
                    return checkUserOnDB;
                }
                memcacheUsers.put(String.format(MemcacheUtils.USER_ENTITY_KEY, username), userOnDB);
            }
            final Entity personalEventCreated = createPersonalEvent(personalEventKey, data);
            memcachePersonalEvents.put(String.format(MemcacheUtils.PERSONAL_EVENT_ENTITY_KEY, id), personalEventCreated);
            txn.put(personalEventCreated);
            txn.commit();
            LOG.info("Personal event was created - " + id);
            return Response.ok(gson.toJson(id)).build();
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

    private Response checkData(CreatePersonalEventData data) {
        if (data == null || !data.validateData()) {
            LOG.fine("Invalid data: at least one required field is null");
            return Response.status(Response.Status.BAD_REQUEST).entity(gson.toJson("Bad Request - invalid data")).build();
        } else if (!data.checkDatesValidity()) {
            LOG.fine("Invalid data: startDate >= endDate");
            return Response.status(Response.Status.BAD_REQUEST).entity(gson.toJson("Bad Request - start date has to be before end date")).build();
        } else if (!data.areDatesOnFuture()) {
            LOG.fine("Invalid data: dates have to be in the future");
            return Response.status(Response.Status.BAD_REQUEST).entity(gson.toJson("Bad Request - dates have to be in the future")).build();
        }
        return null;
    }

    private String generatePersonalEventId() {
        return UUID.randomUUID().toString();
    }

    private Key createPersonalEventKey(String username, String id) {
        return datastore.newKeyFactory().setKind(DatastoreTypes.PERSONAL_EVENT_TYPE)
                .addAncestors(PathElement.of(DatastoreTypes.USER_TYPE, username))
                .newKey(id);
    }

    private Response checkUserOnDB(Entity userOnDB) {
        if (userOnDB == null) {
            LOG.fine("User does not exist");
            return Response.status(Response.Status.NOT_FOUND).entity(gson.toJson("Not Found - user does not exist")).build();
        }
        return null;
    }

    private Entity createPersonalEvent(Key personalEventKey, CreatePersonalEventData data) {
        Entity.Builder eb = Entity.newBuilder(personalEventKey)
                .set(DatastoreTypes.EVENT_NAME_ATTR, data.name)
                .set(DatastoreTypes.EVENT_START_DATE_ATTR, Timestamp.parseTimestamp(data.startDate))
                .set(DatastoreTypes.EVENT_END_DATE_ATTR, Timestamp.parseTimestamp(data.endDate))
                .set(DatastoreTypes.EVENT_COLOR_ATTR, data.color);
        setWithNull(eb, DatastoreTypes.EVENT_LOCATION_ATTR, data.location);
        setWithNull(eb, DatastoreTypes.EVENT_DESCRIPTION_ATTR, data.description);
        setWithNull(eb, DatastoreTypes.EVENT_RECURRENCE_RULE_ATTR, data.recurrenceRule);
        return eb.build();
    }

    private void setWithNull(Entity.Builder eb, String propertyName, String value) {
        if (value == null) {
            eb.setNull(propertyName);
        } else {
            eb.set(propertyName, value);
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
}