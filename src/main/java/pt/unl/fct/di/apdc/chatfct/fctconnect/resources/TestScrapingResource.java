package pt.unl.fct.di.apdc.chatfct.fctconnect.resources;

import com.google.gson.Gson;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.JsoupUtils;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/scrape")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class TestScrapingResource {

    private final Gson gson = new Gson();

    public TestScrapingResource() {
    }

    @GET
    public Response doAddFriend() {
        return Response.ok(gson.toJson(JsoupUtils.scrape())).build();
    }
}