package pt.unl.fct.di.apdc.adcdemo.resources;

import com.google.cloud.datastore.*;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import pt.unl.fct.di.apdc.adcdemo.util.AuthToken;

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

@Path("/logout")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class LogoutResource {

    private static final Logger LOG = Logger.getLogger(LogoutResource.class.getName());
    private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    private final Gson g = new Gson();

    public LogoutResource() {
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response doLogout(String usernameJSON, @Context HttpHeaders headers, @Context HttpServletRequest request) {
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
            Entity userOnDB = datastore.get(userKey);
            if (userOnDB == null) {
                LOG.fine("User dont exist");
                txn.rollback();
                return Response.status(Response.Status.UNAUTHORIZED).entity(g.toJson("Wrong credentials")).build();
            }
            Entity.Builder tokenChangedBuilder = Entity.newBuilder(tokenOnDB);
            tokenChangedBuilder.set("isRevoked", true);
            Entity tokenChanged = tokenChangedBuilder.build();
            txn.put(tokenChanged);
            txn.commit();
            LOG.fine("Logout was successful - token revoked");
            return Response.ok(g.toJson("Logout was successful")).build();
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