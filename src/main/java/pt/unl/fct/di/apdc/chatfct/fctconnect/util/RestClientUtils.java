package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

public final class RestClientUtils {

    private static final String ADD_FRIEND_URI_DB = "https://fctconnectdb.oa.r.appspot.com/rest/addfriend";
    private static final String REMOVE_FRIEND_URI_DB = "https://fctconnectdb.oa.r.appspot.com/rest/removefriend";
    private static final String GET_FRIENDS_URI_DB = "https://fctconnectdb.oa.r.appspot.com/rest/getfriends";

    private RestClientUtils() {
    }

    public static Response postFriend(AddFriendData data) {
        return ClientBuilder.newClient()
                .target(ADD_FRIEND_URI_DB)
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.entity(data, MediaType.APPLICATION_JSON));
    }

    public static Response deleteFriend(RemoveFriendData data) {
        return ClientBuilder.newClient()
                .target(REMOVE_FRIEND_URI_DB)
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.entity(data, MediaType.APPLICATION_JSON));
    }

    public static Response getFriends(String username) {
        return ClientBuilder.newClient()
                .target(GET_FRIENDS_URI_DB)
                .path(username)
                .request(MediaType.APPLICATION_JSON)
                .get();
    }
}