package pt.unl.fct.di.apdc.chatfct.fctconnect.resources;

import com.google.cloud.Timestamp;
import com.google.cloud.datastore.*;
import com.google.gson.Gson;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.*;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.UUID;
import java.util.logging.Logger;

@Path("/register")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class RegisterResource {

    private static final String EMAIL_DELIMITER = "@";
    private static final Logger LOG = Logger.getLogger(RegisterResource.class.getName());
    private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    private final KeyFactory userKeyFactory = datastore.newKeyFactory().setKind(DatastoreTypes.USER_TYPE);
    private final KeyFactory backOfficeUserKeyFactory = datastore.newKeyFactory().setKind(DatastoreTypes.BACK_OFFICE_USER_TYPE);
    private final KeyFactory accountConfirmationFactory = datastore.newKeyFactory().setKind(DatastoreTypes.ACCOUNT_CONFIRMATION_TYPE);
    private final MemcacheUtils memcacheBackOfficeUsers = MemcacheUtils.getMemcache(MemcacheUtils.BACK_OFFICE_USER_NAMESPACE);
    private final MemcacheUtils memcacheUsers = MemcacheUtils.getMemcache(MemcacheUtils.USER_NAMESPACE);
    private final MemcacheUtils memcacheSpecificUsers = MemcacheUtils.getMemcache(MemcacheUtils.SPECIFIC_USERS_NAMESPACE);
    private final Gson gson = new Gson();

    public RegisterResource() {
    }

    private Entity getBackOfficeUserCached(String username) {
        final String key = String.format(MemcacheUtils.BACK_OFFICE_USER_ENTITY_KEY, username);
        return memcacheBackOfficeUsers.get(key, Entity.class);
    }

    private Entity getUserCached(String username) {
        final String key = String.format(MemcacheUtils.USER_ENTITY_KEY, username);
        return memcacheUsers.get(key, Entity.class);
    }

    private boolean isCached(Entity e) {
        return e != null;
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
        final boolean checkUsernameInBackOfficeUsers = checkUsernameInBackOfficeUsers(username);
        if (!checkUsernameInBackOfficeUsers) {
            return Response.status(Response.Status.CONFLICT).entity(gson.toJson("Conflict - username is already taken")).build();
        }
        Key key = userKeyFactory.newKey(username);
        Transaction txn = datastore.newTransaction();
        try {
            Entity userOnDB;
            final Entity userCached = getUserCached(username);
            final boolean isUserCached = isCached(userCached);
            if (isUserCached) {
                userOnDB = userCached;
            } else {
                userOnDB = txn.get(key);
                final Response checkUserOnDB = checkUserOnDB(userOnDB);
                if (checkUserOnDB != null) {
                    memcacheUsers.put(String.format(MemcacheUtils.USER_ENTITY_KEY, username), userOnDB);
                    txn.rollback();
                    return checkUserOnDB;
                }
            }
            Entity user = createUser(data, key);
            memcacheUsers.put(String.format(MemcacheUtils.USER_ENTITY_KEY, username), user);
            txn.put(user);
            Entity specificUser = createSpecificUser(username, data.role);
            memcacheSpecificUsers.put(String.format(MemcacheUtils.SPECIFIC_USER_ENTITY_KEY, username), specificUser);
            txn.put(specificUser);
            final Response createAccountConfirmation = createAccountConfirmation(txn, username, data.email);
            if (createAccountConfirmation != null) {
                txn.rollback();
                return createAccountConfirmation;
            }
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

    private boolean checkUsernameInBackOfficeUsers(final String username) {
        final Key key = backOfficeUserKeyFactory.newKey(username);
        Transaction txn = datastore.newTransaction();
        try {
            Entity backOfficeUserOnDB;
            final Entity userCached = getBackOfficeUserCached(username);
            final boolean isUserCached = isCached(userCached);
            if (isUserCached) {
                backOfficeUserOnDB = userCached;
            } else {
                backOfficeUserOnDB = txn.get(key);
                final Response checkRegularUserOnDB = checkUserOnDB(backOfficeUserOnDB);
                if (checkRegularUserOnDB != null) {
                    memcacheBackOfficeUsers.put(String.format(MemcacheUtils.BACK_OFFICE_USER_ENTITY_KEY, username), backOfficeUserOnDB);
                    txn.rollback();
                    return false;
                }
            }
            txn.commit();
            LOG.fine("Username is not taken by back office user");
            return true;
        } catch (Exception e) {
            txn.rollback();
            LOG.severe(e.getLocalizedMessage());
            return false;
        } finally {
            if (txn.isActive()) {
                txn.rollback();
                return false;
            }
        }
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

    private void setEntityBuilderByType(Entity.Builder eb, String role) {
        if (role.equals(RegexExp.ROLE_STUDENT_REGEX)) {
            eb.setNull(DatastoreTypes.COURSE_STUDENT_ATTR)
                    .setNull(DatastoreTypes.STUDENT_NUM_ATTR)
                    .setNull(DatastoreTypes.YEAR_STUDENT_ATTR)
                    .setNull(DatastoreTypes.CREDITS_STUDENT_ATTR)
                    .setNull(DatastoreTypes.AVERAGE_STUDENT_ATTR);
        } else if (role.equals(RegexExp.ROLE_PROFESSOR_REGEX)) {
            eb.setNull(DatastoreTypes.DEPARTMENT_ATTR)
                    .setNull(DatastoreTypes.OFFICE_PROFESSOR_ATTR);
        } else {
            eb.setNull(DatastoreTypes.DEPARTMENT_ATTR)
                    .setNull(DatastoreTypes.JOB_TITLE_EMPLOYEE_ATTR);
        }
    }

    private Entity createSpecificUser(String username, String role) {
        final Key key = datastore.newKeyFactory().setKind(DatastoreTypes.formatRoleType(role)).addAncestors(PathElement.of(DatastoreTypes.USER_TYPE, username)).newKey(username);
        final Entity.Builder eb = Entity.newBuilder(key);
        setEntityBuilderByType(eb, role);
        return eb.build();
    }

    private Entity createUser(RegisterMandatoryData data, Key key) {
        Entity.Builder eb = Entity.newBuilder(key)
                .set(DatastoreTypes.EMAIL_ATTR, data.email)
                .set(DatastoreTypes.NAME_ATTR, data.name)
                .set(DatastoreTypes.PASSWORD_ATTR, PasswordUtils.hashPass(data.password))
                .set(DatastoreTypes.CREATION_DATE_ATTR, Timestamp.now())
                .set(DatastoreTypes.ROLE_ATTR, data.role)
                .set(DatastoreTypes.VISIBILITY_ATTR, DatastoreTypes.DEFAULT_VISIBILITY)
                .set(DatastoreTypes.USER_STATUS_ATTR, DatastoreTypes.DEFAULT_STATUS)
                .setNull(DatastoreTypes.BIRTH_DATE_ATTR)
                .setNull(DatastoreTypes.PHONE_NUM_ATTR)
                .setNull(DatastoreTypes.STREET_ATTR)
                .setNull(DatastoreTypes.LOCALE_ATTR)
                .setNull(DatastoreTypes.ZIP_CODE_ATTR)
                .setNull(DatastoreTypes.PHOTO_ATTR);
        return eb.build();
    }

    private Response createAccountConfirmation(Transaction txn, String username, String email) {
        final String code = UUID.randomUUID().toString();
        final Key key = accountConfirmationFactory.newKey(code);
        final Entity accountConfOnDB = txn.get(key);
        final Response checkAccountConfirmationOnDB = checkAccountConfirmationOnDB(accountConfOnDB);
        if (checkAccountConfirmationOnDB != null) {
            return checkAccountConfirmationOnDB;
        }
        final Entity accountConfirmation = createAccountConf(key, username);
        txn.put(accountConfirmation);
        final boolean isEmailSent = RegisterEmailConfirmationUtils.sendEmail(email, code);
        if (!isEmailSent) {
            LOG.fine("Email was not sent - register cancelled");
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(gson.toJson("Server Error")).build();
        }
        LOG.fine("Email sent successfully");
        return null;
    }

    private Response checkAccountConfirmationOnDB(Entity accountConfOnDB) {
        if (accountConfOnDB != null) {
            LOG.fine("Account confirmation already generated");
            return Response.status(Response.Status.CONFLICT).entity(gson.toJson("Conflict - account confirmation already generated")).build();
        }
        return null;
    }

    private Entity createAccountConf(Key key, String username) {
        return Entity.newBuilder(key)
                .set(DatastoreTypes.USERNAME_ACCOUNT_CONF, username)
                .set(DatastoreTypes.CREATION_DATE_ACCOUNT_CONF, Timestamp.now())
                .build();
    }
}