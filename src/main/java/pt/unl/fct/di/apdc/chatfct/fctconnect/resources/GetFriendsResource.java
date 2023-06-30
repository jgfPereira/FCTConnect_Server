package pt.unl.fct.di.apdc.chatfct.fctconnect.resources;

import com.google.cloud.datastore.*;
import com.google.gson.Gson;
import io.jsonwebtoken.JwtException;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.DatastoreTypes;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.RestClientUtils;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.TokenInfo;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.TokenUtils;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.logging.Logger;

@Path("/getfriends")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class GetFriendsResource {

    private static final Logger LOG = Logger.getLogger(GetFriendsResource.class.getName());
    private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    private final KeyFactory userKeyFactory = datastore.newKeyFactory().setKind(DatastoreTypes.USER_TYPE);
    private final Gson gson = new Gson();

    public GetFriendsResource() {
    }

    @GET
    public Response doGetFriends(@Context HttpHeaders headers, @Context HttpServletRequest request) {
        LOG.fine("User attempt to get friends");
        final String token = TokenUtils.extractTokenFromHeaders(request);
        TokenInfo tokenInfo = verifyToken(token);
        if (tokenInfo == null) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(gson.toJson("Invalid credentials")).build();
        }
        LOG.fine("Valid token. Proceeding...");
        final String username = tokenInfo.getUsername();
        Key usernameKey = userKeyFactory.newKey(username);
        Transaction txn = datastore.newTransaction();
        try {
            Entity userOnDB = txn.get(usernameKey);
            final Response checkUserOnDB = checkUserOnDB(userOnDB);
            if (checkUserOnDB != null) {
                txn.rollback();
                return checkUserOnDB;
            }
            final Response getFriendsDBRequest = RestClientUtils.getFriends(username);
            if (getFriendsDBRequest.getStatus() == Response.Status.OK.getStatusCode()) {
                txn.commit();
                LOG.fine("Fetching of friends was successful");
            } else {
                txn.rollback();
                LOG.fine("Server Error");
            }
            return getFriendsDBRequest;
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

    private Response checkUserOnDB(Entity userOnDb) {
        if (userOnDb == null) {
            LOG.fine("User does not exist");
            return Response.status(Response.Status.NOT_FOUND).entity(gson.toJson("Not Found - User does not exist")).build();
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
