package local.tvhome;

import android.graphics.Outline;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;

/**
 * How tiles show focus, after the macOS dock: the focused tile grows the most, its neighbours
 * a little, and the row slides apart so nothing overlaps (numbers in {@link DockMagnifier}).
 * App tiles grow upward out of the glass; cards grow about their centre.
 */
final class FocusStyle {

    /** Tiles no longer carry a ring, so holders need no extra space around the picture. */
    static final float HOLDER_INSET_DP = 0f;

    /** All apps grid: a plain zoom, since a wave across a wrapped grid reads as noise. */
    static final float GRID_FOCUSED_SCALE = 1.14f;
    static final float GRID_RESTING_SCALE = 0.96f;

    private static final int TARGET_TAG = R.id.focus_target;


    /** Told which tile in a row now has focus (null when the row lost it), and where it will settle. */
    interface FocusListener {
        /** {@code spreadEachSidePx}: how far the row reaches past its resting ends on each side. */
        void onRowFocus(View focusedTile, float finalScale, float finalShift, float finalLiftPx, float spreadEachSidePx);
    }

    private FocusStyle() {
    }

    /** Rounded clipping for the picture inside a holder. */
    static void roundCorners(View view, float radiusPx) {
        view.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View outlined, Outline outline) {
                outline.setRoundRect(0, 0, outlined.getWidth(), outlined.getHeight(), radiusPx);
            }
        });
        view.setClipToOutline(true);
    }

    /**
     * Gives {@code row} the dock wave. A focus listener a tile already has (a card revealing its
     * details) keeps running, called first. {@code growUp} anchors tiles at their bottom edge, as on
     * the macOS dock. {@code listener} may be null.
     */
    static void magnifyRow(ViewGroup row, DockMagnifier.Profile profile, boolean growUp, FocusListener listener) {
        row.setClipChildren(false);
        for (int index = 0; index < row.getChildCount(); index++) {
            View tile = row.getChildAt(index);
            View.OnFocusChangeListener ownListener = tile.getOnFocusChangeListener();
            tile.setOnFocusChangeListener((changed, hasFocus) -> {
                if (ownListener != null) {
                    ownListener.onFocusChange(changed, hasFocus);
                }
                if (hasFocus) {
                    magnify(row, row.indexOfChild(changed), profile, growUp, listener);
                } else {
                    row.post(() -> settleIfRowLostFocus(row, profile, growUp, listener));
                }
            });
        }
        View focused = row.getFocusedChild();
        magnify(row, focused == null ? DockMagnifier.NO_FOCUS : row.indexOfChild(focused), profile, growUp, listener);
    }

    /** All apps: the focused tile zooms and lifts; the rest sit slightly smaller. */
    static void zoomGrid(ViewGroup grid) {
        grid.setClipChildren(false);
        for (int index = 0; index < grid.getChildCount(); index++) {
            View tile = grid.getChildAt(index);
            tile.setScaleX(GRID_RESTING_SCALE);
            tile.setScaleY(GRID_RESTING_SCALE);
            tile.setOnFocusChangeListener((changed, hasFocus) -> {
                float scale = hasFocus ? GRID_FOCUSED_SCALE : GRID_RESTING_SCALE;
                changed.animate().cancel();
                changed.animate().scaleX(scale).scaleY(scale)
                        .setInterpolator(Motion.GLIDE).setDuration(Motion.duration(changed.getContext(), Motion.FOCUS_MS)).start();
            });
        }
    }

    private static void settleIfRowLostFocus(ViewGroup row, DockMagnifier.Profile profile, boolean growUp, FocusListener listener) {
        if (!row.hasFocus()) {
            magnify(row, DockMagnifier.NO_FOCUS, profile, growUp, listener);
        }
    }

    /** Tile positions are only known after layout: a freshly rebuilt row waits for its next layout pass. */
    private static void magnify(ViewGroup row, int focusedIndex, DockMagnifier.Profile profile, boolean growUp, FocusListener listener) {
        if (row.isLayoutRequested() || row.getWidth() == 0) {
            row.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    row.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    View focused = row.getFocusedChild();
                    int current = focused == null ? DockMagnifier.NO_FOCUS : row.indexOfChild(focused);
                    magnifyLaidOut(row, current, profile, growUp, listener);
                }
            });
            return;
        }
        magnifyLaidOut(row, focusedIndex, profile, growUp, listener);
    }

    private static void magnifyLaidOut(ViewGroup row, int focusedIndex, DockMagnifier.Profile profile, boolean growUp,
            FocusListener listener) {
        int count = row.getChildCount();
        float[] lefts = new float[count];
        float[] widths = new float[count];
        for (int index = 0; index < count; index++) {
            View tile = row.getChildAt(index);
            lefts[index] = tile.getLeft();
            widths[index] = tile.getWidth();
        }
        float[] scales = DockMagnifier.scales(count, focusedIndex, profile);
        float[] shifts = profile != null && profile.spread
                ? DockMagnifier.shifts(lefts, widths, scales) : new float[count];
        float[] liftsPx = new float[count];
        for (int index = 0; index < count; index++) {
            View tile = row.getChildAt(index);
            liftsPx[index] = dp(tile, DockMagnifier.liftAt(index, focusedIndex, profile));
            animateTile(tile, scales[index], shifts[index], liftsPx[index], growUp);
        }
        if (listener != null) {
            boolean hasFocus = focusedIndex >= 0 && focusedIndex < count;
            listener.onRowFocus(hasFocus ? row.getChildAt(focusedIndex) : null,
                    hasFocus ? scales[focusedIndex] : 1f, hasFocus ? shifts[focusedIndex] : 0f,
                    hasFocus ? liftsPx[focusedIndex] : 0f,
                    profile != null && profile.spread ? DockMagnifier.spreadEachSide(widths, scales) : 0f);
        }
    }

    /**
     * Animates one tile towards its new size and place. A tile already heading there is left alone,
     * so a move only animates the two tiles that change. No shadow and no offscreen layer: both
     * cost a redraw per frame on this TV's GPU (measured: 6 slow draws in 14 moves).
     */
    private static void animateTile(View tile, float scale, float shift, float liftPx, boolean growUp) {
        float[] target = {scale, shift, -liftPx};
        if (java.util.Arrays.equals(target, (float[]) tile.getTag(TARGET_TAG))) {
            return;
        }
        tile.setTag(TARGET_TAG, target);
        tile.setPivotX(tile.getWidth() / 2f);
        tile.setPivotY(growUp ? tile.getHeight() : tile.getHeight() / 2f);
        tile.animate()
                .scaleX(scale)
                .scaleY(scale)
                .translationX(shift)
                .translationY(-liftPx)
                .setInterpolator(Motion.GLIDE)
                .setDuration(Motion.duration(tile.getContext(), Motion.FOCUS_MS))
                .start();
    }

    private static float dp(View view, float value) {
        return value * view.getResources().getDisplayMetrics().density;
    }
}
