package pt.unl.fct.di.apdc.chatfct.fctconnect.resources;

import com.google.cloud.Timestamp;
import com.google.cloud.datastore.*;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.DatastoreTypes;

import java.util.logging.Logger;

public class TokenRevocationListResource {

    private static final Logger LOG = Logger.getLogger(TokenRevocationListResource.class.getName());
    private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    private final KeyFactory tokenRevokedFactory = datastore.newKeyFactory().setKind(DatastoreTypes.TOKEN_REVOKED_TYPE);

    public TokenRevocationListResource() {
    }

    public void revokeToken(final String tokenID) {
        Key key = tokenRevokedFactory.newKey(tokenID);
        Transaction txn = datastore.newTransaction();
        try {
            Entity tokenRevokedOnDB = txn.get(key);
            if (tokenRevokedOnDB != null) {
                txn.rollback();
                LOG.fine("Token was already revoked");
                return;
            }
            Entity tokenRevokedEntity = createTokenRevoked(key);
            txn.put(tokenRevokedEntity);
            txn.commit();
            LOG.fine("Token is successfully revoked");
        } catch (Exception e) {
            txn.rollback();
            LOG.severe(e.getLocalizedMessage());
        } finally {
            if (txn.isActive()) {
                txn.rollback();
            }
        }
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
}