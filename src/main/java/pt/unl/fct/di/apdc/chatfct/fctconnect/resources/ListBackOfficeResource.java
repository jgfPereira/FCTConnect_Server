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

@Path("/backoffice/list")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class ListBackOfficeResource {

    private static final Logger LOG = Logger.getLogger(ListBackOfficeResource.class.getName());
    private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    private final KeyFactory backOfficeUserKeyFactory = datastore.newKeyFactory().setKind(DatastoreTypes.BACK_OFFICE_USER_TYPE);
    private final Gson gson = new Gson();

    public ListBackOfficeResource() {
    }

    @POST
    @Path("/regularusers")
    public Response doListRegularUsers(@Context HttpHeaders headers, @Context HttpServletRequest request) {
        LOG.fine("Back office user attempt to list regular users");
        final String token = TokenUtils.extractTokenFromHeaders(request);
        TokenInfo tokenInfo = verifyToken(token);
        if (tokenInfo == null) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(gson.toJson("Invalid credentials")).build();
        }
        LOG.fine("Valid token. Proceeding...");
        final String username = tokenInfo.getUsername();
        Key key = backOfficeUserKeyFactory.newKey(username);
        Transaction txn = datastore.newTransaction();
        try {
            Entity userOnDB = txn.get(key);
            final Response checkUserOnDB = checkUserOnDB(userOnDB);
            if (checkUserOnDB != null) {
                txn.rollback();
                return checkUserOnDB;
            }
            final Response checkAccountState = BackOfficeStateChecker.checkAccountState(username);
            if (!isResponseOK(checkAccountState)) {
                txn.rollback();
                return checkAccountState;
            }
            Query<Entity> getUsersQuery = getRegularUsersQuery();
            QueryResults<Entity> allUsers = txn.run(getUsersQuery);
            List<UserInfo> usersList = getRegularUsersList(txn, allUsers);
            txn.commit();
            LOG.fine("Regular user listing complete");
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

    private Response checkUserOnDB(Entity userOnDB) {
        if (userOnDB == null) {
            LOG.fine("User does not exist");
            return Response.status(Response.Status.NOT_FOUND).entity(gson.toJson("Not Found - username is not recognized")).build();
        }
        return null;
    }

    private boolean isResponseOK(Response r) {
        return r.getStatus() == Response.Status.OK.getStatusCode();
    }

    private Query<Entity> getRegularUsersQuery() {
        return Query.newEntityQueryBuilder()
                .setKind(DatastoreTypes.USER_TYPE)
                .setOrderBy(StructuredQuery.OrderBy.asc(DatastoreTypes.NAME_ATTR))
                .build();
    }

    private List<UserInfo> getRegularUsersList(Transaction txn, QueryResults<Entity> allUsers) {
        List<UserInfo> usersList = new ArrayList<>();
        allUsers.forEachRemaining(user -> addUserToListBasedOnRole(txn, usersList, user));
        return usersList;
    }

    private String getUserRole(Entity user) {
        return user.getString(DatastoreTypes.ROLE_ATTR);
    }

    private String getUserUsername(Entity user) {
        return user.getKey().getName();
    }

    private Key getSpecificUserKey(String username, String role) {
        return datastore.newKeyFactory().setKind(DatastoreTypes.formatRoleType(role))
                .addAncestors(PathElement.of(DatastoreTypes.USER_TYPE, username)).newKey(username);
    }

    private Entity getSpecificUser(Transaction txn, Entity user) {
        final String role = user.getString(DatastoreTypes.ROLE_ATTR);
        final String username = user.getKey().getName();
        final Key key = getSpecificUserKey(username, role);
        return txn.get(key);
    }

    private void addUserToListBasedOnRole(Transaction txn, List<UserInfo> usersList, Entity user) {
        final String role = getUserRole(user);
        if (role.matches(RegexExp.ROLE_STUDENT_REGEX)) {
            Entity studentUser = getSpecificUser(txn, user);
            usersList.add(UserInfoStudent.createUserInfoStudent(user, studentUser));
        } else if (role.matches(RegexExp.ROLE_PROFESSOR_REGEX)) {
            Entity professorUser = getSpecificUser(txn, user);
            usersList.add(UserInfoProfessor.createUserInfoProfessor(user, professorUser));
        } else {
            Entity employeeUser = getSpecificUser(txn, user);
            usersList.add(UserInfoEmployee.createUserInfoEmployee(user, employeeUser));
        }
    }

    @POST
    @Path("/backofficeusers")
    public Response doListBackOfficeUsers(@Context HttpHeaders headers, @Context HttpServletRequest request) {
        LOG.fine("Back office user attempt to list back office users");
        final String token = TokenUtils.extractTokenFromHeaders(request);
        TokenInfo tokenInfo = verifyToken(token);
        if (tokenInfo == null) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(gson.toJson("Invalid credentials")).build();
        }
        LOG.fine("Valid token. Proceeding...");
        final String username = tokenInfo.getUsername();
        final String role = tokenInfo.getRole();
        Key key = backOfficeUserKeyFactory.newKey(username);
        Transaction txn = datastore.newTransaction();
        try {
            Entity userOnDB = txn.get(key);
            final Response checkUserOnDB = checkUserOnDB(userOnDB);
            if (checkUserOnDB != null) {
                txn.rollback();
                return checkUserOnDB;
            }
            final Response checkAccountState = BackOfficeStateChecker.checkAccountState(username);
            if (!isResponseOK(checkAccountState)) {
                txn.rollback();
                return checkAccountState;
            }
            Query<Entity> getBackOfficeUsersQuery = getBackOfficeUsersQuery(role);
            QueryResults<Entity> allBackOfficeUsers = txn.run(getBackOfficeUsersQuery);
            List<ListedBackOfficeUser> backOfficeUsersList = getBackOfficeUsersList(allBackOfficeUsers, username);
            txn.commit();
            LOG.fine("Back office user listing complete");
            return Response.ok(gson.toJson(backOfficeUsersList)).build();
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

    private Query<Entity> getBackOfficeUsersQuery(String role) {
        EntityQuery.Builder eqb = Query.newEntityQueryBuilder()
                .setKind(DatastoreTypes.BACK_OFFICE_USER_TYPE)
                .setOrderBy(StructuredQuery.OrderBy.asc(DatastoreTypes.NAME_ATTR));
        if (!BackOfficeRolePermissions.canListAdmins(role)) {
            eqb.setFilter(StructuredQuery.PropertyFilter
                    .eq(DatastoreTypes.ROLE_ATTR, BackOfficeRolePermissions.MOD_ROLE));
        }
        return eqb.build();
    }

    private List<ListedBackOfficeUser> getBackOfficeUsersList(QueryResults<Entity> allBackOfficeUsers, String username) {
        List<ListedBackOfficeUser> backOfficeUsersList = new ArrayList<>();
        allBackOfficeUsers.forEachRemaining(user -> addBackOfficeUserToList(backOfficeUsersList, user, username));
        return backOfficeUsersList;
    }

    private void addBackOfficeUserToList(List<ListedBackOfficeUser> backOfficeUsersList, Entity user, String username) {
        final String listedUsername = getUserUsername(user);
        if (!BackOfficeRolePermissions.isSelf(username, listedUsername)) {
            backOfficeUsersList.add(ListedBackOfficeUser.createListedBackOfficeUser(user));
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
}