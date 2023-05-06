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

    private static final String STUDENT_TYPE = "Student";
    private static final String PROFESSOR_TYPE = "Professor";
    private static final String EMPLOYEE_TYPE = "Employee";
    private static final Logger LOG = Logger.getLogger(RegisterResource.class.getName());
    private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    private final KeyFactory studentKeyFactory = datastore.newKeyFactory().setKind(STUDENT_TYPE);
    private final KeyFactory professorKeyFactory = datastore.newKeyFactory().setKind(PROFESSOR_TYPE);
    private final KeyFactory employeeKeyFactory = datastore.newKeyFactory().setKind(EMPLOYEE_TYPE);
    private final Gson gson = new Gson();

    public RegisterResource() {
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

    private void setAddress(Entity.Builder eb, Address address) {
        if (address == null) {
            eb.setNull("street");
            eb.setNull("locale");
            eb.setNull("zipCode");
        } else {
            setWithNulls(eb, "street", address.street);
            setWithNulls(eb, "locale", address.locale);
            setWithNulls(eb, "zipCode", address.zipCode);
        }
    }

    @POST
    @Path("/student")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response doRegisterStudent(RegisterStudentData data) {
        LOG.fine("User attempt to register");
        Response checkData = checkDataStudent(data);
        if (checkData != null) {
            return checkData;
        }
        Key key = studentKeyFactory.newKey(data.basicData.username);
        Transaction txn = datastore.newTransaction();
        try {
            Entity userOnDB = txn.get(key);
            Response checkUserOnDB = checkUserOnDB(txn, userOnDB);
            if (checkUserOnDB != null) {
                return checkUserOnDB;
            }
            Entity user = createStudent(data, key);
            txn.put(user);
            txn.commit();
            LOG.fine("Register done: " + data.basicData.username);
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

    @POST
    @Path("/other")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response doRegisterOther(RegisterBasicData data) {
        LOG.fine("User attempt to register");
        Response checkData = checkDataOther(data);
        if (checkData != null) {
            return checkData;
        }
        Key key = getKeyOther(data.username, data.role);
        Transaction txn = datastore.newTransaction();
        try {
            Entity userOnDB = txn.get(key);
            Response checkUserOnDB = checkUserOnDB(txn, userOnDB);
            if (checkUserOnDB != null) {
                return checkUserOnDB;
            }
            Entity user = createOther(data, key);
            txn.put(user);
            txn.commit();
            LOG.fine("Register done: " + data.username);
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

    private Key getKeyOther(String username, String role) {
        if (role.equals(RolePermissions.PROFESSOR_ROLE)) {
            return this.professorKeyFactory.newKey(username);
        } else {
            return this.employeeKeyFactory.newKey(username);
        }
    }

    private Response checkDataOther(RegisterBasicData data) {
        Response checkBasicData = checkBasicData(data);
        if (checkBasicData != null) {
            return checkBasicData;
        }
        return checkRole(data.role, RegexExp.ROLE_OTHER_REGEX);
    }

    private Response checkDataStudent(RegisterStudentData data) {
        Response checkBasicData = checkBasicData(data.basicData);
        if (checkBasicData != null) {
            return checkBasicData;
        }
        Response checkRole = checkRole(data.basicData.role, RegexExp.ROLE_STUDENT_REGEX);
        if (checkRole != null) {
            return checkRole;
        } else if (!data.validateStudentNumber()) {
            LOG.fine("Student number dont meet constraints");
            return Response.status(Response.Status.BAD_REQUEST).entity(gson.toJson("Bad Request - student number dont meet constraints")).build();
        }
        return null;
    }

    private Response checkBasicData(RegisterBasicData data) {
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
        } else if (!data.validateBirthDate()) {
            LOG.fine("Birth date dont meet constraints");
            return Response.status(Response.Status.BAD_REQUEST).entity(gson.toJson("Bad Request - birth date dont meet constraints")).build();
        } else if (!data.validatePhoneNum()) {
            LOG.fine("Phone number dont meet constraints");
            return Response.status(Response.Status.BAD_REQUEST).entity(gson.toJson("Bad Request - phone number dont meet constraints")).build();
        } else if (!data.validateNif()) {
            LOG.fine("NIF dont meet constraints");
            return Response.status(Response.Status.BAD_REQUEST).entity(gson.toJson("Bad Request - NIF dont meet constraints")).build();
        } else if (!data.validateVisibility()) {
            LOG.fine("Visibility dont meet constraints");
            return Response.status(Response.Status.BAD_REQUEST).entity(gson.toJson("Bad Request - visibility dont meet constraints")).build();
        } else if (!data.validateZipCode()) {
            LOG.fine("Zip code dont meet constraints");
            return Response.status(Response.Status.BAD_REQUEST).entity(gson.toJson("Bad Request - zip code dont meet constraints")).build();
        } else if (!data.validateRole()) {
            LOG.fine("Unrecognized role");
            return Response.status(Response.Status.BAD_REQUEST).entity(gson.toJson("Bad Request - unrecognized role")).build();
        }
        return null;
    }

    private Response checkRole(String role, String regex) {
        final boolean check = role.matches(regex);
        if (!check) {
            LOG.fine("Invalid role");
        }
        return check ? null : Response.status(Response.Status.BAD_REQUEST).entity(gson.toJson("Bad Request - invalid role")).build();
    }

    private Response checkUserOnDB(Transaction txn, Entity userOnDB) {
        if (userOnDB != null) {
            txn.rollback();
            LOG.fine("User already exists");
            return Response.status(Response.Status.CONFLICT).entity(gson.toJson("Conflict - username is already taken")).build();
        }
        return null;
    }

    private Entity createOther(RegisterBasicData data, Key key) {
        return setBasicData(data, key).build();
    }

    private Entity createStudent(RegisterStudentData data, Key key) {
        Entity.Builder eb = setBasicData(data.basicData, key);
        eb.set("studentNumber", data.studentNumber);
        return eb.build();
    }

    private Entity.Builder setBasicData(RegisterBasicData data, Key key) {
        Entity.Builder eb = Entity.newBuilder(key)
                .set("email", data.email)
                .set("name", data.name)
                .set("password", PasswordUtils.hashPass(data.password))
                .set("creationDate", Timestamp.now())
                .set("role", data.role);
        setWithNulls(eb, "birthDate", data.birthDate);
        setWithNulls(eb, "phoneNum", data.phoneNum);
        setWithNulls(eb, "nif", data.nif);
        setVisibility(eb, data.visibility);
        setAddress(eb, data.address);
        setWithNulls(eb, "photo", data.photo);
        return eb;
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