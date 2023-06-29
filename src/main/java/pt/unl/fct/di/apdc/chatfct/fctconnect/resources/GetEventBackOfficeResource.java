package pt.unl.fct.di.apdc.chatfct.fctconnect.resources;

import com.google.cloud.datastore.*;
import com.google.gson.Gson;
import io.jsonwebtoken.JwtException;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.DatastoreTypes;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.ListedBackOfficeEvent;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.TokenInfo;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.TokenUtils;

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

@Path("/backoffice/getevent")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class GetEventBackOfficeResource {

    private static final String ID_PATH_PARAM = "id";
    private static final Logger LOG = Logger.getLogger(GetEventBackOfficeResource.class.getName());
    private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    private final KeyFactory backOfficeUserKeyFactory = datastore.newKeyFactory().setKind(DatastoreTypes.BACK_OFFICE_USER_TYPE);
    private final KeyFactory eventKeyFactory = datastore.newKeyFactory().setKind(DatastoreTypes.EVENT_TYPE);
    private final Gson gson = new Gson();

    public GetEventBackOfficeResource() {
    }

    @GET
    @Path("/{id}")
    public Response doGetEvent(@PathParam(ID_PATH_PARAM) String id, @Context HttpHeaders headers, @Context HttpServletRequest request) {
        LOG.fine("Back office user attempt to list events");
        final String token = TokenUtils.extractTokenFromHeaders(request);
        TokenInfo tokenInfo = verifyToken(token);
        if (tokenInfo == null) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(gson.toJson("Invalid credentials")).build();
        }
        LOG.fine("Valid token. Proceeding...");
        final String username = tokenInfo.getUsername();
        final Response checkId = checkData(id);
        if (checkId != null) {
            return checkId;
        }
        final Key backOfficeUserKey = backOfficeUserKeyFactory.newKey(username);
        final Key eventKey = eventKeyFactory.newKey(id);
        Transaction txn = datastore.newTransaction();
        try {
            final Entity backOfficeUserOnDB = txn.get(backOfficeUserKey);
            final Response checkBackOfficeUserOnDB = checkBackOfficeUserOnDB(backOfficeUserOnDB);
            if (checkBackOfficeUserOnDB != null) {
                txn.rollback();
                return checkBackOfficeUserOnDB;
            }
            final Entity eventOnDB = txn.get(eventKey);
            final Response checkEventOnDB = checkEventOnDB(eventOnDB);
            if (checkEventOnDB != null) {
                txn.rollback();
                return checkEventOnDB;
            }
            txn.commit();
            LOG.info("Event was fetched");
            return Response.ok(gson.toJson(ListedBackOfficeEvent.createListedBackOfficeEvent(eventOnDB))).build();
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

    private Response checkData(String id) {
        if (id == null || id.isBlank()) {
            LOG.fine("Invalid data: id is invalid");
            return Response.status(Response.Status.BAD_REQUEST).entity(gson.toJson("Bad Request - invalid data")).build();
        }
        return null;
    }

    private Response checkBackOfficeUserOnDB(Entity backOfficeUserOnDB) {
        if (backOfficeUserOnDB == null) {
            LOG.fine("Back office user does not exist");
            return Response.status(Response.Status.NOT_FOUND).entity(gson.toJson("Not Found - username is not recognized")).build();
        }
        return null;
    }

    private Response checkEventOnDB(Entity eventOnDB) {
        if (eventOnDB == null) {
            LOG.fine("Event does not exist");
            return Response.status(Response.Status.NOT_FOUND).entity(gson.toJson("Not Found - Event does not exist")).build();
        }
        return null;
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