package pt.unl.fct.di.apdc.chatfct.fctconnect.resources;

import com.google.cloud.tasks.v2.*;
import com.google.gson.Gson;
import com.google.protobuf.Timestamp;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.net.URI;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Clock;
import java.time.Instant;
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

    @POST
    @Path("/compute")
    public Response executeComputeTask() {
        try {
            Thread.sleep(60 * 1000 * 10); // 10 min
        } catch (Exception e) {
            return Response.serverError().build();
        }
        return Response.ok().build();
    }

    @GET
    @Path("/compute")
    public Response triggerExecuteComputeTask() throws IOException {
        String projectId = "fctconnect23";
        String queueName = "default";
        String location = "europe-west6";
        try (CloudTasksClient client = CloudTasksClient.create()) {
            String queuePath = QueueName.of(projectId, location, queueName).toString();
            Task.Builder taskBuilder = Task.newBuilder().setAppEngineHttpRequest(AppEngineHttpRequest.newBuilder()
                    .setRelativeUri("/rest/utils/compute").setHttpMethod(HttpMethod.POST).build());
            taskBuilder.setScheduleTime(
                    Timestamp.newBuilder().setSeconds(Instant.now(Clock.systemUTC()).getEpochSecond()));
            client.createTask(queuePath, taskBuilder.build());
        }
        return Response.ok().build();
    }
}