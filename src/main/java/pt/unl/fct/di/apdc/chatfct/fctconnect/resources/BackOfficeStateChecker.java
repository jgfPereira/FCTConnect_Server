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

@Path("/backoffice/approve")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class BackOfficeStateChecker {

    private static final Logger LOG = Logger.getLogger(BackOfficeStateChecker.class.getName());
    private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    private final KeyFactory backOfficeUserKeyFactory = datastore.newKeyFactory().setKind(DatastoreTypes.BACK_OFFICE_USER_TYPE);
    private final MemcacheUtils memcacheBackOfficeUsers = MemcacheUtils.getMemcache(MemcacheUtils.BACK_OFFICE_USER_NAMESPACE);
    private final Gson gson = new Gson();

    public BackOfficeStateChecker() {
    }

    public static Response checkAccountState(String username) {
        return new BackOfficeStateChecker().checkState(username);
    }

    private Entity getBackOfficeUserCached(String username) {
        final String key = String.format(MemcacheUtils.BACK_OFFICE_USER_ENTITY_KEY, username);
        return memcacheBackOfficeUsers.get(key, Entity.class);
    }

    private boolean isCached(Entity e) {
        return e != null;
    }

    private Response checkState(final String username) {
        LOG.fine("Checking back office user state");
        Key key = backOfficeUserKeyFactory.newKey(username);
        Transaction txn = datastore.newTransaction();
        try {
            Entity backOfficeUserOnDB;
            final Entity userCached = getBackOfficeUserCached(username);
            final boolean isUserCached = isCached(userCached);
            if (isUserCached) {
                backOfficeUserOnDB = userCached;
            } else {
                backOfficeUserOnDB = txn.get(key);
                memcacheBackOfficeUsers.put(String.format(MemcacheUtils.BACK_OFFICE_USER_ENTITY_KEY, username), backOfficeUserOnDB);
            }
            final boolean isApproved = isApproved(backOfficeUserOnDB);
            if (!isApproved) {
                txn.rollback();
                LOG.fine("Account has not been approved yet");
                return Response.status(Response.Status.UNAUTHORIZED).entity(gson.toJson("Unauthorized - account approval is pending")).build();
            }
            txn.commit();
            LOG.fine("Account is approved");
            return Response.ok(gson.toJson("Account is approved")).build();
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

    private boolean isApproved(Entity user) {
        final String state = user.getString(DatastoreTypes.STATE_ATTR);
        return state.equals(RegexExp.APPROVED_STATE_REGEX);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response doApproveBackOfficeUser(ApproveBackOfficeUserData data, @Context HttpHeaders headers, @Context HttpServletRequest request) {
        LOG.fine("Attempt to approve back office user account");
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
            final Response canApproveAccount = canApproveAccount(role);
            if (canApproveAccount != null) {
                txn.rollback();
                return canApproveAccount;
            }
            final Response checkAccountState = BackOfficeStateChecker.checkAccountState(username);
            if (!isResponseOK(checkAccountState)) {
                txn.rollback();
                return checkAccountState;
            }
            Entity userChanged = approveAccount(otherOnDB);
            memcacheBackOfficeUsers.put(String.format(MemcacheUtils.BACK_OFFICE_USER_ENTITY_KEY, data.username), userChanged);
            txn.update(userChanged);
            txn.commit();
            LOG.fine("Account has been successfully approved");
            return Response.ok(gson.toJson("Account has been successfully approved")).build();
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

    private Response checkData(ApproveBackOfficeUserData data) {
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

    private Response canApproveAccount(String role) {
        final boolean canApproveAccount = BackOfficeRolePermissions.canApproveAccount(role);
        if (!canApproveAccount) {
            LOG.fine("Dont have permission to approve account");
            return Response.status(Response.Status.FORBIDDEN).entity(gson.toJson("Dont have permission to approve account")).build();
        }
        return null;
    }

    private Entity approveAccount(Entity otherUser) {
        return Entity.newBuilder(otherUser)
                .set(DatastoreTypes.STATE_ATTR, DatastoreTypes.APPROVED_STATE)
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