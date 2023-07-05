package pt.unl.fct.di.apdc.chatfct.fctconnect.resources;

import com.google.cloud.Timestamp;
import com.google.cloud.datastore.*;
import com.google.gson.Gson;
import io.jsonwebtoken.JwtException;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.*;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

@Path("/updateevent")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class UpdateEventBackOfficeResource {

    private static final String SEPARATOR = " ";
    private static final Logger LOG = Logger.getLogger(UpdateEventBackOfficeResource.class.getName());
    private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    private final KeyFactory backOfficeUserKeyFactory = datastore.newKeyFactory().setKind(DatastoreTypes.BACK_OFFICE_USER_TYPE);
    private final KeyFactory eventKeyFactory = datastore.newKeyFactory().setKind(DatastoreTypes.EVENT_TYPE);
    private final MemcacheUtils memcacheBackOfficeUsers = MemcacheUtils.getMemcache(MemcacheUtils.BACK_OFFICE_USER_NAMESPACE);
    private final MemcacheUtils memcacheEvents = MemcacheUtils.getMemcache(MemcacheUtils.EVENTS_NAMESPACE);
    private final Gson gson = new Gson();

    public UpdateEventBackOfficeResource() {
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

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public Response doUpdateEvent(UpdateEventData data, @Context HttpHeaders headers, @Context HttpServletRequest request) {
        LOG.fine("Backoffice user attempt to update event");
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
            final Response canUpdateEvent = canUpdateEvent(role);
            if (canUpdateEvent != null) {
                txn.rollback();
                return canUpdateEvent;
            }
            Entity eventOnDB;
            final Entity eventCached = getEventCached(data.id);
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
                memcacheEvents.put(String.format(MemcacheUtils.EVENT_ENTITY_KEY, data.id), eventOnDB);
            }
            final List<String> invalidFormatUpdates = new ArrayList<>();
            final Entity updatedEvent = updateEvent(eventOnDB, data, invalidFormatUpdates);
            if (didEventChanged(invalidFormatUpdates, data.updateEntries)) {
                txn.update(updatedEvent);
                memcacheEvents.put(String.format(MemcacheUtils.EVENT_ENTITY_KEY, data.id), updatedEvent);
            }
            txn.commit();
            LOG.info("Event was updated - if format checked out");
            return createResponseBasedOnUpdates(data, invalidFormatUpdates);
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

    private Response checkData(UpdateEventData data) {
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

    private Response canUpdateEvent(String role) {
        final boolean canUpdateEvent = BackOfficeRolePermissions.canUpdateEvent(role);
        if (!canUpdateEvent) {
            LOG.fine("Dont have permission to update event");
            return Response.status(Response.Status.FORBIDDEN).entity(gson.toJson("Forbidden - Dont have permission to update event")).build();
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

    private Entity updateEvent(Entity event, UpdateEventData data, List<String> invalidFormatUpdates) {
        Entity.Builder eb = Entity.newBuilder(event);
        for (UpdateEntry entry : data.updateEntries) {
            if (!checkPropertyFormat(entry.propertyName, entry.newValue)) {
                LOG.fine("Format of the new value is invalid for the property " + entry.propertyName);
                invalidFormatUpdates.add(entry.propertyName);
            } else {
                updateProperty(eb, entry.propertyName, entry.newValue);
            }
        }
        handleDateUpdates(eb, event, data, invalidFormatUpdates);
        return eb.build();
    }

    private void handleDateUpdates(Entity.Builder eb, Entity event, UpdateEventData data, List<String> invalidFormatUpdates) {
        final int indexOfStartDate = data.indexOfDate(DatastoreTypes.EVENT_START_DATE_ATTR);
        final int indexOfEndDate = data.indexOfDate(DatastoreTypes.EVENT_END_DATE_ATTR);
        if (indexOfStartDate != -1 && indexOfEndDate != -1) {
            final String startDate = data.updateEntries[indexOfStartDate].newValue;
            final String endDate = data.updateEntries[indexOfEndDate].newValue;
            final boolean canSetDates = canSetDates(invalidFormatUpdates, startDate, endDate, DateUpdate.ALL);
            if (!canSetDates) {
                return;
            }
            eb.set(DatastoreTypes.EVENT_START_DATE_ATTR, Timestamp.parseTimestamp(startDate))
                    .set(DatastoreTypes.EVENT_END_DATE_ATTR, Timestamp.parseTimestamp(endDate));
            LOG.fine("Updated event dates");
        } else if (indexOfStartDate != -1) {
            final String startDate = data.updateEntries[indexOfStartDate].newValue;
            final String endDate = DateUtils.dateToStringUTC(event.getTimestamp(DatastoreTypes.EVENT_END_DATE_ATTR).toDate());
            final boolean canSetDates = canSetDates(invalidFormatUpdates, startDate, endDate, DateUpdate.START_DATE);
            if (!canSetDates) {
                return;
            }
            eb.set(DatastoreTypes.EVENT_START_DATE_ATTR, Timestamp.parseTimestamp(startDate));
            LOG.fine("Updated event start date (end date is the same)");
        } else if (indexOfEndDate != -1) {
            final String startDate = DateUtils.dateToStringUTC(event.getTimestamp(DatastoreTypes.EVENT_START_DATE_ATTR).toDate());
            final String endDate = data.updateEntries[indexOfEndDate].newValue;
            final boolean canSetDates = canSetDates(invalidFormatUpdates, startDate, endDate, DateUpdate.END_DATE);
            if (!canSetDates) {
                return;
            }
            eb.set(DatastoreTypes.EVENT_END_DATE_ATTR, Timestamp.parseTimestamp(endDate));
            LOG.fine("Updated event end date (start date is the same)");
        }
    }

    private boolean canSetDates(List<String> invalidFormatUpdates, String startDate, String endDate, DateUpdate dateUpdate) {
        if (!DateUtils.isTimestampValid(startDate) || !DateUtils.isTimestampValid(endDate)) {
            handleInvalidDates(dateUpdate, invalidFormatUpdates);
            LOG.fine("Invalid format - event dates have to conform to RFC 3339");
            return false;
        } else if (!DateUtils.areTimestampsOnFuture(startDate) || !DateUtils.areTimestampsOnFuture(endDate)) {
            handleInvalidDates(dateUpdate, invalidFormatUpdates);
            LOG.fine("Invalid format - event dates have to be on future");
            return false;
        } else if (!DateUtils.isTimestampBefore(startDate, endDate)) {
            handleInvalidDates(dateUpdate, invalidFormatUpdates);
            LOG.fine("Invalid format - start date has to be before end date");
            return false;
        }
        return true;
    }

    private void handleInvalidDates(DateUpdate dateUpdate, List<String> invalidFormatUpdates) {
        if (dateUpdate == DateUpdate.ALL) {
            invalidFormatUpdates.add(DatastoreTypes.EVENT_START_DATE_ATTR);
            invalidFormatUpdates.add(DatastoreTypes.EVENT_END_DATE_ATTR);
        } else if (dateUpdate == DateUpdate.START_DATE) {
            invalidFormatUpdates.add(DatastoreTypes.EVENT_START_DATE_ATTR);
        } else {
            invalidFormatUpdates.add(DatastoreTypes.EVENT_END_DATE_ATTR);
        }
    }

    private boolean isDateProperty(String property) {
        return property.equals(DatastoreTypes.EVENT_START_DATE_ATTR) || property.equals(DatastoreTypes.EVENT_END_DATE_ATTR);
    }

    private void updateProperty(Entity.Builder eb, String propertyName, String newValue) {
        if (!isDateProperty(propertyName)) {
            eb.set(propertyName, newValue);
            LOG.fine("Updated property: " + propertyName);
        }
    }

    private boolean checkPropertyFormat(String property, String newValue) {
        switch (property) {
            case DatastoreTypes.EVENT_START_DATE_ATTR:
            case DatastoreTypes.EVENT_END_DATE_ATTR:
            case DatastoreTypes.EVENT_NAME_ATTR:
            case DatastoreTypes.EVENT_DESCRIPTION_ATTR:
            case DatastoreTypes.EVENT_COLOR_ATTR:
            case DatastoreTypes.EVENT_RECURRENCE_RULE_ATTR:
                return true;
            case DatastoreTypes.EVENT_LOCATION_ATTR:
                return isLocationValid(newValue);
            default:
                return false;
        }
    }

    private boolean isLocationValid(String location) {
        final List<String> allLocations = new GetLocationsResource().getLocations();
        for (String l : allLocations) {
            if (l.equals(location))
                return true;
        }
        return false;
    }

    private boolean didEventChanged(List<String> invalidFormatUpdates, UpdateEntry[] updateEntries) {
        return invalidFormatUpdates.size() != updateEntries.length;
    }

    private Response createResponseBasedOnUpdates(UpdateEventData data, List<String> invalidFormatUpdates) {
        if (invalidFormatUpdates.isEmpty()) {
            LOG.fine("Updated all properties");
            return Response.ok(gson.toJson("Updated all properties")).build();
        } else if (invalidFormatUpdates.size() == data.updateEntries.length) {
            LOG.info("None of the properties were updated - invalid format");
            return Response.status(Response.Status.BAD_REQUEST).entity(gson.toJson(createResponseString("None of the properties were updated:", invalidFormatUpdates))).build();
        } else {
            LOG.info("Some properties were not updated");
            return Response.ok(gson.toJson(createResponseString("Some properties were not updated:", invalidFormatUpdates))).build();
        }
    }

    private String createResponseString(String baseString, List<String> invalidFormatUpdates) {
        final StringBuilder sb = new StringBuilder();
        sb.append(baseString);
        appendPropertiesWithEmptyCheck(sb, invalidFormatUpdates, "\nInvalid format updates:");
        return sb.toString();
    }

    private void appendPropertiesWithEmptyCheck(StringBuilder sb, List<String> l, String baseStr) {
        if (!l.isEmpty()) {
            sb.append(baseStr);
            appendPropertiesNotUpdated(sb, l);
        }
    }

    private void appendPropertiesNotUpdated(StringBuilder sb, List<String> list) {
        for (String s : list) {
            sb.append(SEPARATOR).append(s);
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

    private enum DateUpdate {
        ALL, START_DATE, END_DATE
    }
}