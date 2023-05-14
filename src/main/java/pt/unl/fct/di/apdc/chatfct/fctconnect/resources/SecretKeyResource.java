package pt.unl.fct.di.apdc.chatfct.fctconnect.resources;

import com.google.cloud.datastore.*;
import com.google.gson.Gson;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.DatastoreTypes;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.logging.Logger;

@Path("/admin/createkey")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class SecretKeyResource {

    private static final String ENCRYPT_ALG = "AES/CBC/PKCS5Padding";
    private static final String KEY_TYPE = "AES";
    private static final int KEY_SIZE = 256;
    private static final Logger LOG = Logger.getLogger(SecretKeyResource.class.getName());
    private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    private final KeyFactory secretKeyFactory = datastore.newKeyFactory().setKind(DatastoreTypes.SECRET_KEY_TYPE);
    private final Gson gson = new Gson();

    public SecretKeyResource() {
    }

    @POST
    public Response doCreateSecretKey() {
        final String[] secretKeyData = generateSecretKey();
        final Response checkSecretKeyData = checkSecretKey(secretKeyData);
        if (checkSecretKeyData != null) {
            return checkSecretKeyData;
        }
        Key key = secretKeyFactory.newKey(DatastoreTypes.SECRET_KEY_KEY);
        Transaction txn = datastore.newTransaction();
        try {
            Entity secretKeyOnDB = txn.get(key);
            Response checkSecretKeyOnDB = checkSecretKeyOnDB(secretKeyOnDB);
            if (checkSecretKeyOnDB != null) {
                txn.rollback();
                return checkSecretKeyOnDB;
            }
            Entity secretKeyEntity = createSecretKey(secretKeyData, key);
            txn.put(secretKeyEntity);
            txn.commit();
            LOG.fine("Secret key was created");
            return Response.ok(gson.toJson("Secret key was created")).build();
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

    private Response checkSecretKeyOnDB(Entity secretKeyOnDB) {
        if (secretKeyOnDB != null) {
            LOG.fine("Secret key was already generated");
            return Response.status(Response.Status.CONFLICT).entity(gson.toJson("Conflict - Secret key was already generated")).build();
        }
        return null;
    }

    private Entity createSecretKey(String[] secretKeyData, Key key) {
        final String secretKey = secretKeyData[0];
        final String aesKey = secretKeyData[1];
        final String initializationVector = secretKeyData[2];
        return Entity.newBuilder(key)
                .set(DatastoreTypes.SECRET_KEY_ATTR, secretKey)
                .set(DatastoreTypes.AES_KEY_ATTR, aesKey)
                .set(DatastoreTypes.INIT_VECTOR_ATTR, initializationVector)
                .build();
    }

    private String[] generateSecretKey() {
        try {
            SecretKey secretKey = Keys.secretKeyFor(SignatureAlgorithm.HS512);
            KeyGenerator keyGenerator = KeyGenerator.getInstance(KEY_TYPE);
            keyGenerator.init(KEY_SIZE);
            SecretKey aesKey = keyGenerator.generateKey();
            Cipher cipher = Cipher.getInstance(ENCRYPT_ALG);
            byte[] initializationVector = new byte[cipher.getBlockSize()];
            SecureRandom secureRandom = new SecureRandom();
            secureRandom.nextBytes(initializationVector);
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, new IvParameterSpec(initializationVector));
            byte[] encryptedKey = cipher.doFinal(secretKey.getEncoded());
            String base64SecretKey = Base64.getEncoder().encodeToString(encryptedKey);
            String base64AesKey = Base64.getEncoder().encodeToString(aesKey.getEncoded());
            String base64InitializationVector = Base64.getEncoder().encodeToString(initializationVector);
            return new String[]{base64SecretKey, base64AesKey, base64InitializationVector};
        } catch (Exception ex) {
            LOG.warning("Error encrypting secret key --> " + ex.getLocalizedMessage());
            return null;
        }
    }

    private Response checkSecretKey(String[] secretKeyData) {
        if (secretKeyData == null || secretKeyData.length != 3) {
            LOG.fine("Invalid data");
            return Response.status(Response.Status.BAD_REQUEST).entity(gson.toJson("Bad Request - invalid data")).build();
        } else {
            return null;
        }
    }
}