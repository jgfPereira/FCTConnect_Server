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

@Path("/acceptfriendshiprequest")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class AcceptFriendshipRequestResource {

    private static final Logger LOG = Logger.getLogger(AcceptFriendshipRequestResource.class.getName());
    private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    private final KeyFactory userKeyFactory = datastore.newKeyFactory().setKind(DatastoreTypes.USER_TYPE);
    private final MemcacheUtils memcacheUsers = MemcacheUtils.getMemcache(MemcacheUtils.USER_NAMESPACE);
    private final Gson gson = new Gson();

    public AcceptFriendshipRequestResource() {
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
    public Response doAcceptFriendShipRequest(AcceptFriendshipRequestData data, @Context HttpHeaders headers, @Context HttpServletRequest request) {
        LOG.fine("User attempt to accept friendship request");
        final String token = TokenUtils.extractTokenFromHeaders(request);
        TokenInfo tokenInfo = verifyToken(token);
        if (tokenInfo == null) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(gson.toJson("Invalid credentials")).build();
        }
        LOG.fine("Valid token. Proceeding...");
        final String username = tokenInfo.getUsername();
        final Response checkData = checkData(data, username);
        if (checkData != null) {
            return checkData;
        }
        Key usernameKey = userKeyFactory.newKey(username);
        Key otherKey = userKeyFactory.newKey(data.requesterUsername);
        Transaction txn = datastore.newTransaction();
        try {
            Entity userOnDB;
            final Entity userCached = getUserCached(username);
            final boolean isUserCached = isCached(userCached);
            if (isUserCached) {
                userOnDB = userCached;
            } else {
                userOnDB = txn.get(usernameKey);
                final Response checkUserOnDB = checkUserOnDB(userOnDB);
                if (checkUserOnDB != null) {
                    txn.rollback();
                    return checkUserOnDB;
                }
                memcacheUsers.put(String.format(MemcacheUtils.USER_ENTITY_KEY, username), userOnDB);
            }
            Entity otherOnDB;
            final Entity otherCached = getUserCached(data.requesterUsername);
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
                memcacheUsers.put(String.format(MemcacheUtils.USER_ENTITY_KEY, data.requesterUsername), otherOnDB);
            }
            final Response postAcceptFriendshipRequestDBRequest = RestClientUtils.postAcceptFriendshipRequest(data);
            if (postAcceptFriendshipRequestDBRequest.getStatus() == Response.Status.OK.getStatusCode()) {
                txn.commit();
                LOG.fine("Friendship request was accepted");
            } else {
                txn.rollback();
                LOG.fine("Server Error");
            }
            return postAcceptFriendshipRequestDBRequest;
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

    private Response checkData(AcceptFriendshipRequestData data, String username) {
        if (data == null || !data.validateData()) {
            LOG.fine("Invalid data: at least one field is null");
            return Response.status(Response.Status.BAD_REQUEST).entity(gson.toJson("Bad Request - invalid data")).build();
        } else if (!data.isTokenSameUser(username)) {
            LOG.fine("Invalid data: usernames dont match");
            return Response.status(Response.Status.BAD_REQUEST).entity(gson.toJson("Bad Request - usernames dont match")).build();
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