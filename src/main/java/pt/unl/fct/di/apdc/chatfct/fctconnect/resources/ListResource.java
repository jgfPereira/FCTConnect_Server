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

@Path("/list")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class ListResource {

    private static final Logger LOG = Logger.getLogger(ListResource.class.getName());
    private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    private final KeyFactory userKeyFactory = datastore.newKeyFactory().setKind(DatastoreTypes.USER_TYPE);
    private final MemcacheUtils memcacheUsers = MemcacheUtils.getMemcache(MemcacheUtils.USER_NAMESPACE);
    private final Gson gson = new Gson();

    public ListResource() {
    }

    private Entity getUserCached(String username) {
        final String key = String.format(MemcacheUtils.USER_ENTITY_KEY, username);
        return memcacheUsers.get(key, Entity.class);
    }

    private boolean isCached(Entity e) {
        return e != null;
    }

    @GET
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
            Entity userOnDB;
            final Entity userCached = getUserCached(username);
            final boolean isUserCached = isCached(userCached);
            if (isUserCached) {
                userOnDB = userCached;
            } else {
                userOnDB = txn.get(key);
                final Response checkUserOnDB = checkUserOnDB(userOnDB);
                if (checkUserOnDB != null) {
                    txn.rollback();
                    return checkUserOnDB;
                }
                memcacheUsers.put(String.format(MemcacheUtils.USER_ENTITY_KEY, username), userOnDB);
            }
            Query<ProjectionEntity> getUsersQuery = getUsersQuery();
            QueryResults<ProjectionEntity> allUsers = txn.run(getUsersQuery);
            List<ListedUser> usersList = getUsersList(txn, allUsers);
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
                        DatastoreTypes.ROLE_ATTR, DatastoreTypes.CREATION_DATE_ATTR, DatastoreTypes.PHOTO_ATTR)
                .setFilter(StructuredQuery.PropertyFilter.eq(DatastoreTypes.VISIBILITY_ATTR, RegexExp.PUBLIC_VISIBILITY_REGEX))
                .setOrderBy(StructuredQuery.OrderBy.asc(DatastoreTypes.NAME_ATTR))
                .build();
    }

    private List<ListedUser> getUsersList(Transaction txn, QueryResults<ProjectionEntity> allUsers) {
        List<ListedUser> usersList = new ArrayList<>();
        allUsers.forEachRemaining(user -> processSpecificUser(txn, usersList, user));
        return usersList;
    }

    private Key getSpecificUserKey(String username, String role) {
        return datastore.newKeyFactory().setKind(DatastoreTypes.formatRoleType(role))
                .addAncestors(PathElement.of(DatastoreTypes.USER_TYPE, username)).newKey(username);
    }

    private void processSpecificUser(Transaction txn, List<ListedUser> usersList, ProjectionEntity user) {
        final String role = user.getString(DatastoreTypes.ROLE_ATTR);
        final String username = user.getKey().getName();
        final Key key = getSpecificUserKey(username, role);
        final Entity specificUser = txn.get(key);
        if (role.equals(RegexExp.ROLE_STUDENT_REGEX)) {
            final String course = specificUser.getString(DatastoreTypes.COURSE_STUDENT_ATTR);
            final String year = specificUser.getString(DatastoreTypes.YEAR_STUDENT_ATTR);
            usersList.add(ListedUserStudent.createListedUserStudent(user, course, year));
        } else if (role.equals(RegexExp.ROLE_PROFESSOR_REGEX)) {
            final String department = specificUser.getString(DatastoreTypes.DEPARTMENT_ATTR);
            final String office = specificUser.getString(DatastoreTypes.OFFICE_PROFESSOR_ATTR);
            usersList.add(ListedUserProfessor.createListedUserProfessor(user, department, office));
        } else {
            final String department = specificUser.getString(DatastoreTypes.DEPARTMENT_ATTR);
            final String jobTitle = specificUser.getString(DatastoreTypes.JOB_TITLE_EMPLOYEE_ATTR);
            usersList.add(ListedUserEmployee.createListedUserEmployee(user, department, jobTitle));
        }
    }
}