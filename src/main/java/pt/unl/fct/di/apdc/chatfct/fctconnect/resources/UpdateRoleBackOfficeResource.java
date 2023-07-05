package pt.unl.fct.di.apdc.chatfct.fctconnect.resources;

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
import java.util.logging.Logger;

@Path("/backoffice/updaterole")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class UpdateRoleBackOfficeResource {

    private static final Logger LOG = Logger.getLogger(UpdateRoleBackOfficeResource.class.getName());
    private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    private final KeyFactory backOfficeUserKeyFactory = datastore.newKeyFactory().setKind(DatastoreTypes.BACK_OFFICE_USER_TYPE);
    private final MemcacheUtils memcacheBackOfficeUsers = MemcacheUtils.getMemcache(MemcacheUtils.BACK_OFFICE_USER_NAMESPACE);
    private final Gson gson = new Gson();

    public UpdateRoleBackOfficeResource() {
    }

    private Entity getBackOfficeUserCached(String username) {
        final String key = String.format(MemcacheUtils.BACK_OFFICE_USER_ENTITY_KEY, username);
        return memcacheBackOfficeUsers.get(key, Entity.class);
    }

    private boolean isCached(Entity e) {
        return e != null;
    }

    @PUT
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
            Entity usernameOnDB;
            final Entity userCached = getBackOfficeUserCached(username);
            final boolean isUserCached = isCached(userCached);
            if (isUserCached) {
                usernameOnDB = userCached;
            } else {
                usernameOnDB = txn.get(usernameKey);
                final Response checkBackOfficeUserOnDB = checkBackOfficeUserOnDB(usernameOnDB);
                if (checkBackOfficeUserOnDB != null) {
                    txn.rollback();
                    return checkBackOfficeUserOnDB;
                }
                memcacheBackOfficeUsers.put(String.format(MemcacheUtils.BACK_OFFICE_USER_ENTITY_KEY, username), usernameOnDB);
            }
            Entity otherOnDB;
            final Entity otherCached = getBackOfficeUserCached(data.username);
            final boolean isOtherCached = isCached(otherCached);
            if (isOtherCached) {
                otherOnDB = otherCached;
            } else {
                otherOnDB = txn.get(otherKey);
                final Response checkBackOfficeUserOnDB = checkBackOfficeUserOnDB(otherOnDB);
                if (checkBackOfficeUserOnDB != null) {
                    txn.rollback();
                    return checkBackOfficeUserOnDB;
                }
                memcacheBackOfficeUsers.put(String.format(MemcacheUtils.BACK_OFFICE_USER_ENTITY_KEY, data.username), otherOnDB);
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
            memcacheBackOfficeUsers.put(String.format(MemcacheUtils.BACK_OFFICE_USER_ENTITY_KEY, data.username), userChanged);
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

    private Response checkBackOfficeUserOnDB(Entity backOfficeUserOnDB) {
        if (backOfficeUserOnDB == null) {
            LOG.fine("Backoffice user does not exist");
            return Response.status(Response.Status.NOT_FOUND).entity(gson.toJson("Not Found - Backoffice user does not exist")).build();
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
        final boolean canUpdate = BackOfficeRolePermissions.canUpdateRole(usernameRole, otherRole);
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