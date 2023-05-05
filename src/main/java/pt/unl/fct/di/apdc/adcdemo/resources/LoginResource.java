package pt.unl.fct.di.apdc.adcdemo.resources;

import com.google.cloud.Timestamp;
import com.google.cloud.datastore.*;
import com.google.cloud.datastore.StructuredQuery.CompositeFilter;
import com.google.cloud.datastore.StructuredQuery.OrderBy;
import com.google.cloud.datastore.StructuredQuery.PropertyFilter;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.commons.codec.digest.DigestUtils;
import pt.unl.fct.di.apdc.adcdemo.util.AuthToken;
import pt.unl.fct.di.apdc.adcdemo.util.LoginData;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

@Path("/login")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class LoginResource {

    private static final Logger LOG = Logger.getLogger(LoginResource.class.getName());
    private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    private final Gson g = new Gson();

    public LoginResource() {
    }

    private String hashPass(String pass) {
        return DigestUtils.sha3_512Hex(pass);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response doLogin(LoginData data, @Context HttpHeaders headers, @Context HttpServletRequest request) {
        LOG.fine("Login attempt by user " + data.username);
        Key userKey = datastore.newKeyFactory().setKind("User").newKey(data.username);
        Key loginRegistryKey = datastore.newKeyFactory().addAncestors(PathElement.of("User", data.username))
                .setKind("LoginRegistry").newKey("loginReg");
        Key loginLogKey = datastore.allocateId(datastore.newKeyFactory()
                .addAncestors(PathElement.of("User", data.username)).setKind("LoginLog").newKey());
        Transaction txn = datastore.newTransaction();
        try {
            Entity userOnDB = txn.get(userKey);
            if (userOnDB != null) {
                Entity loginRegistry = txn.get(loginRegistryKey);
                if (loginRegistry == null) {
                    // creating for the first time
                    loginRegistry = Entity.newBuilder(loginRegistryKey)
                            .set("success_logins", 0L)
                            .set("fail_logins", 0L)
                            .set("first_login", Timestamp.now())
                            .set("last_login", Timestamp.now())
                            .setNull("last_attempt")
                            .build();
                }
                final String givenPasswordHash = hashPass(data.password);
                if (userOnDB.getString("password").equals(givenPasswordHash)) {
                    AuthToken tokenAuth = new AuthToken(data.username);
                    Entity loginLog = Entity.newBuilder(loginLogKey)
                            .set("login_ip", request.getRemoteAddr())
                            .set("login_host", request.getRemoteHost())
                            .set("login_country", headers.getHeaderString("X-AppEngine-Country"))
                            .set("login_city", headers.getHeaderString("X-AppEngine-City"))
                            .set("login_time", Timestamp.now())
                            .set("login_coords",
                                    StringValue.newBuilder(headers.getHeaderString("X-AppEngine-CityLatLong"))
                                            .setExcludeFromIndexes(true).build())
                            .build();
                    Key loginAuthTokenKey = datastore.newKeyFactory()
                            .addAncestors(PathElement.of("User", data.username)).setKind("AuthToken").newKey(tokenAuth.tokenID);
                    Entity loginAuthToken = Entity.newBuilder(loginAuthTokenKey)
                            .set("tokenID", tokenAuth.tokenID)
                            .set("username", tokenAuth.username)
                            .set("creationDate", tokenAuth.creationDate)
                            .set("expirationDate", tokenAuth.expirationDate)
                            .set("isRevoked", tokenAuth.isRevoked)
                            .build();
                    Entity.Builder loginRegistryBuilder = Entity.newBuilder(loginRegistry);
                    loginRegistryBuilder
                            .set("success_logins", 1 + loginRegistry.getLong("success_logins"))
                            .set("last_login", Timestamp.now());
                    Entity loginRegistryNew = loginRegistryBuilder.build();
                    LOG.fine("Password is correct - Generated token and logs");
                    txn.put(loginLog, loginRegistryNew, loginAuthToken);
                    txn.commit();
                    return Response.ok(g.toJson(tokenAuth.tokenID)).build();
                } else {
                    Entity.Builder loginRegistryBuilder = Entity.newBuilder(loginRegistry);
                    loginRegistryBuilder
                            .set("fail_logins", 1 + loginRegistry.getLong("fail_logins"))
                            .set("last_attempt", Timestamp.now());
                    Entity loginRegistryNew = loginRegistryBuilder.build();
                    LOG.fine("Wrong password");
                    txn.put(loginRegistryNew);
                    txn.commit();
                    return Response.status(Status.UNAUTHORIZED).entity(g.toJson("Wrong credentials")).build();
                }
            } else {
                LOG.fine("User does not exist");
                txn.rollback();
                return Response.status(Status.UNAUTHORIZED).entity(g.toJson("Wrong credentials")).build();
            }
        } catch (Exception e) {
            txn.rollback();
            LOG.fine(e.getLocalizedMessage());
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(g.toJson("Server Error")).build();
        } finally {
            if (txn.isActive()) {
                txn.rollback();
                return Response.status(Status.INTERNAL_SERVER_ERROR).entity(g.toJson("Server Error")).build();
            }
        }
    }

    @POST
    @Path("/history")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response doLoginTimes(String usernameJSON, @Context HttpHeaders headers, @Context HttpServletRequest request) {
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
        Entity tokenOnDB = datastore.get(loginAuthTokenKey);
        if (tokenOnDB == null) {
            LOG.fine("Wrong credentials/token - not found");
            return Response.status(Response.Status.UNAUTHORIZED).entity(g.toJson("Invalid credentials")).build();
        } else {
            boolean isTokenValid = AuthToken.isValid(tokenOnDB.getLong("expirationDate"), tokenOnDB.getBoolean("isRevoked"));
            if (!isTokenValid) {
                LOG.fine("Expired token");
                return Response.status(Response.Status.UNAUTHORIZED).entity(g.toJson("Invalid credentials")).build();
            }
            LOG.fine("Valid token - proceeding");
        }
        Entity userOnDB = datastore.get(userKey);
        if (userOnDB == null) {
            LOG.fine("User dont exist");
            return Response.status(Status.UNAUTHORIZED).entity(g.toJson("Wrong credentials")).build();
        }
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, -1);
        Timestamp yesterday = Timestamp.of(cal.getTime());
        Query<Entity> query = Query.newEntityQueryBuilder()
                .setKind("LoginLog")
                .setFilter(CompositeFilter.and(
                        PropertyFilter.hasAncestor(userKey),
                        PropertyFilter.ge("login_time", yesterday)))
                .setOrderBy(OrderBy.desc("login_time"))
                .setLimit(3)
                .build();
        QueryResults<Entity> logs = datastore.run(query);
        List<Date> loginTimes = new ArrayList<>();
        logs.forEachRemaining(userLog -> {
            loginTimes.add(userLog.getTimestamp("login_time").toDate());
        });
        return Response.ok(g.toJson(loginTimes)).build();
    }
}
