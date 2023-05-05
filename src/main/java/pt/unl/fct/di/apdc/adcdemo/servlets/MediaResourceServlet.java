package pt.unl.fct.di.apdc.adcdemo.servlets;

import com.google.cloud.storage.*;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

@SuppressWarnings("serial")
public class MediaResourceServlet extends HttpServlet {

    public static final String BUCKET = "adc-demo-383221.appspot.com";
    public static final String USER_PHOTO_NAME_FMT = "photo_%s";


    /**
     * Retrieves a file from GCS and returns it in the http response.
     * If the request path is /gcs/Foo/Bar this will be interpreted as
     * a request to read the GCS file named Bar in the bucket Foo.
     */
    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // Download file from a specified bucket. The request must have the form /gcs/<bucket>/<object>
        Storage storage = StorageOptions.getDefaultInstance().getService();
        // Parse the request URL
        Path objectPath = Paths.get(req.getPathInfo());
        if (objectPath.getNameCount() != 2) {
            throw new IllegalArgumentException("The URL is not formed as expected. " +
                    "Expecting /gcs/<bucket>/<object>");
        }
        // Get the bucket and the object names
        String bucketName = objectPath.getName(0).toString();
        String srcFilename = objectPath.getName(1).toString();

        Blob blob = storage.get(BlobId.of(bucketName, srcFilename));

        // Download object to the output stream. See Google's documentation.
        resp.setContentType(blob.getContentType());
        //forces download in all cases - otherwise some file formats (like .jpg, .png) wont download
        resp.setHeader("Content-disposition", "attachment; filename=" + srcFilename);
        blob.downloadTo(resp.getOutputStream());
    }

    @Override
    public void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // Upload file to specified bucket. The request must have the form /gcs/<bucket>/<object>
        Path objectPath = Paths.get(req.getPathInfo());
        if (objectPath.getNameCount() != 2) {
            throw new IllegalArgumentException("The URL is not formed as expected. " +
                    "Expecting /gcs/<bucket>/<object>");
        }
        // Get the bucket and object from the URL
        String bucketName = objectPath.getName(0).toString();
        String srcFilename = objectPath.getName(1).toString();

        // Upload to Google Cloud Storage (see Google's documentation)
        Storage storage = StorageOptions.getDefaultInstance().getService();
        BlobId blobId = BlobId.of(bucketName, srcFilename);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                .setAcl(Collections.singletonList(Acl.newBuilder(Acl.User.ofAllUsers(), Acl.Role.READER).build()))
                .setContentType(req.getContentType())
                .build();
        // The following is deprecated since it is better to upload directly to GCS from the client
        Blob blob = storage.create(blobInfo, req.getInputStream());
    }
}