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

@Path("/backoffice/userinfo")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class GetUserInfoBackOfficeResource {

    private static final Logger LOG = Logger.getLogger(GetUserInfoBackOfficeResource.class.getName());
    private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    private final KeyFactory userKeyFactory = datastore.newKeyFactory().setKind(DatastoreTypes.USER_TYPE);
    private final KeyFactory backOfficeUserKeyFactory = datastore.newKeyFactory().setKind(DatastoreTypes.BACK_OFFICE_USER_TYPE);
    private final Gson gson = new Gson();

    public GetUserInfoBackOfficeResource() {
    }

    @POST
    @Path("/regularuser")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response doGetRegularUserInfo(GetUserInfoData data, @Context HttpHeaders headers, @Context HttpServletRequest request) {
        LOG.fine("Back office user attempt to get regular user info");
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
        Key usernameKey = backOfficeUserKeyFactory.newKey(username);
        Key otherKey = userKeyFactory.newKey(data.username);
        Transaction txn = datastore.newTransaction();
        try {
            Entity usernameOnDB = txn.get(usernameKey);
            Entity otherOnDB = txn.get(otherKey);
            final Response checkUsersOnDB = checkUsersOnDB(usernameOnDB, otherOnDB);
            if (checkUsersOnDB != null) {
                txn.rollback();
                return checkUsersOnDB;
            }
            final Response checkAccountState = BackOfficeStateChecker.checkAccountState(username);
            if (!isResponseOK(checkAccountState)) {
                txn.rollback();
                return checkAccountState;
            }
            Response resp;
            final String otherRole = getUserRole(otherOnDB);
            if (otherRole.matches(RegexExp.ROLE_OTHER_REGEX)) {
                final UserInfo userInfo = UserInfo.createUserInfo(otherOnDB);
                resp = Response.ok(gson.toJson(userInfo)).build();
            } else {
                final Key studentUserKey = getStudentUserKey(data.username);
                final Entity studentOnDB = txn.get(studentUserKey);
                final UserInfoStudent userInfoStudent = UserInfoStudent.createUserInfoStudent(otherOnDB, studentOnDB);
                resp = Response.ok(gson.toJson(userInfoStudent)).build();
            }
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

    private Response checkData(GetUserInfoData data) {
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

    private String getUserRole(Entity user) {
        return user.getString(DatastoreTypes.ROLE_ATTR);
    }

    private Key getStudentUserKey(String username) {
        return datastore.newKeyFactory().setKind(DatastoreTypes.STUDENT_TYPE)
                .addAncestors(PathElement.of(DatastoreTypes.USER_TYPE, username)).newKey(username);
    }

    @POST
    @Path("/backofficeuser")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response doGetBackOfficeUserInfo(GetUserInfoData data, @Context HttpHeaders headers, @Context HttpServletRequest request) {
        LOG.fine("Back office user attempt to get another back office user info");
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
                resp = Response.ok(gson.toJson(listedUser)).build();
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