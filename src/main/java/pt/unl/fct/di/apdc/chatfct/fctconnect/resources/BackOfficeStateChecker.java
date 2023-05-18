package pt.unl.fct.di.apdc.chatfct.fctconnect.resources;

import com.google.cloud.datastore.*;
import com.google.gson.Gson;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.DatastoreTypes;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.RegexExp;

import javax.ws.rs.core.Response;
import java.util.logging.Logger;

public class BackOfficeStateChecker {

    private static final Logger LOG = Logger.getLogger(BackOfficeStateChecker.class.getName());
    private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    private final KeyFactory backOfficeUserKeyFactory = datastore.newKeyFactory().setKind(DatastoreTypes.BACK_OFFICE_USER_TYPE);
    private final Gson gson = new Gson();

    public BackOfficeStateChecker() {
    }

    public static Response checkAccountState(String username) {
        return new BackOfficeStateChecker().checkState(username);
    }

    private Response checkState(final String username) {
        LOG.fine("Checking back office user state");
        Key key = backOfficeUserKeyFactory.newKey(username);
        Transaction txn = datastore.newTransaction();
        try {
            Entity backOfficeUserOnDB = txn.get(key);
            final boolean isApproved = isApproved(backOfficeUserOnDB);
            if (!isApproved) {
                txn.rollback();
                LOG.fine("Account has not been approved yet");
                return Response.status(Response.Status.UNAUTHORIZED).entity(gson.toJson("Unauthorized - account approval is pending")).build();
            }
            txn.commit();
            LOG.fine("Account is approved");
            return Response.ok(gson.toJson("Account is approved")).build();
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

    private boolean isApproved(Entity user) {
        final String state = user.getString(DatastoreTypes.STATE_ATTR);
        return state.equals(RegexExp.APPROVED_STATE_REGEX);
    }
}