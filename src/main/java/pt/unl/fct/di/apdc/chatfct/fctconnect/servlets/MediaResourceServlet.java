package pt.unl.fct.di.apdc.chatfct.fctconnect.servlets;

import com.google.cloud.storage.*;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

public class MediaResourceServlet extends HttpServlet {

    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Storage storage = StorageOptions.getDefaultInstance().getService();
        Path objectPath = Paths.get(req.getPathInfo());
        if (objectPath.getNameCount() != 2) {
            throw new IllegalArgumentException("The URL is not formed as expected");
        }
        String bucketName = objectPath.getName(0).toString();
        String srcFilename = objectPath.getName(1).toString();
        Blob blob = storage.get(BlobId.of(bucketName, srcFilename));
        resp.setContentType(blob.getContentType());
        resp.setHeader("Content-disposition", "attachment; filename=" + srcFilename);
        blob.downloadTo(resp.getOutputStream());
    }

    @Override
    public void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Path objectPath = Paths.get(req.getPathInfo());
        if (objectPath.getNameCount() != 2) {
            throw new IllegalArgumentException("The URL is not formed as expected");
        }
        String bucketName = objectPath.getName(0).toString();
        String srcFilename = objectPath.getName(1).toString();
        Storage storage = StorageOptions.getDefaultInstance().getService();
        BlobId blobId = BlobId.of(bucketName, srcFilename);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                .setAcl(Collections.singletonList(Acl.newBuilder(Acl.User.ofAllUsers(), Acl.Role.READER).build()))
                .setContentType(req.getContentType())
                .build();
        storage.create(blobInfo, req.getInputStream().readAllBytes());
    }
}