package pt.unl.fct.di.apdc.chatfct.fctconnect.util;


import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class ImageUtils {

    private static final String JPEG_IMAGE_FORMAT = "jpeg";
    private static final int[] RGB_MASKS = {0xFF0000, 0xFF00, 0xFF};
    private static final ColorModel RGB_OPAQUE = new DirectColorModel(32, RGB_MASKS[0], RGB_MASKS[1], RGB_MASKS[2]);

    private ImageUtils() {
    }

    public static byte[] convertToJPEG(InputStream inputStream) {
        try {
            final Image img = Toolkit.getDefaultToolkit().createImage(inputStream.readAllBytes());
            final PixelGrabber pixelGrabber = new PixelGrabber(img, 0, 0, -1, -1, true);
            pixelGrabber.grabPixels();
            final int width = pixelGrabber.getWidth();
            final int height = pixelGrabber.getHeight();
            final DataBuffer buffer = new DataBufferInt((int[]) pixelGrabber.getPixels(), width * height);
            final WritableRaster raster = Raster.createPackedRaster(buffer, width, height, width, RGB_MASKS, null);
            final BufferedImage bufferedImage = new BufferedImage(RGB_OPAQUE, raster, false, null);
            final ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            ImageIO.write(bufferedImage, JPEG_IMAGE_FORMAT, byteStream);
            return byteStream.toByteArray();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}