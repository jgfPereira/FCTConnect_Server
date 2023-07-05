package pt.unl.fct.di.apdc.chatfct.fctconnect.resources;

import com.google.cloud.datastore.*;
import com.google.gson.Gson;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.DatastoreTypes;
import pt.unl.fct.di.apdc.chatfct.fctconnect.util.MemcacheUtils;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
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

    public static final String ENCRYPT_ALG = "AES/CBC/PKCS5Padding";
    public static final String KEY_TYPE = "AES";
    private static final int SECRET_KEY_EXP_TIME_CACHE = 10;
    private static final int KEY_SIZE = 256;
    private static final String JWT_SIGNATURE_ALG = "HmacSHA512";
    private static final Logger LOG = Logger.getLogger(SecretKeyResource.class.getName());
    private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
    private final KeyFactory secretKeyFactory = datastore.newKeyFactory().setKind(DatastoreTypes.SECRET_KEY_TYPE);
    private final MemcacheUtils memcacheSecretKeys = MemcacheUtils.getMemcache(MemcacheUtils.SECRET_KEYS_NAMESPACE);
    private final Gson gson = new Gson();

    public SecretKeyResource() {
    }

    private Entity getSecretKeyEntityCached() {
        return memcacheSecretKeys.get(MemcacheUtils.SECRET_KEY_ENTITY_KEY, Entity.class);
    }

    private byte[] getSecretKeyTokensCached() {
        return memcacheSecretKeys.get(MemcacheUtils.SECRET_KEY_TOKENS_KEY, byte[].class);
    }

    private boolean isCached(Object o) {
        return o != null;
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
            Entity secretKeyOnDB;
            final Entity secretKeyEntityCached = getSecretKeyEntityCached();
            final boolean isSecretKeyEntityCached = isCached(secretKeyEntityCached);
            if (isSecretKeyEntityCached) {
                secretKeyOnDB = secretKeyEntityCached;
            } else {
                secretKeyOnDB = txn.get(key);
                final Response checkSecretKeyOnDB = checkSecretKeyOnDB(secretKeyOnDB);
                if (checkSecretKeyOnDB != null) {
                    memcacheSecretKeys.put(MemcacheUtils.SECRET_KEY_ENTITY_KEY, secretKeyOnDB);
                    txn.rollback();
                    return checkSecretKeyOnDB;
                }
            }
            final Entity secretKeyEntity = createSecretKey(secretKeyData, key);
            memcacheSecretKeys.put(MemcacheUtils.SECRET_KEY_ENTITY_KEY, secretKeyEntity);
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

    private byte[][] getSecretKey() {
        Key key = secretKeyFactory.newKey(DatastoreTypes.SECRET_KEY_KEY);
        Transaction txn = datastore.newTransaction();
        try {
            Entity secretKeyOnDB;
            final Entity secretKeyEntityCached = getSecretKeyEntityCached();
            final boolean isSecretKeyEntityCached = isCached(secretKeyEntityCached);
            if (isSecretKeyEntityCached) {
                secretKeyOnDB = secretKeyEntityCached;
            } else {
                secretKeyOnDB = txn.get(key);
                final Response doesSecretKeyExist = doesSecretKeyExist(secretKeyOnDB);
                if (doesSecretKeyExist != null) {
                    txn.rollback();
                    return null;
                }
                memcacheSecretKeys.put(MemcacheUtils.SECRET_KEY_ENTITY_KEY, secretKeyOnDB);
            }
            byte[][] secretKeyData = extractSecretKeyData(secretKeyOnDB);
            txn.commit();
            LOG.fine("Secret key was fetched");
            return secretKeyData;
        } catch (Exception e) {
            txn.rollback();
            LOG.severe(e.getLocalizedMessage());
            return null;
        } finally {
            if (txn.isActive()) {
                txn.rollback();
            }
        }
    }

    private Response doesSecretKeyExist(Entity secretKey) {
        if (secretKey == null) {
            LOG.fine("Secret key does not exist");
            return Response.status(Response.Status.NOT_FOUND).entity(gson.toJson("Not Found - secret key does not exist")).build();
        }
        return null;
    }

    public SecretKey decryptSecretKey() {
        byte[][] secretKeyData = getSecretKey();
        if (secretKeyData == null) {
            LOG.warning("Impossible to get secret key from datastore");
            return null;
        }
        final byte[] secretKeyEncrypted = secretKeyData[0];
        final byte[] aesKeyDecoded = secretKeyData[1];
        final byte[] initVectorDecoded = secretKeyData[2];
        try {
            byte[] decryptedSecretKey;
            final byte[] secretKeyTokensCached = getSecretKeyTokensCached();
            final boolean isSecretKeyTokensCached = isCached(secretKeyTokensCached);
            if (isSecretKeyTokensCached) {
                decryptedSecretKey = secretKeyTokensCached;
            } else {
                SecretKey aesKey = new SecretKeySpec(aesKeyDecoded, KEY_TYPE);
                Cipher cipher = Cipher.getInstance(ENCRYPT_ALG);
                cipher.init(Cipher.DECRYPT_MODE, aesKey, new IvParameterSpec(initVectorDecoded));
                decryptedSecretKey = cipher.doFinal(secretKeyEncrypted);
                memcacheSecretKeys.put(MemcacheUtils.SECRET_KEY_TOKENS_KEY, decryptedSecretKey, SECRET_KEY_EXP_TIME_CACHE);
            }
            LOG.fine("Secret key was decrypted");
            return new SecretKeySpec(decryptedSecretKey, JWT_SIGNATURE_ALG);
        } catch (Exception ex) {
            LOG.warning("Error decrypting secret key --> " + ex.getLocalizedMessage());
            return null;
        }
    }

    private byte[][] extractSecretKeyData(Entity secretKeyOnDB) {
        String secretKey = secretKeyOnDB.getString(DatastoreTypes.SECRET_KEY_ATTR);
        String aesKey = secretKeyOnDB.getString(DatastoreTypes.AES_KEY_ATTR);
        String initVector = secretKeyOnDB.getString(DatastoreTypes.INIT_VECTOR_ATTR);
        return new byte[][]{decodeBase64(secretKey), decodeBase64(aesKey), decodeBase64(initVector)};
    }

    private byte[] decodeBase64(String str) {
        return Base64.getDecoder().decode(str);
    }
}