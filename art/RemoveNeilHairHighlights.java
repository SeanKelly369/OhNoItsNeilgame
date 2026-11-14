import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import javax.imageio.ImageIO;

/**
 * Removes cyan/white hair glints without resampling the sprite atlas.
 * Usage: java art/RemoveNeilHairHighlights.java input.png output.png [backup.png]
 * Use a separate output path to review before replacing production artwork.
 */
class RemoveNeilHairHighlights {
    static int red(int p) { return (p >>> 16) & 255; }
    static int green(int p) { return (p >>> 8) & 255; }
    static int blue(int p) { return p & 255; }
    static boolean hair(int p) {
        int r = red(p), g = green(p), b = blue(p);
        return (p >>> 24) != 0 && b > 35 && b > r * 1.5
            && b > g * 1.05 && g > r * 1.15;
    }
    static boolean pale(int p) {
        return (p >>> 24) != 0 && red(p) >= 60 && green(p) >= 75
            && blue(p) >= 100 && blue(p) >= red(p) - 8 && blue(p) >= green(p) - 8;
    }
    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length > 3) throw new IllegalArgumentException("Expected input.png output.png [backup.png]");
        BufferedImage input = ImageIO.read(new File(args[0]));
        if (input == null || !input.getColorModel().hasAlpha()) {
            throw new IllegalArgumentException("Expected an RGBA PNG");
        }
        int w = input.getWidth(), h = input.getHeight();
        if (w % 4 != 0 || h % (w / 4) != 0) throw new IllegalArgumentException("Expected four columns of square cells");
        int cellSize = w / 4;
        int frameCount = 4 * (h / cellSize);
        int[] pixels = input.getRGB(0, 0, w, h, null, 0, w);
        int[] result = pixels.clone();
        boolean[] whiteHair = new boolean[pixels.length];
        boolean[] visited = new boolean[pixels.length];
        int whiteComponents = 0;
        // White glints are enclosed by blue hair. Eye whites and clothing have
        // black/skin/brown boundaries, so they fail the enclosure check.
        for (int start = 0; start < pixels.length; start++) {
            if (visited[start] || !pale(pixels[start])) continue;
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            ArrayList<Integer> component = new ArrayList<>();
            queue.add(start);
            visited[start] = true;
            int boundary = 0, blueBoundary = 0;
            while (!queue.isEmpty()) {
                int i = queue.removeFirst();
                component.add(i);
                int x = i % w, y = i / w;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0) continue;
                        int nx = x + dx, ny = y + dy;
                        if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                        int n = ny * w + nx;
                        if (pale(pixels[n])) {
                            if (!visited[n]) { visited[n] = true; queue.add(n); }
                        } else {
                            boundary++;
                            if (hair(pixels[n])) blueBoundary++;
                        }
                    }
                }
            }
            int frame = (start / w / cellSize) * 4 + (start % w / (w / 4));
            double enclosure = frame >= 28 && frame <= 31 ? 0.50 : 0.75;
            if (boundary > 0 && blueBoundary >= boundary * enclosure) {
                for (int i : component) whiteHair[i] = true;
                whiteComponents++;
            }
        }
        int[] changedByFrame = new int[frameCount];
        int whitePixels = 0;
        for (int i = 0; i < pixels.length; i++) {
            int p = pixels[i];
            if (whiteHair[i]) {
                result[i] = (p & 0xff000000) | 0x0c2ab4;
                whitePixels++;
            } else if (hair(p)) {
                // An idempotent dark-blue ceiling removes bright streaks while
                // retaining the original darker strand shadows.
                result[i] = (p & 0xff000000) | (Math.min(red(p), 12) << 16)
                    | (Math.min(green(p), 42) << 8) | Math.min(blue(p), 180);
            }
            if ((p >>> 24) != (result[i] >>> 24)) throw new AssertionError("Alpha changed");
            if (p != result[i]) changedByFrame[(i / w / cellSize) * 4 + (i % w / (w / 4))]++;
            if (!whiteHair[i] && !hair(p) && p != result[i]) throw new AssertionError("Non-hair pixel changed");
        }
        BufferedImage output = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        output.setRGB(0, 0, w, h, result, 0, w);
        if (args.length == 3) java.nio.file.Files.copy(new File(args[0]).toPath(), new File(args[2]).toPath());
        if (!ImageIO.write(output, "png", new File(args[1]))) throw new IllegalStateException("PNG writer missing");
        BufferedImage saved = ImageIO.read(new File(args[1]));
        if (saved.getWidth() != w || saved.getHeight() != h
            || !Arrays.equals(result, saved.getRGB(0, 0, w, h, null, 0, w))) {
            throw new AssertionError("PNG round trip changed pixels");
        }
        System.out.println("Preserved dimensions (" + w + " x " + h + "), every alpha value and all pixels outside the hair mask.");
        System.out.println("Removed " + whitePixels + " pale highlight pixels in " + whiteComponents + " enclosed components.");
        System.out.println("Changed hair pixels per frame: " + Arrays.toString(changedByFrame));
    }
}
