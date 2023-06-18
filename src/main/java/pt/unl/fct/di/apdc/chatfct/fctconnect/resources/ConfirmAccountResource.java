package pt.unl.fct.di.apdc.chatfct.fctconnect.resources;

import com.google.cloud.datastore.*;
import com.google.gson.Gson;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.DatastoreTypes;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.TokenUtils;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.util.logging.Logger;

@Path("/register/confirm")
public class ConfirmAccountResource {

    private static final String CODE_QUERY_PARAM = "code";
    private static final Logger LOG = Logger.getLogger(ConfirmAccountResource.class.getName());
    private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    private final KeyFactory userKeyFactory = datastore.newKeyFactory().setKind(DatastoreTypes.USER_TYPE);
    private final Gson gson = new Gson();

    public ConfirmAccountResource() {
    }

    @GET
    public Response doConfirmAccount(@QueryParam(CODE_QUERY_PARAM) String code, @Context HttpHeaders headers, @Context HttpServletRequest request) {
        Transaction txn = datastore.newTransaction();
        try {
            txn.commit();
            LOG.fine("Wrong password - updated logs");
            return Response.ok().build();
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

    private String createToken(String username, Entity user) {
        final String role = user.getString(DatastoreTypes.ROLE_ATTR);
        return TokenUtils.createToken(username, role);
    }
}