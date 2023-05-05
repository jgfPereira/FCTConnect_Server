package pt.unl.fct.di.apdc.adcdemo.resources;

import com.google.cloud.datastore.*;
import com.google.gson.Gson;
import org.apache.commons.codec.digest.DigestUtils;
import pt.unl.fct.di.apdc.adcdemo.util.AuthToken;
import pt.unl.fct.di.apdc.adcdemo.util.NewPasswordData;

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

@Path("/newpass")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class NewPasswordResource {

    private static final Logger LOG = Logger.getLogger(NewPasswordResource.class.getName());
    private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    private final Gson g = new Gson();

    public NewPasswordResource() {
    }

    private String hashPass(String pass) {
        return DigestUtils.sha3_512Hex(pass);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response doNewPassword(NewPasswordData data, @Context HttpHeaders headers, @Context HttpServletRequest request) {
        LOG.fine("User attempt to change password");
        if (data == null || !data.validateData()) {
            LOG.fine("Invalid data: at least one field is null");
            return Response.status(Response.Status.BAD_REQUEST).entity(g.toJson("Bad Request - Invalid data")).build();
        } else if (!data.validateChanger()) {
            LOG.fine("Cant change others password");
            return Response.status(Response.Status.FORBIDDEN).entity(g.toJson("Forbidden - Cant change others password")).build();
        }
        Key userKey = datastore.newKeyFactory().setKind("User").newKey(data.username);
        String headerToken = AuthToken.getAuthHeader(request);
        if (headerToken == null) {
            LOG.fine("Wrong credentials/token - no auth header or invalid auth type");
            return Response.status(Response.Status.UNAUTHORIZED).entity(g.toJson("Invalid credentials")).build();
        }
        Key loginAuthTokenKey = datastore.newKeyFactory()
                .addAncestors(PathElement.of("User", data.username)).setKind("AuthToken").newKey(headerToken);
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
            Entity userOnDB = txn.get(userKey);
            if (userOnDB == null) {
                LOG.fine("User dont exist");
                txn.rollback();
                return Response.status(Response.Status.NOT_FOUND).entity(g.toJson("Not Found - User dont exist")).build();
            }
            final String passwordOnDB = userOnDB.getString("password");
            final String hashedPass = hashPass(data.password);
            if (!passwordOnDB.equals(hashedPass)) {
                LOG.fine("Incorrect password");
                txn.rollback();
                return Response.status(Response.Status.UNAUTHORIZED).entity(g.toJson("Unauthorized - Incorrect password")).build();
            } else if (!data.validatePasswords()) {
                LOG.fine("Passwords dont match");
                txn.rollback();
                return Response.status(Response.Status.BAD_REQUEST).entity(g.toJson("Bad Request - Passwords dont match")).build();
            } else if (!data.validatePasswordConstraints()) {
                LOG.fine("Passwords dont meet constraints");
                txn.rollback();
                return Response.status(Response.Status.BAD_REQUEST).entity(g.toJson("Bad Request - password dont meet constraints")).build();
            }
            Entity.Builder userChangedBuilder = Entity.newBuilder(txn.get(userKey));
            final String hashedNewPass = hashPass(data.newPassword);
            Entity userChanged = userChangedBuilder
                    .set("password", hashedNewPass)
                    .build();
            txn.put(userChanged);
            LOG.fine("Password changed successfully");
            txn.commit();
            return Response.ok(g.toJson("Password changed successfully")).build();
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