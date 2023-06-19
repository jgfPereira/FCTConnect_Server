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
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

@Path("/listevents")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class ListEventsResource {

    private static final Logger LOG = Logger.getLogger(ListEventsResource.class.getName());
    private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    private final KeyFactory userKeyFactory = datastore.newKeyFactory().setKind(DatastoreTypes.USER_TYPE);
    private final Gson gson = new Gson();

    public ListEventsResource() {
    }

    @GET
    public Response doListEvents(@Context HttpHeaders headers, @Context HttpServletRequest request) {
        LOG.fine("User attempt to list events");
        final String token = TokenUtils.extractTokenFromHeaders(request);
        TokenInfo tokenInfo = verifyToken(token);
        if (tokenInfo == null) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(gson.toJson("Invalid credentials")).build();
        }
        LOG.fine("Valid token. Proceeding...");
        final String username = tokenInfo.getUsername();
        final String role = tokenInfo.getRole();
        Key userKey = userKeyFactory.newKey(username);
        Transaction txn = datastore.newTransaction();
        try {
            final Entity userOnDB = txn.get(userKey);
            final Response checkUserOnDB = checkUserOnDB(userOnDB);
            if (checkUserOnDB != null) {
                txn.rollback();
                return checkUserOnDB;
            }
            Query<Entity> loginLogsQuery = getUserEventsQuery(role);
            QueryResults<Entity> userEventsResults = txn.run(loginLogsQuery);
            List<ListedEvent> userEvents = getUserEvents(userEventsResults);
            txn.commit();
            LOG.info("Events were successfully fetched");
            return Response.ok(gson.toJson(userEvents)).build();
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

    private List<ListedEvent> getUserEvents(QueryResults<Entity> userEventsResults) {
        List<ListedEvent> res = new ArrayList<>();
        userEventsResults.forEachRemaining(event -> res.add(ListedEvent.createListedEvent(event)));
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