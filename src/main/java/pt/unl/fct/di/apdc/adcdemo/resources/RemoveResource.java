package pt.unl.fct.di.apdc.adcdemo.resources;

import com.google.cloud.datastore.*;
import com.google.gson.Gson;
import pt.unl.fct.di.apdc.adcdemo.util.AuthToken;
import pt.unl.fct.di.apdc.adcdemo.util.RemoveData;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.logging.Logger;

@Path("/remove")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class RemoveResource {

    private static final Logger LOG = Logger.getLogger(RemoveResource.class.getName());
    private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    private final Gson g = new Gson();

    public RemoveResource() {
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response doRemove(RemoveData data, @Context HttpHeaders headers, @Context HttpServletRequest request) {
        LOG.fine("User attempt to remove other user");
        if (data == null || !data.validateData()) {
            LOG.fine("Invalid data: at least one field is null");
            return Response.status(Response.Status.BAD_REQUEST).entity(g.toJson("Bad Request - Invalid data")).build();
        }
        Key removerUserKey = datastore.newKeyFactory().setKind("User").newKey(data.removerUsername);
        Key removedUserKey = datastore.newKeyFactory().setKind("User").newKey(data.removedUsername);
        String headerToken = AuthToken.getAuthHeader(request);
        if (headerToken == null) {
            LOG.fine("Wrong credentials/token - no auth header or invalid auth type");
            return Response.status(Response.Status.UNAUTHORIZED).entity(g.toJson("Invalid credentials")).build();
        }
        Key loginAuthTokenKey = datastore.newKeyFactory()
                .addAncestors(PathElement.of("User", data.removerUsername)).setKind("AuthToken").newKey(headerToken);
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
            Entity removerOnDB = txn.get(removerUserKey);
            Entity removedOnDB = txn.get(removedUserKey);
            if (removerOnDB == null || removedOnDB == null) {
                LOG.fine("At least one of the users dont exist");
                txn.rollback();
                return Response.status(Response.Status.NOT_FOUND).entity(g.toJson("Not Found - At least one of the users dont exist")).build();
            }
            final String removerRole = removerOnDB.getString("role");
            final String removedRole = removedOnDB.getString("role");
            if (!data.validateRemovalPermissions(removerRole, removedRole)) {
                LOG.fine("Dont have permission to remove users of role " + removerRole);
                txn.rollback();
                return Response.status(Response.Status.FORBIDDEN).entity(g.toJson("Forbidden - Dont have permission to remove users of role " + removerRole)).build();
            }
            txn.delete(removedUserKey);
            LOG.fine("Remove done: " + data.removedUsername);
            txn.commit();
            return Response.ok(g.toJson("Remove done")).build();
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