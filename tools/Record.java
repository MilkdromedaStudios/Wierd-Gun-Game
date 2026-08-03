import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Grabs the game viewport on a timer and writes downscaled frames.
 * Usage: Record <outDir> <fps> <seconds>
 */
public class Record {

    // The dev client renders 854x480 centred in the 1280x720 virtual screen.
    static final int X = 213, Y = 118, W = 854, H = 480;
    static final int OUT_W = 480, OUT_H = 270;

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

        Robot robot = new Robot();
        Rectangle region = new Rectangle(X, Y, W, H);
        int index = 0;
        while (System.currentTimeMillis() < deadline) {
            long start = System.currentTimeMillis();
            BufferedImage shot = robot.createScreenCapture(region);
            BufferedImage small = new BufferedImage(OUT_W, OUT_H, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = small.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(shot, 0, 0, OUT_W, OUT_H, null);
            g.dispose();
            ImageIO.write(small, "png", new File(dir, String.format("f%05d.png", index++)));
            long spent = System.currentTimeMillis() - start;
            if (spent < interval) {
                Thread.sleep(interval - spent);
            }
        }
        System.out.println("captured " + index + " frames");
    }
}
