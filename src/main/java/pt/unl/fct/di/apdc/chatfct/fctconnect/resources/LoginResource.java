package pt.unl.fct.di.apdc.chatfct.fctconnect.resources;

import com.google.cloud.Timestamp;
import com.google.cloud.datastore.*;
import com.google.cloud.datastore.StructuredQuery.CompositeFilter;
import com.google.cloud.datastore.StructuredQuery.OrderBy;
import com.google.cloud.datastore.StructuredQuery.PropertyFilter;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.AuthToken;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.LoginData;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.PasswordUtils;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.TokenUtils;

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

    private static final String USER_TYPE = "User";
    private static final Logger LOG = Logger.getLogger(LoginResource.class.getName());
    private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    private final KeyFactory userKeyFactory = datastore.newKeyFactory().setKind(USER_TYPE);
    private final Gson gson = new Gson();

    public LoginResource() {
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response doLogin(LoginData data, @Context HttpHeaders headers, @Context HttpServletRequest request) {
        LOG.fine("Login attempt by user " + data.username);
        Response checkData = checkData(data);
        if (checkData != null) {
            return checkData;
        }
        Key userKey = userKeyFactory.newKey(data.username);
        Key loginRegistryKey = createLoginRegistryKey(data.username);
        Key loginLogKey = createLoginLogKey(data.username);
        Transaction txn = datastore.newTransaction();
        try {
            Entity userOnDB = txn.get(userKey);
            Response checkUserOnDB = checkUserOnDB(userOnDB);
            if (checkUserOnDB != null) {
                txn.rollback();
                return checkUserOnDB;
            }
            Entity loginRegistry = txn.get(loginRegistryKey);
            loginRegistry = createLoginRegistryIfMissing(loginRegistry, loginRegistryKey);
            final boolean checkPassword = checkPassword(data.password, userOnDB);
            if (checkPassword) {
                Entity loginLog = createLoginLog(loginLogKey, headers, request);
                loginRegistry = updateLoginRegistryOnLoginSuccess(loginRegistry);
                txn.update(loginRegistry);
                txn.put(loginLog);
                txn.commit();
                final String token = createToken(data.username, userOnDB);
                LOG.fine("Correct password - generated token and logs");
                return Response.ok(gson.toJson(token)).header(TokenUtils.AUTH_HEADER, TokenUtils.AUTH_TYPE + token).build();
            } else {
                loginRegistry = updateLoginRegistryOnLoginFail(loginRegistry);
                txn.update(loginRegistry);
                txn.commit();
                LOG.fine("Wrong password - updated logs");
                return Response.status(Status.UNAUTHORIZED).entity(gson.toJson("Wrong credentials")).build();
            }
        } catch (Exception e) {
            txn.rollback();
            LOG.severe(e.getLocalizedMessage());
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(gson.toJson("Server Error")).build();
        } finally {
            if (txn.isActive()) {
                txn.rollback();
                return Response.status(Status.INTERNAL_SERVER_ERROR).entity(gson.toJson("Server Error")).build();
            }
        }
    }

    private Response checkData(LoginData data) {
        final boolean check = data != null && data.validateData();
        if (!check) {
            LOG.fine("Invalid data: at least one required field is null");
        }
        return check ? null : Response.status(Status.BAD_REQUEST).entity(gson.toJson("Bad Request - invalid data")).build();
    }

    private Key createLoginRegistryKey(String username) {
        return datastore.newKeyFactory().setKind("LoginRegistry").addAncestors(PathElement.of(USER_TYPE, username))
                .newKey("loginReg");
    }

    private Key createLoginLogKey(String username) {
        return datastore.allocateId(datastore.newKeyFactory().setKind("LoginLog")
                .addAncestors(PathElement.of(USER_TYPE, username)).newKey());
    }

    private boolean checkLoginRegistryOnDB(Entity e) {
        return e != null;
    }

    private Response checkUserOnDB(Entity userOnDB) {
        if (userOnDB == null) {
            LOG.fine("User does not exist");
            return Response.status(Status.NOT_FOUND).entity(gson.toJson("Not Found - username is not recognized")).build();
        }
        return null;
    }

    private Entity createLoginRegistryIfMissing(Entity e, Key key) {
        final boolean checkLoginRegistryOnDB = checkLoginRegistryOnDB(e);
        if (!checkLoginRegistryOnDB) {
            return Entity.newBuilder(key)
                    .set("success_logins", 0L)
                    .set("fail_logins", 0L)
                    .set("first_login", Timestamp.now())
                    .set("last_login", Timestamp.now())
                    .setNull("last_attempt")
                    .build();
        }
        return e;
    }

    private boolean checkPassword(String password, Entity userOnDB) {
        return userOnDB.getString("password").equals(PasswordUtils.hashPass(password));
    }

    private Entity createLoginLog(Key key, HttpHeaders headers, HttpServletRequest request) {
        return Entity.newBuilder(key)
                .set("login_ip", request.getRemoteAddr())
                .set("login_host", request.getRemoteHost())
                .set("login_country", headers.getHeaderString("X-AppEngine-Country"))
                .set("login_city", headers.getHeaderString("X-AppEngine-City"))
                .set("login_time", Timestamp.now())
                .set("login_coords", StringValue.newBuilder(headers.getHeaderString("X-AppEngine-CityLatLong"))
                        .setExcludeFromIndexes(true).build())
                .build();
    }

    private Entity updateLoginRegistryOnLoginSuccess(Entity e) {
        return Entity.newBuilder(e)
                .set("success_logins", 1 + e.getLong("success_logins"))
                .set("last_login", Timestamp.now()).build();
    }

    private Entity updateLoginRegistryOnLoginFail(Entity e) {
        return Entity.newBuilder(e)
                .set("fail_logins", 1 + e.getLong("fail_logins"))
                .set("last_attempt", Timestamp.now()).build();
    }

    private String createToken(String username, Entity userOnDB) {
        final String role = userOnDB.getString("role");
        return TokenUtils.createToken(username, role);
    }

    @POST
    @Path("/history")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response doLoginTimes(String usernameJSON, @Context HttpHeaders headers, @Context HttpServletRequest request) {
        JsonObject jsonObj = new Gson().fromJson(usernameJSON, JsonObject.class);
        String username = null;
        if (jsonObj == null) {
            LOG.fine("Invalid data");
            return Response.status(Response.Status.BAD_REQUEST).entity(gson.toJson("Bad Request - Invalid data")).build();
        } else {
            JsonElement jsonElement = jsonObj.get("username");
            if (jsonElement == null) {
                LOG.fine("Invalid data");
                return Response.status(Response.Status.BAD_REQUEST).entity(gson.toJson("Bad Request - Invalid data")).build();
            }
            username = jsonElement.getAsString();
        }
        Key userKey = datastore.newKeyFactory().setKind("User").newKey(username);
        String headerToken = AuthToken.getAuthHeader(request);
        if (headerToken == null) {
            LOG.fine("Wrong credentials/token - no auth header or invalid auth type");
            return Response.status(Response.Status.UNAUTHORIZED).entity(gson.toJson("Invalid credentials")).build();
        }
        Key loginAuthTokenKey = datastore.newKeyFactory()
                .addAncestors(PathElement.of("User", username)).setKind("AuthToken").newKey(headerToken);
        Entity tokenOnDB = datastore.get(loginAuthTokenKey);
        if (tokenOnDB == null) {
            LOG.fine("Wrong credentials/token - not found");
            return Response.status(Response.Status.UNAUTHORIZED).entity(gson.toJson("Invalid credentials")).build();
        } else {
            boolean isTokenValid = AuthToken.isValid(tokenOnDB.getLong("expirationDate"), tokenOnDB.getBoolean("isRevoked"));
            if (!isTokenValid) {
                LOG.fine("Expired token");
                return Response.status(Response.Status.UNAUTHORIZED).entity(gson.toJson("Invalid credentials")).build();
            }
            LOG.fine("Valid token - proceeding");
        }
        Entity userOnDB = datastore.get(userKey);
        if (userOnDB == null) {
            LOG.fine("User dont exist");
            return Response.status(Status.UNAUTHORIZED).entity(gson.toJson("Wrong credentials")).build();
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
        return Response.ok(gson.toJson(loginTimes)).build();
    }
}
