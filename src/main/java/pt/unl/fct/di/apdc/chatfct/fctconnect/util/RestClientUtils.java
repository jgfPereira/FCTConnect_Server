package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

public final class RestClientUtils {

    private static final String SEND_FRIENDSHIP_REQUEST_URI_DB = "https://fctconnectdb.oa.r.appspot.com/rest/sendfriendshiprequest";
    private static final String ACCEPT_FRIENDSHIP_REQUEST_URI_DB = "https://fctconnectdb.oa.r.appspot.com/rest/acceptfriendshiprequest";
    private static final String REJECT_FRIENDSHIP_REQUEST_URI_DB = "https://fctconnectdb.oa.r.appspot.com/rest/rejectfriendshiprequest";
    private static final String REMOVE_FRIEND_URI_DB = "https://fctconnectdb.oa.r.appspot.com/rest/removefriend";
    private static final String ADD_ONLINE_PLAYER_URI_DB = "https://fctconnectdb.oa.r.appspot.com/rest/addonlineplayer";
    private static final String REMOVE_ONLINE_PLAYER_URI_DB = "https://fctconnectdb.oa.r.appspot.com/rest/removeonlineplayer";
    private static final String ADD_CHECKPOINT_URI_DB = "https://fctconnectdb.oa.r.appspot.com/rest/addcheckpoint";
    private static final String ADD_ITEM_TO_INVENTORY_URI_DB = "https://fctconnectdb.oa.r.appspot.com/rest/updateinventory/additem";
    private static final String DROP_ITEM_TO_INVENTORY_URI_DB = "https://fctconnectdb.oa.r.appspot.com/rest/updateinventory/dropitem";

    private RestClientUtils() {
    }

    public static Response postFriendshipRequest(SendFriendshipRequestData data) {
        return ClientBuilder.newClient()
                .target(SEND_FRIENDSHIP_REQUEST_URI_DB)
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.entity(data, MediaType.APPLICATION_JSON));
    }

    public static Response postAcceptFriendshipRequest(AcceptFriendshipRequestData data) {
        return ClientBuilder.newClient()
                .target(ACCEPT_FRIENDSHIP_REQUEST_URI_DB)
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.entity(data, MediaType.APPLICATION_JSON));
    }

    public static Response postRejectFriendshipRequest(RejectFriendshipRequestData data) {
        return ClientBuilder.newClient()
                .target(REJECT_FRIENDSHIP_REQUEST_URI_DB)
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.entity(data, MediaType.APPLICATION_JSON));
    }

    public static Response deleteFriend(RemoveFriendData data) {
        return ClientBuilder.newClient()
                .target(REMOVE_FRIEND_URI_DB)
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.entity(data, MediaType.APPLICATION_JSON));
    }

    public static Response postOnlinePlayer(AddOnlinePlayerData data) {
        return ClientBuilder.newClient()
                .target(ADD_ONLINE_PLAYER_URI_DB)
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.entity(data, MediaType.APPLICATION_JSON));
    }

    public static Response deleteOnlinePlayer(String username) {
        return ClientBuilder.newClient()
                .target(REMOVE_ONLINE_PLAYER_URI_DB)
                .path(username)
                .request(MediaType.APPLICATION_JSON)
                .delete();
    }

    public static Response postCheckpoint(AddCheckpointData data) {
        return ClientBuilder.newClient()
                .target(ADD_CHECKPOINT_URI_DB)
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.entity(data, MediaType.APPLICATION_JSON));
    }

    public static Response putItemOnInventory(UpdateCheckpointInventoryData data) {
        return ClientBuilder.newClient()
                .target(ADD_ITEM_TO_INVENTORY_URI_DB)
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.entity(data, MediaType.APPLICATION_JSON));
    }

    public static Response deleteItemFromInventory(UpdateCheckpointInventoryData data) {
        return ClientBuilder.newClient()
                .target(DROP_ITEM_TO_INVENTORY_URI_DB)
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.entity(data, MediaType.APPLICATION_JSON));
    }
}