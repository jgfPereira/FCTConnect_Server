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

@Path("/backoffice/remove")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class RemoveBackOfficeResource {

    private static final Logger LOG = Logger.getLogger(RemoveBackOfficeResource.class.getName());
    private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    private final KeyFactory userKeyFactory = datastore.newKeyFactory().setKind(DatastoreTypes.USER_TYPE);
    private final KeyFactory backOfficeUserKeyFactory = datastore.newKeyFactory().setKind(DatastoreTypes.BACK_OFFICE_USER_TYPE);
    private final Gson gson = new Gson();

    public RemoveBackOfficeResource() {
    }

    @POST
    @Path("/regularuser")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response doRemoveRegularUser(RemoveData data, @Context HttpHeaders headers, @Context HttpServletRequest request) {
        LOG.fine("Back office user attempt to remove regular user");
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
        Key usernameKey = backOfficeUserKeyFactory.newKey(username);
        Key otherKey = userKeyFactory.newKey(data.removedUsername);
        Transaction txn = datastore.newTransaction();
        try {
            Entity usernameOnDB = txn.get(usernameKey);
            Entity otherOnDB = txn.get(otherKey);
            final Response checkUsersOnDB = checkUsersOnDB(usernameOnDB, otherOnDB);
            if (checkUsersOnDB != null) {
                txn.rollback();
                return checkUsersOnDB;
            }
            final Response checkRemovePermissions = checkRemoveRegularUserPermissions(usernameRole, data.removedUsername);
            if (checkRemovePermissions != null) {
                txn.rollback();
                return checkRemovePermissions;
            }
            final Response checkAccountState = BackOfficeStateChecker.checkAccountState(username);
            if (!isResponseOK(checkAccountState)) {
                txn.rollback();
                return checkAccountState;
            }
            final String otherRole = getOtherRole(otherOnDB);
            Key specificUserKey = getSpecificUserKey(data.removedUsername, otherRole);
            txn.delete(specificUserKey);
            Key loginRegistryKey = getLoginRegistryKey(data.removedUsername);
            Entity loginRegistry = txn.get(loginRegistryKey);
            final boolean isLoginRegistryOnDB = checkLoginRegistryOnDB(loginRegistry);
            if (isLoginRegistryOnDB) {
                txn.delete(loginRegistryKey);
                Query<Entity> loginLogsQuery = getLoginLogsQuery(otherKey);
                QueryResults<Entity> loginLogs = txn.run(loginLogsQuery);
                Key[] keysToRemove = removeLoginLogs(loginLogs);
                txn.delete(keysToRemove);
            }
            txn.delete(otherKey);
            txn.commit();
            LOG.fine("Remove done: " + data.removedUsername);
            return Response.ok(gson.toJson("Remove done")).build();
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

    private Response checkData(RemoveData data) {
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

    private boolean isResponseOK(Response r) {
        return r.getStatus() == Response.Status.OK.getStatusCode();
    }

    private Response checkRemoveRegularUserPermissions(String usernameRole, String removedUsername) {
        if (!BackOfficeRolePermissions.canRemoveRegularUser(usernameRole)) {
            LOG.fine("Dont have permission to remove user " + removedUsername);
            return Response.status(Response.Status.FORBIDDEN).entity(gson.toJson("Forbidden - Dont have permission to remove user " + removedUsername)).build();
        }
        return null;
    }

    private String getOtherRole(Entity other) {
        return other.getString(DatastoreTypes.ROLE_ATTR);
    }

    private Key getSpecificUserKey(String username, String role) {
        return datastore.newKeyFactory().setKind(DatastoreTypes.formatRoleType(role))
                .addAncestors(PathElement.of(DatastoreTypes.USER_TYPE, username))
                .newKey(username);
    }

    private Key getLoginRegistryKey(String username) {
        return datastore.newKeyFactory().setKind(DatastoreTypes.LOGIN_REGISTRY_TYPE)
                .addAncestors(PathElement.of(DatastoreTypes.USER_TYPE, username))
                .newKey(DatastoreTypes.LOGIN_REGISTRY_KEY);
    }

    private boolean checkLoginRegistryOnDB(Entity e) {
        return e != null;
    }

    private Query<Entity> getLoginLogsQuery(Key userKey) {
        return Query.newEntityQueryBuilder().setKind(DatastoreTypes.LOGIN_LOG_TYPE)
                .setFilter(StructuredQuery.PropertyFilter
                        .hasAncestor(userKey))
                .build();
    }

    private Key[] removeLoginLogs(QueryResults<Entity> loginLogs) {
        List<Key> keys = new ArrayList<>();
        loginLogs.forEachRemaining(log -> keys.add(log.getKey()));
        return keys.toArray(new Key[keys.size()]);
    }

    private TokenInfo verifyToken(final String token) {
        try {
            return TokenUtils.verifyToken(token);
        } catch (JwtException ex) {
            LOG.warning("Invalid token --> " + ex.getLocalizedMessage());
            return null;
        }
    }

    @POST
    @Path("/backofficeuser")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response doRemoveBackOfficeUser(RemoveData data, @Context HttpHeaders headers, @Context HttpServletRequest request) {
        LOG.fine("Back office user attempt to remove another back office user");
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
        Key usernameKey = backOfficeUserKeyFactory.newKey(username);
        Key otherKey = backOfficeUserKeyFactory.newKey(data.removedUsername);
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
            final Response checkRemovePermissions = checkRemoveBackOfficeUserPermissions(data, username, usernameRole, otherRole);
            if (checkRemovePermissions != null) {
                txn.rollback();
                return checkRemovePermissions;
            }
            final Response checkAccountState = BackOfficeStateChecker.checkAccountState(username);
            if (!isResponseOK(checkAccountState)) {
                txn.rollback();
                return checkAccountState;
            }
            txn.delete(otherKey);
            txn.commit();
            LOG.fine("Remove done: " + data.removedUsername);
            return Response.ok(gson.toJson("Remove done")).build();
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

    private Response checkRemoveBackOfficeUserPermissions(RemoveData data, String username, String usernameRole, String otherRole) {
        if (!BackOfficeRolePermissions.canRemoveBackOfficeUser(data, username, usernameRole, otherRole)) {
            LOG.fine("Dont have permission to remove back office user " + data.removedUsername);
            return Response.status(Response.Status.FORBIDDEN).entity(gson.toJson("Forbidden - Dont have permission to remove back office user " + data.removedUsername)).build();
        }
        return null;
    }
}