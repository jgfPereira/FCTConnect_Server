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

@Path("/sendfriendshiprequest")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class SendFriendshipRequestResource {

    private static final Logger LOG = Logger.getLogger(SendFriendshipRequestResource.class.getName());
    private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    private final KeyFactory userKeyFactory = datastore.newKeyFactory().setKind(DatastoreTypes.USER_TYPE);
    private final MemcacheUtils memcacheUsers = MemcacheUtils.getMemcache(MemcacheUtils.USER_NAMESPACE);
    private final Gson gson = new Gson();

    public SendFriendshipRequestResource() {
    }

    private Entity getUserCached(String username) {
        final String key = String.format(MemcacheUtils.USER_ENTITY_KEY, username);
        return memcacheUsers.get(key, Entity.class);
    }

    private boolean isCached(Entity e) {
        return e != null;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON + ";charset=utf-8")
    public Response doSendFriendShipRequest(SendFriendshipRequestData data, @Context HttpHeaders headers, @Context HttpServletRequest request) {
        LOG.fine("User attempt to send friendship request");
        final String token = TokenUtils.extractTokenFromHeaders(request);
        TokenInfo tokenInfo = verifyToken(token);
        if (tokenInfo == null) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(gson.toJson("Invalid credentials")).build();
        }
        LOG.fine("Valid token. Proceeding...");
        final String requesterUsername = tokenInfo.getUsername();
        final Response checkData = checkData(data, requesterUsername);
        if (checkData != null) {
            return checkData;
        }
        Key requesterKey = userKeyFactory.newKey(requesterUsername);
        Key otherKey = userKeyFactory.newKey(data.username);
        Transaction txn = datastore.newTransaction();
        try {
            Entity requesterOnDB;
            final Entity userCached = getUserCached(requesterUsername);
            final boolean isUserCached = isCached(userCached);
            if (isUserCached) {
                requesterOnDB = userCached;
            } else {
                requesterOnDB = txn.get(requesterKey);
                final Response checkUserOnDB = checkUserOnDB(requesterOnDB);
                if (checkUserOnDB != null) {
                    txn.rollback();
                    return checkUserOnDB;
                }
                memcacheUsers.put(String.format(MemcacheUtils.USER_ENTITY_KEY, requesterUsername), requesterOnDB);
            }
            Entity otherOnDB;
            final Entity otherCached = getUserCached(data.username);
            final boolean isOtherCached = isCached(otherCached);
            if (isOtherCached) {
                otherOnDB = otherCached;
            } else {
                otherOnDB = txn.get(otherKey);
                final Response checkOtherOnDB = checkUserOnDB(otherOnDB);
                if (checkOtherOnDB != null) {
                    txn.rollback();
                    return checkOtherOnDB;
                }
                memcacheUsers.put(String.format(MemcacheUtils.USER_ENTITY_KEY, data.username), otherOnDB);
            }
            final Response postFriendshipRequestDBRequest = RestClientUtils.postFriendshipRequest(data);
            if (postFriendshipRequestDBRequest.getStatus() == Response.Status.OK.getStatusCode()) {
                txn.commit();
                LOG.fine("Friendship request was sent");
            } else {
                txn.rollback();
                LOG.fine("Server Error");
            }
            return postFriendshipRequestDBRequest;
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

    private Response checkData(SendFriendshipRequestData data, String username) {
        if (data == null || !data.validateData()) {
            LOG.fine("Invalid data: at least one field is null");
            return Response.status(Response.Status.BAD_REQUEST).entity(gson.toJson("Bad Request - invalid data")).build();
        } else if (!data.isTokenSameUser(username)) {
            LOG.fine("Invalid data: usernames dont match");
            return Response.status(Response.Status.BAD_REQUEST).entity(gson.toJson("Bad Request - usernames dont match")).build();
        } else if (data.isFriendshipRequestToSameUser()) {
            LOG.fine("Invalid data: cant send request to self");
            return Response.status(Response.Status.BAD_REQUEST).entity(gson.toJson("Bad Request - cant send request to self")).build();
        }
        return null;
    }

    private Response checkUserOnDB(Entity user) {
        if (user == null) {
            LOG.fine("User does not exist");
            return Response.status(Response.Status.NOT_FOUND).entity(gson.toJson("Not Found - username is not recognized")).build();
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