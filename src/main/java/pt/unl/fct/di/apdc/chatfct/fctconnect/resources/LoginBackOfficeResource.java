package pt.unl.fct.di.apdc.chatfct.fctconnect.resources;

import com.google.cloud.datastore.*;
import com.google.gson.Gson;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.DatastoreTypes;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.LoginData;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.PasswordUtils;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.TokenUtils;

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

@Path("/backoffice/login")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class LoginBackOfficeResource {

    private static final Logger LOG = Logger.getLogger(LoginBackOfficeResource.class.getName());
    private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    private final KeyFactory backOfficeUserKeyFactory = datastore.newKeyFactory().setKind(DatastoreTypes.BACK_OFFICE_USER_TYPE);
    private final Gson gson = new Gson();

    public LoginBackOfficeResource() {
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response doLogin(LoginData data, @Context HttpHeaders headers, @Context HttpServletRequest request) {
        LOG.fine("Login attempt by back office user ");
        final Response checkData = checkData(data);
        if (checkData != null) {
            return checkData;
        }
        Key key = backOfficeUserKeyFactory.newKey(data.username);
        Transaction txn = datastore.newTransaction();
        try {
            Entity userOnDB = txn.get(key);
            final Response checkBackOfficeUserOnDB = checkBackOfficeUserOnDB(userOnDB);
            if (checkBackOfficeUserOnDB != null) {
                txn.rollback();
                return checkBackOfficeUserOnDB;
            }
            final boolean checkPassword = checkPassword(data.password, userOnDB);
            if (checkPassword) {
                final Response checkAccountState = BackOfficeStateChecker.checkAccountState(data.username);
                if (!isResponseOK(checkAccountState)) {
                    return checkAccountState;
                }
                final String token = createToken(data.username, userOnDB);
                txn.commit();
                LOG.fine("Correct password - generated token");
                return Response.ok(gson.toJson(token)).header(TokenUtils.AUTH_HEADER, TokenUtils.AUTH_TYPE + token).build();
            } else {
                txn.rollback();
                LOG.fine("Wrong password");
                return Response.status(Response.Status.UNAUTHORIZED).entity(gson.toJson("Wrong credentials")).build();
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

    private Response checkData(LoginData data) {
        final boolean check = data != null && data.validateData();
        if (!check) {
            LOG.fine("Invalid data: at least one required field is null");
        }
        return check ? null : Response.status(Response.Status.BAD_REQUEST).entity(gson.toJson("Bad Request - invalid data")).build();
    }

    private Response checkBackOfficeUserOnDB(Entity user) {
        if (user == null) {
            LOG.fine("User does not exist");
            return Response.status(Response.Status.NOT_FOUND).entity(gson.toJson("Not found - user does not exist")).build();
        }
        return null;
    }

    private boolean isResponseOK(Response r) {
        return r.getStatus() == Response.Status.OK.getStatusCode();
    }

    private boolean checkPassword(String password, Entity userOnDB) {
        return userOnDB.getString(DatastoreTypes.PASSWORD_ATTR).equals(PasswordUtils.hashPass(password));
    }

    private String createToken(String username, Entity userOnDB) {
        final String role = userOnDB.getString(DatastoreTypes.ROLE_ATTR);
        return TokenUtils.createToken(username, role);
    }
}