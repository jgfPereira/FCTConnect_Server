package pt.unl.fct.di.apdc.chatfct.fctconnect.resources;

import com.google.cloud.datastore.*;
import com.google.gson.Gson;
import io.jsonwebtoken.JwtException;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.*;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

@Path("/searchevent")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class SearchEventResource {

    private static final String EVENT_NAME_QUERY_PARAM = "name";
    private static final Logger LOG = Logger.getLogger(SearchEventResource.class.getName());
    private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    private final KeyFactory userKeyFactory = datastore.newKeyFactory().setKind(DatastoreTypes.USER_TYPE);
    private final MemcacheUtils memcacheUsers = MemcacheUtils.getMemcache(MemcacheUtils.USER_NAMESPACE);
    private final Gson gson = new Gson();

    public SearchEventResource() {
    }

    private Entity getUserCached(String username) {
        final String key = String.format(MemcacheUtils.USER_ENTITY_KEY, username);
        return memcacheUsers.get(key, Entity.class);
    }

    private boolean isCached(Entity e) {
        return e != null;
    }

    @GET
    public Response doSearchEvent(@QueryParam(EVENT_NAME_QUERY_PARAM) String eventNameFuzzy, @Context HttpHeaders headers, @Context HttpServletRequest request) {
        LOG.fine("User attempt to search event by name");
        final String token = TokenUtils.extractTokenFromHeaders(request);
        TokenInfo tokenInfo = verifyToken(token);
        if (tokenInfo == null) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(gson.toJson("Invalid credentials")).build();
        }
        LOG.fine("Valid token. Proceeding...");
        final String username = tokenInfo.getUsername();
        final String role = tokenInfo.getRole();
        final Response checkData = checkData(eventNameFuzzy);
        if (checkData != null) {
            return checkData;
        }
        Key userKey = userKeyFactory.newKey(username);
        Transaction txn = datastore.newTransaction();
        try {
            Entity userOnDB;
            final Entity userCached = getUserCached(username);
            final boolean isUserCached = isCached(userCached);
            if (isUserCached) {
                userOnDB = userCached;
            } else {
                userOnDB = txn.get(userKey);
                final Response checkUserOnDB = checkUserOnDB(userOnDB);
                if (checkUserOnDB != null) {
                    txn.rollback();
                    return checkUserOnDB;
                }
                memcacheUsers.put(String.format(MemcacheUtils.USER_ENTITY_KEY, username), userOnDB);
            }
            Query<Entity> userEventsQuery = getUserEventsQuery(role);
            QueryResults<Entity> userEventsResults = txn.run(userEventsQuery);
            List<String> allEventsNames = getAllEventsNames(userEventsResults);
            final List<String> hits = FuzzySearcher.fuzzySearch(allEventsNames, DatastoreTypes.EVENT_NAME_ATTR, eventNameFuzzy);
            txn.commit();
            LOG.info("Event was fetched by name");
            return Response.ok(gson.toJson(hits)).build();
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

    private Response checkData(String eventNameFuzzy) {
        if (eventNameFuzzy == null || eventNameFuzzy.isBlank()) {
            LOG.fine("Invalid data: invalid query parameter");
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

    private Query<Entity> getUserEventsQuery(String role) {
        return Query.newEntityQueryBuilder()
                .setKind(DatastoreTypes.EVENT_TYPE)
                .setFilter(StructuredQuery.PropertyFilter.in(DatastoreTypes.EVENT_ACL_ATTR, ListValue.of(role)))
                .build();
    }

    private List<String> getAllEventsNames(QueryResults<Entity> userEventsResults) {
        List<String> names = new ArrayList<>();
        userEventsResults.forEachRemaining(event -> names.add(event.getString(DatastoreTypes.EVENT_NAME_ATTR)));
        return names;
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