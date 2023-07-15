package pt.unl.fct.di.apdc.chatfct.fctconnect.resources;

import com.google.cloud.datastore.*;
import com.google.gson.Gson;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.DatastoreTypes;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.MemcacheUtils;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.RestClientUtils;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.SaveDeviceTokenData;

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

@Path("/savedevicetoken")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class SaveDeviceTokenResource {

    private static final Logger LOG = Logger.getLogger(SaveDeviceTokenResource.class.getName());
    private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    private final KeyFactory userKeyFactory = datastore.newKeyFactory().setKind(DatastoreTypes.USER_TYPE);
    private final MemcacheUtils memcacheUsers = MemcacheUtils.getMemcache(MemcacheUtils.USER_NAMESPACE);
    private final Gson gson = new Gson();

    public SaveDeviceTokenResource() {
    }

    private Entity getUserCached(String username) {
        final String key = String.format(MemcacheUtils.USER_ENTITY_KEY, username);
        return memcacheUsers.get(key, Entity.class);
    }

    private boolean isCached(Entity e) {
        return e != null;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON + ";charset=utf-8")
    public Response doSaveDeviceToken(SaveDeviceTokenData data, @Context HttpHeaders headers, @Context HttpServletRequest request) {
        LOG.fine("User attempt to save device token");
        final Response checkData = checkData(data);
        if (checkData != null) {
            return checkData;
        }
        Key usernameKey = userKeyFactory.newKey(data.username);
        Transaction txn = datastore.newTransaction();
        try {
            Entity userOnDB;
            final Entity userCached = getUserCached(data.username);
            final boolean isUserCached = isCached(userCached);
            if (isUserCached) {
                userOnDB = userCached;
            } else {
                userOnDB = txn.get(usernameKey);
                Response checkUserOnDB = checkUserOnDB(userOnDB);
                if (checkUserOnDB != null) {
                    txn.rollback();
                    return checkUserOnDB;
                }
                memcacheUsers.put(String.format(MemcacheUtils.USER_ENTITY_KEY, data.username), userOnDB);
            }
            final Response postSaveDeviceTokenDBRequest = RestClientUtils.postSaveDeviceToken(data);
            if (postSaveDeviceTokenDBRequest.getStatus() == Response.Status.OK.getStatusCode()) {
                txn.commit();
                LOG.fine("Device token was saved");
            } else {
                txn.rollback();
                LOG.fine("Server Error");
            }
            return postSaveDeviceTokenDBRequest;
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

    private Response checkData(SaveDeviceTokenData data) {
        if (data == null || !data.validateData()) {
            LOG.fine("Invalid data: at least one field is null");
            return Response.status(Response.Status.BAD_REQUEST).entity(gson.toJson("Bad Request - invalid data")).build();
        }
        return null;
    }

    private Response checkUserOnDB(Entity userOnDB) {
        if (userOnDB == null) {
            LOG.fine("User does not exist");
            return Response.status(Response.Status.NOT_FOUND).entity(gson.toJson("Not Found - User does not exist")).build();
        }
        return null;
    }
}