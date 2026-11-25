package local.tvhome;

/**
 * Pixel work for icons and card backdrops, on plain ARGB int arrays. Pure logic, no Android types.
 */
final class IconTrim {

    /** How close (per colour channel, 0..255) a pixel must be to the background colour to be erased. */
    static final int TOLERANCE = 40;
    /** Below this share of full saturation, a colour counts as grey and is not a brand colour. */
    private static final float MIN_BRAND_SATURATION = 0.25f;

    private IconTrim() {
    }

    /**
     * Makes the background around an icon transparent: starting from the edges, every pixel close to
     * the corner colour and connected to the edge is cleared. Shapes inside the icon (the white play
     * triangle inside YouTube's red) are not connected to the edge, so they stay.
     * Does nothing when the corner is already transparent.
     */
    static void clearEdgeBackground(int[] pixels, int width, int height) {
        if (pixels == null || width <= 0 || height <= 0 || pixels.length < width * height) {
            return;
        }
        int background = pixels[0];
        if (alpha(background) < 128) {
            return;
        }
        boolean[] seen = new boolean[width * height];
        int[] queue = new int[width * height];
        int head = 0;
        int tail = 0;
        for (int x = 0; x < width; x++) {
            tail = enqueue(x, 0, width, pixels, background, seen, queue, tail);
            tail = enqueue(x, height - 1, width, pixels, background, seen, queue, tail);
        }
        for (int y = 0; y < height; y++) {
            tail = enqueue(0, y, width, pixels, background, seen, queue, tail);
            tail = enqueue(width - 1, y, width, pixels, background, seen, queue, tail);
        }
        while (head < tail) {
            int index = queue[head++];
            pixels[index] = 0;
            int x = index % width;
            int y = index / width;
            if (x > 0) {
                tail = enqueue(x - 1, y, width, pixels, background, seen, queue, tail);
            }
            if (x < width - 1) {
                tail = enqueue(x + 1, y, width, pixels, background, seen, queue, tail);
            }
            if (y > 0) {
                tail = enqueue(x, y - 1, width, pixels, background, seen, queue, tail);
            }
            if (y < height - 1) {
                tail = enqueue(x, y + 1, width, pixels, background, seen, queue, tail);
            }
        }
    }

    /** The most vivid colour among {@code pixels}, or their average when they are all greyish; opaque. */
    static int brandColour(int[] pixels) {
        if (pixels == null || pixels.length == 0) {
            return 0xFF2A2447;
        }
        int best = -1;
        float bestScore = 0f;
        long red = 0;
        long green = 0;
        long blue = 0;
        for (int pixel : pixels) {
            red += (pixel >> 16) & 0xFF;
            green += (pixel >> 8) & 0xFF;
            blue += pixel & 0xFF;
            float score = saturation(pixel) * value(pixel);
            if (saturation(pixel) >= MIN_BRAND_SATURATION && score > bestScore) {
                bestScore = score;
                best = pixel;
            }
        }
        if (best != -1) {
            return best | 0xFF000000;
        }
        int count = pixels.length;
        return 0xFF000000 | (int) (red / count) << 16 | (int) (green / count) << 8 | (int) (blue / count);
    }

    /** True when a good share of the pixels are vivid colour, not black, white or grey. */
    static boolean isColourful(int[] pixels) {
        if (pixels == null || pixels.length == 0) {
            return false;
        }
        int vivid = 0;
        for (int pixel : pixels) {
            if (alpha(pixel) >= 128 && saturation(pixel) >= MIN_BRAND_SATURATION && value(pixel) >= 0.25f) {
                vivid++;
            }
        }
        return vivid * 3 >= pixels.length;
    }

    /** {@code colour} with each channel scaled by {@code keep} (0 = black, 1 = unchanged); opaque. */
    static int darken(int colour, float keep) {
        float share = Math.max(0f, Math.min(1f, keep));
        int red = Math.round(((colour >> 16) & 0xFF) * share);
        int green = Math.round(((colour >> 8) & 0xFF) * share);
        int blue = Math.round((colour & 0xFF) * share);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static int enqueue(int x, int y, int width, int[] pixels, int background, boolean[] seen, int[] queue, int tail) {
        int index = y * width + x;
        if (seen[index] || !isClose(pixels[index], background)) {
            return tail;
        }
        seen[index] = true;
        queue[tail] = index;
        return tail + 1;
    }

    static boolean isClose(int pixel, int background) {
        return Math.abs(((pixel >> 16) & 0xFF) - ((background >> 16) & 0xFF)) <= TOLERANCE
                && Math.abs(((pixel >> 8) & 0xFF) - ((background >> 8) & 0xFF)) <= TOLERANCE
                && Math.abs((pixel & 0xFF) - (background & 0xFF)) <= TOLERANCE
                && Math.abs(alpha(pixel) - alpha(background)) <= TOLERANCE;
    }

    private static int alpha(int pixel) {
        return (pixel >>> 24) & 0xFF;
    }

    private static float value(int pixel) {
        int max = Math.max((pixel >> 16) & 0xFF, Math.max((pixel >> 8) & 0xFF, pixel & 0xFF));
        return max / 255f;
    }

    private static float saturation(int pixel) {
        int red = (pixel >> 16) & 0xFF;
        int green = (pixel >> 8) & 0xFF;
        int blue = pixel & 0xFF;
        int max = Math.max(red, Math.max(green, blue));
        int min = Math.min(red, Math.min(green, blue));
        return max == 0 ? 0f : (max - min) / (float) max;
    }
}
