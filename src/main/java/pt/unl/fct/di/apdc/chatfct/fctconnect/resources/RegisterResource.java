package pt.unl.fct.di.apdc.chatfct.fctconnect.resources;

import com.google.cloud.Timestamp;
import com.google.cloud.datastore.*;
import com.google.gson.Gson;
import org.apache.commons.io.FileUtils;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.glassfish.jersey.media.multipart.file.FileDataBodyPart;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.*;

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

    private static final String EMAIL_DELIMITER = "@";
    private static final Logger LOG = Logger.getLogger(RegisterResource.class.getName());
    private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    private final KeyFactory userKeyFactory = datastore.newKeyFactory().setKind(DatastoreTypes.USER_TYPE);
    private final Gson gson = new Gson();

    public RegisterResource() {
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response doRegister(RegisterMandatoryData data) {
        LOG.fine("User attempt to register");
        final Response checkMandatoryData = checkMandatoryData(data);
        if (checkMandatoryData != null) {
            return checkMandatoryData;
        }
        final String username = extractUsername(data.email);
        Key key = userKeyFactory.newKey(username);
        Transaction txn = datastore.newTransaction();
        try {
            Entity userOnDB = txn.get(key);
            final Response checkUserOnDB = checkUserOnDB(userOnDB);
            if (checkUserOnDB != null) {
                txn.rollback();
                return checkUserOnDB;
            }
            Entity user = createUser(data, key);
            txn.put(user);
            Entity specificUser = createSpecificUser(username, data.role);
            txn.put(specificUser);
            txn.commit();
            LOG.fine("Register done: " + username);
            return Response.ok(gson.toJson("Register done")).build();
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

    private Response checkMandatoryData(RegisterMandatoryData data) {
        if (data == null || !data.validateData()) {
            LOG.fine("Invalid data: at least one required field is null");
            return Response.status(Response.Status.BAD_REQUEST).entity(gson.toJson("Bad Request - invalid data")).build();
        } else if (!data.validateEmail()) {
            LOG.fine("Email dont meet constraints");
            return Response.status(Response.Status.BAD_REQUEST).entity(gson.toJson("Bad Request - email dont meet constraints")).build();
        } else if (!data.comparePasswords()) {
            LOG.fine("Passwords dont match");
            return Response.status(Response.Status.BAD_REQUEST).entity(gson.toJson("Bad Request - passwords dont match")).build();
        } else if (!data.validatePassword()) {
            LOG.fine("Passwords dont meet constraints");
            return Response.status(Response.Status.BAD_REQUEST).entity(gson.toJson("Bad Request - password dont meet constraints")).build();
        } else if (!data.validateRole()) {
            LOG.fine("Unrecognized role");
            return Response.status(Response.Status.BAD_REQUEST).entity(gson.toJson("Bad Request - unrecognized role")).build();
        }
        return null;
    }

    private String extractUsername(String email) {
        final String[] split = email.split(EMAIL_DELIMITER);
        return split[0].trim();
    }

    private Response checkUserOnDB(Entity userOnDB) {
        if (userOnDB != null) {
            LOG.fine("User already exists");
            return Response.status(Response.Status.CONFLICT).entity(gson.toJson("Conflict - username is already taken")).build();
        }
        return null;
    }

    private Entity createSpecificUser(String username, String role) {
        final Key key = datastore.newKeyFactory().setKind(DatastoreTypes.formatRoleType(role)).addAncestors(PathElement.of(DatastoreTypes.USER_TYPE, username)).newKey(username);
        final Entity.Builder eb = Entity.newBuilder(key);
        if (role.equals(RegexExp.ROLE_STUDENT_REGEX)) {
            eb.setNull(DatastoreTypes.STUDENT_NUM_ATTR);
        }
        return eb.build();
    }

    private Entity createUser(RegisterMandatoryData data, Key key) {
        Entity.Builder eb = Entity.newBuilder(key)
                .set(DatastoreTypes.EMAIL_ATTR, data.email)
                .set(DatastoreTypes.NAME_ATTR, data.name)
                .set(DatastoreTypes.PASSWORD_ATTR, PasswordUtils.hashPass(data.password))
                .set(DatastoreTypes.CREATION_DATE_ATTR, Timestamp.now())
                .set(DatastoreTypes.ROLE_ATTR, data.role)
                .setNull(DatastoreTypes.BIRTH_DATE_ATTR)
                .setNull(DatastoreTypes.PHONE_NUM_ATTR)
                .setNull(DatastoreTypes.NIF_ATTR)
                .setNull(DatastoreTypes.VISIBILITY_ATTR)
                .setNull(DatastoreTypes.STREET_ATTR)
                .setNull(DatastoreTypes.LOCALE_ATTR)
                .setNull(DatastoreTypes.ZIP_CODE_ATTR)
                .setNull(DatastoreTypes.PHOTO_ATTR);
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
            return Response.status(Response.Status.BAD_REQUEST).entity(gson.toJson("Bad Request - Invalid data")).build();
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
            return Response.ok(gson.toJson("Profile picture added")).build();
        } else {
            return Response.status(r.getStatus()).entity(gson.toJson("Profile picture not added")).build();
        }
    }
}