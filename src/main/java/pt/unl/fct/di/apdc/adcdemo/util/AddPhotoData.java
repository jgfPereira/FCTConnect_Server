package pt.unl.fct.di.apdc.adcdemo.util;

import org.apache.commons.io.FilenameUtils;

import java.util.Base64;

public class AddPhotoData {

    public String username;
    public String photo;
    public String photoBase64;

    public AddPhotoData() {
    }

    public boolean validateData() {
        return !(this.username == null || this.photo == null || this.photoBase64 == null);
    }

    public String getPhotoType() {
        return this.photoBase64.split(";base64,")[0].split("data:")[1];
    }

    public byte[] getPhotoBinary() {
        String s = this.photoBase64.split(";base64,")[1];
        return Base64.getDecoder().decode(s);
    }

    public String getExtension() {
        return FilenameUtils.getExtension(this.photo);
    }
}
