import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

/**
 * One-off generator for the test sprite sheet.
 * Produces src/main/resources/assets/inventorygif/textures/gui/animation.png
 * — 8 frames of 128x128 stacked vertically (128x1024 total), each frame a
 * different hue with a frame number badge, so looping is clearly visible.
 *
 * Run from the project root with: java tools/GenerateAnimation.java
 * (single-file source-code program, part of the JDK — no Python needed).
 */
public class GenerateAnimation {

    private static final int FRAMES = 8;
    private static final int SIZE = 128;

    public static void main(String[] args) throws IOException {
        BufferedImage sheet = new BufferedImage(SIZE, SIZE * FRAMES, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sheet.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        for (int i = 0; i < FRAMES; i++) {
            int top = i * SIZE;

            // Frame background: distinct hue per frame.
            Color bg = Color.getHSBColor(i / (float) FRAMES, 0.65f, 0.85f);
            g.setColor(bg);
            g.fillRect(0, top, SIZE, SIZE);

            // Border so the frame bounds are visible over the inventory GUI.
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(4f));
            g.drawRect(2, top + 2, SIZE - 4, SIZE - 4);

            // Rotating dot to make motion obvious.
            double angle = (Math.PI * 2.0 * i) / FRAMES;
            int cx = SIZE / 2 + (int) (Math.cos(angle) * 36);
            int cy = top + SIZE / 2 + (int) (Math.sin(angle) * 36);
            g.setColor(Color.BLACK);
            g.fill(new Ellipse2D.Double(cx - 14, cy - 14, 28, 28));
            g.setColor(Color.WHITE);
            g.fill(new Ellipse2D.Double(cx - 10, cy - 10, 20, 20));

            // Frame number badge.
            g.setColor(new Color(0, 0, 0, 160));
            g.fillRoundRect(SIZE / 2 - 18, top + SIZE - 34, 36, 26, 8, 8);
            g.setColor(Color.WHITE);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
            String label = String.valueOf(i);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(label, SIZE / 2 - fm.stringWidth(label) / 2, top + SIZE - 14);
        }

        g.dispose();

        Path out = Path.of("src", "main", "resources", "assets", "inventorygif", "textures", "gui", "animation.png");
        Files.createDirectories(out.getParent());
        ImageIO.write(sheet, "PNG", out.toFile());

        System.out.println("Wrote " + out.toAbsolutePath() + " (" + SIZE + "x" + (SIZE * FRAMES) + ")");
    }
}
