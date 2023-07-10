package pt.unl.fct.di.apdc.chatfct.fctconnect.util;


import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class ImageUtils {

    private static final String JPEG_IMAGE_FORMAT = "jpeg";

    private ImageUtils() {
    }

    public static byte[] convertToJPEG(InputStream inputStream) {
        try {
            final BufferedImage image = ImageIO.read(inputStream);
            final BufferedImage imageNoAlphaChannel = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            final ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            ImageIO.write(imageNoAlphaChannel, JPEG_IMAGE_FORMAT, byteStream);
            return byteStream.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}