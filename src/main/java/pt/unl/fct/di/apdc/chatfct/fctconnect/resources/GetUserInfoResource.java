package pt.unl.fct.di.apdc.chatfct.fctconnect.resources;

import com.google.cloud.datastore.*;
import com.google.gson.Gson;
import io.jsonwebtoken.JwtException;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.*;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.logging.Logger;

@Path("/userinfo")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class GetUserInfoResource {

    private static final Logger LOG = Logger.getLogger(GetUserInfoResource.class.getName());
    private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    private final KeyFactory userKeyFactory = datastore.newKeyFactory().setKind(DatastoreTypes.USER_TYPE);
    private final MemcacheUtils memcacheUsers = MemcacheUtils.getMemcache(MemcacheUtils.USER_NAMESPACE);
    private final MemcacheUtils memcacheSpecificUsers = MemcacheUtils.getMemcache(MemcacheUtils.SPECIFIC_USERS_NAMESPACE);
    private final Gson gson = new Gson();

    public GetUserInfoResource() {
    }

    private Entity getUserCached(String username) {
        final String key = String.format(MemcacheUtils.USER_ENTITY_KEY, username);
        return memcacheUsers.get(key, Entity.class);
    }

    private Entity getSpecificUserCached(String username) {
        final String key = String.format(MemcacheUtils.SPECIFIC_USER_ENTITY_KEY, username);
        return memcacheSpecificUsers.get(key, Entity.class);
    }

    private boolean isCached(Entity e) {
        return e != null;
    }

    @GET
    public Response doGetUserInfo(@Context HttpHeaders headers, @Context HttpServletRequest request) {
        LOG.fine("User attempt to get profile info");
        final String token = TokenUtils.extractTokenFromHeaders(request);
        TokenInfo tokenInfo = verifyToken(token);
        if (tokenInfo == null) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(gson.toJson("Invalid credentials")).build();
        }
        LOG.fine("Valid token. Proceeding...");
        final String username = tokenInfo.getUsername();
        final String role = tokenInfo.getRole();
        Key key = userKeyFactory.newKey(username);
        Transaction txn = datastore.newTransaction();
        try {
            Entity userOnDB;
            final Entity userCached = getUserCached(username);
            final boolean isUserCached = isCached(userCached);
            if (isUserCached) {
                userOnDB = userCached;
            } else {
                userOnDB = txn.get(key);
                final Response checkUserOnDB = checkUserOnDB(userOnDB);
                if (checkUserOnDB != null) {
                    txn.rollback();
                    return checkUserOnDB;
                }
                memcacheUsers.put(String.format(MemcacheUtils.USER_ENTITY_KEY, username), userOnDB);
            }
            final Key specificUserKey = getSpecificUserKey(username, role);
            Entity specificUserOnDB;
            final Entity specificUserCached = getSpecificUserCached(username);
            final boolean isSpecificUserCached = isCached(specificUserCached);
            if (isSpecificUserCached) {
                specificUserOnDB = specificUserCached;
            } else {
                specificUserOnDB = txn.get(specificUserKey);
                memcacheSpecificUsers.put(String.format(MemcacheUtils.SPECIFIC_USER_ENTITY_KEY, username), specificUserOnDB);
            }
            final Response resp = getUserInfo(userOnDB, specificUserOnDB, role);
            txn.commit();
            LOG.fine("User info fetched");
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

    private Response checkUserOnDB(Entity userOnDB) {
        if (userOnDB == null) {
            LOG.fine("User does not exist");
            return Response.status(Response.Status.NOT_FOUND).entity(gson.toJson("Not Found - username is not recognized")).build();
        }
        return null;
    }

    private Key getSpecificUserKey(String username, String role) {
        return datastore.newKeyFactory().setKind(DatastoreTypes.formatRoleType(role))
                .addAncestors(PathElement.of(DatastoreTypes.USER_TYPE, username)).newKey(username);
    }

    private Response getUserInfo(Entity userOnDB, Entity specificUserOnDB, String role) {
        if (role.equals(RegexExp.ROLE_STUDENT_REGEX)) {
            final UserInfoStudent userInfoStudent = UserInfoStudent.createUserInfoStudent(userOnDB, specificUserOnDB);
            return Response.ok(gson.toJson(userInfoStudent)).build();
        } else if (role.equals(RegexExp.ROLE_PROFESSOR_REGEX)) {
            final UserInfoProfessor userInfoProfessor = UserInfoProfessor.createUserInfoProfessor(userOnDB, specificUserOnDB);
            return Response.ok(gson.toJson(userInfoProfessor)).build();
        } else {
            final UserInfoEmployee userInfoEmployee = UserInfoEmployee.createUserInfoEmployee(userOnDB, specificUserOnDB);
            return Response.ok(gson.toJson(userInfoEmployee)).build();
        }
    }
}