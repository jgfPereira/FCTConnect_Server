package pt.unl.fct.di.apdc.chatfct.fctconnect.resources;

import com.google.cloud.datastore.*;
import com.google.gson.Gson;
import io.jsonwebtoken.JwtException;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.DatastoreTypes;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.ListedEvent;
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

@Path("/getpersonalevent")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class GetPersonalEventResource {

    private static final Logger LOG = Logger.getLogger(GetPersonalEventResource.class.getName());
    private static final String ID_PATH_PARAM = "id";
    private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    private final KeyFactory userKeyFactory = datastore.newKeyFactory().setKind(DatastoreTypes.USER_TYPE);
    private final Gson gson = new Gson();

    public GetPersonalEventResource() {
    }

    @GET
    @Path("/{id}")
    public Response doGetPersonalEvent(@PathParam(ID_PATH_PARAM) String id, @Context HttpHeaders headers, @Context HttpServletRequest request) {
        LOG.fine("User attempt to fetch personal event");
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
        Key userKey = userKeyFactory.newKey(username);
        Key personalEventKey = createPersonalEventKey(username, id);
        Transaction txn = datastore.newTransaction();
        try {
            final Entity userOnDB = txn.get(userKey);
            final Response checkUserOnDB = checkUserOnDB(userOnDB);
            if (checkUserOnDB != null) {
                txn.rollback();
                return checkUserOnDB;
            }
            final Entity personalEventOnDB = txn.get(personalEventKey);
            final Response checkPersonalEventOnDB = checkPersonalEventOnDB(personalEventOnDB);
            if (checkPersonalEventOnDB != null) {
                txn.rollback();
                return checkPersonalEventOnDB;
            }
            txn.commit();
            LOG.info("Event was fetched - " + id);
            return Response.ok(gson.toJson(ListedEvent.createListedEvent(personalEventOnDB))).build();
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

    private Key createPersonalEventKey(String username, String id) {
        return datastore.newKeyFactory().setKind(DatastoreTypes.PERSONAL_EVENT_TYPE)
                .addAncestors(PathElement.of(DatastoreTypes.USER_TYPE, username))
                .newKey(id);
    }

    private Response checkUserOnDB(Entity userOnDB) {
        if (userOnDB == null) {
            LOG.fine("User does not exist");
            return Response.status(Response.Status.NOT_FOUND).entity(gson.toJson("Not Found - user does not exist")).build();
        }
        return null;
    }

    private Response checkPersonalEventOnDB(Entity personalEventOnDB) {
        if (personalEventOnDB == null) {
            LOG.fine("Personal event does not exist");
            return Response.status(Response.Status.NOT_FOUND).entity(gson.toJson("Not Found - Personal event does not exist")).build();
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