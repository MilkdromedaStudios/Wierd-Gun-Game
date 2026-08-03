import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Grabs the game viewport at native size on a timer, writing JPEG frames for
 * ffmpeg to encode. JPEG rather than PNG purely for disk: five minutes of
 * 854x480 PNG is most of a gigabyte.
 *
 * Usage: Capture &lt;outDir&gt; &lt;fps&gt; &lt;seconds&gt;
 */
public class Capture {

    // The dev client renders 854x480 centred in the 1280x720 virtual screen.
    static final int X = 213, Y = 118, W = 854, H = 480;

    public static void main(String[] args) throws Exception {
        File dir = new File(args[0]);
        dir.mkdirs();
        for (File old : dir.listFiles()) {
            old.delete();
        }
        int fps = Integer.parseInt(args[1]);
        long seconds = Long.parseLong(args[2]);
        long interval = 1000 / fps;
        long deadline = System.currentTimeMillis() + seconds * 1000;

        ImageWriter jpeg = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam params = jpeg.getDefaultWriteParam();
        params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        params.setCompressionQuality(0.88f);

        Robot robot = new Robot();
        Rectangle region = new Rectangle(X, Y, W, H);
        int index = 0;
        long slow = 0;
        while (System.currentTimeMillis() < deadline) {
            long start = System.currentTimeMillis();
            BufferedImage shot = robot.createScreenCapture(region);
            BufferedImage rgb = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
            rgb.getGraphics().drawImage(shot, 0, 0, null);
            try (ImageOutputStream out = ImageIO.createImageOutputStream(
                    new File(dir, String.format("f%05d.jpg", index++)))) {
                jpeg.setOutput(out);
                jpeg.write(null, new IIOImage(rgb, null, null), params);
            }
            long spent = System.currentTimeMillis() - start;
            if (spent < interval) {
                Thread.sleep(interval - spent);
            } else {
                slow++;
            }
        }
        jpeg.dispose();
        System.out.println("captured " + index + " frames (" + slow + " over budget)");
    }
}
