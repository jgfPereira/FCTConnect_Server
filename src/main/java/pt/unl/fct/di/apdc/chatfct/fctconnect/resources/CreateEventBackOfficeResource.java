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
import java.util.List;
import java.util.logging.Logger;

@Path("/createevent")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class CreateEventBackOfficeResource {

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
        data.removeDuplicates();
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
        } else if (!data.checkDatesValidity()) {
            LOG.fine("Invalid data: startDate >= endDate");
            return Response.status(Response.Status.BAD_REQUEST).entity(gson.toJson("Bad Request - start date has to be before end date")).build();
        } else if (!data.areDatesOnFuture()) {
            LOG.fine("Invalid data: dates have to be in the future");
            return Response.status(Response.Status.BAD_REQUEST).entity(gson.toJson("Bad Request - dates have to be in the future")).build();
        } else if (!data.validateAclTags()) {
            LOG.fine("Invalid data: unrecognized acl tag(s)");
            return Response.status(Response.Status.BAD_REQUEST).entity(gson.toJson("Bad Request - unrecognized acl tag(s)")).build();
        } else if (!isLocationValid(data.location)) {
            LOG.fine("Invalid data: unrecognized location");
            return Response.status(Response.Status.BAD_REQUEST).entity(gson.toJson("Bad Request - unrecognized location")).build();
        }
        return null;
    }

    private boolean isLocationValid(String location) {
        final List<String> allLocations = new GetLocationsResource().getLocations();
        for (String l : allLocations) {
            if (l.equals(location))
                return true;
        }
        return false;
    }

    private Response checkBackOfficeUserOnDB(Entity backOfficeUserOnDB) {
        if (backOfficeUserOnDB == null) {
            LOG.fine("Backoffice user does not exist");
            return Response.status(Response.Status.NOT_FOUND).entity(gson.toJson("Not Found - Backoffice user does not exist")).build();
        }
        return null;
    }

    private Response canCreateEvent(String role) {
        final boolean canCreateEvent = BackOfficeRolePermissions.canCreateEvent(role);
        if (!canCreateEvent) {
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
                .set(DatastoreTypes.EVENT_LOCATION_ATTR, data.location)
                .set(DatastoreTypes.EVENT_DESCRIPTION_ATTR, data.description)
                .set(DatastoreTypes.EVENT_ACL_ATTR, ListValue.of(data.getAclFirst(), data.getAclRest()))
                .set(DatastoreTypes.EVENT_START_DATE_ATTR, Timestamp.parseTimestamp(data.startDate))
                .set(DatastoreTypes.EVENT_END_DATE_ATTR, Timestamp.parseTimestamp(data.endDate));
        return eb.build();
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