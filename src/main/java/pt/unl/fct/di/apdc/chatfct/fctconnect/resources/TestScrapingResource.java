package pt.unl.fct.di.apdc.chatfct.fctconnect.resources;

import com.google.gson.Gson;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.JsoupUtils;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/scrape")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class TestScrapingResource {

    private static final String PAGE_PATH_PARAM = "page";
    private final Gson gson = new Gson();

    public TestScrapingResource() {
    }

    @GET
    @Path("/{page}")
    public Response doAddFriend(@PathParam(PAGE_PATH_PARAM) String page) {
        return Response.ok(gson.toJson(JsoupUtils.scrape(Integer.parseInt(page)))).build();
    }
}