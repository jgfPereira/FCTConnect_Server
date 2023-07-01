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

@Path("/updatepersonalevent")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class UpdatePersonalEventResource {

    private static final Logger LOG = Logger.getLogger(UpdatePersonalEventResource.class.getName());
    private static final String SEPARATOR = " ";
    private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    private final KeyFactory userKeyFactory = datastore.newKeyFactory().setKind(DatastoreTypes.USER_TYPE);
    private final Gson gson = new Gson();

    public UpdatePersonalEventResource() {
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public Response doUpdatePersonalEvent(UpdateEventData data, @Context HttpHeaders headers, @Context HttpServletRequest request) {
        LOG.fine("User attempt to update personal event");
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
        Key userKey = userKeyFactory.newKey(username);
        Key personalEventKey = createPersonalEventKey(username, data.id);
        Transaction txn = datastore.newTransaction();
        try {
            final Entity userOnDB = txn.get(userKey);
            final Response checkUserOnDB = checkUserOnDB(userOnDB);
            if (checkUserOnDB != null) {
                txn.rollback();
                return checkUserOnDB;
            }
            final Entity personalEventOnDB = txn.get(personalEventKey);
            final Response checkPersonalEventOnDB = checkPersonalEventOnDB(personalEventOnDB);
            if (checkPersonalEventOnDB != null) {
                txn.rollback();
                return checkPersonalEventOnDB;
            }
            final List<String> invalidFormatUpdates = new ArrayList<>();
            final Entity updatePersonalEvent = updatePersonalEvent(personalEventOnDB, data, invalidFormatUpdates);
            if (didPersonalEventChanged(invalidFormatUpdates, data.updateEntries)) {
                txn.update(updatePersonalEvent);
            }
            txn.commit();
            LOG.info("Personal event was updated - if format checked out");
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

    private Response checkPersonalEventOnDB(Entity personalEventOnDB) {
        if (personalEventOnDB == null) {
            LOG.fine("Personal event does not exist");
            return Response.status(Response.Status.NOT_FOUND).entity(gson.toJson("Not Found - Personal event does not exist")).build();
        }
        return null;
    }

    private Entity updatePersonalEvent(Entity personalEvent, UpdateEventData data, List<String> invalidFormatUpdates) {
        Entity.Builder eb = Entity.newBuilder(personalEvent);
        for (UpdateEntry entry : data.updateEntries) {
            if (!checkPropertyFormat(entry.propertyName)) {
                LOG.fine("Format of the new value is invalid for the property " + entry.propertyName);
                invalidFormatUpdates.add(entry.propertyName);
            } else {
                updateProperty(eb, entry.propertyName, entry.newValue);
            }
        }
        handleDateUpdates(eb, personalEvent, data, invalidFormatUpdates);
        return eb.build();
    }

    private boolean checkPropertyFormat(String property) {
        switch (property) {
            case DatastoreTypes.EVENT_START_DATE_ATTR:
            case DatastoreTypes.EVENT_END_DATE_ATTR:
            case DatastoreTypes.EVENT_NAME_ATTR:
            case DatastoreTypes.EVENT_DESCRIPTION_ATTR:
            case DatastoreTypes.EVENT_COLOR_ATTR:
            case DatastoreTypes.EVENT_RECURRENCE_RULE_ATTR:
            case DatastoreTypes.EVENT_LOCATION_ATTR:
                return true;
            default:
                return false;
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

    private void handleDateUpdates(Entity.Builder eb, Entity personalEvent, UpdateEventData data, List<String> invalidFormatUpdates) {
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
            LOG.fine("Updated personal event dates");
        } else if (indexOfStartDate != -1) {
            final String startDate = data.updateEntries[indexOfStartDate].newValue;
            final String endDate = DateUtils.dateToStringUTC(personalEvent.getTimestamp(DatastoreTypes.EVENT_END_DATE_ATTR).toDate());
            final boolean canSetDates = canSetDates(invalidFormatUpdates, startDate, endDate, DateUpdate.START_DATE);
            if (!canSetDates) {
                return;
            }
            eb.set(DatastoreTypes.EVENT_START_DATE_ATTR, Timestamp.parseTimestamp(startDate));
            LOG.fine("Updated personal event start date (end date is the same)");
        } else if (indexOfEndDate != -1) {
            final String startDate = DateUtils.dateToStringUTC(personalEvent.getTimestamp(DatastoreTypes.EVENT_START_DATE_ATTR).toDate());
            final String endDate = data.updateEntries[indexOfEndDate].newValue;
            final boolean canSetDates = canSetDates(invalidFormatUpdates, startDate, endDate, DateUpdate.END_DATE);
            if (!canSetDates) {
                return;
            }
            eb.set(DatastoreTypes.EVENT_END_DATE_ATTR, Timestamp.parseTimestamp(endDate));
            LOG.fine("Updated personal event end date (start date is the same)");
        }
    }

    private boolean canSetDates(List<String> invalidFormatUpdates, String startDate, String endDate, DateUpdate dateUpdate) {
        if (!DateUtils.isTimestampValid(startDate) || !DateUtils.isTimestampValid(endDate)) {
            handleInvalidDates(dateUpdate, invalidFormatUpdates);
            LOG.fine("Invalid format - personal event dates have to conform to RFC 3339");
            return false;
        } else if (!DateUtils.areTimestampsOnFuture(startDate) || !DateUtils.areTimestampsOnFuture(endDate)) {
            handleInvalidDates(dateUpdate, invalidFormatUpdates);
            LOG.fine("Invalid format - personal event dates have to be on future");
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

    private boolean didPersonalEventChanged(List<String> invalidFormatUpdates, UpdateEntry[] updateEntries) {
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