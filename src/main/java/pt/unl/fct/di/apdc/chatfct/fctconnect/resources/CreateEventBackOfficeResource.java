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
import java.util.logging.Logger;

@Path("/createevent")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class CreateEventBackOfficeResource {

    private static final String START_OF_DAY_UTC = "T00:00:00Z";
    private static final Logger LOG = Logger.getLogger(CreateEventBackOfficeResource.class.getName());
    private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    private final KeyFactory backOfficeUserKeyFactory = datastore.newKeyFactory().setKind(DatastoreTypes.BACK_OFFICE_USER_TYPE);
    private final KeyFactory eventKeyFactory = datastore.newKeyFactory().setKind(DatastoreTypes.EVENT_TYPE);
    private final Gson gson = new Gson();

    public CreateEventBackOfficeResource() {
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response doCreateEvent(CreateEventData data, @Context HttpHeaders headers, @Context HttpServletRequest request) {
        LOG.fine("Backoffice user attempt to create event");
        final String token = TokenUtils.extractTokenFromHeaders(request);
        TokenInfo tokenInfo = verifyToken(token);
        if (tokenInfo == null) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(gson.toJson("Invalid credentials")).build();
        }
        LOG.fine("Valid token. Proceeding...");
        final String username = tokenInfo.getUsername();
        final String role = tokenInfo.getRole();
        final Response checkData = checkData(data);
        if (checkData != null) {
            return checkData;
        }
        data.removeDuplicatesAndFormat();
        Key backOfficeUserKey = backOfficeUserKeyFactory.newKey(username);
        Key eventKey = eventKeyFactory.newKey(data.id);
        Transaction txn = datastore.newTransaction();
        try {
            final Entity backOfficeUserOnDB = txn.get(backOfficeUserKey);
            final Response checkBackOfficeUserOnDB = checkBackOfficeUserOnDB(backOfficeUserOnDB);
            if (checkBackOfficeUserOnDB != null) {
                txn.rollback();
                return checkBackOfficeUserOnDB;
            }
            final Response canCreateEvent = canCreateEvent(role);
            if (canCreateEvent != null) {
                txn.rollback();
                return canCreateEvent;
            }
            final Entity eventOnDB = txn.get(eventKey);
            final Response checkEventOnDB = checkEventOnDB(eventOnDB);
            if (checkEventOnDB != null) {
                txn.rollback();
                return checkEventOnDB;
            }
            final Entity eventCreated = createEvent(eventKey, data);
            txn.put(eventCreated);
            txn.commit();
            LOG.info("Event was created - " + data.id);
            return Response.ok(gson.toJson("Event was created - " + data.id)).build();
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

    private Response checkData(CreateEventData data) {
        if (data == null || !data.validateData()) {
            LOG.fine("Invalid data: at least one required field is null");
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

    private Response canCreateEvent(String role) {
        final boolean canApproveAccount = BackOfficeRolePermissions.canCreateEvent(role);
        if (!canApproveAccount) {
            LOG.fine("Dont have permission to create event");
            return Response.status(Response.Status.FORBIDDEN).entity(gson.toJson("Forbidden - Dont have permission to create event")).build();
        }
        return null;
    }

    private Response checkEventOnDB(Entity eventOnDB) {
        if (eventOnDB != null) {
            LOG.fine("Event already exist");
            return Response.status(Response.Status.CONFLICT).entity(gson.toJson("Conflict - Event already exist")).build();
        }
        return null;
    }

    private Entity createEvent(Key eventKey, CreateEventData data) {
        Entity.Builder eb = Entity.newBuilder(eventKey)
                .set(DatastoreTypes.EVENT_NAME_ATTR, data.name)
                .set(DatastoreTypes.EVENT_COORD_X_ATTR, data.coordX)
                .set(DatastoreTypes.EVENT_COORD_Y_ATTR, data.coordY)
                .set(DatastoreTypes.EVENT_DESCRIPTION_ATTR, data.description)
                .set(DatastoreTypes.EVENT_ACL_ATTR, ListValue.of(data.getAclFirst(), data.getAclRest()));
        updateDateProperty(eb, DatastoreTypes.EVENT_START_DATE_ATTR, data.startDate);
        updateDateProperty(eb, DatastoreTypes.EVENT_END_DATE_ATTR, data.endDate);
        return eb.build();
    }

    private void updateDateProperty(Entity.Builder eb, String propertyName, String date) {
        final String dateWithTime = date + START_OF_DAY_UTC;
        eb.set(propertyName, Timestamp.parseTimestamp(dateWithTime));
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