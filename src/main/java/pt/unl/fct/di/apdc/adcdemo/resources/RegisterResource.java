package pt.unl.fct.di.apdc.adcdemo.resources;

import com.google.cloud.Timestamp;
import com.google.cloud.datastore.*;
import com.google.gson.Gson;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.glassfish.jersey.media.multipart.file.FileDataBodyPart;
import pt.unl.fct.di.apdc.adcdemo.util.AddPhotoData;
import pt.unl.fct.di.apdc.adcdemo.util.RegisterData;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

@Path("/register")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class RegisterResource {

    private static final Logger LOG = Logger.getLogger(RegisterResource.class.getName());
    private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    private final Gson g = new Gson();

    public RegisterResource() {
    }

    private String hashPass(String pass) {
        return DigestUtils.sha3_512Hex(pass);
    }

    private void setWithNulls(Entity.Builder eb, String name, String value) {
        if (value == null) {
            eb.setNull(name);
        } else {
            eb.set(name, value);
        }
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response doRegister(RegisterData data) {
        LOG.fine("User attempt to register");
        if (data == null || !data.validateData()) {
            LOG.fine("Invalid data: at least one field is null");
            return Response.status(Response.Status.BAD_REQUEST).entity(g.toJson("Bad Request - Invalid data")).build();
        } else if (!data.validateEmail()) {
            LOG.fine("Email dont meet constraints");
            return Response.status(Response.Status.BAD_REQUEST).entity(g.toJson("Bad Request - Email dont meet constraints")).build();
        } else if (!data.validatePasswords()) {
            LOG.fine("Passwords dont match");
            return Response.status(Response.Status.BAD_REQUEST).entity(g.toJson("Bad Request - Passwords dont match")).build();
        } else if (!data.validatePasswordConstraints()) {
            LOG.fine("Passwords dont meet constraints");
            return Response.status(Response.Status.BAD_REQUEST).entity(g.toJson("Bad Request - password dont meet constraints")).build();
        } else if (!data.validateZipCode()) {
            LOG.fine("Zip code dont meet constraints");
            return Response.status(Response.Status.BAD_REQUEST).entity(g.toJson("Bad Request - Zip code dont meet constraints")).build();
        }
        Key userKey = datastore.newKeyFactory().setKind("User").newKey(data.username);
        Transaction txn = datastore.newTransaction();
        try {
            Entity userOnDB = txn.get(userKey);
            if (userOnDB != null) {
                LOG.fine("User already exists");
                txn.rollback();
                return Response.status(Response.Status.CONFLICT).entity(g.toJson("Conflict - username is already taken")).build();
            }
            Entity.Builder eb = Entity.newBuilder(userKey)
                    .set("password", hashPass(data.password))
                    .set("email", data.email)
                    .set("name", data.name)
                    .set("creationDate", Timestamp.now())
                    .set("role", RegisterData.DEFAULT_ROLE)
                    .set("state", RegisterData.DEFAULT_STATE);
            setWithNulls(eb, "photo", data.photo);
            setWithNulls(eb, "visibility", data.visibility);
            setWithNulls(eb, "homePhoneNum", data.homePhoneNum);
            setWithNulls(eb, "phoneNum", data.phoneNum);
            setWithNulls(eb, "occupation", data.occupation);
            setWithNulls(eb, "placeOfWork", data.placeOfWork);
            setWithNulls(eb, "nif", data.nif);
            setWithNulls(eb, "street", data.street);
            setWithNulls(eb, "locale", data.locale);
            setWithNulls(eb, "zipCode", data.zipCode);
            Entity user = eb.build();
            txn.put(user);
            LOG.fine("Register done: " + data.username);
            txn.commit();
            return Response.ok(g.toJson("Register done")).build();
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

    @POST
    @Path("/addphoto")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response doAddPhoto(AddPhotoData data) throws IOException {
        String urlBucket = "https://adc-demo-383221.oa.r.appspot.com/gcs/adc-demo-383221.appspot.com/"; // TODO change to new project
        LOG.fine("Adding profile picture of user");
        if (data == null || !data.validateData()) {
            LOG.fine("Invalid data: at least one field is null");
            return Response.status(Response.Status.BAD_REQUEST).entity(g.toJson("Bad Request - Invalid data")).build();
        }
        Client client = ClientBuilder.newClient(new ClientConfig());
        WebTarget webTarget = client.target(urlBucket + data.photo);
        File file = new File("tmp." + data.getExtension());
        FileUtils.writeByteArrayToFile(file, data.getPhotoBinary());
        final FileDataBodyPart filePart = new FileDataBodyPart(data.photo, file);
        FormDataMultiPart multipart = new FormDataMultiPart();
        multipart.bodyPart(filePart);
        Response r = webTarget.request().accept(MediaType.APPLICATION_JSON)
                .post(javax.ws.rs.client.Entity.entity(multipart, multipart.getMediaType()));
        if (r.getStatus() == Response.Status.OK.getStatusCode()) {
            return Response.ok(g.toJson("Profile picture added")).build();
        } else {
            return Response.status(r.getStatus()).entity(g.toJson("Profile picture not added")).build();
        }
    }
}