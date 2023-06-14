package pt.unl.fct.di.apdc.chatfct.fctconnect.resources;

import com.google.cloud.datastore.*;
import com.google.gson.Gson;
import io.jsonwebtoken.JwtException;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.PlayerData;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.TokenInfo;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.TokenUtils;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

@Path("/test")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class TestPlayerResource {

    private static final Logger LOG = Logger.getLogger(TestPlayerResource.class.getName());
    private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    private final KeyFactory playerKeyFactory = datastore.newKeyFactory().setKind("Player");
    private final Gson gson = new Gson();

    public TestPlayerResource() {
    }

    @POST
    @Path("/add")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response doAdd(PlayerData data, @Context HttpHeaders headers, @Context HttpServletRequest request) {
        LOG.fine("Test player locations");
        final String token = TokenUtils.extractTokenFromHeaders(request);
        TokenInfo tokenInfo = verifyToken(token);
        if (tokenInfo == null) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(gson.toJson("Invalid credentials")).build();
        }
        LOG.fine("Valid token. Proceeding...");
        final String username = tokenInfo.getUsername();
        Key key = playerKeyFactory.newKey(username);
        Transaction txn = datastore.newTransaction();
        try {
            Entity playerOnDB = txn.get(key);
            final Response checkPlayerOnDB = checkPlayerOnDB(playerOnDB);
            if (checkPlayerOnDB != null) {
                txn.rollback();
                return checkPlayerOnDB;
            }
            Entity player = createPlayer(data, key);
            txn.put(player);
            txn.commit();
            LOG.fine("Player created");
            return Response.ok(gson.toJson("Player added")).build();
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

    @GET
    @Path("/fetch")
    public Response doFetch(@Context HttpHeaders headers, @Context HttpServletRequest request) {
        LOG.fine("Test player locations");
        final String token = TokenUtils.extractTokenFromHeaders(request);
        TokenInfo tokenInfo = verifyToken(token);
        if (tokenInfo == null) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(gson.toJson("Invalid credentials")).build();
        }
        LOG.fine("Valid token. Proceeding...");
        Transaction txn = datastore.newTransaction();
        try {
            Query<Entity> query = Query.newEntityQueryBuilder().setKind("Player").build();
            QueryResults<Entity> allPlayers = txn.run(query);
            List<PlayerData> res = new ArrayList<>();
            allPlayers.forEachRemaining(p -> res.add(new PlayerData(p.getString("X"), p.getString("Y"))));
            txn.commit();
            LOG.fine("Players fetched");
            return Response.ok(gson.toJson(res)).build();
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

    private Response checkPlayerOnDB(Entity playerOnDB) {
        if (playerOnDB != null) {
            LOG.fine("Player already exists");
            return Response.status(Response.Status.CONFLICT).entity(gson.toJson("Conflict - player already added")).build();
        }
        return null;
    }

    private Entity createPlayer(PlayerData data, Key key) {
        Entity.Builder eb = Entity.newBuilder(key)
                .set("X", data.x)
                .set("Y", data.y);
        return eb.build();
    }

    private TokenInfo verifyToken(final String token) {
        try {
            return TokenUtils.verifyToken(token);
        } catch (JwtException ex) {
            LOG.warning("Invalid token --> " + ex.getLocalizedMessage());
            return null;
        }
    }
}
