package pt.unl.fct.di.apdc.chatfct.fctconnect.resources;

import com.google.cloud.Timestamp;
import com.google.cloud.datastore.*;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.*;
import com.google.gson.Gson;
import io.jsonwebtoken.JwtException;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.*;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

@Path("/update")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class UpdateResource {

    private static final String START_OF_DAY_UTC = "T00:00:00Z";
    private static final Logger LOG = Logger.getLogger(UpdateResource.class.getName());
    private static final String SEPARATOR = " ";
    private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    private final KeyFactory userKeyFactory = datastore.newKeyFactory().setKind(DatastoreTypes.USER_TYPE);
    private final Gson gson = new Gson();

    public UpdateResource() {
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public Response doUpdate(UpdateData data, @Context HttpHeaders headers, @Context HttpServletRequest request) {
        LOG.fine("User attempt to update attribute(s)");
        final String token = TokenUtils.extractTokenFromHeaders(request);
        TokenInfo tokenInfo = verifyToken(token);
        if (tokenInfo == null) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(gson.toJson("Invalid credentials")).build();
        }
        LOG.fine("Valid token. Proceeding...");
        final String username = tokenInfo.getUsername();
        final String role = tokenInfo.getRole();
        final Response checkData = checkData(data);
        if (checkData != null) {
            return checkData;
        }
        data.removeDuplicates();
        data.formatVisibilityProperty();
        Key key = userKeyFactory.newKey(username);
        Transaction txn = datastore.newTransaction();
        try {
            Entity userOnDB = txn.get(key);
            final Response checkUserOnDB = checkUserOnDB(userOnDB);
            if (checkUserOnDB != null) {
                txn.rollback();
                return checkUserOnDB;
            }
            final List<String> forbiddenUpdates = new ArrayList<>();
            final List<String> invalidFormatUpdates = new ArrayList<>();
            final Key specificUserKey = getSpecificUserKey(username, role);
            final Entity.Builder specificUserEntityBuilder = Entity.newBuilder(txn.get(specificUserKey));
            final Entity updatedUser = updateUser(userOnDB, specificUserEntityBuilder, data, role, forbiddenUpdates, invalidFormatUpdates);
            final Entity updatedSpecificUser = specificUserEntityBuilder.build();
            if (didUserChanged(forbiddenUpdates, invalidFormatUpdates, data.updateEntries)) {
                txn.update(updatedUser, updatedSpecificUser);
            }
            txn.commit();
            LOG.fine("User was updated - if permissions and format checked out");
            return createResponseBasedOnUpdates(data, forbiddenUpdates, invalidFormatUpdates);
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

    private Response checkData(UpdateData data) {
        if (data == null || !data.validateData()) {
            LOG.fine("Invalid data: at least one field is null");
            return Response.status(Response.Status.BAD_REQUEST).entity(gson.toJson("Bad Request - Invalid data")).build();
        }
        return null;
    }

    private Response checkUserOnDB(Entity usernameOnDB) {
        if (usernameOnDB == null) {
            LOG.fine("User does not exist");
            return Response.status(Response.Status.NOT_FOUND).entity(gson.toJson("Not Found - User does not exist")).build();
        }
        return null;
    }

    private Entity updateUser(Entity user, Entity.Builder specificUserEB, UpdateData data, String role, List<String> forbiddenUpdates, List<String> invalidFormatUpdates) {
        Entity.Builder eb = Entity.newBuilder(user);
        for (UpdateEntry entry : data.updateEntries) {
            if (!RolePermissions.canUpdate(entry.propertyName, role)) {
                LOG.fine("Dont have permission to update property " + entry.propertyName);
                forbiddenUpdates.add(entry.propertyName);
            } else if (!checkPropertyFormat(entry.propertyName, entry.newValue)) {
                LOG.fine("Format of the new value is invalid for the property " + entry.propertyName);
                invalidFormatUpdates.add(entry.propertyName);
            } else {
                if (RolePermissions.isSpecificProperty(entry.propertyName)) {
                    updateSpecificProperty(specificUserEB, entry.propertyName, entry.newValue);
                } else {
                    updateProperty(eb, entry.propertyName, entry.newValue);
                }
                LOG.fine("Updated property " + entry.propertyName);
            }
        }
        return eb.build();
    }

    private boolean checkPropertyFormat(String property, String newValue) {
        switch (property) {
            case DatastoreTypes.BIRTH_DATE_ATTR:
                return newValue.matches(RegexExp.DATE_REGEX);
            case DatastoreTypes.LOCALE_ATTR:
            case DatastoreTypes.NAME_ATTR:
            case DatastoreTypes.STREET_ATTR:
            case DatastoreTypes.COURSE_STUDENT_ATTR:
            case DatastoreTypes.DEPARTMENT_ATTR:
            case DatastoreTypes.OFFICE_PROFESSOR_ATTR:
            case DatastoreTypes.JOB_TITLE_EMPLOYEE_ATTR:
                return true;
            case DatastoreTypes.PHONE_NUM_ATTR:
                return newValue.matches(RegexExp.PHONE_NUM_REGEX);
            case DatastoreTypes.VISIBILITY_ATTR:
                return newValue.matches(RegexExp.VISIBILITY_REGEX);
            case DatastoreTypes.ZIP_CODE_ATTR:
                return newValue.matches(RegexExp.ZIP_CODE_REGEX);
            case DatastoreTypes.STUDENT_NUM_ATTR:
                return newValue.matches(RegexExp.STUDENT_NUMBER_REGEX);
            case DatastoreTypes.YEAR_STUDENT_ATTR:
            case DatastoreTypes.CREDITS_STUDENT_ATTR:
                return newValue.matches(RegexExp.WHOLE_NUMBER_REGEX);
            case DatastoreTypes.AVERAGE_STUDENT_ATTR:
                return newValue.matches(RegexExp.REAL_NUMBER_REGEX);
            default:
                return false;
        }
    }

    private boolean didUserChanged(List<String> forbiddenUpdates, List<String> invalidFormatUpdates, UpdateEntry[] updateEntries) {
        return forbiddenUpdates.size() + invalidFormatUpdates.size() != updateEntries.length;
    }

    private Response createResponseBasedOnUpdates(UpdateData data, List<String> forbiddenUpdates, List<String> invalidFormatUpdates) {
        if (forbiddenUpdates.isEmpty() && invalidFormatUpdates.isEmpty()) {
            LOG.fine("Updated all properties");
            return Response.ok(gson.toJson("Updated all properties")).build();
        } else if (forbiddenUpdates.size() + invalidFormatUpdates.size() == data.updateEntries.length) {
            LOG.info("None of the properties were updated");
            return Response.status(Response.Status.FORBIDDEN).entity(gson.toJson(createResponseString("None of the properties were updated:", forbiddenUpdates, invalidFormatUpdates))).build();
        } else {
            LOG.info("Some properties were not updated");
            return Response.ok(gson.toJson(createResponseString("Some properties were not updated:", forbiddenUpdates, invalidFormatUpdates))).build();
        }
    }

    private String createResponseString(String baseString, List<String> forbiddenUpdates, List<String> invalidFormatUpdates) {
        final StringBuilder sb = new StringBuilder();
        sb.append(baseString);
        appendPropertiesWithEmptyCheck(sb, forbiddenUpdates, "\nForbidden updates:");
        appendPropertiesWithEmptyCheck(sb, invalidFormatUpdates, "\nInvalid format updates:");
        return sb.toString();
    }

    private void appendPropertiesWithEmptyCheck(StringBuilder sb, List<String> l, String baseStr) {
        if (!l.isEmpty()) {
            sb.append(baseStr);
            appendPropertiesNotUpdated(sb, l);
        }
    }

    private void appendPropertiesNotUpdated(StringBuilder sb, List<String> list) {
        for (String s : list) {
            sb.append(SEPARATOR).append(s);
        }
    }

    private boolean isBirthDateProperty(String property) {
        return property.equals(DatastoreTypes.BIRTH_DATE_ATTR);
    }

    private void updateBirthDate(Entity.Builder eb, String birthDate) {
        final String birthDateWithTime = birthDate + START_OF_DAY_UTC;
        eb.set(DatastoreTypes.BIRTH_DATE_ATTR, Timestamp.parseTimestamp(birthDateWithTime));
    }

    private void updateProperty(Entity.Builder eb, String propertyName, String newValue) {
        if (isBirthDateProperty(propertyName)) {
            updateBirthDate(eb, newValue);
        } else {
            eb.set(propertyName, newValue);
        }
    }

    private void updateSpecificProperty(Entity.Builder eb, String propertyName, String newValue) {
        eb.set(propertyName, newValue);
    }

    private Key getSpecificUserKey(String username, String role) {
        return datastore.newKeyFactory().setKind(DatastoreTypes.formatRoleType(role))
                .addAncestors(PathElement.of(DatastoreTypes.USER_TYPE, username)).newKey(username);
    }

    private TokenInfo verifyToken(final String token) {
        try {
            return TokenUtils.verifyToken(token);
        } catch (JwtException ex) {
            LOG.warning("Invalid token --> " + ex.getLocalizedMessage());
            return null;
        }
    }

    @POST
    @Path("/addphoto")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response doAddPhoto(@Context HttpHeaders headers, @Context HttpServletRequest request) {
        LOG.fine("User attempt to update attribute(s)");
        final String token = TokenUtils.extractTokenFromHeaders(request);
        TokenInfo tokenInfo = verifyToken(token);
        if (tokenInfo == null) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(gson.toJson("Invalid credentials")).build();
        }
        LOG.fine("Valid token. Proceeding...");
        final String username = tokenInfo.getUsername();
        Key key = userKeyFactory.newKey(username);
        Transaction txn = datastore.newTransaction();
        try {
            Entity userOnDB = txn.get(key);
            final Response checkUserOnDB = checkUserOnDB(userOnDB);
            if (checkUserOnDB != null) {
                txn.rollback();
                return checkUserOnDB;
            }
            final Blob photo = addPhotoToStorage(request, username);
            if (photo == null) {
                txn.rollback();
                LOG.severe("Error writing file to cloud storage");
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(gson.toJson("Server Error")).build();
            }
            final Entity updatedUser = linkPhotoWithUser(userOnDB, photo.getName());
            txn.update(updatedUser);
            txn.commit();
            LOG.fine("Profile photo added - " + photo.getName());
            return Response.ok(gson.toJson("Profile photo added - " + photo.getName())).build();
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

    private PhotoData parsePhotoFromRequest(HttpServletRequest request) {
        try {
            ServletFileUpload upload = new ServletFileUpload(new DiskFileItemFactory());
            List<FileItem> items = upload.parseRequest(request);
            for (FileItem item : items) {
                if (!item.isFormField())
                    return new PhotoData(item.getInputStream(), item.getName());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private Blob addPhotoToStorage(HttpServletRequest request, String username) {
        final PhotoData photoData = parsePhotoFromRequest(request);
        if (photoData == null) {
            return null;
        }
        final String photoName = String.format(DatastoreTypes.PHOTO_NAME_FMT, username) + photoData.getFileExtension();
        final Storage storage = StorageOptions.getDefaultInstance().getService();
        final BlobId blobId = BlobId.of(DatastoreTypes.BUCKET_NAME, photoName);
        final BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                .setAcl(Collections.singletonList(Acl.newBuilder(Acl.User.ofAllUsers(), Acl.Role.READER).build()))
                .setContentType(request.getContentType())
                .build();
        try {
            return storage.create(blobInfo, photoData.getBytes());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private Entity linkPhotoWithUser(Entity e, String photoName) {
        return Entity.newBuilder(e).set(DatastoreTypes.PHOTO_ATTR, photoName).build();
    }
}