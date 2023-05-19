package pt.unl.fct.di.apdc.chatfct.fctconnect.resources;

import com.google.cloud.datastore.*;
import com.google.gson.Gson;
import io.jsonwebtoken.JwtException;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.BackOfficeUpdateNameData;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.DatastoreTypes;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.TokenInfo;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.TokenUtils;

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

@Path("/backoffice/updatename")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class UpdateNameBackOfficeResource {

    private static final Logger LOG = Logger.getLogger(UpdateNameBackOfficeResource.class.getName());
    private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    private final KeyFactory backOfficeUserKeyFactory = datastore.newKeyFactory().setKind(DatastoreTypes.BACK_OFFICE_USER_TYPE);
    private final Gson gson = new Gson();

    public UpdateNameBackOfficeResource() {
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public Response doUpdateName(BackOfficeUpdateNameData data, @Context HttpHeaders headers, @Context HttpServletRequest request) {
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
            Entity user = txn.get(key);
            final Response checkUsersOnDB = checkUserOnDB(user);
            if (checkUsersOnDB != null) {
                txn.rollback();
                return checkUsersOnDB;
            }
            final Response checkAccountState = BackOfficeStateChecker.checkAccountState(username);
            if (!isResponseOK(checkAccountState)) {
                txn.rollback();
                return checkAccountState;
            }
            Entity userChanged = updateName(user, data.name);
            txn.update(userChanged);
            txn.commit();
            LOG.fine("Name has been successfully updated");
            return Response.ok(gson.toJson("Name has been successfully updated")).build();
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

    private Response checkData(BackOfficeUpdateNameData data) {
        if (data == null || !data.validateData()) {
            LOG.fine("Invalid data: at least one field is null");
            return Response.status(Response.Status.BAD_REQUEST).entity(gson.toJson("Bad Request - Invalid data")).build();
        }
        return null;
    }

    private Response checkUserOnDB(Entity user) {
        if (user == null) {
            LOG.fine("User does not exist");
            return Response.status(Response.Status.NOT_FOUND).entity(gson.toJson("User does not exist")).build();
        }
        return null;
    }

    private boolean isResponseOK(Response r) {
        return r.getStatus() == Response.Status.OK.getStatusCode();
    }

    private Entity updateName(Entity user, String newName) {
        return Entity.newBuilder(user)
                .set(DatastoreTypes.NAME_ATTR, newName.trim())
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