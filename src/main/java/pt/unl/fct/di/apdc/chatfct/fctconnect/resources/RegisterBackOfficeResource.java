package pt.unl.fct.di.apdc.chatfct.fctconnect.resources;

import com.google.cloud.Timestamp;
import com.google.cloud.datastore.*;
import com.google.gson.Gson;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.BackOfficeRegisterData;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.DatastoreTypes;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.PasswordUtils;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.TokenUtils;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.logging.Logger;

@Path("/backoffice/register")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class RegisterBackOfficeResource {

    private static final String EMAIL_DELIMITER = "@";
    private static final Logger LOG = Logger.getLogger(RegisterBackOfficeResource.class.getName());
    private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    private final KeyFactory userKeyFactory = datastore.newKeyFactory().setKind(DatastoreTypes.USER_TYPE);
    private final KeyFactory backOfficeUserKeyFactory = datastore.newKeyFactory().setKind(DatastoreTypes.BACK_OFFICE_USER_TYPE);
    private final Gson gson = new Gson();

    public RegisterBackOfficeResource() {
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response doRegister(BackOfficeRegisterData data) {
        LOG.fine("Back office user attempt to register");
        final Response checkData = checkData(data);
        if (checkData != null) {
            return checkData;
        }
        final String username = extractUsername(data.email);
        final boolean checkUsernameInRegularUsers = checkUsernameInRegularUsers(username);
        if (!checkUsernameInRegularUsers) {
            return Response.status(Response.Status.CONFLICT).entity(gson.toJson("Conflict - username is already taken")).build();
        }
        Key key = backOfficeUserKeyFactory.newKey(username);
        Transaction txn = datastore.newTransaction();
        try {
            Entity backOfficeUserOnDB = txn.get(key);
            final Response checkBackOfficeUserOnDB = checkUserOnDB(backOfficeUserOnDB);
            if (checkBackOfficeUserOnDB != null) {
                txn.rollback();
                return checkBackOfficeUserOnDB;
            }
            Entity backOfficeUser = createBackOfficeUser(data, key);
            txn.put(backOfficeUser);
            final String token = createToken(username, backOfficeUser);
            txn.commit();
            LOG.fine("Back office register done: " + username);
            return Response.ok(gson.toJson("Register done")).header(TokenUtils.AUTH_HEADER, TokenUtils.AUTH_TYPE + token).build();
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

    private Response checkData(BackOfficeRegisterData data) {
        if (data == null || !data.validateData()) {
            LOG.fine("Invalid data: at least one required field is null");
            return Response.status(Response.Status.BAD_REQUEST).entity(gson.toJson("Bad Request - invalid data")).build();
        } else if (!data.validateEmail()) {
            LOG.fine("Email dont meet constraints");
            return Response.status(Response.Status.BAD_REQUEST).entity(gson.toJson("Bad Request - email dont meet constraints")).build();
        } else if (!data.comparePasswords()) {
            LOG.fine("Passwords dont match");
            return Response.status(Response.Status.BAD_REQUEST).entity(gson.toJson("Bad Request - passwords dont match")).build();
        } else if (!data.validatePassword()) {
            LOG.fine("Passwords dont meet constraints");
            return Response.status(Response.Status.BAD_REQUEST).entity(gson.toJson("Bad Request - password dont meet constraints")).build();
        } else if (!data.validateRole()) {
            LOG.fine("Unrecognized role");
            return Response.status(Response.Status.BAD_REQUEST).entity(gson.toJson("Bad Request - unrecognized role")).build();
        }
        return null;
    }

    private String extractUsername(String email) {
        final String[] split = email.split(EMAIL_DELIMITER);
        return split[0].trim();
    }

    private boolean checkUsernameInRegularUsers(final String username) {
        Key key = userKeyFactory.newKey(username);
        Transaction txn = datastore.newTransaction();
        try {
            Entity regularUserOnDB = txn.get(key);
            final Response checkRegularUserOnDB = checkUserOnDB(regularUserOnDB);
            if (checkRegularUserOnDB != null) {
                txn.rollback();
                return false;
            }
            txn.commit();
            LOG.fine("Username is not taken by regular user");
            return true;
        } catch (Exception e) {
            txn.rollback();
            LOG.severe(e.getLocalizedMessage());
            return false;
        } finally {
            if (txn.isActive()) {
                txn.rollback();
                return false;
            }
        }
    }

    private Response checkUserOnDB(Entity user) {
        if (user != null) {
            LOG.fine("User already exists");
            return Response.status(Response.Status.CONFLICT).entity(gson.toJson("Conflict - username is already taken")).build();
        }
        return null;
    }

    private Entity createBackOfficeUser(BackOfficeRegisterData data, Key key) {
        return Entity.newBuilder(key)
                .set(DatastoreTypes.EMAIL_ATTR, data.email)
                .set(DatastoreTypes.NAME_ATTR, data.name)
                .set(DatastoreTypes.PASSWORD_ATTR, PasswordUtils.hashPass(data.password))
                .set(DatastoreTypes.CREATION_DATE_ATTR, Timestamp.now())
                .set(DatastoreTypes.ROLE_ATTR, data.role)
                .set(DatastoreTypes.STATE_ATTR, DatastoreTypes.DEFAULT_STATE)
                .build();
    }

    private String createToken(String username, Entity user) {
        final String role = user.getString(DatastoreTypes.ROLE_ATTR);
        return TokenUtils.createToken(username, role);
    }
}