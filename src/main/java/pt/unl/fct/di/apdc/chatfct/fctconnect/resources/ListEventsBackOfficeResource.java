package pt.unl.fct.di.apdc.chatfct.fctconnect.resources;

import com.google.cloud.datastore.*;
import com.google.gson.Gson;
import io.jsonwebtoken.JwtException;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.*;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

@Path("/backoffice/listevents")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class ListEventsBackOfficeResource {

    private static final Logger LOG = Logger.getLogger(ListEventsBackOfficeResource.class.getName());
    private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    private final KeyFactory backOfficeUserKeyFactory = datastore.newKeyFactory().setKind(DatastoreTypes.BACK_OFFICE_USER_TYPE);
    private final MemcacheUtils memcacheBackOfficeUsers = MemcacheUtils.getMemcache(MemcacheUtils.BACK_OFFICE_USER_NAMESPACE);
    private final Gson gson = new Gson();

    public ListEventsBackOfficeResource() {
    }

    private Entity getBackOfficeUserCached(String username) {
        final String key = String.format(MemcacheUtils.BACK_OFFICE_USER_ENTITY_KEY, username);
        return memcacheBackOfficeUsers.get(key, Entity.class);
    }

    private boolean isCached(Entity e) {
        return e != null;
    }

    @GET
    public Response doListEvents(@Context HttpHeaders headers, @Context HttpServletRequest request) {
        LOG.fine("Back office user attempt to list events");
        final String token = TokenUtils.extractTokenFromHeaders(request);
        TokenInfo tokenInfo = verifyToken(token);
        if (tokenInfo == null) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(gson.toJson("Invalid credentials")).build();
        }
        LOG.fine("Valid token. Proceeding...");
        final String username = tokenInfo.getUsername();
        final Key backOfficeUserKey = backOfficeUserKeyFactory.newKey(username);
        Transaction txn = datastore.newTransaction();
        try {
            Entity backOfficeUserOnDB;
            final Entity userCached = getBackOfficeUserCached(username);
            final boolean isUserCached = isCached(userCached);
            if (isUserCached) {
                backOfficeUserOnDB = userCached;
            } else {
                backOfficeUserOnDB = txn.get(backOfficeUserKey);
                final Response checkBackOfficeUserOnDB = checkBackOfficeUserOnDB(backOfficeUserOnDB);
                if (checkBackOfficeUserOnDB != null) {
                    txn.rollback();
                    return checkBackOfficeUserOnDB;
                }
                memcacheBackOfficeUsers.put(String.format(MemcacheUtils.BACK_OFFICE_USER_ENTITY_KEY, username), backOfficeUserOnDB);
            }
            Query<Entity> eventsQuery = getEventsQuery();
            QueryResults<Entity> eventsResults = txn.run(eventsQuery);
            List<ListedBackOfficeEvent> allEvents = getAllEvents(eventsResults);
            txn.commit();
            LOG.info("Events were successfully fetched");
            return Response.ok(gson.toJson(allEvents)).build();
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

    private Response checkBackOfficeUserOnDB(Entity backOfficeUserOnDB) {
        if (backOfficeUserOnDB == null) {
            LOG.fine("Back office user does not exist");
            return Response.status(Response.Status.NOT_FOUND).entity(gson.toJson("Not Found - username is not recognized")).build();
        }
        return null;
    }

    private Query<Entity> getEventsQuery() {
        return Query.newEntityQueryBuilder()
                .setKind(DatastoreTypes.EVENT_TYPE)
                .build();
    }

    private List<ListedBackOfficeEvent> getAllEvents(QueryResults<Entity> eventsResults) {
        List<ListedBackOfficeEvent> res = new ArrayList<>();
        eventsResults.forEachRemaining(event -> res.add(ListedBackOfficeEvent.createListedBackOfficeEvent(event)));
        return res;
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