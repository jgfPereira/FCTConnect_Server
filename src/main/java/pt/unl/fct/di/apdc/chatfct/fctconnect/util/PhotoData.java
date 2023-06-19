package pt.unl.fct.di.apdc.chatfct.fctconnect.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;

public class PhotoData {

    private static final String EXTENSION_SEPARATOR = ".";
    private final InputStream bytes;
    private final String fullFileName;

    public PhotoData(InputStream bytes, String fullFileName) {
        this.bytes = bytes;
        this.fullFileName = fullFileName;
    }

    public byte[] getBytes() throws IOException {
        return bytes.readAllBytes();
    }

    public String getCompleteFileExtension() {
        final String filename = Paths.get(fullFileName).getFileName().toString();
        final int dotIndex = filename.lastIndexOf(EXTENSION_SEPARATOR);
        if (dotIndex != -1) {
            return filename.substring(dotIndex);
        }
        throw new RuntimeException("Invalid filename");
    }

    public String getOnlyFileExtension() {
        return getCompleteFileExtension().substring(1);
    }

    @Override
    public String toString() {
        return "PhotoData{" +
                "bytes=" + bytes +
                ", fullFileName='" + fullFileName + '\'' +
                '}';
    }
}