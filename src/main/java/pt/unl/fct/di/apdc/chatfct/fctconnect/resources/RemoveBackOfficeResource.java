package pt.unl.fct.di.apdc.chatfct.fctconnect.resources;

import com.google.cloud.datastore.*;
import com.google.gson.Gson;
import io.jsonwebtoken.JwtException;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.BackOfficeRolePermissions;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.DatastoreTypes;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.TokenInfo;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.TokenUtils;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DELETE;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
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
    private static final String USERNAME_PATH_PARAM = "username";
    private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    private final KeyFactory userKeyFactory = datastore.newKeyFactory().setKind(DatastoreTypes.USER_TYPE);
    private final KeyFactory backOfficeUserKeyFactory = datastore.newKeyFactory().setKind(DatastoreTypes.BACK_OFFICE_USER_TYPE);
    private final Gson gson = new Gson();

    public RemoveBackOfficeResource() {
    }

    @DELETE
    @Path("/regularuser")
    public Response doRemoveRegularUser(@PathParam(USERNAME_PATH_PARAM) String otherUsername, @Context HttpHeaders headers, @Context HttpServletRequest request) {
        LOG.fine("Back office user attempt to remove regular user");
        final String token = TokenUtils.extractTokenFromHeaders(request);
        TokenInfo tokenInfo = verifyToken(token);
        if (tokenInfo == null) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(gson.toJson("Invalid credentials")).build();
        }
        LOG.fine("Valid token. Proceeding...");
        final String username = tokenInfo.getUsername();
        final String usernameRole = tokenInfo.getRole();
        final Response checkData = checkData(otherUsername);
        if (checkData != null) {
            return checkData;
        }
        Key usernameKey = backOfficeUserKeyFactory.newKey(username);
        Key otherKey = userKeyFactory.newKey(otherUsername);
        Transaction txn = datastore.newTransaction();
        try {
            Entity usernameOnDB = txn.get(usernameKey);
            Entity otherOnDB = txn.get(otherKey);
            final Response checkUsersOnDB = checkUsersOnDB(usernameOnDB, otherOnDB);
            if (checkUsersOnDB != null) {
                txn.rollback();
                return checkUsersOnDB;
            }
            final Response checkRemovePermissions = checkRemoveRegularUserPermissions(usernameRole, otherUsername);
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
            Key specificUserKey = getSpecificUserKey(otherUsername, otherRole);
            txn.delete(specificUserKey);
            Key loginRegistryKey = getLoginRegistryKey(otherUsername);
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
            LOG.fine("Remove done: " + otherUsername);
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

    private Response checkData(String otherUsername) {
        if (otherUsername == null || otherUsername.isBlank()) {
            LOG.fine("Invalid data: id is invalid");
            return Response.status(Response.Status.BAD_REQUEST).entity(gson.toJson("Bad Request - invalid data")).build();
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

    @DELETE
    @Path("/backofficeuser")
    public Response doRemoveBackOfficeUser(@PathParam(USERNAME_PATH_PARAM) String otherUsername, @Context HttpHeaders headers, @Context HttpServletRequest request) {
        LOG.fine("Back office user attempt to remove another back office user");
        final String token = TokenUtils.extractTokenFromHeaders(request);
        TokenInfo tokenInfo = verifyToken(token);
        if (tokenInfo == null) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(gson.toJson("Invalid credentials")).build();
        }
        LOG.fine("Valid token. Proceeding...");
        final String username = tokenInfo.getUsername();
        final String usernameRole = tokenInfo.getRole();
        final Response checkData = checkData(otherUsername);
        if (checkData != null) {
            return checkData;
        }
        Key usernameKey = backOfficeUserKeyFactory.newKey(username);
        Key otherKey = backOfficeUserKeyFactory.newKey(otherUsername);
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
            final Response checkRemovePermissions = checkRemoveBackOfficeUserPermissions(otherUsername, username, usernameRole, otherRole);
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
            LOG.fine("Remove done: " + otherUsername);
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

    private Response checkRemoveBackOfficeUserPermissions(String otherUsername, String username, String usernameRole, String otherRole) {
        if (!BackOfficeRolePermissions.canRemoveBackOfficeUser(otherUsername, username, usernameRole, otherRole)) {
            LOG.fine("Dont have permission to remove back office user " + otherUsername);
            return Response.status(Response.Status.FORBIDDEN).entity(gson.toJson("Forbidden - Dont have permission to remove back office user " + otherUsername)).build();
        }
        return null;
    }
}