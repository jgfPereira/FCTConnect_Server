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

@Path("/backoffice/update")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class UpdateRoleBackOfficeResource {

    private static final Logger LOG = Logger.getLogger(UpdateRoleBackOfficeResource.class.getName());
    private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    private final KeyFactory backOfficeUserKeyFactory = datastore.newKeyFactory().setKind(DatastoreTypes.BACK_OFFICE_USER_TYPE);
    private final Gson gson = new Gson();

    public UpdateRoleBackOfficeResource() {
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response doUpdateRole(BackOfficeUpdateRoleData data, @Context HttpHeaders headers, @Context HttpServletRequest request) {
        LOG.fine("Attempt to update role of back office user");
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
        Key usernameKey = backOfficeUserKeyFactory.newKey(username);
        Key otherKey = backOfficeUserKeyFactory.newKey(data.username);
        Transaction txn = datastore.newTransaction();
        try {
            Entity usernameOnDB = txn.get(usernameKey);
            Entity otherOnDB = txn.get(otherKey);
            final Response checkUsersOnDB = checkUsersOnDB(usernameOnDB, otherOnDB);
            if (checkUsersOnDB != null) {
                txn.rollback();
                return checkUsersOnDB;
            }
            final String otherRole = getOtherRole(otherOnDB);
            final Response canUpdateRole = canUpdateRole(role, otherRole);
            if (canUpdateRole != null) {
                txn.rollback();
                return canUpdateRole;
            }
            final Response checkAccountState = BackOfficeStateChecker.checkAccountState(username);
            if (!isResponseOK(checkAccountState)) {
                txn.rollback();
                return checkAccountState;
            }
            Entity userChanged = updateRole(otherOnDB);
            txn.update(userChanged);
            txn.commit();
            LOG.fine("Role has been successfully updated");
            return Response.ok(gson.toJson("Role has been successfully updated")).build();
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

    private Response checkData(BackOfficeUpdateRoleData data) {
        final boolean check = data != null && data.validateData();
        if (!check) {
            LOG.fine("Invalid data: at least one required field is null");
        }
        return check ? null : Response.status(Response.Status.BAD_REQUEST).entity(gson.toJson("Bad Request - invalid data")).build();
    }

    private Response checkUsersOnDB(Entity usernameOnDB, Entity otherOnDB) {
        if (usernameOnDB == null || otherOnDB == null) {
            LOG.fine("At least one of the users dont exist");
            return Response.status(Response.Status.NOT_FOUND).entity(gson.toJson("Not Found - At least one of the users dont exist")).build();
        }
        return null;
    }

    private boolean isResponseOK(Response r) {
        return r.getStatus() == Response.Status.OK.getStatusCode();
    }

    private String getOtherRole(Entity other) {
        return other.getString(DatastoreTypes.ROLE_ATTR);
    }

    private Response canUpdateRole(String usernameRole, String otherRole) {
        final boolean canUpdate = BackOfficeRolePermissions.canUpdate(usernameRole, otherRole);
        if (!canUpdate) {
            LOG.fine("Dont have permission to update");
            return Response.status(Response.Status.FORBIDDEN).entity(gson.toJson("Dont have permission to update")).build();
        }
        return null;
    }

    private Entity updateRole(Entity other) {
        return Entity.newBuilder(other)
                .set(DatastoreTypes.ROLE_ATTR, BackOfficeRolePermissions.ADMIN_ROLE)
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