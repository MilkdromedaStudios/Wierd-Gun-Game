import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Assembles captured frames into a looping GIF.
 * <p>
 * Long still stretches — world loading, mostly — are collapsed: a frame is
 * kept only if it differs enough from the last one kept, or if too many have
 * been dropped in a row to leave the clip feeling frozen.
 * <p>
 * Usage: Gif &lt;frameDir&gt; &lt;out.gif&gt; &lt;centisecondDelay&gt; &lt;diffThreshold&gt; &lt;maxSkip&gt;
 */
public class Gif {

    public static void main(String[] args) throws Exception {
        String[] dirs = args[0].split(",");
        File out = new File(args[1]);
        int delay = Integer.parseInt(args[2]);
        double threshold = Double.parseDouble(args[3]);
        int maxSkip = Integer.parseInt(args[4]);
        double scale = args.length > 5 ? Double.parseDouble(args[5]) : 1.0;

        List<File> files = new ArrayList<>();
        for (String name : dirs) {
            File[] batch = new File(name).listFiles((d, n) -> n.endsWith(".png"));
            Arrays.sort(batch);
            files.addAll(Arrays.asList(batch));
        }

        List<BufferedImage> kept = new ArrayList<>();
        BufferedImage last = null;
        int skipped = 0;
        for (File file : files) {
            BufferedImage frame = posterize(resize(ImageIO.read(file), scale));
            if (last == null || difference(last, frame) > threshold || skipped >= maxSkip) {
                kept.add(frame);
                last = frame;
                skipped = 0;
            } else {
                skipped++;
            }
        }
        System.out.println("kept " + kept.size() + " of " + files.size() + " frames");

        ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(stream);
            writer.prepareWriteSequence(null);
            for (int i = 0; i < kept.size(); i++) {
                writer.writeToSequence(
                        new IIOImage(kept.get(i), null, metadata(writer, delay, i == 0)), null);
            }
            writer.endWriteSequence();
        }
        writer.dispose();
        System.out.println(out + " " + (out.length() / 1024) + " KB");
    }

    /**
     * Flattens each channel to 5 bits. GIF is LZW over an indexed image, so it
     * pays for gradients: the software renderer's dithered sky costs more than
     * the entire rest of the frame. Minecraft's art survives the banding.
     */
    private static BufferedImage posterize(BufferedImage source) {
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                source.setRGB(x, y, source.getRGB(x, y) & 0xFFF8F8F8);
            }
        }
        return source;
    }

    private static BufferedImage resize(BufferedImage source, double scale) {
        if (scale == 1.0) {
            return source;
        }
        int w = (int) (source.getWidth() * scale);
        int h = (int) (source.getHeight() * scale);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(source, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    /** Mean per-channel difference, sampled on a grid so it stays cheap. */
    private static double difference(BufferedImage a, BufferedImage b) {
        long total = 0;
        int samples = 0;
        for (int y = 0; y < a.getHeight(); y += 3) {
            for (int x = 0; x < a.getWidth(); x += 3) {
                int p = a.getRGB(x, y);
                int q = b.getRGB(x, y);
                total += Math.abs(((p >> 16) & 0xFF) - ((q >> 16) & 0xFF))
                        + Math.abs(((p >> 8) & 0xFF) - ((q >> 8) & 0xFF))
                        + Math.abs((p & 0xFF) - (q & 0xFF));
                samples++;
            }
        }
        return (double) total / (samples * 3);
    }

    private static IIOMetadata metadata(ImageWriter writer, int delay, boolean first) throws Exception {
        ImageWriteParam params = writer.getDefaultWriteParam();
        ImageTypeSpecifier type = ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_INT_RGB);
        IIOMetadata meta = writer.getDefaultImageMetadata(type, params);
        String format = meta.getNativeMetadataFormatName();
        IIOMetadataNode root = (IIOMetadataNode) meta.getAsTree(format);

        IIOMetadataNode control = child(root, "GraphicControlExtension");
        control.setAttribute("disposalMethod", "none");
        control.setAttribute("userInputFlag", "FALSE");
        control.setAttribute("transparentColorFlag", "FALSE");
        control.setAttribute("delayTime", String.valueOf(delay));
        control.setAttribute("transparentColorIndex", "0");

        if (first) {
            // The Netscape extension is the only way to say "loop forever".
            IIOMetadataNode extensions = child(root, "ApplicationExtensions");
            IIOMetadataNode netscape = new IIOMetadataNode("ApplicationExtension");
            netscape.setAttribute("applicationID", "NETSCAPE");
            netscape.setAttribute("authenticationCode", "2.0");
            netscape.setUserObject(new byte[]{0x1, 0x0, 0x0});
            extensions.appendChild(netscape);
        }

        meta.setFromTree(format, root);
        return meta;
    }

    private static IIOMetadataNode child(IIOMetadataNode root, String name) {
        for (int i = 0; i < root.getLength(); i++) {
            if (root.item(i).getNodeName().equalsIgnoreCase(name)) {
                return (IIOMetadataNode) root.item(i);
            }
        }
        IIOMetadataNode node = new IIOMetadataNode(name);
        root.appendChild(node);
        return node;
    }
}
