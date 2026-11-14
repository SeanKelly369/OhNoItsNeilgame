import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import javax.imageio.ImageIO;

/**
 * Adds a consistent, restrained blue sheen to the existing dark hair strands.
 * java art/AddNeilHairHighlights.java input.png output.png [backup.png]
 * Works on four-column atlases of square cells; never resizes or moves pixels.
 */
class AddNeilHairHighlights {
    static final int BASE = 0x0c2ab4;
    static final int SOFT = 0x1748c3;
    static final int LIGHT = 0x2b65d8;

    static boolean body(int p) {
        int r = (p >>> 16) & 255, g = (p >>> 8) & 255, b = p & 255;
        return (p >>> 24) >= 128 && b >= 160 && b > r * 1.5
            && b > g * 1.05 && g > r * 1.15;
    }

    static int[] shade(int[] source, int width, int height, boolean report) {
        int cell = width / 4, count = height / cell * 4;
        int[] output = source.clone();
        for (int frame = 0; frame < count; frame++) {
            int ox = frame % 4 * cell, oy = frame / 4 * cell;
            boolean[] mask = new boolean[cell * cell];
            double[] distance = new double[mask.length];
            int left = cell, right = 0, top = cell, bottom = 0, pixels = 0;
            for (int y = 0; y < cell; y++) for (int x = 0; x < cell; x++) {
                int local = y * cell + x, global = (oy + y) * width + ox + x;
                mask[local] = body(source[global]);
                distance[local] = mask[local] ? cell : 0;
                if (mask[local]) {
                    // Normalize the lit portion first so repeated exports are identical.
                    output[global] = (source[global] & 0xff000000) | BASE;
                    left = Math.min(left, x); right = Math.max(right, x);
                    top = Math.min(top, y); bottom = Math.max(bottom, y);
                    pixels++;
                }
            }
            if (pixels < 100) throw new IllegalArgumentException("No usable hair in frame " + frame);
            // Distance to dark grooves and outer edges makes the sheen follow the
            // actual drawn locks instead of painting a fixed stripe over the pose.
            for (int y = 0; y < cell; y++) for (int x = 0; x < cell; x++) {
                int i = y * cell + x;
                if (!mask[i]) continue;
                if (x == 0 || y == 0) distance[i] = 1;
                if (x > 0) distance[i] = Math.min(distance[i], distance[i - 1] + 1);
                if (y > 0) distance[i] = Math.min(distance[i], distance[i - cell] + 1);
                if (x > 0 && y > 0) distance[i] = Math.min(distance[i], distance[i - cell - 1] + 1.414);
                if (x + 1 < cell && y > 0) distance[i] = Math.min(distance[i], distance[i - cell + 1] + 1.414);
            }
            for (int y = cell - 1; y >= 0; y--) for (int x = cell - 1; x >= 0; x--) {
                int i = y * cell + x;
                if (!mask[i]) continue;
                if (x == cell - 1 || y == cell - 1) distance[i] = Math.min(distance[i], 1);
                if (x + 1 < cell) distance[i] = Math.min(distance[i], distance[i + 1] + 1);
                if (y + 1 < cell) distance[i] = Math.min(distance[i], distance[i + cell] + 1);
                if (x + 1 < cell && y + 1 < cell) distance[i] = Math.min(distance[i], distance[i + cell + 1] + 1.414);
                if (x > 0 && y + 1 < cell) distance[i] = Math.min(distance[i], distance[i + cell - 1] + 1.414);
            }
            double[] scores = new double[mask.length];
            ArrayList<Integer> eligible = new ArrayList<>();
            for (int y = 1; y + 1 < cell; y++) for (int x = 1; x + 1 < cell; x++) {
                int i = y * cell + x;
                if (!mask[i] || distance[i] < 2) continue;
                double smooth = 0;
                for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                    smooth += distance[i + dy * cell + dx];
                }
                double u = (x - left) / (double)Math.max(1, right - left);
                double v = (y - top) / (double)Math.max(1, bottom - top);
                scores[i] = smooth / 9 * (1 - 0.25 * v) * (1 - 0.1 * u);
                eligible.add(i);
            }
            eligible.sort(Comparator.<Integer>comparingDouble(i -> scores[i]).reversed().thenComparingInt(i -> i));
            int lit = (int)Math.round(pixels * 0.20);
            int bright = (int)Math.round(pixels * 0.05);
            if (eligible.size() < lit) throw new IllegalArgumentException("Insufficient strand interiors in frame " + frame);
            for (int rank = 0; rank < lit; rank++) {
                int i = eligible.get(rank);
                int global = (oy + i / cell) * width + ox + i % cell;
                output[global] = (source[global] & 0xff000000) | (rank < bright ? LIGHT : SOFT);
            }
            if (report) System.out.println("Frame " + frame + ": " + lit + "/" + pixels
                + " hair pixels highlighted (20%, including 5% lighter blue).");
        }
        for (int i = 0; i < source.length; i++) {
            if ((source[i] >>> 24) != (output[i] >>> 24)) throw new AssertionError("Alpha changed");
            if (!body(source[i]) && source[i] != output[i]) throw new AssertionError("Pixel outside hair mask changed");
        }
        return output;
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length > 3) throw new IllegalArgumentException("Expected input.png output.png [backup.png]");
        File inputFile = new File(args[0]), outputFile = new File(args[1]);
        byte[] originalBytes = Files.readAllBytes(inputFile.toPath());
        BufferedImage input = ImageIO.read(new java.io.ByteArrayInputStream(originalBytes));
        if (input == null || !input.getColorModel().hasAlpha()) throw new IllegalArgumentException("Expected transparent PNG");
        int w = input.getWidth(), h = input.getHeight();
        if (w < 4 || w % 4 != 0 || h % (w / 4) != 0) throw new IllegalArgumentException("Expected four columns of square cells");
        int[] original = input.getRGB(0, 0, w, h, null, 0, w);
        int[] edited = shade(original, w, h, true);
        if (!Arrays.equals(edited, shade(edited, w, h, false))) throw new AssertionError("Repeated application changed the result");
        BufferedImage output = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        output.setRGB(0, 0, w, h, edited, 0, w);
        if (!Arrays.equals(originalBytes, Files.readAllBytes(inputFile.toPath()))) {
            throw new IllegalStateException("Input changed during processing; retry with the current image");
        }
        if (args.length == 3) Files.write(new File(args[2]).toPath(), originalBytes, java.nio.file.StandardOpenOption.CREATE_NEW);
        if (!ImageIO.write(output, "png", outputFile)) throw new IllegalStateException("PNG writer missing");
        BufferedImage saved = ImageIO.read(outputFile);
        if (saved.getWidth() != w || saved.getHeight() != h
            || !Arrays.equals(edited, saved.getRGB(0, 0, w, h, null, 0, w))) throw new AssertionError("PNG round trip changed pixels");
        System.out.println("Verified dimensions, every alpha value, pixels outside the hair mask, PNG round trip, and idempotence.");
    }
}
