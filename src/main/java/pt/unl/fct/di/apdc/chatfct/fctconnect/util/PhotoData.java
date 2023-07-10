package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

import java.io.InputStream;

public class PhotoData {

    public static final String IMAGE_EXTENSION = ".jpeg";
    private final InputStream bytes;

    public PhotoData(InputStream bytes) {
        this.bytes = bytes;
    }

    public InputStream getInputStream() {
        return bytes;
    }

    @Override
    public String toString() {
        return "PhotoData{" +
                "bytes=" + bytes +
                '}';
    }
}