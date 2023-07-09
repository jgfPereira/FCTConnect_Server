package pt.unl.fct.di.apdc.chatfct.fctconnect.resources;

import com.google.gson.Gson;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

@Path("/utils")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class ComputationResource {

    private static final DateFormat FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSz");
    private final Gson gson = new Gson();

    public ComputationResource() {
    }

    @GET
    @Path("/hello")
    @Produces(MediaType.TEXT_PLAIN)
    public Response hello() {
        try {
            return Response.ok().entity("Hello").build();
        } catch (Exception e) {
            return Response.temporaryRedirect(URI.create("/error/500.html")).build();
        }
    }

    @GET
    @Path("/time")
    public Response getCurrentTime() {
        return Response.ok().entity(gson.toJson(FMT.format(new Date()))).build();
    }
}