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
import java.util.logging.Logger;

@Path("/addplaces")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class AddLocationsResource {

    private static final Logger LOG = Logger.getLogger(AddLocationsResource.class.getName());
    private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    private final KeyFactory backOfficeUserKeyFactory = datastore.newKeyFactory().setKind(DatastoreTypes.BACK_OFFICE_USER_TYPE);
    private final KeyFactory locationsKeyFactory = datastore.newKeyFactory().setKind(DatastoreTypes.LOCATIONS_TYPE);
    private final Gson gson = new Gson();

    public AddLocationsResource() {
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response doAddLocations(AddLocationsData data, @Context HttpHeaders headers, @Context HttpServletRequest request) {
        LOG.fine("Backoffice user attempt to add locations");
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
        Key locationsKey = locationsKeyFactory.newKey(DatastoreTypes.LOCATIONS_TYPE_KEY);
        Transaction txn = datastore.newTransaction();
        try {
            final Entity backOfficeUserOnDB = txn.get(backOfficeUserKey);
            final Response checkBackOfficeUserOnDB = checkBackOfficeUserOnDB(backOfficeUserOnDB);
            if (checkBackOfficeUserOnDB != null) {
                txn.rollback();
                return checkBackOfficeUserOnDB;
            }
            final Response canAddLocations = canAddLocations(role);
            if (canAddLocations != null) {
                txn.rollback();
                return canAddLocations;
            }
            final Entity locationsOnDB = txn.get(locationsKey);
            final Response checkLocationsOnDB = checkLocationsOnDB(locationsOnDB);
            if (checkLocationsOnDB != null) {
                txn.rollback();
                return checkLocationsOnDB;
            }
            final Entity locationsCreated = createLocations(locationsKey, data);
            txn.put(locationsCreated);
            txn.commit();
            LOG.info("Locations was added successfully");
            return Response.ok(gson.toJson("Locations was added successfully")).build();
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

    private Response checkData(AddLocationsData data) {
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

    private Response canAddLocations(String role) {
        final boolean canAddLocations = BackOfficeRolePermissions.canAddLocations(role);
        if (!canAddLocations) {
            LOG.fine("Dont have permission to add locations");
            return Response.status(Response.Status.FORBIDDEN).entity(gson.toJson("Forbidden - Dont have permission to add locations")).build();
        }
        return null;
    }

    private Response checkLocationsOnDB(Entity locationsOnDB) {
        if (locationsOnDB != null) {
            LOG.fine("Locations already exist");
            return Response.status(Response.Status.CONFLICT).entity(gson.toJson("Conflict - Locations already exist")).build();
        }
        return null;
    }

    private Entity createLocations(Key locationsKey, AddLocationsData data) {
        return Entity.newBuilder(locationsKey)
                .set(DatastoreTypes.EVENT_ACL_ATTR, ListValue.of(data.getLocationsFirst(), data.getLocationsRest()))
                .build();
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