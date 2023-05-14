package pt.unl.fct.di.apdc.chatfct.fctconnect.resources;

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
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

@Path("/update")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class UpdateResource {

    private static final Logger LOG = Logger.getLogger(UpdateResource.class.getName());
    private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    private final KeyFactory userKeyFactory = datastore.newKeyFactory().setKind(DatastoreTypes.USER_TYPE);
    private final Gson gson = new Gson();

    public UpdateResource() {
    }

    @POST
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
        final String usernameRole = tokenInfo.getRole();
        final Response checkData = checkData(data);
        if (checkData != null) {
            return checkData;
        }
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
            Entity updatedUser = updateUser(otherOnDB, data, username, forbiddenUpdates);
            txn.update(updatedUser);
            txn.commit();
            LOG.fine("User was updated - if permissions checked out");
            return checkForbiddenUpdates(data, forbiddenUpdates);
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

    private Entity updateUser(Entity e, UpdateData data, String username, List<String> forbiddenUpdates) {
        Entity.Builder eb = Entity.newBuilder(e);
        for (UpdateEntry entry : data.updateEntries) {
            if (RolePermissions.canUpdate(data, username, entry.propertyName)) {
                eb.set(entry.propertyName, entry.newValue);
                LOG.fine("Updated property " + entry.propertyName);
            } else {
                LOG.fine("Dont have permission to update property " + entry.propertyName);
                forbiddenUpdates.add(entry.propertyName);
            }
        }
        return eb.build();
    }

    private boolean checkPropertyFormat(String property) {
        switch (property) {
            case DatastoreTypes.BIRTH_DATE_ATTR:
                return property.matches(RegexExp.DATE_REGEX);
            case DatastoreTypes.LOCALE_ATTR:
            case DatastoreTypes.NAME_ATTR:
            case DatastoreTypes.PHOTO_ATTR:
            case DatastoreTypes.STREET_ATTR:
                return true;
            case DatastoreTypes.NIF_ATTR:
                return property.matches(RegexExp.NIF_REGEX);
            case DatastoreTypes.PHONE_NUM_ATTR:
                return property.matches(RegexExp.PHONE_NUM_REGEX);
            case DatastoreTypes.VISIBILITY_ATTR:
                return property.matches(RegexExp.VISIBILITY_REGEX);
            case DatastoreTypes.ZIP_CODE_ATTR:
                return property.matches(RegexExp.ZIP_CODE_REGEX);
            default:
                return false;
        }
    }

    private Response checkForbiddenUpdates(UpdateData data, List<String> forbiddenUpdates) {
        if (forbiddenUpdates.isEmpty()) {
            LOG.fine("Updated all properties");
            return Response.ok(gson.toJson("Updated all properties")).build();
        } else if (forbiddenUpdates.size() == data.updateEntries.length) {
            LOG.info("None of the properties were updated");
            return Response.status(Response.Status.UNAUTHORIZED).entity(gson.toJson("None of the properties were updated: " + appendForbiddenUpdates(forbiddenUpdates))).build();
        } else {
            LOG.info("Some properties were not updated");
            return Response.ok(gson.toJson("Some properties were not updated: " + appendForbiddenUpdates(forbiddenUpdates))).build();
        }
    }

    private String appendForbiddenUpdates(List<String> forbiddenUpdates) {
        StringBuilder sb = new StringBuilder();
        for (String s : forbiddenUpdates) {
            sb.append(" ").append(s);
        }
        return sb.toString();
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