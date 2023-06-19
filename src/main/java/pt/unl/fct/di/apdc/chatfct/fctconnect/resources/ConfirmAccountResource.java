package pt.unl.fct.di.apdc.chatfct.fctconnect.resources;

import com.google.cloud.Timestamp;
import com.google.cloud.datastore.*;
import com.google.gson.Gson;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.DatastoreTypes;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.TokenUtils;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

@Path("/register/confirm")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class ConfirmAccountResource {

    private static final String DEFAULT_TIME_ZONE = "UTC";
    private static final Duration TWO_HOURS_DURATION = Duration.ofHours(2);
    private static final String CODE_QUERY_PARAM = "code";
    private static final Logger LOG = Logger.getLogger(ConfirmAccountResource.class.getName());
    private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    private final KeyFactory userKeyFactory = datastore.newKeyFactory().setKind(DatastoreTypes.USER_TYPE);
    private final KeyFactory accountConfirmationFactory = datastore.newKeyFactory().setKind(DatastoreTypes.ACCOUNT_CONFIRMATION_TYPE);
    private final Gson gson = new Gson();

    public ConfirmAccountResource() {
    }

    @GET
    public Response doConfirmAccount(@QueryParam(CODE_QUERY_PARAM) String code, @Context HttpHeaders headers, @Context HttpServletRequest request) {
        final Response checkData = checkData(code);
        if (checkData != null) {
            return checkData;
        }
        final Key key = accountConfirmationFactory.newKey(code);
        Transaction txn = datastore.newTransaction();
        try {
            final Entity accountConfOnDB = txn.get(key);
            final Response checkAccountConfirmationOnDB = checkAccountConfirmationOnDB(accountConfOnDB);
            if (checkAccountConfirmationOnDB != null) {
                txn.rollback();
                return checkAccountConfirmationOnDB;
            }
            final boolean isExpired = checkExpirationDate(accountConfOnDB);
            if (isExpired) {
                txn.rollback();
                LOG.fine("Account confirmation has expired");
                return Response.status(Response.Status.UNAUTHORIZED).entity(gson.toJson("Unauthorized - account confirmation has expired")).build();
            }
            final Entity user = getUserOnDB(txn, accountConfOnDB);
            final Response checkUserOnDB = checkUserOnDB(user);
            if (checkUserOnDB != null) {
                txn.rollback();
                return checkUserOnDB;
            }
            final Entity confirmedUser = confirmUser(user);
            txn.update(confirmedUser);
            txn.delete(key);
            txn.commit();
            LOG.fine("Account Confirmed");
            final String token = createToken(user);
            return Response.ok("Account was confirmed").header(TokenUtils.AUTH_HEADER, TokenUtils.AUTH_TYPE + token).build();
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

    private Response checkData(String code) {
        if (code == null || code.isBlank()) {
            LOG.fine("Invalid data: invalid query parameter");
            return Response.status(Response.Status.BAD_REQUEST).entity(gson.toJson("Bad Request - invalid data")).build();
        }
        return null;
    }

    private Response checkAccountConfirmationOnDB(Entity accountConfOnDB) {
        if (accountConfOnDB == null) {
            LOG.fine("Account confirmation does not exist");
            return Response.status(Response.Status.NOT_FOUND).entity(gson.toJson("Not Found - account confirmation does not exist")).build();
        }
        return null;
    }

    private boolean checkExpirationDate(Entity accountConfOnDB) {
        Timestamp creationDate = accountConfOnDB.getTimestamp(DatastoreTypes.CREATION_DATE_ACCOUNT_CONF);
        Instant creationDateInstant = setTimeZoneInstant(creationDate.toSqlTimestamp().toInstant());
        Timestamp expirationDate = Timestamp.of(new java.sql.Timestamp(creationDateInstant.plus(TWO_HOURS_DURATION).toEpochMilli()));
        Instant expirationDateInstant = setTimeZoneInstant(expirationDate.toSqlTimestamp().toInstant());
        Instant currentInstant = setTimeZoneInstant(Timestamp.now().toSqlTimestamp().toInstant());
        return currentInstant.isAfter(expirationDateInstant);
    }

    private Instant setTimeZoneInstant(Instant i) {
        return i.atZone(ZoneId.of(DEFAULT_TIME_ZONE)).toInstant();
    }

    private Entity getUserOnDB(Transaction txn, Entity accountConfOnDB) {
        final String username = accountConfOnDB.getString(DatastoreTypes.USERNAME_ACCOUNT_CONF);
        Key key = userKeyFactory.newKey(username);
        return txn.get(key);
    }

    private Response checkUserOnDB(Entity userOnDB) {
        if (userOnDB == null) {
            LOG.fine("User does not exist");
            return Response.status(Response.Status.NOT_FOUND).entity(gson.toJson("Not Found - username is not recognized")).build();
        }
        return null;
    }

    private Entity confirmUser(Entity user) {
        return Entity.newBuilder(user)
                .set(DatastoreTypes.USER_STATUS_ATTR, DatastoreTypes.STATUS_CONFIRMED)
                .build();
    }

    private String createToken(Entity user) {
        final String username = user.getKey().getName();
        final String role = user.getString(DatastoreTypes.ROLE_ATTR);
        return TokenUtils.createToken(username, role);
    }

    public void cleanupExpiredAccountConfirmations() {
        Transaction txn = datastore.newTransaction();
        try {
            Query<Entity> accountConfirmations = getAccountConfirmations();
            QueryResults<Entity> allAccountConfs = txn.run(accountConfirmations);
            Key[] keys = selectExpiredAccountConfirmations(allAccountConfs);
            txn.delete(keys);
            txn.commit();
            LOG.fine("Cleanup of expired account confirmations was successful");
        } catch (Exception e) {
            txn.rollback();
            LOG.severe(e.getLocalizedMessage());
        } finally {
            if (txn.isActive()) {
                txn.rollback();
            }
        }
    }

    private Query<Entity> getAccountConfirmations() {
        return Query.newEntityQueryBuilder()
                .setKind(DatastoreTypes.ACCOUNT_CONFIRMATION_TYPE)
                .build();
    }

    private Key[] selectExpiredAccountConfirmations(QueryResults<Entity> allAccountConfs) {
        List<Key> keys = new ArrayList<>();
        allAccountConfs.forEachRemaining(conf -> {
            if (checkExpirationDate(conf)) {
                keys.add(conf.getKey());
            }
        });
        return keys.toArray(new Key[keys.size()]);
    }
}