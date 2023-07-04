package pt.unl.fct.di.apdc.chatfct.fctconnect.resources;

import com.google.cloud.datastore.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.jsonwebtoken.JwtException;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.*;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.logging.Logger;

@Path("/backoffice/userinfo")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class GetUserInfoBackOfficeResource {

    private static final Logger LOG = Logger.getLogger(GetUserInfoBackOfficeResource.class.getName());
    private static final String USERNAME_PATH_PARAM = "username";
    private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    private final KeyFactory userKeyFactory = datastore.newKeyFactory().setKind(DatastoreTypes.USER_TYPE);
    private final KeyFactory backOfficeUserKeyFactory = datastore.newKeyFactory().setKind(DatastoreTypes.BACK_OFFICE_USER_TYPE);
    private final MemcacheUtils memcacheUsers = MemcacheUtils.getMemcache(MemcacheUtils.USER_NAMESPACE);
    private final MemcacheUtils memcacheBackOfficeUsers = MemcacheUtils.getMemcache(MemcacheUtils.BACK_OFFICE_USER_NAMESPACE);
    private final MemcacheUtils memcacheSpecificUsers = MemcacheUtils.getMemcache(MemcacheUtils.SPECIFIC_USERS_NAMESPACE);
    private final Gson gson = new Gson();

    public GetUserInfoBackOfficeResource() {
    }

    private static Gson getGsonWithExclusion() {
        return new GsonBuilder().addSerializationExclusionStrategy(new ListedBackOfficeUserExclusionStrategy()).create();
    }

    private Entity getUserCached(String username) {
        final String key = String.format(MemcacheUtils.USER_ENTITY_KEY, username);
        return memcacheUsers.get(key, Entity.class);
    }

    private Entity getBackOfficeUserCached(String username) {
        final String key = String.format(MemcacheUtils.BACK_OFFICE_USER_ENTITY_KEY, username);
        return memcacheBackOfficeUsers.get(key, Entity.class);
    }

    private Entity getSpecificUserCached(String username) {
        final String key = String.format(MemcacheUtils.SPECIFIC_USER_ENTITY_KEY, username);
        return memcacheSpecificUsers.get(key, Entity.class);
    }

    private boolean isCached(Entity e) {
        return e != null;
    }

    @GET
    @Path("/regularuser/{username}")
    public Response doGetRegularUserInfo(@PathParam(USERNAME_PATH_PARAM) String otherUsername, @Context HttpHeaders headers, @Context HttpServletRequest request) {
        LOG.fine("Back office user attempt to get regular user info");
        final String token = TokenUtils.extractTokenFromHeaders(request);
        TokenInfo tokenInfo = verifyToken(token);
        if (tokenInfo == null) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(gson.toJson("Invalid credentials")).build();
        }
        LOG.fine("Valid token. Proceeding...");
        final String username = tokenInfo.getUsername();
        final Response checkData = checkData(otherUsername);
        if (checkData != null) {
            return checkData;
        }
        Key usernameKey = backOfficeUserKeyFactory.newKey(username);
        Key otherKey = userKeyFactory.newKey(otherUsername);
        Transaction txn = datastore.newTransaction();
        try {
            Entity usernameOnDB;
            final Entity userCached = getBackOfficeUserCached(username);
            final boolean isUserCached = isCached(userCached);
            if (isUserCached) {
                usernameOnDB = userCached;
            } else {
                usernameOnDB = txn.get(usernameKey);
                final Response checkUserOnDB = checkUserOnDB(usernameOnDB);
                if (checkUserOnDB != null) {
                    txn.rollback();
                    return checkUserOnDB;
                }
                memcacheBackOfficeUsers.put(String.format(MemcacheUtils.BACK_OFFICE_USER_ENTITY_KEY, username), usernameOnDB);
            }
            Entity otherOnDB;
            final Entity otherCached = getUserCached(otherUsername);
            final boolean isOtherCached = isCached(otherCached);
            if (isOtherCached) {
                otherOnDB = otherCached;
            } else {
                otherOnDB = txn.get(otherKey);
                final Response checkUserOnDB = checkUserOnDB(otherOnDB);
                if (checkUserOnDB != null) {
                    txn.rollback();
                    return checkUserOnDB;
                }
                memcacheUsers.put(String.format(MemcacheUtils.USER_ENTITY_KEY, otherUsername), otherOnDB);
            }
            final Response checkAccountState = BackOfficeStateChecker.checkAccountState(username);
            if (!isResponseOK(checkAccountState)) {
                txn.rollback();
                return checkAccountState;
            }
            final String otherRole = getUserRole(otherOnDB);
            final Key specificUserKey = getSpecificUserKey(otherUsername, otherRole);
            Entity specificUserOnDB;
            final Entity specificUserCached = getSpecificUserCached(otherUsername);
            final boolean isSpecificUserCached = isCached(specificUserCached);
            if (isSpecificUserCached) {
                specificUserOnDB = specificUserCached;
            } else {
                specificUserOnDB = txn.get(specificUserKey);
                memcacheSpecificUsers.put(String.format(MemcacheUtils.SPECIFIC_USER_ENTITY_KEY, otherUsername), specificUserOnDB);
            }
            final Response resp = getUserInfo(otherOnDB, specificUserOnDB, otherRole);
            txn.commit();
            LOG.fine("Regular user info fetched");
            return resp;
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
            LOG.fine("Invalid data: username is invalid");
            return Response.status(Response.Status.BAD_REQUEST).entity(gson.toJson("Bad Request - invalid data")).build();
        }
        return null;
    }

    private Response checkUserOnDB(Entity user) {
        if (user == null) {
            LOG.fine("User does not exist");
            return Response.status(Response.Status.NOT_FOUND).entity(gson.toJson("Not Found - username is not recognized")).build();
        }
        return null;
    }

    private boolean isResponseOK(Response r) {
        return r.getStatus() == Response.Status.OK.getStatusCode();
    }

    private String getUserRole(Entity user) {
        return user.getString(DatastoreTypes.ROLE_ATTR);
    }

    private Key getSpecificUserKey(String username, String role) {
        return datastore.newKeyFactory().setKind(DatastoreTypes.formatRoleType(role))
                .addAncestors(PathElement.of(DatastoreTypes.USER_TYPE, username)).newKey(username);
    }

    private Response getUserInfo(Entity otherOnDB, Entity specificUserOnDB, String role) {
        if (role.equals(RegexExp.ROLE_STUDENT_REGEX)) {
            return Response.ok(gson.toJson(UserInfoStudent.createUserInfoStudent(otherOnDB, specificUserOnDB))).build();
        } else if (role.equals(RegexExp.ROLE_PROFESSOR_REGEX)) {
            return Response.ok(gson.toJson(UserInfoProfessor.createUserInfoProfessor(otherOnDB, specificUserOnDB))).build();
        } else {
            return Response.ok(gson.toJson(UserInfoEmployee.createUserInfoEmployee(otherOnDB, specificUserOnDB))).build();
        }
    }

    @GET
    @Path("/backofficeuser/{username}")
    public Response doGetBackOfficeUserInfo(@PathParam(USERNAME_PATH_PARAM) String otherUsername, @Context HttpHeaders headers, @Context HttpServletRequest request) {
        LOG.fine("Back office user attempt to get another back office user info");
        final String token = TokenUtils.extractTokenFromHeaders(request);
        TokenInfo tokenInfo = verifyToken(token);
        if (tokenInfo == null) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(gson.toJson("Invalid credentials")).build();
        }
        LOG.fine("Valid token. Proceeding...");
        final String username = tokenInfo.getUsername();
        final String role = tokenInfo.getRole();
        final Response checkData = checkData(otherUsername);
        if (checkData != null) {
            return checkData;
        }
        Key usernameKey = backOfficeUserKeyFactory.newKey(username);
        Key otherKey = backOfficeUserKeyFactory.newKey(otherUsername);
        Transaction txn = datastore.newTransaction();
        try {
            Entity usernameOnDB;
            final Entity userCached = getBackOfficeUserCached(username);
            final boolean isUserCached = isCached(userCached);
            if (isUserCached) {
                usernameOnDB = userCached;
            } else {
                usernameOnDB = txn.get(usernameKey);
                final Response checkUserOnDB = checkUserOnDB(usernameOnDB);
                if (checkUserOnDB != null) {
                    txn.rollback();
                    return checkUserOnDB;
                }
                memcacheBackOfficeUsers.put(String.format(MemcacheUtils.BACK_OFFICE_USER_ENTITY_KEY, username), usernameOnDB);
            }
            Entity otherOnDB;
            final Entity otherCached = getBackOfficeUserCached(otherUsername);
            final boolean isOtherCached = isCached(otherCached);
            if (isOtherCached) {
                otherOnDB = otherCached;
            } else {
                otherOnDB = txn.get(otherKey);
                final Response checkUserOnDB = checkUserOnDB(otherOnDB);
                if (checkUserOnDB != null) {
                    txn.rollback();
                    return checkUserOnDB;
                }
                memcacheBackOfficeUsers.put(String.format(MemcacheUtils.BACK_OFFICE_USER_ENTITY_KEY, otherUsername), otherOnDB);
            }
            final Response checkAccountState = BackOfficeStateChecker.checkAccountState(username);
            if (!isResponseOK(checkAccountState)) {
                txn.rollback();
                return checkAccountState;
            }
            Response resp;
            final String otherRole = getUserRole(otherOnDB);
            if (otherRole.equals(BackOfficeRolePermissions.ADMIN_ROLE) && !BackOfficeRolePermissions.canGetAdminsInfo(role)) {
                resp = Response.status(Response.Status.FORBIDDEN).entity(gson.toJson("Dont have permission to fetch this user info")).build();
            } else {
                final ListedBackOfficeUser listedUser = ListedBackOfficeUser.createListedBackOfficeUser(otherOnDB);
                resp = Response.ok(getGsonWithExclusion().toJson(listedUser)).build();
            }
            txn.commit();
            LOG.fine("Back office user info fetched");
            return resp;
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

    private TokenInfo verifyToken(final String token) {
        try {
            return TokenUtils.verifyToken(token);
        } catch (JwtException ex) {
            LOG.warning("Invalid token --> " + ex.getLocalizedMessage());
            return null;
        }
    }
}