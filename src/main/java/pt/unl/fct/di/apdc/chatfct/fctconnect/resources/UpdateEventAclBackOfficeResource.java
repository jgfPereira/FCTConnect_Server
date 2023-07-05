package pt.unl.fct.di.apdc.chatfct.fctconnect.resources;

import com.google.cloud.datastore.*;
import com.google.gson.Gson;
import io.jsonwebtoken.JwtException;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.*;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

@Path("/updateeventacl")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class UpdateEventAclBackOfficeResource {

    private static final Logger LOG = Logger.getLogger(UpdateEventAclBackOfficeResource.class.getName());
    private static final int ONE_TAG_COUNT = 1;
    private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    private final KeyFactory backOfficeUserKeyFactory = datastore.newKeyFactory().setKind(DatastoreTypes.BACK_OFFICE_USER_TYPE);
    private final KeyFactory eventKeyFactory = datastore.newKeyFactory().setKind(DatastoreTypes.EVENT_TYPE);
    private final MemcacheUtils memcacheBackOfficeUsers = MemcacheUtils.getMemcache(MemcacheUtils.BACK_OFFICE_USER_NAMESPACE);
    private final MemcacheUtils memcacheEvents = MemcacheUtils.getMemcache(MemcacheUtils.EVENTS_NAMESPACE);
    private final Gson gson = new Gson();

    public UpdateEventAclBackOfficeResource() {
    }

    private Entity getBackOfficeUserCached(String username) {
        final String key = String.format(MemcacheUtils.BACK_OFFICE_USER_ENTITY_KEY, username);
        return memcacheBackOfficeUsers.get(key, Entity.class);
    }

    private Entity getEventCached(String id) {
        final String key = String.format(MemcacheUtils.EVENT_ENTITY_KEY, id);
        return memcacheEvents.get(key, Entity.class);
    }

    private boolean isCached(Entity e) {
        return e != null;
    }

    @PUT
    @Path("/addtag")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response doAddEventAclTag(UpdateEventAclData data, @Context HttpHeaders headers, @Context HttpServletRequest request) {
        LOG.fine("Backoffice user attempt to update event acl - adding tag");
        final String token = TokenUtils.extractTokenFromHeaders(request);
        TokenInfo tokenInfo = verifyToken(token);
        if (tokenInfo == null) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(gson.toJson("Invalid credentials")).build();
        }
        LOG.fine("Valid token. Proceeding...");
        final String username = tokenInfo.getUsername();
        final String role = tokenInfo.getRole();
        final Response checkData = checkData(data);
        if (checkData != null) {
            return checkData;
        }
        Key backOfficeUserKey = backOfficeUserKeyFactory.newKey(username);
        Key eventKey = eventKeyFactory.newKey(data.id);
        Transaction txn = datastore.newTransaction();
        try {
            Entity backOfficeUserOnDB;
            final Entity userCached = getBackOfficeUserCached(username);
            final boolean isUserCached = isCached(userCached);
            if (isUserCached) {
                backOfficeUserOnDB = userCached;
            } else {
                backOfficeUserOnDB = txn.get(backOfficeUserKey);
                final Response checkBackOfficeUserOnDB = checkBackOfficeUserOnDB(backOfficeUserOnDB);
                if (checkBackOfficeUserOnDB != null) {
                    txn.rollback();
                    return checkBackOfficeUserOnDB;
                }
                memcacheBackOfficeUsers.put(String.format(MemcacheUtils.BACK_OFFICE_USER_ENTITY_KEY, username), backOfficeUserOnDB);
            }
            final Response canUpdateEvent = canUpdateEvent(role);
            if (canUpdateEvent != null) {
                txn.rollback();
                return canUpdateEvent;
            }
            Entity eventOnDB;
            final Entity eventCached = getEventCached(data.id);
            final boolean isEventCached = isCached(eventCached);
            if (isEventCached) {
                eventOnDB = eventCached;
            } else {
                eventOnDB = txn.get(eventKey);
                final Response checkEventOnDB = checkEventOnDB(eventOnDB);
                if (checkEventOnDB != null) {
                    txn.rollback();
                    return checkEventOnDB;
                }
            }
            final Entity updatedEvent = addAclTag(eventOnDB, data);
            memcacheEvents.put(String.format(MemcacheUtils.EVENT_ENTITY_KEY, data.id), updatedEvent);
            txn.update(updatedEvent);
            txn.commit();
            LOG.info("ACL tag was added to event");
            return Response.ok(gson.toJson("ACL tag was added - " + data.tag)).build();
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

    private Response checkData(UpdateEventAclData data) {
        if (data == null || !data.validateData()) {
            LOG.fine("Invalid data: at least one required field is null");
            return Response.status(Response.Status.BAD_REQUEST).entity(gson.toJson("Bad Request - invalid data")).build();
        } else if (!data.validateAclTag()) {
            LOG.fine("Invalid data: unrecognized acl tag");
            return Response.status(Response.Status.BAD_REQUEST).entity(gson.toJson("Bad Request - unrecognized acl tag")).build();
        }
        return null;
    }

    private Response checkBackOfficeUserOnDB(Entity backOfficeUserOnDB) {
        if (backOfficeUserOnDB == null) {
            LOG.fine("Backoffice user does not exist");
            return Response.status(Response.Status.NOT_FOUND).entity(gson.toJson("Not Found - Backoffice user does not exist")).build();
        }
        return null;
    }

    private Response canUpdateEvent(String role) {
        final boolean canUpdateEvent = BackOfficeRolePermissions.canUpdateEvent(role);
        if (!canUpdateEvent) {
            LOG.fine("Dont have permission to update event");
            return Response.status(Response.Status.FORBIDDEN).entity(gson.toJson("Forbidden - Dont have permission to update event")).build();
        }
        return null;
    }

    private Response checkEventOnDB(Entity eventOnDB) {
        if (eventOnDB == null) {
            LOG.fine("Event does not exist");
            return Response.status(Response.Status.NOT_FOUND).entity(gson.toJson("Not Found - Event does not exist")).build();
        }
        return null;
    }

    private Entity addAclTag(Entity event, UpdateEventAclData data) {
        Entity.Builder eb = Entity.newBuilder(event);
        final List<String> currAcl = getCurrentAcl(event);
        addIfAbsent(currAcl, data.tag);
        final String[] updatedAcl = currAcl.toArray(new String[currAcl.size()]);
        eb.set(DatastoreTypes.EVENT_ACL_ATTR, ListValue.of(DatastoreTypes.getAclFirst(updatedAcl), DatastoreTypes.getAclRest(updatedAcl)));
        return eb.build();
    }

    private void addIfAbsent(List<String> currAcl, String tag) {
        if (!currAcl.contains(tag))
            currAcl.add(tag);
    }

    private List<String> getCurrentAcl(Entity eventOnDB) {
        List<Value<?>> list = eventOnDB.getList(DatastoreTypes.EVENT_ACL_ATTR);
        List<String> res = new ArrayList<>();
        for (Value<?> v : list) {
            res.add((String) v.get());
        }
        return res;
    }

    private TokenInfo verifyToken(final String token) {
        try {
            return TokenUtils.verifyToken(token);
        } catch (JwtException ex) {
            LOG.warning("Invalid token --> " + ex.getLocalizedMessage());
            return null;
        }
    }

    @PUT
    @Path("/removetag")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response doRemoveEventAclTag(UpdateEventAclData data, @Context HttpHeaders headers, @Context HttpServletRequest request) {
        LOG.fine("Backoffice user attempt to update event acl - removing tag");
        final String token = TokenUtils.extractTokenFromHeaders(request);
        TokenInfo tokenInfo = verifyToken(token);
        if (tokenInfo == null) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(gson.toJson("Invalid credentials")).build();
        }
        LOG.fine("Valid token. Proceeding...");
        final String username = tokenInfo.getUsername();
        final String role = tokenInfo.getRole();
        final Response checkData = checkData(data);
        if (checkData != null) {
            return checkData;
        }
        Key backOfficeUserKey = backOfficeUserKeyFactory.newKey(username);
        Key eventKey = eventKeyFactory.newKey(data.id);
        Transaction txn = datastore.newTransaction();
        try {
            final Entity backOfficeUserOnDB = txn.get(backOfficeUserKey);
            final Response checkBackOfficeUserOnDB = checkBackOfficeUserOnDB(backOfficeUserOnDB);
            if (checkBackOfficeUserOnDB != null) {
                txn.rollback();
                return checkBackOfficeUserOnDB;
            }
            final Response canUpdateEvent = canUpdateEvent(role);
            if (canUpdateEvent != null) {
                txn.rollback();
                return canUpdateEvent;
            }
            final Entity eventOnDB = txn.get(eventKey);
            final Response checkEventOnDB = checkEventOnDB(eventOnDB);
            if (checkEventOnDB != null) {
                txn.rollback();
                return checkEventOnDB;
            }
            final Response resp = removeAclTag(txn, eventOnDB, data);
            txn.commit();
            return resp;
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

    private boolean isEventOneTagOnly(Entity eventOnDB) {
        return getCurrentAcl(eventOnDB).size() == ONE_TAG_COUNT;
    }

    private boolean isTagOnAcl(List<String> acl, String tag) {
        return acl.contains(tag);
    }

    private Response checkAclTags(Entity eventOnDB, List<String> acl, String tag) {
        if (!isTagOnAcl(acl, tag)) {
            LOG.fine("Not found - tag does not exist");
            return Response.status(Response.Status.NOT_FOUND).entity(gson.toJson("Not found - tag does not exist")).build();
        } else if (isEventOneTagOnly(eventOnDB)) {
            LOG.fine("Forbidden - can not remove the tag since events have to be associated with at least 1 tag");
            return Response.status(Response.Status.FORBIDDEN).entity(gson.toJson("Forbidden - can not remove the tag since events have to be associated with at least 1 tag")).build();
        }
        return null;
    }

    private Response removeAclTag(Transaction txn, Entity event, UpdateEventAclData data) {
        final List<String> acl = getCurrentAcl(event);
        final Response checkAclTags = checkAclTags(event, acl, data.tag);
        if (checkAclTags != null) {
            return checkAclTags;
        }
        Entity.Builder eb = Entity.newBuilder(event);
        acl.remove(data.tag);
        final String[] updatedAcl = acl.toArray(new String[acl.size()]);
        eb.set(DatastoreTypes.EVENT_ACL_ATTR, ListValue.of(DatastoreTypes.getAclFirst(updatedAcl), DatastoreTypes.getAclRest(updatedAcl)));
        txn.update(eb.build());
        LOG.info("ACL tag was removed - " + data.tag);
        return Response.ok(gson.toJson("ACL tag was removed - " + data.tag)).build();
    }
}