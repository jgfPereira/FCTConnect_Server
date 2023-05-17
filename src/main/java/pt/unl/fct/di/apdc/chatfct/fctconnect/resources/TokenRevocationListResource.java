package pt.unl.fct.di.apdc.chatfct.fctconnect.resources;

import com.google.cloud.Timestamp;
import com.google.cloud.datastore.*;
import com.google.gson.Gson;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.DatastoreTypes;

import javax.ws.rs.core.Response;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class TokenRevocationListResource {

    private static final String DEFAULT_TIME_ZONE = "UTC";
    private static final Duration TWO_HOURS_DURATION = Duration.ofHours(2);
    private static final Logger LOG = Logger.getLogger(TokenRevocationListResource.class.getName());
    private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    private final KeyFactory tokenRevokedFactory = datastore.newKeyFactory().setKind(DatastoreTypes.TOKEN_REVOKED_TYPE);
    private final Gson gson = new Gson();

    public TokenRevocationListResource() {
    }

    public Response revokeToken(final String tokenID) {
        Key key = tokenRevokedFactory.newKey(tokenID);
        Transaction txn = datastore.newTransaction();
        try {
            Entity tokenRevokedOnDB = txn.get(key);
            final Response checkTokenRevokedOnDB = checkTokenRevokedOnDB(tokenRevokedOnDB);
            if (checkTokenRevokedOnDB != null) {
                txn.rollback();
                return checkTokenRevokedOnDB;
            }
            Entity tokenRevokedEntity = createTokenRevoked(key);
            txn.put(tokenRevokedEntity);
            txn.commit();
            LOG.fine("Logout was successful - token revoked");
            return Response.ok(gson.toJson("Logout was successful - token revoked")).build();
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

    public void cleanupRevokedTokens() {
        Transaction txn = datastore.newTransaction();
        try {
            Query<Entity> revokedTokensQuery = getRevokedTokensQuery();
            QueryResults<Entity> allRevokedTokens = txn.run(revokedTokensQuery);
            Key[] keys = selectExpiredRevokedTokens(allRevokedTokens);
            txn.delete(keys);
            txn.commit();
            LOG.fine("Cleanup of revoked tokens was successful");
        } catch (Exception e) {
            txn.rollback();
            LOG.severe(e.getLocalizedMessage());
        } finally {
            if (txn.isActive()) {
                txn.rollback();
            }
        }
    }

    private Response checkTokenRevokedOnDB(Entity e) {
        if (e != null) {
            LOG.fine("Token was already revoked");
            return Response.ok(gson.toJson("Logout was successful - token was already revoked")).build();
        }
        return null;
    }

    public Boolean isTokenRevoked(final String tokenID) {
        Key key = tokenRevokedFactory.newKey(tokenID);
        Transaction txn = datastore.newTransaction();
        try {
            Entity tokenRevokedOnDB = txn.get(key);
            txn.commit();
            return tokenRevokedOnDB != null ? Boolean.TRUE : Boolean.FALSE;
        } catch (Exception e) {
            txn.rollback();
            LOG.severe(e.getLocalizedMessage());
            return null;
        } finally {
            if (txn.isActive()) {
                txn.rollback();
            }
        }
    }

    private Entity createTokenRevoked(Key key) {
        return Entity.newBuilder(key)
                .set(DatastoreTypes.TOKEN_REVOCATION_DATE_ATTR, Timestamp.now())
                .build();
    }

    private Query<Entity> getRevokedTokensQuery() {
        return Query.newEntityQueryBuilder()
                .setKind(DatastoreTypes.TOKEN_REVOKED_TYPE)
                .build();
    }

    private Key[] selectExpiredRevokedTokens(QueryResults<Entity> allRevokedTokens) {
        List<Key> keys = new ArrayList<>();
        allRevokedTokens.forEachRemaining(token -> {
            if (checkSafeTokenRemoval(token)) {
                keys.add(token.getKey());
            }
        });
        return keys.toArray(new Key[keys.size()]);
    }

    private boolean checkSafeTokenRemoval(Entity token) {
        Timestamp revocationDate = token.getTimestamp(DatastoreTypes.TOKEN_REVOCATION_DATE_ATTR);
        Instant revocationDateInstant = setTimeZoneInstant(revocationDate.toSqlTimestamp().toInstant());
        Timestamp atLeastMaxExpirationDate = Timestamp.of(new java.sql.Timestamp(revocationDateInstant.plus(TWO_HOURS_DURATION).toEpochMilli()));
        Instant atLeastMaxExpirationInstant = setTimeZoneInstant(atLeastMaxExpirationDate.toSqlTimestamp().toInstant());
        Instant currentInstant = setTimeZoneInstant(Timestamp.now().toSqlTimestamp().toInstant());
        return currentInstant.isAfter(atLeastMaxExpirationInstant);
    }

    private Instant setTimeZoneInstant(Instant i) {
        return i.atZone(ZoneId.of(DEFAULT_TIME_ZONE)).toInstant();
    }
}