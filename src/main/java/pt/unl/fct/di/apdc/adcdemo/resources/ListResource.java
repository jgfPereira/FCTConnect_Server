package pt.unl.fct.di.apdc.adcdemo.resources;

import com.google.cloud.datastore.*;
import com.google.cloud.datastore.StructuredQuery.CompositeFilter;
import com.google.cloud.datastore.StructuredQuery.PropertyFilter;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import pt.unl.fct.di.apdc.adcdemo.util.AuthToken;
import pt.unl.fct.di.apdc.adcdemo.util.RolePermissions;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
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
    private final Gson g = new Gson();

    public ListResource() {
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response doList(String usernameJSON, @Context HttpHeaders headers, @Context HttpServletRequest request) {
        JsonObject jsonObj = new Gson().fromJson(usernameJSON, JsonObject.class);
        String username = null;
        if (jsonObj == null) {
            LOG.fine("Invalid data");
            return Response.status(Response.Status.BAD_REQUEST).entity(g.toJson("Bad Request - Invalid data")).build();
        } else {
            JsonElement jsonElement = jsonObj.get("username");
            if (jsonElement == null) {
                LOG.fine("Invalid data");
                return Response.status(Response.Status.BAD_REQUEST).entity(g.toJson("Bad Request - Invalid data")).build();
            }
            username = jsonElement.getAsString();
        }
        Key userKey = datastore.newKeyFactory().setKind("User").newKey(username);
        String headerToken = AuthToken.getAuthHeader(request);
        if (headerToken == null) {
            LOG.fine("Wrong credentials/token - no auth header or invalid auth type");
            return Response.status(Response.Status.UNAUTHORIZED).entity(g.toJson("Invalid credentials")).build();
        }
        Key loginAuthTokenKey = datastore.newKeyFactory()
                .addAncestors(PathElement.of("User", username)).setKind("AuthToken").newKey(headerToken);
        Transaction txn = datastore.newTransaction();
        try {
            Entity tokenOnDB = txn.get(loginAuthTokenKey);
            if (tokenOnDB == null) {
                LOG.fine("Wrong credentials/token - not found");
                txn.rollback();
                return Response.status(Response.Status.UNAUTHORIZED).entity(g.toJson("Invalid credentials")).build();
            } else {
                boolean isTokenValid = AuthToken.isValid(tokenOnDB.getLong("expirationDate"), tokenOnDB.getBoolean("isRevoked"));
                if (!isTokenValid) {
                    LOG.fine("Expired token");
                    txn.rollback();
                    return Response.status(Response.Status.UNAUTHORIZED).entity(g.toJson("Invalid credentials")).build();
                }
                LOG.fine("Valid token - proceeding");
            }
            Entity userOnDB = txn.get(userKey);
            if (userOnDB == null) {
                LOG.fine("User dont exist");
                txn.rollback();
                return Response.status(Response.Status.NOT_FOUND).entity(g.toJson("Not Found - User dont exist")).build();
            }
            final String userRole = userOnDB.getString("role");
            if (userRole.equals(RolePermissions.USER_ROLE)) {
                LOG.fine("Listing clients for role " + userRole);
                Query<ProjectionEntity> query = Query.newProjectionEntityQueryBuilder()
                        .setKind("User")
                        .setProjection("__key__", "name", "email")
                        .setFilter(CompositeFilter.and(CompositeFilter.and(
                                        PropertyFilter.eq("role", RolePermissions.USER_ROLE),
                                        PropertyFilter.eq("state", "ACTIVE")),
                                PropertyFilter.eq("visibility", "PUBLIC")))
                        .build();
                QueryResults<ProjectionEntity> queryRes = datastore.run(query);
                List<String> usersListing = new ArrayList<>();
                queryRes.forEachRemaining(e -> {
                    usersListing.add(e.getKey().getName() + " "
                            + e.getString("name") + " "
                            + e.getString("email"));
                });
                LOG.fine("Listing complete");
                txn.commit();
                return Response.ok(g.toJson(usersListing)).build();
            } else if (userRole.equals(RolePermissions.GBO_ROLE) || userRole.equals(RolePermissions.GA_ROLE)) {
                LOG.fine("Listing clients for role " + userRole);
                Query<Entity> query = Query.newEntityQueryBuilder()
                        .setKind("User")
                        .setFilter(PropertyFilter.eq("role", RolePermissions.USER_ROLE))
                        .build();
                QueryResults<Entity> queryRes = datastore.run(query);
                List<String> usersListing = new ArrayList<>();
                queryRes.forEachRemaining(e -> {
                    usersListing.add(e.getKey().getName() + " "
                            + e.getString("name") + " "
                            + e.getString("email") + " "
                            + e.getTimestamp("creationDate").toString() + " "
                            + e.getString("role") + " "
                            + e.getString("state") + " "
                            + e.getString("photo") + " "
                            + e.getString("visibility") + " "
                            + e.getString("homePhoneNum") + " "
                            + e.getString("phoneNum") + " "
                            + e.getString("occupation") + " "
                            + e.getString("placeOfWork") + " "
                            + e.getString("nif") + " "
                            + e.getString("street") + " "
                            + e.getString("locale") + " "
                            + e.getString("zipCode"));
                });
                LOG.fine("Listing complete");
                txn.commit();
                return Response.ok(g.toJson(usersListing)).build();
            } else if (userRole.equals(RolePermissions.GS_ROLE)) {
                LOG.fine("Listing clients for role " + userRole);
                Query<Entity> queryUserRole = Query.newEntityQueryBuilder()
                        .setKind("User")
                        .setFilter(PropertyFilter.eq("role", RolePermissions.USER_ROLE))
                        .build();
                QueryResults<Entity> queryResUserRole = datastore.run(queryUserRole);
                List<String> usersListing = new ArrayList<>();
                queryResUserRole.forEachRemaining(e -> {
                    usersListing.add(e.getKey().getName() + " "
                            + e.getString("name") + " "
                            + e.getString("email") + " "
                            + e.getTimestamp("creationDate").toString() + " "
                            + e.getString("role") + " "
                            + e.getString("state") + " "
                            + e.getString("photo") + " "
                            + e.getString("visibility") + " "
                            + e.getString("homePhoneNum") + " "
                            + e.getString("phoneNum") + " "
                            + e.getString("occupation") + " "
                            + e.getString("placeOfWork") + " "
                            + e.getString("nif") + " "
                            + e.getString("street") + " "
                            + e.getString("locale") + " "
                            + e.getString("zipCode"));
                });
                Query<Entity> queryGBORole = Query.newEntityQueryBuilder()
                        .setKind("User")
                        .setFilter(PropertyFilter.eq("role", RolePermissions.GBO_ROLE))
                        .build();
                QueryResults<Entity> queryResGBORole = datastore.run(queryGBORole);
                queryResGBORole.forEachRemaining(e -> {
                    usersListing.add(e.getKey().getName() + " "
                            + e.getString("name") + " "
                            + e.getString("email") + " "
                            + e.getTimestamp("creationDate").toString() + " "
                            + e.getString("role") + " "
                            + e.getString("state") + " "
                            + e.getString("photo") + " "
                            + e.getString("visibility") + " "
                            + e.getString("homePhoneNum") + " "
                            + e.getString("phoneNum") + " "
                            + e.getString("occupation") + " "
                            + e.getString("placeOfWork") + " "
                            + e.getString("nif") + " "
                            + e.getString("street") + " "
                            + e.getString("locale") + " "
                            + e.getString("zipCode"));
                });
                LOG.fine("Listing complete");
                txn.commit();
                return Response.ok(g.toJson(usersListing)).build();
            } else if (userRole.equals(RolePermissions.SU_ROLE)) {
                LOG.fine("Listing clients for role " + userRole);
                Query<Entity> query = Query.newEntityQueryBuilder()
                        .setKind("User")
                        .build();
                QueryResults<Entity> queryRes = datastore.run(query);
                List<String> usersListing = new ArrayList<>();
                queryRes.forEachRemaining(e -> {
                    usersListing.add(e.getKey().getName() + " "
                            + e.getString("name") + " "
                            + e.getString("email") + " "
                            + e.getTimestamp("creationDate").toString() + " "
                            + e.getString("role") + " "
                            + e.getString("state") + " "
                            + e.getString("photo") + " "
                            + e.getString("visibility") + " "
                            + e.getString("homePhoneNum") + " "
                            + e.getString("phoneNum") + " "
                            + e.getString("occupation") + " "
                            + e.getString("placeOfWork") + " "
                            + e.getString("nif") + " "
                            + e.getString("street") + " "
                            + e.getString("locale") + " "
                            + e.getString("zipCode"));
                });
                LOG.fine("Listing complete");
                txn.commit();
                return Response.ok(g.toJson(usersListing)).build();
            } else {
                LOG.fine("Unrecognized user role");
                txn.rollback();
                return Response.status(Response.Status.BAD_REQUEST).entity(g.toJson("Bad Request - Unrecognized user role")).build();
            }
        } catch (Exception e) {
            txn.rollback();
            LOG.severe(e.getLocalizedMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(g.toJson("Server Error")).build();
        } finally {
            if (txn.isActive()) {
                txn.rollback();
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(g.toJson("Server Error")).build();
            }
        }
    }
}
