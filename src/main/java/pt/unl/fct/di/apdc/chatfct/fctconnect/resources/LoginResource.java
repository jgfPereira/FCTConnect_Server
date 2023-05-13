package pt.unl.fct.di.apdc.chatfct.fctconnect.resources;

import com.google.cloud.Timestamp;
import com.google.cloud.datastore.*;
import com.google.cloud.datastore.StructuredQuery.CompositeFilter;
import com.google.cloud.datastore.StructuredQuery.OrderBy;
import com.google.cloud.datastore.StructuredQuery.PropertyFilter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
import javax.ws.rs.core.Response.Status;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

@Path("/login")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class LoginResource {

    private static final Logger LOG = Logger.getLogger(LoginResource.class.getName());
    private static final String DEFAULT_TIME_ZONE = "UTC";
    private static final int QUERY_LIMIT = 5;
    private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    private final KeyFactory userKeyFactory = datastore.newKeyFactory().setKind(DatastoreTypes.USER_TYPE);
    private final Gson gson = initGson();

    public LoginResource() {
    }

    private static Gson initGson() {
        return new GsonBuilder().registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter()).create();
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
            final boolean checkLoginRegistry = checkLoginRegistryOnDB(loginRegistry);
            loginRegistry = createLoginRegistryIfMissing(loginRegistry, loginRegistryKey);
            if (!checkLoginRegistry) {
                txn.put(loginRegistry);
            }
            final boolean checkPassword = checkPassword(data.password, userOnDB);
            if (checkPassword) {
                Timestamp time = Timestamp.now();
                Entity loginLog = createLoginLog(loginLogKey, headers, request, time);
                loginRegistry = updateLoginRegistryOnLoginSuccess(loginRegistry, time);
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
        return datastore.newKeyFactory().setKind(DatastoreTypes.LOGIN_REGISTRY_TYPE).addAncestors(PathElement.of(DatastoreTypes.USER_TYPE, username))
                .newKey(DatastoreTypes.LOGIN_REGISTRY_KEY);
    }

    private Key createLoginLogKey(String username) {
        return datastore.allocateId(datastore.newKeyFactory().setKind(DatastoreTypes.LOGIN_LOG_TYPE)
                .addAncestors(PathElement.of(DatastoreTypes.USER_TYPE, username)).newKey());
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
                    .set(DatastoreTypes.SUCCESS_LOGINS_ATTR, 0L)
                    .set(DatastoreTypes.FAIL_LOGINS_ATTR, 0L)
                    .setNull(DatastoreTypes.FIRST_LOGIN_ATTR)
                    .setNull(DatastoreTypes.LAST_LOGIN_ATTR)
                    .setNull(DatastoreTypes.LAST_ATTEMPT_ATTR)
                    .build();
        }
        return e;
    }

    private boolean checkPassword(String password, Entity userOnDB) {
        return userOnDB.getString(DatastoreTypes.PASSWORD_ATTR).equals(PasswordUtils.hashPass(password));
    }

    private Entity createLoginLog(Key key, HttpHeaders headers, HttpServletRequest request, Timestamp time) {
        return Entity.newBuilder(key)
                .set(DatastoreTypes.LOGIN_IP_ATTR, request.getRemoteAddr())
                .set(DatastoreTypes.LOGIN_HOST_ATTR, request.getRemoteHost())
                .set(DatastoreTypes.LOGIN_COUNTRY_ATTR, headers.getHeaderString("X-AppEngine-Country"))
                .set(DatastoreTypes.LOGIN_CITY_ATTR, headers.getHeaderString("X-AppEngine-City"))
                .set(DatastoreTypes.LOGIN_TIME_ATTR, time)
                .set(DatastoreTypes.LOGIN_COORDS_ATTR, StringValue.newBuilder(headers.getHeaderString("X-AppEngine-CityLatLong"))
                        .setExcludeFromIndexes(true).build())
                .build();
    }

    private Entity updateLoginRegistryOnLoginSuccess(Entity e, Timestamp time) {
        Entity.Builder eb = Entity.newBuilder(e);
        if (e.isNull(DatastoreTypes.FIRST_LOGIN_ATTR)) {
            eb.set(DatastoreTypes.FIRST_LOGIN_ATTR, time);
        }
        return eb.set(DatastoreTypes.SUCCESS_LOGINS_ATTR, 1 + e.getLong(DatastoreTypes.SUCCESS_LOGINS_ATTR))
                .set(DatastoreTypes.LAST_LOGIN_ATTR, time).build();
    }

    private Entity updateLoginRegistryOnLoginFail(Entity e) {
        return Entity.newBuilder(e)
                .set(DatastoreTypes.FAIL_LOGINS_ATTR, 1 + e.getLong(DatastoreTypes.FAIL_LOGINS_ATTR))
                .set(DatastoreTypes.LAST_ATTEMPT_ATTR, Timestamp.now()).build();
    }

    private String createToken(String username, Entity userOnDB) {
        final String role = userOnDB.getString(DatastoreTypes.ROLE_ATTR);
        return TokenUtils.createToken(username, role);
    }

    @POST
    @Path("/history")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response doLoginTimes(@Context HttpHeaders headers, @Context HttpServletRequest request) {
        final String token = TokenUtils.extractTokenFromHeaders(request);
        TokenInfo tokenInfo = verifyToken(token);
        if (tokenInfo == null) {
            return Response.status(Status.UNAUTHORIZED).entity(gson.toJson("Invalid credentials")).build();
        }
        Key userKey = userKeyFactory.newKey(tokenInfo.getUsername());
        Transaction txn = datastore.newTransaction();
        try {
            Entity userOnDB = txn.get(userKey);
            Response checkUserOnDB = checkUserOnDB(userOnDB);
            if (checkUserOnDB != null) {
                txn.rollback();
                return checkUserOnDB;
            }
            Query<Entity> loginLogsQuery = getLoginLogsQuery(userKey);
            QueryResults<Entity> loginLogs = txn.run(loginLogsQuery);
            List<LocalDateTime> loginTimes = getLoginTimes(loginLogs);
            txn.commit();
            LOG.fine("Login times were successfully fetched");
            return Response.ok(gson.toJson(loginTimes)).build();
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

    private TokenInfo verifyToken(final String token) {
        try {
            LOG.fine("Valid token. Proceeding...");
            return TokenUtils.verifyToken(token);
        } catch (JwtException ex) {
            LOG.warning("Invalid token --> " + ex.getLocalizedMessage());
            return null;
        }
    }

    private Query<Entity> getLoginLogsQuery(Key key) {
        LocalDate lastDay = LocalDate.now(ZoneId.of(DEFAULT_TIME_ZONE)).minusDays(1);
        Timestamp timestamp = Timestamp.ofTimeMicroseconds(lastDay.
                atStartOfDay(ZoneId.of(DEFAULT_TIME_ZONE)).toInstant().toEpochMilli() * 1000L);
        return Query.newEntityQueryBuilder()
                .setKind(DatastoreTypes.LOGIN_LOG_TYPE)
                .setFilter(CompositeFilter.and(
                        PropertyFilter.hasAncestor(key),
                        PropertyFilter.ge(DatastoreTypes.LOGIN_TIME_ATTR, timestamp)))
                .setOrderBy(OrderBy.desc(DatastoreTypes.LOGIN_TIME_ATTR))
                .setLimit(QUERY_LIMIT)
                .build();
    }

    private List<LocalDateTime> getLoginTimes(QueryResults<Entity> loginLogs) {
        List<LocalDateTime> loginTimes = new ArrayList<>();
        loginLogs.forEachRemaining(log -> loginTimes.add(convertDateToLocalDateTime(log.getTimestamp(DatastoreTypes.LOGIN_TIME_ATTR).toDate())));
        return loginTimes;
    }

    private LocalDateTime convertDateToLocalDateTime(Date date) {
        return date.toInstant().atZone(ZoneId.of(DEFAULT_TIME_ZONE)).toLocalDateTime();
    }
}