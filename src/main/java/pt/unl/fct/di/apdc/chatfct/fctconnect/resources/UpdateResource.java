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

@Path("/update")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class UpdateResource {

    private static final String START_OF_DAY_UTC = "T00:00:00Z";
    private static final Logger LOG = Logger.getLogger(UpdateResource.class.getName());
    private static final String SEPARATOR = " ";
    private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    private final KeyFactory userKeyFactory = datastore.newKeyFactory().setKind(DatastoreTypes.USER_TYPE);
    private final Gson gson = new Gson();

    public UpdateResource() {
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public Response doUpdate(UpdateData data, @Context HttpHeaders headers, @Context HttpServletRequest request) {
        LOG.fine("User attempt to update attribute(s)");
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
        data.removeDuplicates();
        data.formatVisibilityProperty();
        Key usernameKey = userKeyFactory.newKey(username);
        Key otherKey = userKeyFactory.newKey(data.updatedUsername);
        Transaction txn = datastore.newTransaction();
        try {
            Entity usernameOnDB = txn.get(usernameKey);
            Entity otherOnDB = txn.get(otherKey);
            final Response checkUsersOnDB = checkUsersOnDB(usernameOnDB, otherOnDB);
            if (checkUsersOnDB != null) {
                txn.rollback();
                return checkUsersOnDB;
            }
            final List<String> forbiddenUpdates = new ArrayList<>();
            final List<String> invalidFormatUpdates = new ArrayList<>();
            Entity updatedUser = updateUser(otherOnDB, data, username, forbiddenUpdates, invalidFormatUpdates);
            if (didUserChanged(forbiddenUpdates, invalidFormatUpdates, data.updateEntries)) {
                txn.update(updatedUser);
            }
            txn.commit();
            LOG.fine("User was updated - if permissions and format checked out");
            return createResponseBasedOnUpdates(data, forbiddenUpdates, invalidFormatUpdates);
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

    private Response checkData(UpdateData data) {
        if (data == null || !data.validateData()) {
            LOG.fine("Invalid data: at least one field is null");
            return Response.status(Response.Status.BAD_REQUEST).entity(gson.toJson("Bad Request - Invalid data")).build();
        }
        return null;
    }

    private Response checkUsersOnDB(Entity usernameOnDB, Entity otherOnDB) {
        if (usernameOnDB == null || otherOnDB == null) {
            LOG.fine("At least one of the users dont exist");
            return Response.status(Response.Status.NOT_FOUND).entity(gson.toJson("Not Found - At least one of the users dont exist")).build();
        }
        return null;
    }

    private Entity updateUser(Entity e, UpdateData data, String username, List<String> forbiddenUpdates, List<String> invalidFormatUpdates) {
        Entity.Builder eb = Entity.newBuilder(e);
        for (UpdateEntry entry : data.updateEntries) {
            if (!RolePermissions.canUpdate(data, username, entry.propertyName)) {
                LOG.fine("Dont have permission to update property " + entry.propertyName);
                forbiddenUpdates.add(entry.propertyName);
            } else if (!checkPropertyFormat(entry.propertyName, entry.newValue)) {
                LOG.fine("Format of the new value is invalid for the property " + entry.propertyName);
                invalidFormatUpdates.add(entry.propertyName);
            } else {
                updateProperty(eb, entry.propertyName, entry.newValue);
                LOG.fine("Updated property " + entry.propertyName);
            }
        }
        return eb.build();
    }

    private boolean checkPropertyFormat(String property, String newValue) {
        switch (property) {
            case DatastoreTypes.BIRTH_DATE_ATTR:
                return newValue.matches(RegexExp.DATE_REGEX);
            case DatastoreTypes.LOCALE_ATTR:
            case DatastoreTypes.NAME_ATTR:
            case DatastoreTypes.PHOTO_ATTR:
            case DatastoreTypes.STREET_ATTR:
                return true;
            case DatastoreTypes.PHONE_NUM_ATTR:
                return newValue.matches(RegexExp.PHONE_NUM_REGEX);
            case DatastoreTypes.VISIBILITY_ATTR:
                return newValue.matches(RegexExp.VISIBILITY_REGEX);
            case DatastoreTypes.ZIP_CODE_ATTR:
                return newValue.matches(RegexExp.ZIP_CODE_REGEX);
            default:
                return false;
        }
    }

    private boolean didUserChanged(List<String> forbiddenUpdates, List<String> invalidFormatUpdates, UpdateEntry[] updateEntries) {
        return forbiddenUpdates.size() + invalidFormatUpdates.size() != updateEntries.length;
    }

    private Response createResponseBasedOnUpdates(UpdateData data, List<String> forbiddenUpdates, List<String> invalidFormatUpdates) {
        if (forbiddenUpdates.isEmpty() && invalidFormatUpdates.isEmpty()) {
            LOG.fine("Updated all properties");
            return Response.ok(gson.toJson("Updated all properties")).build();
        } else if (forbiddenUpdates.size() + invalidFormatUpdates.size() == data.updateEntries.length) {
            LOG.info("None of the properties were updated");
            return Response.status(Response.Status.FORBIDDEN).entity(gson.toJson(createResponseString("None of the properties were updated:", forbiddenUpdates, invalidFormatUpdates))).build();
        } else {
            LOG.info("Some properties were not updated");
            return Response.ok(gson.toJson(createResponseString("Some properties were not updated:", forbiddenUpdates, invalidFormatUpdates))).build();
        }
    }

    private String createResponseString(String baseString, List<String> forbiddenUpdates, List<String> invalidFormatUpdates) {
        final StringBuilder sb = new StringBuilder();
        sb.append(baseString);
        appendPropertiesWithEmptyCheck(sb, forbiddenUpdates, "\nForbidden updates:");
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

    private boolean isBirthDateProperty(String property) {
        return property.equals(DatastoreTypes.BIRTH_DATE_ATTR);
    }

    private void updateBirthDate(Entity.Builder eb, String birthDate) {
        final String birthDateWithTime = birthDate + START_OF_DAY_UTC;
        eb.set(DatastoreTypes.BIRTH_DATE_ATTR, Timestamp.parseTimestamp(birthDateWithTime));
    }

    private void updateProperty(Entity.Builder eb, String propertyName, String newValue) {
        if (isBirthDateProperty(propertyName)) {
            updateBirthDate(eb, newValue);
        } else {
            eb.set(propertyName, newValue);
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