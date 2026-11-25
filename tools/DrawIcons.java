import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Draws Dusk's app icon and TV banner ("magnified dock": evening sky, sun, frosted
 * glass bar with the middle tile lifted and larger). Run once; the PNGs are kept in res/.
 * Usage: java tools/DrawIcons.java <res-dir>
 */
public final class DrawIcons {

    /** The name written on the banner; keep in step with app_name in res/values/strings.xml. */
    private static final String APP_NAME = "Dusk";
    private static final Color SKY_TOP = new Color(0x1E1B38);
    private static final Color SKY_BOTTOM = new Color(0x7A4A45);
    private static final Color SUN = new Color(0xFFD27A);
    private static final Color GLASS_FILL = new Color(255, 255, 255, 40);
    private static final Color GLASS_EDGE = new Color(255, 255, 255, 95);
    private static final Color RESTING_TILE = new Color(233, 236, 242, 190);
    private static final Color FOCUSED_TILE = Color.WHITE;

    private DrawIcons() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("DrawIcons: give the res directory, e.g. java tools/DrawIcons.java res");
            System.exit(2);
        }
        File folder = new File(args[0], "drawable-xhdpi");
        if (!folder.isDirectory() && !folder.mkdirs()) {
            System.err.println("DrawIcons: could not create " + folder);
            System.exit(1);
        }
        save(drawIcon(192), new File(folder, "icon.png"));
        save(drawBanner(640, 360), new File(folder, "banner.png"));
    }

    /** Square icon with rounded corners: sun top right, glass bar with three tiles, middle lifted. */
    static BufferedImage drawIcon(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D canvas = smooth(image);
        float unit = size / 300f;
        canvas.setClip(new RoundRectangle2D.Float(0, 0, size, size, 132 * unit, 132 * unit));
        paintSky(canvas, size, size);
        paintSun(canvas, 228 * unit, 80 * unit, 24 * unit);
        paintGlass(canvas, 30 * unit, 174 * unit, 240 * unit, 70 * unit, 3 * unit);
        paintTile(canvas, 48 * unit, 188 * unit, 56 * unit, 42 * unit, RESTING_TILE);
        paintTile(canvas, 196 * unit, 188 * unit, 56 * unit, 42 * unit, RESTING_TILE);
        paintTile(canvas, 110 * unit, 146 * unit, 80 * unit, 64 * unit, FOCUSED_TILE);
        canvas.dispose();
        return image;
    }

    /** Wide banner: the app name on the sky, sun top right, glass bar of four tiles with the third lifted. */
    static BufferedImage drawBanner(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D canvas = smooth(image);
        float unit = width / 320f;
        paintSky(canvas, width, height);
        paintSun(canvas, 284 * unit, 38 * unit, 13 * unit);
        paintWord(canvas, APP_NAME, 24 * unit, 74 * unit, 36 * unit);
        paintGlass(canvas, 24 * unit, 112 * unit, 272 * unit, 46 * unit, 1.5f * unit);
        paintTile(canvas, 40 * unit, 121 * unit, 48 * unit, 28 * unit, RESTING_TILE);
        paintTile(canvas, 96 * unit, 121 * unit, 48 * unit, 28 * unit, RESTING_TILE);
        paintTile(canvas, 232 * unit, 121 * unit, 48 * unit, 28 * unit, RESTING_TILE);
        paintTile(canvas, 152 * unit, 98 * unit, 72 * unit, 46 * unit, FOCUSED_TILE);
        canvas.dispose();
        return image;
    }

    private static Graphics2D smooth(BufferedImage image) {
        Graphics2D canvas = image.createGraphics();
        canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        canvas.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        canvas.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        canvas.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return canvas;
    }

    private static void paintSky(Graphics2D canvas, int width, int height) {
        canvas.setPaint(new GradientPaint(0, 0, SKY_TOP, 0, height, SKY_BOTTOM));
        canvas.fillRect(0, 0, width, height);
    }

    /** The sun with a soft glow around it, as on the home screen. */
    private static void paintSun(Graphics2D canvas, float centreX, float centreY, float radius) {
        float glow = radius * 2.6f;
        canvas.setPaint(new RadialGradientPaint(new Point2D.Float(centreX, centreY), glow,
                new float[] {0f, 1f}, new Color[] {new Color(255, 210, 122, 90), new Color(255, 210, 122, 0)}));
        canvas.fill(new java.awt.geom.Ellipse2D.Float(centreX - glow, centreY - glow, glow * 2, glow * 2));
        canvas.setColor(SUN);
        canvas.fill(new java.awt.geom.Ellipse2D.Float(centreX - radius, centreY - radius, radius * 2, radius * 2));
    }

    private static void paintGlass(Graphics2D canvas, float left, float top, float width, float height, float edge) {
        RoundRectangle2D.Float bar = new RoundRectangle2D.Float(left, top, width, height, height, height);
        canvas.setColor(GLASS_FILL);
        canvas.fill(bar);
        canvas.setColor(GLASS_EDGE);
        canvas.setStroke(new BasicStroke(edge));
        canvas.draw(bar);
    }

    private static void paintTile(Graphics2D canvas, float left, float top, float width, float height, Color colour) {
        float corner = height * 0.55f;
        canvas.setColor(colour);
        canvas.fill(new RoundRectangle2D.Float(left, top, width, height, corner, corner));
    }

    private static void paintWord(Graphics2D canvas, String word, float left, float baseline, float size) {
        canvas.setColor(Color.WHITE);
        canvas.setFont(new Font("Helvetica Neue", Font.BOLD, Math.round(size)));
        canvas.drawString(word, left, baseline);
    }

    /** Writing a file reports failure only by throwing; this names the file and stops. */
    private static void save(BufferedImage image, File file) {
        try {
            if (!ImageIO.write(image, "png", file)) {
                System.err.println("DrawIcons: no PNG writer for " + file);
                System.exit(1);
            }
            System.out.println("DrawIcons: wrote " + file + " (" + image.getWidth() + "x" + image.getHeight() + ")");
        } catch (IOException failed) {
            System.err.println("DrawIcons: could not write " + file + ": " + failed.getMessage());
            System.exit(1);
        }
    }
}
