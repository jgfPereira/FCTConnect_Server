package pt.unl.fct.di.apdc.chatfct.fctconnect.resources;

import com.google.cloud.Timestamp;
import com.google.cloud.datastore.*;
import com.google.gson.Gson;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.glassfish.jersey.media.multipart.file.FileDataBodyPart;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.AddPhotoData;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.RegisterBasicData;

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

    private static final String USER_TYPE = "User";
    private static final Logger LOG = Logger.getLogger(RegisterResource.class.getName());
    private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    private final KeyFactory userKeyFactory = datastore.newKeyFactory().setKind(USER_TYPE);
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

    private void setVisibility(Entity.Builder eb, String value) {
        final String val = value == null ? RegisterBasicData.DEFAULT_VISIBILITY : value;
        eb.set("visibility", val);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response doRegister(RegisterBasicData data) {
        LOG.fine("User attempt to register");
        Response checkData = checkData(data);
        if (checkData != null) {
            return checkData;
        }
        Key userKey = userKeyFactory.newKey(data.username);
        Transaction txn = datastore.newTransaction();
        try {
            Entity userOnDB = txn.get(userKey);
            Response checkUserOnDB = checkUserOnDB(txn, userOnDB);
            if (checkUserOnDB != null) {
                return checkUserOnDB;
            }
            Entity user = createUser(data, userKey);
            txn.put(user);
            txn.commit();
            LOG.fine("Register done: " + data.username);
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

    private Response checkData(RegisterBasicData data) {
        if (data == null || !data.validateData()) {
            LOG.fine("Invalid data: at least one field is null");
            return Response.status(Response.Status.BAD_REQUEST).entity(g.toJson("Bad Request - invalid data")).build();
        } else if (!data.validateEmail()) {
            LOG.fine("Email dont meet constraints");
            return Response.status(Response.Status.BAD_REQUEST).entity(g.toJson("Bad Request - email dont meet constraints")).build();
        } else if (!data.comparePasswords()) {
            LOG.fine("Passwords dont match");
            return Response.status(Response.Status.BAD_REQUEST).entity(g.toJson("Bad Request - passwords dont match")).build();
        } else if (!data.validatePassword()) {
            LOG.fine("Passwords dont meet constraints");
            return Response.status(Response.Status.BAD_REQUEST).entity(g.toJson("Bad Request - password dont meet constraints")).build();
        } else if (!data.validateBirthDate()) {
            LOG.fine("Birth date dont meet constraints");
            return Response.status(Response.Status.BAD_REQUEST).entity(g.toJson("Bad Request - birth date dont meet constraints")).build();
        } else if (!data.validatePhoneNum()) {
            LOG.fine("Phone number dont meet constraints");
            return Response.status(Response.Status.BAD_REQUEST).entity(g.toJson("Bad Request - phone number dont meet constraints")).build();
        } else if (!data.validateNif()) {
            LOG.fine("NIF dont meet constraints");
            return Response.status(Response.Status.BAD_REQUEST).entity(g.toJson("Bad Request - NIF dont meet constraints")).build();
        } else if (!data.validateVisibility()) {
            LOG.fine("Visibility dont meet constraints");
            return Response.status(Response.Status.BAD_REQUEST).entity(g.toJson("Bad Request - visibility dont meet constraints")).build();
        } else if (!data.validateZipCode()) {
            LOG.fine("Zip code dont meet constraints");
            return Response.status(Response.Status.BAD_REQUEST).entity(g.toJson("Bad Request - zip code dont meet constraints")).build();
        }
        return null;
    }

    private Response checkUserOnDB(Transaction txn, Entity userOnDB) {
        if (userOnDB != null) {
            txn.rollback();
            LOG.fine("User already exists");
            return Response.status(Response.Status.CONFLICT).entity(g.toJson("Conflict - username is already taken")).build();
        }
        return null;
    }

    private Entity createUser(RegisterBasicData data, Key userKey) {
        Entity.Builder eb = Entity.newBuilder(userKey)
                .set("email", data.email)
                .set("name", data.name)
                .set("password", hashPass(data.password))
                .set("creationDate", Timestamp.now())
                .set("role", RegisterBasicData.DEFAULT_ROLE);
        setWithNulls(eb, "birthDate", data.birthDate);
        setWithNulls(eb, "phoneNum", data.phoneNum);
        setWithNulls(eb, "nif", data.nif);
        setVisibility(eb, data.visibility);
        setWithNulls(eb, "street", data.street);
        setWithNulls(eb, "locale", data.locale);
        setWithNulls(eb, "zipCode", data.zipCode);
        setWithNulls(eb, "photo", data.photo);
        return eb.build();
    }

    @POST
    @Path("/addphoto")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response doAddPhoto(AddPhotoData data) throws IOException {
        String urlBucket = "https://fctconnect.oa.r.appspot.com/gcs/fctconnect.appspot.com/";
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