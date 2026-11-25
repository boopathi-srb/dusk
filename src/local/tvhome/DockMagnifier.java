package local.tvhome;

/**
 * The macOS-dock wave as numbers: how much each tile grows or shrinks and rises given which
 * one has focus, and how far each slides sideways so tiles never overlap. Pure logic.
 */
final class DockMagnifier {

    /** One row's look: sizes and rises by distance from focus, and the size of everything further away. */
    static final class Profile {
        final float[] scales;
        final float[] liftsDp;
        final float restingScale;
        /** Whether other tiles slide aside; when false they never move and the focused tile grows into the gaps. */
        final boolean spread;

        Profile(float[] scales, float[] liftsDp, float restingScale, boolean spread) {
            this.scales = scales;
            this.liftsDp = liftsDp;
            this.restingScale = restingScale;
            this.spread = spread;
        }
    }

    /**
     * App row, as on the macOS dock: the focused tile is largest and rises out of the glass, its
     * neighbours grow a little, everything else stays normal size. The row spreads to make room
     * and the glass stretches with it, so the space to the glass stays the same.
     */
    static final Profile DOCK = new Profile(new float[] {1.22f, 1.08f}, new float[] {16f, 5f}, 1f, true);
    /**
     * Continue watching, the same wave as the app row: the focused card grows and rises a little,
     * its neighbours shrink a little, cards further away shrink more.
     */
    static final Profile CARDS = new Profile(new float[] {1.10f, 0.91f}, new float[] {4f, 0f}, 0.82f, true);

    static final int NO_FOCUS = -1;

    private DockMagnifier() {
    }

    /** With no focus in the row every tile sits at full size; otherwise by distance from focus. */
    static float scaleAt(int index, int focusedIndex, Profile profile) {
        if (focusedIndex < 0 || profile == null) {
            return 1f;
        }
        int distance = Math.abs(index - focusedIndex);
        return distance < profile.scales.length ? profile.scales[distance] : profile.restingScale;
    }

    /** How far up a tile rises, in dp. */
    static float liftAt(int index, int focusedIndex, Profile profile) {
        if (focusedIndex < 0 || profile == null) {
            return 0f;
        }
        int distance = Math.abs(index - focusedIndex);
        return distance < profile.liftsDp.length ? profile.liftsDp[distance] : 0f;
    }

    /** How far the row reaches past its resting ends on each side once spread, in the widths' unit. */
    static float spreadEachSide(float[] widths, float[] scales) {
        if (widths == null || scales == null || widths.length != scales.length) {
            return 0f;
        }
        float extra = 0f;
        for (int index = 0; index < widths.length; index++) {
            extra += widths[index] * (scales[index] - 1f);
        }
        return extra / 2f;
    }

    static float[] scales(int count, int focusedIndex, Profile profile) {
        float[] scales = new float[Math.max(0, count)];
        for (int index = 0; index < scales.length; index++) {
            scales[index] = scaleAt(index, focusedIndex, profile);
        }
        return scales;
    }

    /**
     * Sideways shift for each tile so that, at its new size, it sits next to its neighbours with
     * the original gaps, and the row as a whole stays centred where it was.
     *
     * @param lefts  each tile's resting left edge
     * @param widths each tile's resting width
     * @param scales each tile's new scale (grown about its own centre)
     */
    static float[] shifts(float[] lefts, float[] widths, float[] scales) {
        int count = lefts.length;
        float[] shifts = new float[count];
        if (count == 0 || widths.length != count || scales.length != count) {
            return shifts;
        }
        float restingSpan = lefts[count - 1] + widths[count - 1] - lefts[0];
        float grownSpan = restingSpan;
        for (int index = 0; index < count; index++) {
            grownSpan += widths[index] * (scales[index] - 1f);
        }
        float cursor = lefts[0] - (grownSpan - restingSpan) / 2f;
        for (int index = 0; index < count; index++) {
            float grownWidth = widths[index] * scales[index];
            float restingCentre = lefts[index] + widths[index] / 2f;
            shifts[index] = cursor + grownWidth / 2f - restingCentre;
            float gapToNext = index + 1 < count ? lefts[index + 1] - (lefts[index] + widths[index]) : 0f;
            cursor += grownWidth + gapToNext;
        }
        return shifts;
    }
}
