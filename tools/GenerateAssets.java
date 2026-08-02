import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Draws every texture and writes every model this mod ships.
 * <p>
 * The art is authored here rather than in an image editor so that it is
 * reviewable in a diff, regenerable after a palette change, and consistent:
 * every sprite goes through the same outliner and the same shading pass, which
 * is most of what makes a set of 16x16 icons look like a set rather than a pile.
 * <p>
 * Run it from the repo root, after which {@code src/main/resources/assets/ledger}
 * is entirely generated output:
 * <pre>
 *   java tools/GenerateAssets.java
 * </pre>
 */
public final class GenerateAssets {

    private static final Path ASSETS = Path.of("src/main/resources/assets/ledger");
    private static final Path TEXTURES = ASSETS.resolve("textures/item");
    private static final Path MODELS = ASSETS.resolve("models/item");
    private static final Path ITEMS = ASSETS.resolve("items");

    // ------------------------------------------------------------------ paint

    private static final int NONE = 0x00000000;

    private static int rgb(int hex) {
        return 0xFF000000 | hex;
    }

    /** Mixes toward black; used for the automatic outline and shadow passes. */
    private static int darken(int argb, double amount) {
        int r = (int) (((argb >> 16) & 0xFF) * (1 - amount));
        int g = (int) (((argb >> 8) & 0xFF) * (1 - amount));
        int b = (int) ((argb & 0xFF) * (1 - amount));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int lighten(int argb, double amount) {
        int r = (int) (((argb >> 16) & 0xFF) + (255 - ((argb >> 16) & 0xFF)) * amount);
        int g = (int) (((argb >> 8) & 0xFF) + (255 - ((argb >> 8) & 0xFF)) * amount);
        int b = (int) ((argb & 0xFF) + (255 - (argb & 0xFF)) * amount);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** A square sprite and the handful of drawing calls the art needs. */
    private static final class Canvas {
        final int size;
        final int[] pixels;

        Canvas() {
            this(16);
        }

        Canvas(int size) {
            this.size = size;
            this.pixels = new int[size * size];
        }

        void px(int x, int y, int colour) {
            if (x < 0 || y < 0 || x >= size || y >= size || (colour >>> 24) == 0) {
                return;
            }
            pixels[y * size + x] = colour;
        }

        int at(int x, int y) {
            if (x < 0 || y < 0 || x >= size || y >= size) {
                return NONE;
            }
            return pixels[y * size + x];
        }

        void rect(int x, int y, int w, int h, int colour) {
            for (int dy = 0; dy < h; dy++) {
                for (int dx = 0; dx < w; dx++) {
                    px(x + dx, y + dy, colour);
                }
            }
        }

        void hline(int x, int y, int w, int colour) {
            rect(x, y, w, 1, colour);
        }

        /** Cuts a hole. {@link #px} ignores transparency, so erasing needs its own call. */
        void clear(int x, int y, int w, int h) {
            for (int dy = 0; dy < h; dy++) {
                for (int dx = 0; dx < w; dx++) {
                    int cx = x + dx;
                    int cy = y + dy;
                    if (cx >= 0 && cy >= 0 && cx < size && cy < size) {
                        pixels[cy * size + cx] = NONE;
                    }
                }
            }
        }

        void vline(int x, int y, int h, int colour) {
            rect(x, y, 1, h, colour);
        }

        /** A filled diagonal, one pixel thick, stepping one across per one down. */
        void diagonal(int x, int y, int length, int stepX, int stepY, int colour) {
            for (int i = 0; i < length; i++) {
                px(x + i * stepX, y + i * stepY, colour);
            }
        }

        /**
         * Lays a lighter band along the top of every opaque run and a darker one
         * along the bottom. This is the whole shading model, and at this size it
         * is enough to read as volume.
         */
        void shade(double top, double bottom) {
            int[] copy = pixels.clone();
            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    int here = copy[y * size + x];
                    if ((here >>> 24) == 0) {
                        continue;
                    }
                    boolean openAbove = (copy[Math.max(0, y - 1) * size + x] >>> 24) == 0 || y == 0;
                    boolean openBelow = y == size - 1 || (copy[(y + 1) * size + x] >>> 24) == 0;
                    if (openAbove) {
                        px(x, y, lighten(here, top));
                    } else if (openBelow) {
                        px(x, y, darken(here, bottom));
                    }
                }
            }
        }

        /** Wraps the silhouette in a dark border, which is what sells it as an icon. */
        void outline(int colour) {
            int[] copy = pixels.clone();
            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    if ((copy[y * size + x] >>> 24) != 0) {
                        continue;
                    }
                    boolean touching = opaque(copy, x - 1, y) || opaque(copy, x + 1, y)
                            || opaque(copy, x, y - 1) || opaque(copy, x, y + 1);
                    if (touching) {
                        px(x, y, colour);
                    }
                }
            }
        }

        private boolean opaque(int[] data, int x, int y) {
            return x >= 0 && y >= 0 && x < size && y < size && (data[y * size + x] >>> 24) != 0;
        }

        void write(String name) {
            BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            image.setRGB(0, 0, size, size, pixels, 0, size);
            try {
                Files.createDirectories(TEXTURES);
                ImageIO.write(image, "png", TEXTURES.resolve(name + ".png").toFile());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    /** Four tones is all a 16x16 sprite can carry without turning to mud. */
    private record Palette(int dark, int base, int light, int accent) {
        static Palette of(int base, int accent) {
            return new Palette(darken(rgb(base), 0.55), rgb(base), lighten(rgb(base), 0.3), rgb(accent));
        }
    }

    private static final int OUTLINE = rgb(0x17131c);

    // -------------------------------------------------------------- the guns

    /**
     * Ten guns, one per barrel, because the barrel is what changes a gun's
     * silhouette. Everything behind the muzzle is shared so the family reads as
     * a family; the spec supplies the palette and draws its own front end.
     */
    private record GunSpec(String barrelId, Palette body, Palette metal, Consumer<Canvas> muzzle) {
    }

    private static List<GunSpec> guns() {
        List<GunSpec> specs = new ArrayList<>();

        specs.add(new GunSpec("long_rifled", Palette.of(0x4a4f5a, 0xb9c0cc), Palette.of(0x6b7280, 0xd7dce4), c -> {
            c.rect(10, 6, 6, 2, rgb(0x5c626e));
            c.hline(10, 6, 6, rgb(0x7d8492));
            c.vline(15, 6, 2, rgb(0x2f333b));
            c.px(13, 5, rgb(0x8d94a3));
        }));

        specs.add(new GunSpec("boomstick", Palette.of(0x6b4526, 0xc99a5b), Palette.of(0x565b66, 0xc3c9d4), c -> {
            c.rect(10, 5, 5, 2, rgb(0x4d525c));
            c.rect(10, 7, 5, 2, rgb(0x3f434c));
            c.hline(10, 5, 5, rgb(0x717784));
            c.vline(14, 5, 4, rgb(0x22252b));
        }));

        specs.add(new GunSpec("rpg_tube", Palette.of(0x3f5133, 0x8fae6b), Palette.of(0x4a4038, 0x9a8570), c -> {
            c.rect(9, 5, 6, 4, rgb(0x4d6140));
            c.hline(9, 5, 6, rgb(0x6d8659));
            c.vline(15, 4, 6, rgb(0x2b3324));
            c.px(15, 4, rgb(0x2b3324));
            c.px(15, 9, rgb(0x2b3324));
            c.rect(11, 3, 2, 2, rgb(0xb44a33));
        }));

        specs.add(new GunSpec("gatling", Palette.of(0x4b4b52, 0xb0b3bd), Palette.of(0x8a6a2c, 0xe0c072), c -> {
            c.rect(10, 5, 6, 1, rgb(0x63676f));
            c.rect(10, 6, 6, 1, rgb(0x50545c));
            c.rect(10, 7, 6, 1, rgb(0x63676f));
            c.rect(10, 8, 6, 1, rgb(0x3f434a));
            c.vline(12, 5, 4, rgb(0x2c2f35));
            c.vline(15, 5, 4, rgb(0x24272c));
        }));

        specs.add(new GunSpec("snubnose", Palette.of(0x53575f, 0xbdc2cb), Palette.of(0x7d5a33, 0xd4a566), c -> {
            c.rect(10, 6, 3, 2, rgb(0x5f646d));
            c.hline(10, 6, 3, rgb(0x848b98));
            c.vline(12, 6, 2, rgb(0x2b2e34));
        }));

        specs.add(new GunSpec("singularity", Palette.of(0x2b2340, 0x6f5da8), Palette.of(0x1d1830, 0x4b3f74), c -> {
            c.rect(10, 6, 3, 2, rgb(0x3a3057));
            c.rect(12, 4, 4, 6, rgb(0x120f1d));
            c.rect(13, 5, 2, 4, rgb(0x241d3a));
            c.px(13, 6, rgb(0x8f7ad0));
            c.px(14, 7, rgb(0x6d5aa8));
        }));

        specs.add(new GunSpec("tesla_rod", Palette.of(0x3d4650, 0x93a3b3), Palette.of(0x8a5a2a, 0xe3a45c), c -> {
            c.rect(10, 6, 5, 2, rgb(0x8a5a2a));
            c.px(11, 5, rgb(0xe3a45c));
            c.px(13, 5, rgb(0xe3a45c));
            c.px(12, 8, rgb(0xe3a45c));
            c.px(15, 5, rgb(0x8fe7ff));
            c.px(15, 7, rgb(0x8fe7ff));
            c.px(14, 6, rgb(0xd8f6ff));
        }));

        specs.add(new GunSpec("frostbite", Palette.of(0x33526b, 0x8fc4e0), Palette.of(0x5a7f99, 0xc9e7f7), c -> {
            c.rect(10, 6, 5, 2, rgb(0x6ea6c8));
            c.hline(10, 6, 5, rgb(0xbfe4f5));
            c.px(15, 5, rgb(0xe8f8ff));
            c.px(15, 8, rgb(0xe8f8ff));
            c.px(13, 5, rgb(0xdff2fc));
        }));

        specs.add(new GunSpec("cursed_bone", Palette.of(0x6f6a5b, 0xd9d3bd), Palette.of(0x4c4740, 0x9c958a), c -> {
            c.rect(10, 6, 5, 2, rgb(0xcfc8b2));
            c.px(11, 5, rgb(0xe8e2cd));
            c.px(13, 8, rgb(0x8d8878));
            c.px(15, 5, rgb(0xe8e2cd));
            c.px(15, 8, rgb(0xe8e2cd));
            c.px(12, 7, rgb(0x6b665a));
        }));

        specs.add(new GunSpec("noodle", Palette.of(0x8a6a2f, 0xe7c778), Palette.of(0x6f5424, 0xd3ae5e), c -> {
            c.px(10, 6, rgb(0xe7c778));
            c.px(11, 5, rgb(0xf0d894));
            c.px(12, 6, rgb(0xe7c778));
            c.px(13, 7, rgb(0xd3ae5e));
            c.px(14, 6, rgb(0xe7c778));
            c.px(15, 5, rgb(0xf0d894));
            c.px(10, 7, rgb(0xc59f52));
            c.px(12, 7, rgb(0xc59f52));
        }));

        return specs;
    }

    /**
     * The shared rifle behind the muzzle: stock, receiver, grip, trigger guard
     * and magazine, laid out so a 16x16 sprite still reads as a gun.
     */
    private static Canvas drawGun(GunSpec spec) {
        Canvas c = new Canvas();
        Palette body = spec.body();
        Palette metal = spec.metal();

        // Stock, tapering into the receiver.
        c.rect(1, 7, 4, 3, body.base());
        c.px(1, 6, body.base());
        c.px(2, 6, body.base());
        c.px(4, 10, body.dark());

        // Receiver.
        c.rect(4, 5, 6, 4, metal.base());
        c.hline(4, 5, 6, metal.light());
        c.hline(4, 8, 6, metal.dark());

        // A rail and a front sight post, so it has a top line.
        c.hline(6, 4, 3, metal.dark());
        c.px(9, 4, metal.light());

        // Pistol grip, raked back.
        c.rect(5, 9, 2, 3, body.base());
        c.px(4, 11, body.dark());
        c.px(6, 12, body.dark());

        // Trigger guard.
        c.px(7, 9, metal.dark());
        c.px(8, 10, metal.dark());
        c.px(7, 10, metal.base());

        // Magazine.
        c.rect(8, 9, 3, 4, body.dark());
        c.hline(8, 9, 3, body.base());
        c.px(10, 12, metal.dark());

        spec.muzzle().accept(c);
        c.shade(0.16, 0.22);
        c.outline(OUTLINE);
        return c;
    }

    // ------------------------------------------------------------- the parts

    private enum Section { BARREL, CORE, GRIP, MAGAZINE, SIGHT, STOCK }

    /**
     * A part icon is its section's silhouette in the part's own colours, with a
     * small distinguishing mark drawn on top. Sixty hand-drawn sprites would
     * drift apart; sixty variations on six shapes stay a set.
     */
    private record PartSpec(String id, Section section, Palette palette, Consumer<Canvas> mark) {
    }

    private static final Consumer<Canvas> NO_MARK = c -> {
    };

    private static void drawSection(Canvas c, Section section, Palette p) {
        switch (section) {
            case BARREL -> {
                c.rect(2, 6, 12, 4, p.base());
                c.hline(2, 6, 12, p.light());
                c.hline(2, 9, 12, p.dark());
                c.rect(12, 5, 2, 6, p.dark());
                c.rect(4, 5, 1, 6, p.light());
            }
            case CORE -> {
                c.rect(5, 3, 6, 10, p.base());
                c.rect(4, 5, 8, 6, p.base());
                c.rect(6, 5, 4, 6, p.light());
                c.rect(6, 9, 4, 2, p.dark());
                c.px(5, 4, p.dark());
                c.px(10, 4, p.dark());
                c.px(5, 12, p.dark());
                c.px(10, 12, p.dark());
            }
            case GRIP -> {
                c.rect(4, 2, 8, 3, p.dark());
                c.rect(6, 4, 4, 9, p.base());
                c.rect(6, 4, 2, 9, p.light());
                c.rect(6, 12, 4, 2, p.dark());
            }
            case MAGAZINE -> {
                c.rect(4, 3, 8, 11, p.base());
                c.rect(4, 3, 2, 11, p.light());
                c.rect(10, 3, 2, 11, p.dark());
                c.hline(4, 3, 8, p.light());
                c.hline(4, 13, 8, p.dark());
            }
            case SIGHT -> {
                c.rect(2, 6, 12, 4, p.base());
                c.hline(2, 6, 12, p.light());
                c.hline(2, 9, 12, p.dark());
                c.rect(2, 5, 2, 6, p.dark());
                c.rect(12, 5, 2, 6, p.dark());
                c.rect(6, 10, 4, 2, p.dark());
            }
            case STOCK -> {
                c.rect(2, 4, 3, 9, p.base());
                c.rect(5, 5, 3, 7, p.base());
                c.rect(8, 6, 4, 4, p.base());
                c.rect(2, 4, 3, 2, p.light());
                c.rect(2, 11, 3, 2, p.dark());
                c.rect(11, 6, 1, 4, p.dark());
            }
        }
    }

    private static List<PartSpec> parts() {
        List<PartSpec> parts = new ArrayList<>();

        // ---- barrels
        parts.add(part("snubnose", Section.BARREL, 0x8b9099, 0xd7dbe2, c -> {
            c.clear(2, 4, 6, 8);
            c.vline(8, 5, 6, rgb(0x5a5f68));
        }));
        parts.add(part("long_rifled", Section.BARREL, 0x596170, 0xc2c8d2, c -> c.hline(4, 7, 8, rgb(0x9aa2b0))));
        parts.add(part("boomstick", Section.BARREL, 0x7a4f2b, 0xd2a165, c -> {
            c.hline(2, 8, 11, rgb(0x3b3f46));
            c.hline(2, 7, 11, rgb(0x53585f));
        }));
        parts.add(part("rpg_tube", Section.BARREL, 0x4a6039, 0x9dbd74, c -> {
            c.rect(6, 4, 3, 2, rgb(0xb44a33));
            c.rect(6, 10, 3, 2, rgb(0xb44a33));
        }));
        parts.add(part("gatling", Section.BARREL, 0x53565e, 0xb6bac4, c -> {
            c.hline(2, 7, 11, rgb(0x2f3238));
            c.hline(2, 8, 11, rgb(0x2f3238));
            c.vline(8, 5, 6, rgb(0x2f3238));
        }));
        parts.add(part("noodle", Section.BARREL, 0x9a7433, 0xf0d894, c -> {
            c.px(4, 5, rgb(0xf0d894));
            c.px(8, 11, rgb(0xf0d894));
            c.px(11, 5, rgb(0xf0d894));
        }));
        parts.add(part("tesla_rod", Section.BARREL, 0x8a5a2a, 0xe3a45c, c -> {
            c.vline(5, 5, 6, rgb(0x8fe7ff));
            c.vline(9, 5, 6, rgb(0x8fe7ff));
            c.px(13, 4, rgb(0xd8f6ff));
        }));
        parts.add(part("frostbite", Section.BARREL, 0x497b9c, 0xbfe4f5, c -> {
            c.px(4, 4, rgb(0xe8f8ff));
            c.px(9, 11, rgb(0xe8f8ff));
            c.px(12, 4, rgb(0xe8f8ff));
        }));
        parts.add(part("cursed_bone", Section.BARREL, 0xa9a291, 0xe8e2cd, c -> {
            c.px(5, 7, rgb(0x5b5750));
            c.px(9, 8, rgb(0x5b5750));
            c.px(7, 5, rgb(0x5b5750));
        }));
        parts.add(part("singularity", Section.BARREL, 0x3b3159, 0x8f7ad0, c -> {
            c.rect(9, 4, 5, 8, rgb(0x120f1d));
            c.rect(10, 6, 3, 4, rgb(0x2b2344));
            c.px(11, 7, rgb(0x8f7ad0));
        }));

        // ---- cores
        parts.add(part("iron_core", Section.CORE, 0x9ba0a8, 0xe2e6ec, NO_MARK));
        parts.add(part("redstone_reactor", Section.CORE, 0x8e2020, 0xff4d4d, c -> {
            c.px(7, 7, rgb(0xff8080));
            c.px(8, 8, rgb(0xff8080));
        }));
        parts.add(part("blaze_heart", Section.CORE, 0xc07b12, 0xffd76b, c -> {
            c.px(7, 6, rgb(0xfff0b0));
            c.px(8, 9, rgb(0xfff0b0));
        }));
        parts.add(part("ender_core", Section.CORE, 0x1d5a4f, 0x4fd6b8, c -> {
            c.px(7, 8, rgb(0xd6fff4));
            c.px(8, 6, rgb(0xa8f0dd));
        }));
        parts.add(part("tnt_core", Section.CORE, 0xa32c22, 0xf05a44, c -> {
            c.rect(6, 7, 4, 2, rgb(0xf5f0e6));
            c.px(7, 7, rgb(0x2b2b2b));
            c.px(8, 8, rgb(0x2b2b2b));
        }));
        parts.add(part("amethyst_resonator", Section.CORE, 0x6b3fa0, 0xc79bf0, c -> {
            c.px(7, 5, rgb(0xecd8ff));
            c.px(8, 10, rgb(0xecd8ff));
        }));
        parts.add(part("slime_core", Section.CORE, 0x4f9c3c, 0x9de07f, c -> {
            c.px(7, 6, rgb(0xe4ffd6));
            c.px(9, 8, rgb(0xe4ffd6));
        }));
        parts.add(part("nether_star_core", Section.CORE, 0xb8b48c, 0xfffbe0, c -> {
            c.vline(8, 3, 10, rgb(0xffffff));
            c.hline(4, 8, 8, rgb(0xffffff));
        }));
        parts.add(part("goat_horn_core", Section.CORE, 0x9a8a6b, 0xe4d6b4, c -> {
            c.px(6, 6, rgb(0x6a5c45));
            c.px(9, 9, rgb(0x6a5c45));
        }));
        parts.add(part("chicken_core", Section.CORE, 0xd9d2c2, 0xfffdf5, c -> {
            c.px(7, 6, rgb(0xe8a13c));
            c.px(8, 6, rgb(0xe8a13c));
            c.px(7, 9, rgb(0xd0473c));
        }));

        // ---- grips
        parts.add(part("duct_tape", Section.GRIP, 0x6f7276, 0xb8bcc2, c -> {
            c.hline(6, 6, 4, rgb(0x53565a));
            c.hline(6, 9, 4, rgb(0x53565a));
        }));
        parts.add(part("rubber_grip", Section.GRIP, 0x2f3136, 0x64686f, c -> {
            c.px(7, 6, rgb(0x8b9099));
            c.px(8, 8, rgb(0x8b9099));
            c.px(7, 10, rgb(0x8b9099));
        }));
        parts.add(part("bipod", Section.GRIP, 0x5c6169, 0xb3b9c2, c -> {
            c.diagonal(6, 8, 5, -1, 1, rgb(0x8b9099));
            c.diagonal(9, 8, 5, 1, 1, rgb(0x8b9099));
        }));
        parts.add(part("angled_foregrip", Section.GRIP, 0x3f434a, 0x8b9099, c -> {
            c.diagonal(6, 5, 7, 1, 1, rgb(0xb3b9c2));
        }));
        parts.add(part("vertical_foregrip", Section.GRIP, 0x44484f, 0x9aa0a9, c -> {
            c.vline(8, 5, 8, rgb(0xc2c8d2));
        }));
        parts.add(part("honey_grip", Section.GRIP, 0xc08a1d, 0xf5cf6b, c -> {
            c.px(7, 7, rgb(0xffe9a8));
            c.px(8, 10, rgb(0xffe9a8));
        }));
        parts.add(part("rocket_grip", Section.GRIP, 0x8a3a2a, 0xd9705a, c -> {
            c.px(7, 11, rgb(0xffd06b));
            c.px(8, 12, rgb(0xffd06b));
        }));
        parts.add(part("squid_grip", Section.GRIP, 0x25406b, 0x6b8fd0, c -> {
            c.px(6, 12, rgb(0x9ab4e8));
            c.px(9, 12, rgb(0x9ab4e8));
        }));
        parts.add(part("golden_grip", Section.GRIP, 0xb08717, 0xffe27a, NO_MARK));
        parts.add(part("skeleton_hand", Section.GRIP, 0xa9a291, 0xefe9d6, c -> {
            c.px(7, 6, rgb(0x5b5750));
            c.px(8, 9, rgb(0x5b5750));
        }));

        // ---- magazines
        parts.add(part("stick_mag", Section.MAGAZINE, 0x4a4e56, 0xa2a8b2, c -> {
            c.clear(4, 3, 8, 4);
            c.clear(4, 7, 2, 7);
            c.clear(10, 7, 2, 7);
        }));
        parts.add(part("extended_mag", Section.MAGAZINE, 0x5a5f68, 0xb6bcc6, c -> {
            c.hline(5, 6, 6, rgb(0x3b3f46));
            c.hline(5, 10, 6, rgb(0x3b3f46));
        }));
        parts.add(part("drum_mag", Section.MAGAZINE, 0x52565e, 0xaeb4be, c -> {
            c.rect(5, 6, 6, 6, rgb(0x3b3f46));
            c.rect(6, 7, 4, 4, rgb(0x7a8089));
        }));
        parts.add(part("ammo_belt", Section.MAGAZINE, 0x8a6a2c, 0xe0c072, c -> {
            for (int y = 4; y < 13; y += 3) {
                c.hline(4, y, 8, rgb(0x4a3a18));
            }
        }));
        parts.add(part("rocket_rack", Section.MAGAZINE, 0x4a6039, 0x9dbd74, c -> {
            c.vline(6, 4, 9, rgb(0xb44a33));
            c.vline(9, 4, 9, rgb(0xb44a33));
        }));
        parts.add(part("shell_box", Section.MAGAZINE, 0x7a4f2b, 0xd2a165, c -> {
            c.rect(6, 6, 2, 2, rgb(0xc0392b));
            c.rect(8, 9, 2, 2, rgb(0xc0392b));
        }));
        parts.add(part("bottomless_satchel", Section.MAGAZINE, 0x5a4530, 0xa8845c, c -> {
            c.rect(6, 7, 4, 5, rgb(0x14121a));
        }));
        parts.add(part("nuke_mag", Section.MAGAZINE, 0x2f3136, 0x7a8089, c -> {
            c.rect(6, 6, 4, 4, rgb(0xffd400));
            c.px(7, 7, rgb(0x14121a));
            c.px(8, 8, rgb(0x14121a));
        }));
        parts.add(part("hopper_feed", Section.MAGAZINE, 0x3a3d43, 0x82878f, c -> {
            c.rect(6, 8, 4, 5, rgb(0x22252b));
        }));
        parts.add(part("soul_jar", Section.MAGAZINE, 0x2f5a55, 0x74c2b8, c -> {
            c.px(7, 7, rgb(0xd6fff8));
            c.px(8, 9, rgb(0xd6fff8));
        }));

        // ---- sights
        parts.add(part("iron_sights", Section.SIGHT, 0x6f747c, 0xc2c8d2, c -> {
            c.clear(4, 5, 8, 7);
            c.vline(7, 4, 4, rgb(0x9aa0a9));
            c.vline(8, 4, 4, rgb(0x9aa0a9));
        }));
        parts.add(part("red_dot", Section.SIGHT, 0x3f434a, 0x8b9099, c -> c.px(8, 7, rgb(0xff3b30))));
        parts.add(part("holo", Section.SIGHT, 0x2f3136, 0x7a8089, c -> {
            c.rect(6, 6, 4, 4, rgb(0x4fd6b8));
            c.px(8, 7, rgb(0xd6fff4));
        }));
        parts.add(part("sniper_scope", Section.SIGHT, 0x25272c, 0x6a6f77, c -> {
            c.rect(3, 7, 10, 2, rgb(0x14161a));
            c.px(12, 7, rgb(0xbfe4f5));
            c.px(12, 8, rgb(0xbfe4f5));
        }));
        parts.add(part("thermal", Section.SIGHT, 0x8a3a12, 0xe07a3c, c -> {
            c.px(8, 7, rgb(0xffe0a0));
            c.px(7, 8, rgb(0xffb060));
        }));
        parts.add(part("laser_pointer", Section.SIGHT, 0x3f434a, 0x8b9099, c -> {
            c.hline(9, 7, 5, rgb(0xff3b30));
            c.px(13, 7, rgb(0xffd0cc));
        }));
        parts.add(part("googly_eyes", Section.SIGHT, 0xd9d2c2, 0xfffdf5, c -> {
            c.rect(4, 6, 3, 3, rgb(0xffffff));
            c.rect(9, 6, 3, 3, rgb(0xffffff));
            c.px(5, 7, rgb(0x14121a));
            c.px(10, 8, rgb(0x14121a));
        }));
        parts.add(part("seeker_eye", Section.SIGHT, 0x1d5a4f, 0x4fd6b8, c -> {
            c.rect(6, 6, 4, 4, rgb(0xd6fff4));
            c.px(8, 7, rgb(0x14121a));
        }));
        parts.add(part("cracked_monocle", Section.SIGHT, 0x8a8574, 0xe4dfcd, c -> {
            c.diagonal(5, 5, 6, 1, 1, rgb(0x4a463c));
            c.px(9, 6, rgb(0x4a463c));
        }));
        parts.add(part("target_computer", Section.SIGHT, 0x2f3a4a, 0x6f88a8, c -> {
            c.rect(5, 6, 6, 4, rgb(0x14212b));
            c.hline(6, 7, 4, rgb(0x4fd6b8));
            c.px(6, 8, rgb(0x4fd6b8));
        }));

        // ---- stocks
        parts.add(part("wooden_stock", Section.STOCK, 0x7a5730, 0xc9a06a, NO_MARK));
        parts.add(part("tactical_stock", Section.STOCK, 0x35383e, 0x767b84, c -> {
            c.vline(6, 5, 6, rgb(0x22252b));
            c.vline(9, 6, 4, rgb(0x22252b));
        }));
        parts.add(part("anvil_stock", Section.STOCK, 0x3a3d43, 0x82878f, c -> {
            c.rect(2, 6, 3, 5, rgb(0x22252b));
        }));
        parts.add(part("skeleton_stock", Section.STOCK, 0xa9a291, 0xefe9d6, c -> {
            c.px(4, 6, rgb(0x5b5750));
            c.px(7, 8, rgb(0x5b5750));
        }));
        parts.add(part("slime_pad", Section.STOCK, 0x4f9c3c, 0x9de07f, c -> {
            c.rect(2, 5, 2, 7, rgb(0xc9f5b0));
        }));
        parts.add(part("booster_stock", Section.STOCK, 0x8a3a2a, 0xd9705a, c -> {
            c.px(2, 6, rgb(0xffd06b));
            c.px(2, 9, rgb(0xffd06b));
            c.px(3, 8, rgb(0xffa03c));
        }));
        parts.add(part("cactus_stock", Section.STOCK, 0x3f7a3a, 0x7fbf74, c -> {
            c.px(3, 5, rgb(0xd8f0b0));
            c.px(6, 7, rgb(0xd8f0b0));
            c.px(4, 11, rgb(0xd8f0b0));
        }));
        parts.add(part("cushion_stock", Section.STOCK, 0x8a4a6a, 0xd08fae, c -> {
            c.rect(2, 5, 3, 7, rgb(0xe8b5cd));
        }));
        parts.add(part("jukebox_stock", Section.STOCK, 0x5a4530, 0xa8845c, c -> {
            c.rect(3, 7, 2, 3, rgb(0x14121a));
            c.px(3, 8, rgb(0xd0d0d0));
        }));
        parts.add(part("void_stock", Section.STOCK, 0x241d3a, 0x5c4b8c, c -> {
            c.rect(3, 6, 2, 5, rgb(0x0a0810));
        }));

        return parts;
    }

    private static PartSpec part(String id, Section section, int base, int accent, Consumer<Canvas> mark) {
        return new PartSpec(id, section, Palette.of(base, accent), mark);
    }

    private static Canvas drawPart(PartSpec spec) {
        Canvas c = new Canvas();
        drawSection(c, spec.section(), spec.palette());
        spec.mark().accept(c);
        c.shade(0.14, 0.2);
        c.outline(OUTLINE);
        return c;
    }

    // ------------------------------------------------------- menu furniture

    /** The five buttons the bench needs that are not parts. */
    private static Map<String, Canvas> uiIcons() {
        Map<String, Canvas> icons = new LinkedHashMap<>();

        Canvas merge = new Canvas();
        merge.rect(3, 7, 5, 2, rgb(0x8b9099));
        merge.rect(8, 7, 5, 2, rgb(0x8b9099));
        merge.rect(6, 4, 4, 8, rgb(0xffd400));
        merge.rect(7, 3, 2, 10, rgb(0xfff0a0));
        merge.shade(0.14, 0.2);
        merge.outline(OUTLINE);
        icons.put("icon_merge", merge);

        Canvas reset = new Canvas();
        reset.rect(4, 4, 8, 8, rgb(0xa03a30));
        reset.diagonal(4, 4, 8, 1, 1, rgb(0xffd6d0));
        reset.diagonal(11, 4, 8, -1, 1, rgb(0xffd6d0));
        reset.shade(0.14, 0.2);
        reset.outline(OUTLINE);
        icons.put("icon_reset", reset);

        Canvas back = new Canvas();
        back.rect(5, 7, 8, 2, rgb(0xc2c8d2));
        back.diagonal(5, 8, 4, 1, -1, rgb(0xc2c8d2));
        back.diagonal(5, 8, 4, 1, 1, rgb(0xc2c8d2));
        back.shade(0.14, 0.2);
        back.outline(OUTLINE);
        icons.put("icon_back", back);

        Canvas random = new Canvas();
        random.rect(3, 3, 10, 10, rgb(0xe4dfcd));
        random.px(5, 5, rgb(0x2b2b2b));
        random.px(10, 5, rgb(0x2b2b2b));
        random.px(8, 8, rgb(0x2b2b2b));
        random.px(5, 10, rgb(0x2b2b2b));
        random.px(10, 10, rgb(0x2b2b2b));
        random.shade(0.14, 0.2);
        random.outline(OUTLINE);
        icons.put("icon_random", random);

        Canvas presets = new Canvas();
        presets.rect(2, 5, 12, 8, rgb(0x7a5730));
        presets.hline(2, 5, 12, rgb(0xc9a06a));
        presets.rect(7, 8, 2, 3, rgb(0xffd400));
        presets.shade(0.14, 0.2);
        presets.outline(OUTLINE);
        icons.put("icon_presets", presets);

        return icons;
    }

    // ---------------------------------------------------------- the camera

    /**
     * One box of the camera. These seven are the model — both the one the game
     * loads and the {@code .bbmodel} you can open in Blockbench are written from
     * this list, so the two cannot drift apart.
     * <p>
     * Coordinates are in Blockbench's 16-unit space with the lens pointing at
     * -Z. UVs address the 32x32 sheet {@link #drawCameraSheet()} paints; the
     * item model halves them, because model JSON always works in 0-16 whatever
     * the texture's real size.
     */
    private record Box(String name, double[] from, double[] to, int[] uv) {
    }

    private static final int SHEET = 32;

    private static final List<Box> CAMERA = List.of(
            new Box("mount_plate", new double[]{6, 0, 6}, new double[]{10, 1, 10}, new int[]{0, 0, 4, 4}),
            new Box("mount_arm", new double[]{7.25, 1, 7.25}, new double[]{8.75, 4, 8.75}, new int[]{0, 8, 2, 11}),
            new Box("housing", new double[]{5, 4, 5}, new double[]{11, 9, 11}, new int[]{8, 0, 14, 5}),
            new Box("lens_barrel", new double[]{6.5, 5.25, 2.5}, new double[]{9.5, 8.25, 5}, new int[]{0, 12, 3, 15}),
            new Box("lens_glass", new double[]{7, 5.75, 2}, new double[]{9, 7.75, 2.5}, new int[]{16, 0, 18, 2}),
            new Box("status_light", new double[]{10.5, 8, 6.5}, new double[]{11.25, 8.75, 7.25}, new int[]{20, 0, 21, 1}),
            new Box("antenna", new double[]{7.5, 9, 7.5}, new double[]{8.5, 13, 8.5}, new int[]{0, 20, 1, 24}));

    private static final String[] FACES = {"north", "east", "south", "west", "up", "down"};

    /** The 32x32 sheet, painted to match the UV tiles the boxes above address. */
    private static Canvas drawCameraSheet() {
        Canvas c = new Canvas(SHEET);

        // Mount plate: a dull bolted disc.
        c.rect(0, 0, 4, 4, rgb(0x3a3d43));
        c.px(0, 0, rgb(0x5a5f68));
        c.px(3, 3, rgb(0x22252b));

        // Mount arm.
        c.rect(0, 8, 2, 3, rgb(0x4a4e56));
        c.vline(0, 8, 3, rgb(0x6d727c));

        // Housing: the shell, with a vent line and a seam.
        c.rect(8, 0, 6, 5, rgb(0x53575f));
        c.hline(8, 0, 6, rgb(0x757b86));
        c.hline(8, 4, 6, rgb(0x34373d));
        c.hline(9, 2, 4, rgb(0x3f434a));
        c.px(12, 1, rgb(0x8b9099));

        // Lens barrel: a dark ring.
        c.rect(0, 12, 3, 3, rgb(0x2b2e34));
        c.px(1, 13, rgb(0x14161a));
        c.hline(0, 12, 3, rgb(0x44484f));

        // Lens glass, and the red eye in it.
        c.rect(16, 0, 2, 2, rgb(0x14161a));
        c.px(16, 0, rgb(0xff3b30));
        c.px(17, 1, rgb(0x8a1f18));

        // Status light.
        c.px(20, 0, rgb(0x4fd6b8));

        // Antenna: a thin rod with a bright tip.
        c.vline(0, 20, 4, rgb(0x2b2e34));
        c.px(0, 20, rgb(0xc2c8d2));

        return c;
    }

    /** The item model the game loads: the same boxes, with UVs halved. */
    private static String cameraModel() {
        StringBuilder elements = new StringBuilder();
        for (int i = 0; i < CAMERA.size(); i++) {
            Box box = CAMERA.get(i);
            StringBuilder faces = new StringBuilder();
            for (int f = 0; f < FACES.length; f++) {
                faces.append("        \"%s\": { \"uv\": [%s, %s, %s, %s], \"texture\": \"#0\" }%s\n"
                        .formatted(FACES[f],
                                half(box.uv()[0]), half(box.uv()[1]), half(box.uv()[2]), half(box.uv()[3]),
                                f == FACES.length - 1 ? "" : ","));
            }
            elements.append("""
                        {
                          "name": "%s",
                          "from": [%s, %s, %s],
                          "to": [%s, %s, %s],
                          "faces": {
                    %s      }
                        }%s
                    """.formatted(box.name(),
                    number(box.from()[0]), number(box.from()[1]), number(box.from()[2]),
                    number(box.to()[0]), number(box.to()[1]), number(box.to()[2]),
                    faces, i == CAMERA.size() - 1 ? "" : ","));
        }

        return """
                {
                  "textures": {
                    "0": "ledger:item/camera",
                    "particle": "ledger:item/camera"
                  },
                  "elements": [
                %s  ],
                  "display": {
                    "head":   { "rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [1, 1, 1] },
                    "gui":    { "rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.9, 0.9, 0.9] },
                    "ground": { "rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5] },
                    "fixed":  { "rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1] },
                    "thirdperson_righthand": { "rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55] },
                    "firstperson_righthand": { "rotation": [0, 45, 0], "translation": [0, 4, 0], "scale": [0.6, 0.6, 0.6] }
                  }
                }
                """.formatted(elements);
    }

    /** The same boxes again, in the shape Blockbench opens. */
    private static String cameraBlockbench() {
        StringBuilder elements = new StringBuilder();
        for (int i = 0; i < CAMERA.size(); i++) {
            Box box = CAMERA.get(i);
            StringBuilder faces = new StringBuilder();
            for (int f = 0; f < FACES.length; f++) {
                faces.append("        \"%s\": { \"uv\": [%d, %d, %d, %d], \"texture\": 0 }%s\n"
                        .formatted(FACES[f], box.uv()[0], box.uv()[1], box.uv()[2], box.uv()[3],
                                f == FACES.length - 1 ? "" : ","));
            }
            elements.append("""
                        {
                          "name": "%s",
                          "box_uv": false,
                          "from": [%s, %s, %s],
                          "to": [%s, %s, %s],
                          "origin": [8, 0, 8],
                          "uuid": "ledger-camera-%d",
                          "faces": {
                    %s      }
                        }%s
                    """.formatted(box.name(),
                    number(box.from()[0]), number(box.from()[1]), number(box.from()[2]),
                    number(box.to()[0]), number(box.to()[1]), number(box.to()[2]),
                    i, faces, i == CAMERA.size() - 1 ? "" : ","));
        }

        StringBuilder outliner = new StringBuilder();
        for (int i = 0; i < CAMERA.size(); i++) {
            outliner.append("    \"ledger-camera-%d\"%s\n".formatted(i, i == CAMERA.size() - 1 ? "" : ","));
        }

        return """
                {
                  "meta": { "format_version": "4.10", "model_format": "free", "box_uv": false },
                  "name": "ledger_camera",
                  "model_identifier": "ledger_camera",
                  "resolution": { "width": %d, "height": %d },
                  "description": "Ledger surveillance camera. Lens faces -Z at rest; the armour stand carrying one is pitched down 52 degrees. Generated by tools/GenerateAssets.java - edit that, not this.",
                  "textures": [
                    {
                      "name": "camera",
                      "folder": "item",
                      "namespace": "ledger",
                      "id": "0",
                      "particle": true,
                      "render_mode": "normal",
                      "relative_path": "item/camera.png",
                      "uuid": "ledger-camera-texture"
                    }
                  ],
                  "elements": [
                %s  ],
                  "outliner": [
                %s  ]
                }
                """.formatted(SHEET, SHEET, elements, outliner);
    }

    /** Model JSON works in 0-16 whatever the sheet's real size. */
    private static String half(int uv) {
        return number(uv * 16.0 / SHEET);
    }

    /** Trims a whole number to an integer so the JSON reads cleanly. */
    private static String number(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    // ------------------------------------------------------------- writing

    private static void write(Path path, String text) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * A flat sprite model. Guns parent to {@code handheld} so they are gripped
     * and angled in the hand the way a tool is, rather than held flat out in
     * front like an ingot; menu icons parent to {@code generated} because they
     * are only ever seen in a slot.
     */
    private static String flatModel(String texture, boolean held) {
        return """
                {
                  "parent": "minecraft:item/%s",
                  "textures": {
                    "layer0": "ledger:item/%s"
                  }
                }
                """.formatted(held ? "handheld" : "generated", texture);
    }

    /** An item definition that picks a model from a custom_model_data string. */
    private static String selectDefinition(String fallbackModel, List<String> cases) {
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < cases.size(); i++) {
            String id = cases.get(i);
            body.append("""
                          {
                            "when": "%s",
                            "model": { "type": "minecraft:model", "model": "ledger:item/%s" }
                          }%s
                    """.formatted(id, id, i == cases.size() - 1 ? "" : ","));
        }
        return """
                {
                  "model": {
                    "type": "minecraft:select",
                    "property": "minecraft:custom_model_data",
                    "index": 0,
                    "cases": [
                %s    ],
                    "fallback": { "type": "minecraft:model", "model": "ledger:item/%s" }
                  }
                }
                """.formatted(body, fallbackModel);
    }

    // ------------------------------------------------------------------ main

    public static void main(String[] args) throws IOException {
        Files.createDirectories(TEXTURES);
        Files.createDirectories(MODELS);
        Files.createDirectories(ITEMS);

        List<String> gunCases = new ArrayList<>();
        for (GunSpec spec : guns()) {
            String name = "gun_" + spec.barrelId();
            drawGun(spec).write(name);
            write(MODELS.resolve(name + ".json"), flatModel(name, true));
            gunCases.add(name);
        }
        write(ITEMS.resolve("gun.json"), selectDefinition(gunCases.getFirst(), gunCases));

        List<String> iconCases = new ArrayList<>();
        for (PartSpec spec : parts()) {
            String name = "part_" + spec.id();
            drawPart(spec).write(name);
            write(MODELS.resolve(name + ".json"), flatModel(name, false));
            iconCases.add(name);
        }
        for (Map.Entry<String, Canvas> entry : uiIcons().entrySet()) {
            entry.getValue().write(entry.getKey());
            write(MODELS.resolve(entry.getKey() + ".json"), flatModel(entry.getKey(), false));
            iconCases.add(entry.getKey());
        }
        write(ITEMS.resolve("icon.json"), selectDefinition(iconCases.getFirst(), iconCases));

        drawCameraSheet().write("camera");
        write(MODELS.resolve("camera.json"), cameraModel());
        write(Path.of("models/camera.bbmodel"), cameraBlockbench());
        write(ITEMS.resolve("camera.json"), """
                {
                  "model": { "type": "minecraft:model", "model": "ledger:item/camera" }
                }
                """);

        System.out.println("wrote " + (gunCases.size() + iconCases.size() + 1) + " sprites");
    }
}
