import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Drives the dev client's GUI: the same Robot that captures can also click. */
public class Drive {
    static Robot robot;

    public static void main(String[] args) throws Exception {
        robot = new Robot();
        robot.setAutoDelay(15);
        for (String step : args[0].split(";")) {
            // "cmd:" takes the rest of the step verbatim, so commands may contain commas.
            if (step.startsWith("cmd:")) {
                command(step.substring(4));
                continue;
            }
            String[] bits = step.split(",");
            switch (bits[0]) {
                case "click" -> click(Integer.parseInt(bits[1]), Integer.parseInt(bits[2]));
                case "wait" -> Thread.sleep(Long.parseLong(bits[1]));
                case "rclick" -> rclick(bits.length > 1 ? Integer.parseInt(bits[1]) : 90);
                case "hold" -> hold(Long.parseLong(bits[1]));
                case "mine" -> mine(Long.parseLong(bits[1]));
                case "press" -> press(Integer.parseInt(bits[1]), Long.parseLong(bits[2]));
                case "type" -> type(bits[1]);
                case "key" -> tap(Integer.parseInt(bits[1]));
                case "cmd" -> command(bits[1]);
                case "look" -> look(Integer.parseInt(bits[1]), Integer.parseInt(bits[2]));
                case "shot" -> shot(bits[1]);
                default -> System.out.println("? " + step);
            }
        }
    }

    static void click(int x, int y) {
        robot.mouseMove(x, y);
        robot.delay(200);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.delay(80);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }

    static void rclick(int ms) {
        robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
        robot.delay(ms);
        robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
    }

    /** Holds the right button down, which is how automatics are kept firing. */
    static void hold(long ms) throws Exception {
        robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
        Thread.sleep(ms);
        robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
    }

    /** Holds the left button down, which is how a block gets broken. */
    static void mine(long ms) throws Exception {
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        Thread.sleep(ms);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }

    /** Holds a key down, for walking and anything else that is not a tap. */
    static void press(int keyCode, long ms) throws Exception {
        robot.keyPress(keyCode);
        Thread.sleep(ms);
        robot.keyRelease(keyCode);
    }

    /** Opens chat with the slash key, types the rest, and sends it. */
    static void command(String withoutSlash) throws Exception {
        tap(KeyEvent.VK_SLASH);
        Thread.sleep(300);
        type(withoutSlash);
        Thread.sleep(200);
        tap(KeyEvent.VK_ENTER);
        Thread.sleep(400);
    }

    /** Turns the view by nudging the grabbed cursor. */
    static void look(int dx, int dy) {
        Point at = MouseInfo.getPointerInfo().getLocation();
        robot.mouseMove(at.x + dx, at.y + dy);
        robot.delay(120);
    }

    static void type(String text) {
        for (char c : text.toCharArray()) {
            if (c >= 'a' && c <= 'z') {
                tap(KeyEvent.VK_A + (c - 'a'));
            } else if (c >= 'A' && c <= 'Z') {
                shifted(KeyEvent.VK_A + (c - 'A'));
            } else if (c >= '0' && c <= '9') {
                tap(KeyEvent.VK_0 + (c - '0'));
            } else {
                switch (c) {
                    case ' ' -> tap(KeyEvent.VK_SPACE);
                    case '/' -> tap(KeyEvent.VK_SLASH);
                    case '.' -> tap(KeyEvent.VK_PERIOD);
                    case '-' -> tap(KeyEvent.VK_MINUS);
                    case '=' -> tap(KeyEvent.VK_EQUALS);
                    case ':' -> shifted(KeyEvent.VK_SEMICOLON);
                    case '_' -> shifted(KeyEvent.VK_MINUS);
                    case '~' -> shifted(KeyEvent.VK_BACK_QUOTE);
                    case '@' -> shifted(KeyEvent.VK_2);
                    case '[' -> tap(KeyEvent.VK_OPEN_BRACKET);
                    case ']' -> tap(KeyEvent.VK_CLOSE_BRACKET);
                    case '{' -> shifted(KeyEvent.VK_OPEN_BRACKET);
                    case '}' -> shifted(KeyEvent.VK_CLOSE_BRACKET);
                    case ',' -> tap(KeyEvent.VK_COMMA);
                    case '"' -> shifted(KeyEvent.VK_QUOTE);
                    default -> System.out.println("no key for " + c);
                }
            }
        }
    }

    static void shifted(int keyCode) {
        robot.keyPress(KeyEvent.VK_SHIFT);
        tap(keyCode);
        robot.keyRelease(KeyEvent.VK_SHIFT);
        robot.delay(40);
    }

    static void tap(int keyCode) {
        robot.keyPress(keyCode);
        robot.delay(12);
        robot.keyRelease(keyCode);
        robot.delay(12);
    }

    static void shot(String path) throws Exception {
        BufferedImage img = robot.createScreenCapture(
                new Rectangle(Toolkit.getDefaultToolkit().getScreenSize()));
        ImageIO.write(img, "png", new File(path));
        System.out.println("shot -> " + path);
    }
}
