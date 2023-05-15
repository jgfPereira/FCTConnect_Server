package pt.unl.fct.di.apdc.chatfct.fctconnect.resources;

import com.google.cloud.datastore.*;
import com.google.gson.Gson;
import io.jsonwebtoken.JwtException;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.*;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

@Path("/list")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class ListResource {

    private static final Logger LOG = Logger.getLogger(ListResource.class.getName());
    private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    private final KeyFactory userKeyFactory = datastore.newKeyFactory().setKind(DatastoreTypes.USER_TYPE);
    private final Gson gson = new Gson();

    public ListResource() {
    }

    @POST
    public Response doList(@Context HttpHeaders headers, @Context HttpServletRequest request) {
        LOG.fine("User attempt to list users");
        final String token = TokenUtils.extractTokenFromHeaders(request);
        TokenInfo tokenInfo = verifyToken(token);
        if (tokenInfo == null) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(gson.toJson("Invalid credentials")).build();
        }
        LOG.fine("Valid token. Proceeding...");
        final String username = tokenInfo.getUsername();
        Key key = userKeyFactory.newKey(username);
        Transaction txn = datastore.newTransaction();
        try {
            Entity userOnDB = txn.get(key);
            final Response checkUserOnDB = checkUserOnDB(userOnDB);
            if (checkUserOnDB != null) {
                txn.rollback();
                return checkUserOnDB;
            }
            Query<ProjectionEntity> getUsersQuery = getUsersQuery();
            QueryResults<ProjectionEntity> allUsers = txn.run(getUsersQuery);
            List<ListedUser> usersList = getUsersList(allUsers);
            txn.commit();
            LOG.fine("User listing complete");
            return Response.ok(gson.toJson(usersList)).build();
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

    private TokenInfo verifyToken(final String token) {
        try {
            return TokenUtils.verifyToken(token);
        } catch (JwtException ex) {
            LOG.warning("Invalid token --> " + ex.getLocalizedMessage());
            return null;
        }
    }

    private Response checkUserOnDB(Entity userOnDB) {
        if (userOnDB == null) {
            LOG.fine("User does not exist");
            return Response.status(Response.Status.NOT_FOUND).entity(gson.toJson("Not Found - username is not recognized")).build();
        }
        return null;
    }

    private Query<ProjectionEntity> getUsersQuery() {
        return Query.newProjectionEntityQueryBuilder()
                .setKind(DatastoreTypes.USER_TYPE)
                .setProjection(DatastoreTypes.NAME_ATTR, DatastoreTypes.EMAIL_ATTR,
                        DatastoreTypes.ROLE_ATTR, DatastoreTypes.CREATION_DATE_ATTR,
                        DatastoreTypes.VISIBILITY_ATTR)
                .setFilter(StructuredQuery.PropertyFilter.eq(DatastoreTypes.VISIBILITY_ATTR, RegexExp.PUBLIC_VISIBILITY_REGEX))
                .setOrderBy(StructuredQuery.OrderBy.asc(DatastoreTypes.NAME_ATTR))
                .build();
    }

    private List<ListedUser> getUsersList(QueryResults<ProjectionEntity> allUsers) {
        List<ListedUser> usersList = new ArrayList<>();
        allUsers.forEachRemaining(user -> usersList.add(ListedUser.createListedUser(user)));
        return usersList;
    }
}