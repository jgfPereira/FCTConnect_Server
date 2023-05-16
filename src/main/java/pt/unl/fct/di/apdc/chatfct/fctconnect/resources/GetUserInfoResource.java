package pt.unl.fct.di.apdc.chatfct.fctconnect.resources;

import com.google.cloud.datastore.*;
import com.google.gson.Gson;
import io.jsonwebtoken.JwtException;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.*;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.POST;
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
    private final Gson gson = new Gson();

    public GetUserInfoResource() {
    }

    @POST
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
            Entity userOnDB = txn.get(key);
            final Response checkUserOnDB = checkUserOnDB(userOnDB);
            if (checkUserOnDB != null) {
                txn.rollback();
                return checkUserOnDB;
            }
            LOG.fine("User info fetched");
            if (role.matches(RegexExp.ROLE_OTHER_REGEX)) {
                txn.commit();
                final UserInfo userInfo = UserInfo.createUserInfo(userOnDB);
                return Response.ok(gson.toJson(userInfo)).build();
            } else {
                final Key studentUserKey = getStudentUserKey(username);
                final Entity studentOnDB = txn.get(studentUserKey);
                txn.commit();
                final UserInfoStudent userInfoStudent = UserInfoStudent.createUserInfoStudent(userOnDB, studentOnDB);
                return Response.ok(gson.toJson(userInfoStudent)).build();
            }
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

    private Key getStudentUserKey(String username) {
        return datastore.newKeyFactory().setKind(DatastoreTypes.STUDENT_TYPE)
                .addAncestors(PathElement.of(DatastoreTypes.USER_TYPE, username)).newKey(username);
    }
}