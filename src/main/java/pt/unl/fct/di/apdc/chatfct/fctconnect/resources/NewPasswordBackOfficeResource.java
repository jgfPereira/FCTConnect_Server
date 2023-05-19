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

@Path("/backoffice/newpass")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class NewPasswordBackOfficeResource {

    private static final Logger LOG = Logger.getLogger(NewPasswordBackOfficeResource.class.getName());
    private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    private final KeyFactory backOfficeUserKeyFactory = datastore.newKeyFactory().setKind(DatastoreTypes.BACK_OFFICE_USER_TYPE);
    private final Gson gson = new Gson();

    public NewPasswordBackOfficeResource() {
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public Response doNewPassword(NewPasswordData data, @Context HttpHeaders headers, @Context HttpServletRequest request) {
        LOG.fine("Attempt to update name of back office user");
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
        Key key = backOfficeUserKeyFactory.newKey(username);
        Transaction txn = datastore.newTransaction();
        try {
            Entity userOnDB = txn.get(key);
            final Response checkUserOnDB = checkUserOnDB(userOnDB);
            if (checkUserOnDB != null) {
                txn.rollback();
                return checkUserOnDB;
            }
            final Response checkAccountState = BackOfficeStateChecker.checkAccountState(username);
            if (!isResponseOK(checkAccountState)) {
                txn.rollback();
                return checkAccountState;
            }
            final Response checkPassword = checkPassword(userOnDB, data);
            if (checkPassword != null) {
                txn.rollback();
                return checkPassword;
            }
            Entity userChanged = updatePassword(userOnDB, data.newPassword);
            txn.update(userChanged);
            txn.commit();
            LOG.fine("Password changed successfully");
            return Response.ok(gson.toJson("Password changed successfully")).build();
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

    private Response checkData(NewPasswordData data) {
        if (data == null || !data.validateData()) {
            LOG.fine("Invalid data: at least one required field is null");
            return Response.status(Response.Status.BAD_REQUEST).entity(gson.toJson("Bad Request - invalid data")).build();
        }
        return null;
    }

    private Response checkUserOnDB(Entity userOnDB) {
        if (userOnDB == null) {
            LOG.fine("User does not exist");
            return Response.status(Response.Status.NOT_FOUND).entity(gson.toJson("Not Found - username is not recognized")).build();
        }
        return null;
    }

    private boolean isResponseOK(Response r) {
        return r.getStatus() == Response.Status.OK.getStatusCode();
    }

    private Response checkPassword(Entity userOnDB, NewPasswordData data) {
        final String passwordOnDB = userOnDB.getString(DatastoreTypes.PASSWORD_ATTR);
        final String hashedPassword = PasswordUtils.hashPass(data.password);
        if (!passwordOnDB.equals(hashedPassword)) {
            LOG.fine("Incorrect password");
            return Response.status(Response.Status.UNAUTHORIZED).entity(gson.toJson("Unauthorized - Incorrect password")).build();
        } else if (data.isPasswordSameAsOld()) {
            LOG.fine("New password is equal to old password");
            return Response.status(Response.Status.BAD_REQUEST).entity(gson.toJson("Bad Request - new password cant be the same as old password")).build();
        } else if (!data.comparePasswords()) {
            LOG.fine("Passwords dont match");
            return Response.status(Response.Status.BAD_REQUEST).entity(gson.toJson("Bad Request - passwords dont match")).build();
        } else if (!data.validatePassword()) {
            LOG.fine("Password dont meet constraints");
            return Response.status(Response.Status.BAD_REQUEST).entity(gson.toJson("Bad Request - password dont meet constraints")).build();
        }
        return null;
    }

    private Entity updatePassword(Entity user, String newPass) {
        final String hashedPassword = PasswordUtils.hashPass(newPass);
        return Entity.newBuilder(user)
                .set(DatastoreTypes.PASSWORD_ATTR, hashedPassword)
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