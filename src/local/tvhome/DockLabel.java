package local.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/**
 * A name that follows the focused tile: in a small pill above the focused dock tile (the app's
 * name, as on the macOS dock), or as plain small text below the focused Continue watching card
 * (the show's title).
 * It glides to each new tile and fades out when focus leaves the row.
 *
 * Drawn by hand on a fixed-size view rather than a TextView: changing a TextView's text
 * re-lays out the whole home screen on every move (measured: slow frames on this TV).
 */
final class DockLabel extends View {

    /** Space between the pill and the tile, above or below it. */
    private static final float GAP_ABOVE_TILE_DP = 8f;
    private static final float TEXT_SP = 13f;
    /** Under a card the title is plain text, a size below the "Continue watching" heading (10 sp). */
    private static final float PLAIN_TEXT_SP = 9f;
    private static final int PLAIN_TEXT_COLOUR = 0xE6FFFFFF;
    private static final int PLAIN_TEXT_SHADOW = 0x99000000;
    private static final float PAD_X_DP = 12f;
    private static final float PILL_HEIGHT_DP = 26f;
    /** Wide enough for any app name; the pill itself is drawn at the name's width, centred. */
    private static final float VIEW_WIDTH_DP = 320f;
    private static final int PILL_FILL = 0xCC14122A;
    private static final int TEXT_COLOUR = 0xFFFFFFFF;

    /** Which side of the focused tile the pill sits on. */
    enum Side { ABOVE, BELOW }

    private final ViewGroup root;
    private final Side side;
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF pill = new RectF();
    private final Rect scratch = new Rect();
    /** Separate from {@link #scratch}, which still holds the tile's place while the limit is read. */
    private final Rect limitRect = new Rect();
    private final float padX;
    private final float pillHeight;
    private final float gapAbove;
    private String name = "";
    private boolean shown;
    /** Below the cards, the title never starts left of this view (the "Continue watching" heading). */
    private View leftLimit;

    private DockLabel(Context context, ViewGroup root, Side side) {
        super(context);
        this.root = root;
        this.side = side;
        fillPaint.setColor(PILL_FILL);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(UiKit.FROSTED_STROKE);
        strokePaint.setStrokeWidth(UiKit.dp(context, 1));
        boolean plain = side == Side.BELOW;
        textPaint.setColor(plain ? PLAIN_TEXT_COLOUR : TEXT_COLOUR);
        textPaint.setTypeface(Typeface.create(plain ? "sans-serif" : "sans-serif-medium", Typeface.NORMAL));
        textPaint.setTextAlign(plain ? Paint.Align.LEFT : Paint.Align.CENTER);
        textPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, plain ? PLAIN_TEXT_SP : TEXT_SP,
                context.getResources().getDisplayMetrics()));
        if (plain) {
            textPaint.setShadowLayer(UiKit.dp(context, 3), 0, UiKit.dp(context, 1), PLAIN_TEXT_SHADOW);
        }
        padX = UiKit.dp(context, PAD_X_DP);
        pillHeight = UiKit.dp(context, PILL_HEIGHT_DP);
        gapAbove = UiKit.dp(context, GAP_ABOVE_TILE_DP);
        setAlpha(0f);
        setFocusable(false);
    }

    static DockLabel attach(FrameLayout root, Side side) {
        DockLabel label = new DockLabel(root.getContext(), root, side);
        root.addView(label, new FrameLayout.LayoutParams(
                UiKit.dp(root.getContext(), VIEW_WIDTH_DP), Math.round(label.pillHeight) + 2));
        return label;
    }

    /** Below the cards: the title starts no further left than {@code view} does. */
    void setLeftLimit(View view) {
        leftLimit = view;
    }

    /** Shows the tile's name (its description) next to it; see {@link #follow(View, float, float, float, String)}. */
    void follow(View tile, float finalScale, float finalShift, float finalLiftPx) {
        follow(tile, finalScale, finalShift, finalLiftPx, tile == null ? null : String.valueOf(tile.getContentDescription()));
    }

    /**
     * Shows {@code text} beside {@code tile} at the tile's final size and place; hides when there is
     * no tile or no text. Dock tiles grow up from their bottom edge; cards grow from their centre.
     */
    void follow(View tile, float finalScale, float finalShift, float finalLiftPx, String text) {
        if (tile == null || tile.getWidth() == 0 || text == null || text.isEmpty()) {
            hide();
            return;
        }
        name = text;
        invalidate();
        tile.getDrawingRect(scratch);
        root.offsetDescendantRectToMyCoords(tile, scratch);
        float tileCentreX = scratch.exactCenterX() + finalShift;
        float x = tileCentreX - getWidth() / 2f;
        float y;
        if (side == Side.BELOW) {
            x = Math.max(tileCentreX - scratch.width() * finalScale / 2f, leftEdgeOfLimit());
        }
        if (side == Side.ABOVE) {
            float grownTop = scratch.bottom - scratch.height() * finalScale - finalLiftPx;
            y = grownTop - getHeight() - gapAbove;
        } else {
            float grownBottom = scratch.exactCenterY() + scratch.height() * finalScale / 2f - finalLiftPx;
            y = grownBottom + gapAbove;
        }
        animate().cancel();
        if (!shown) {
            setX(x);
            setY(y);
            shown = true;
        }
        animate().x(x).y(y).alpha(1f).setInterpolator(Motion.GLIDE)
                .setDuration(Motion.duration(getContext(), Motion.FOCUS_MS)).start();
    }

    /** Where the limit view starts, in the same coordinates as this label; no limit when there is none. */
    private float leftEdgeOfLimit() {
        if (leftLimit == null || leftLimit.getWidth() == 0) {
            return Float.NEGATIVE_INFINITY;
        }
        leftLimit.getDrawingRect(limitRect);
        root.offsetDescendantRectToMyCoords(leftLimit, limitRect);
        return limitRect.left;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (side == Side.BELOW) {
            float baseline = pillHeight / 2f - (textPaint.descent() + textPaint.ascent()) / 2f;
            canvas.drawText(name, 0, baseline, textPaint);
            return;
        }
        float textWidth = textPaint.measureText(name);
        float pillWidth = textWidth + 2 * padX;
        float left = (getWidth() - pillWidth) / 2f;
        pill.set(left, 1, left + pillWidth, 1 + pillHeight);
        float radius = pillHeight / 2f;
        if (side == Side.ABOVE) {
            canvas.drawRoundRect(pill, radius, radius, fillPaint);
            canvas.drawRoundRect(pill, radius, radius, strokePaint);
        }
        float baseline = pill.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f;
        canvas.drawText(name, pill.centerX(), baseline, textPaint);
    }

    private void hide() {
        if (!shown) {
            return;
        }
        shown = false;
        animate().cancel();
        animate().alpha(0f).setDuration(Motion.duration(getContext(), Motion.FADE_MS)).start();
    }
}
