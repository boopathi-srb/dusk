package local.tvhome;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Builds the app tiles and the Continue watching cards. Each is a focusable holder that
 * draws the focus ring, around a rounded, clipped picture (see {@link FocusStyle}).
 */
final class TileFactory {

    static final float TILE_WIDTH_DP = 84f;
    static final float TILE_HEIGHT_DP = 47f;
    static final float TILE_RADIUS_DP = 16f;
    /** The All apps tile is a square the height of the others. */
    static final float ALL_APPS_SIZE_DP = TILE_HEIGHT_DP;

    /** 16:9, like the thumbnails apps provide. */
    static final float CARD_WIDTH_DP = 144f;
    static final float CARD_HEIGHT_DP = 81f;
    static final float CARD_RADIUS_DP = 16f;

    private static final float ALL_APPS_ICON_DP = 26f;
    private static final int ALL_APPS_FILL = 0x2EFFFFFF;

    /** Fallback tile colours, used when an app has no TV banner. Pairs: top-left, bottom-right. */
    private static final int[][] TILE_COLOURS = {
        {0xFFF08C86, 0xFFD2616E},
        {0xFF6F9BF2, 0xFF5468D8},
        {0xFFF4BC6A, 0xFFE49A4E},
        {0xFF7AD7B2, 0xFF55AE8C},
        {0xFFAE98F4, 0xFF8A6FDF},
        {0xFF5FC6D8, 0xFF3E96B8},
        {0xFFE98FC0, 0xFFC2649C},
    };

    private static final int CARD_FALLBACK_TOP = 0x993A3360;
    private static final int CARD_FALLBACK_BOTTOM = 0xCC1A1730;

    /**
     * An adaptive icon's symbol layer is drawn on a 108-unit canvas with the symbol in the middle
     * 72 units, so it is drawn at 1.5x the tile height to make the symbol itself fill the height.
     */
    private static final float ADAPTIVE_SYMBOL_SCALE = 1.5f;
    /** Older flat icons already fill their own square: drawn at the tile height, with a little margin. */
    private static final float FLAT_ICON_SCALE = 0.82f;
    /** Darkens the bottom of the picture so the title stays readable. */
    private static final int SCRIM_BOTTOM = 0xE6000000;
    private static final int SCRIM_TOP = 0x00000000;
    private static final int CARD_TITLE = 0xFFFFFFFF;
    private static final int CARD_STATUS = 0xFFE0D8F4;
    /** Cards without a picture keep a light scrim at rest for their title; it darkens under the details. */
    private static final float SCRIM_RESTING_ALPHA = 0.5f;
    /** Black at the card's bottom edge fading to clear 30% of the way up, always on: the progress bar reads on any picture. */
    private static final float PROGRESS_FADE_SHARE = 0.3f;
    private static final int PROGRESS_FADE_BOTTOM = 0xFF000000;
    private static final float BADGE_SIZE_DP = 20f;
    /** Icons are trimmed at this size: sharp at the badge's 20 dp, cheap to scan. */
    private static final int BADGE_RENDER_PX = 96;
    /** The app tile is sampled small to find its colour. */
    private static final int SAMPLE_WIDTH_PX = 32;
    private static final int SAMPLE_HEIGHT_PX = 18;
    private static final float BACKDROP_LIGHT_SHARE = 0.55f;
    private static final float BACKDROP_DARK_SHARE = 0.22f;
    /** How far the badge hangs past the card's top and right edges. */
    private static final float BADGE_OVERHANG_DP = 6f;
    private static final int PROGRESS_FILL = 0xFFF4BC6A;
    private static final int PROGRESS_TRACK = 0x40FFFFFF;

    private TileFactory() {
    }

    /** Holder size for a picture of {@code pictureDp}: the picture plus room for the ring on both sides. */
    static float holderSize(float pictureDp) {
        return pictureDp + 2 * FocusStyle.HOLDER_INSET_DP;
    }

    static View appTile(Context context, AppCatalog.LaunchableApp app, int position) {
        FrameLayout picture = roundedPicture(context, TILE_RADIUS_DP);
        if (app.banner != null) {
            picture.addView(image(context, app.banner), matchParent());
        } else {
            fillWithIcon(context, picture, app.icon, position);
        }
        return holder(context, picture, TILE_RADIUS_DP, app.label);
    }

    /**
     * For apps with no TV banner (most of Amazon's own): the icon fills the tile, no text.
     * A modern (adaptive) icon is split: its background layer covers the whole tile and its
     * symbol sits large in the middle. An older flat icon fills the tile's height over a colour.
     */
    private static void fillWithIcon(Context context, FrameLayout picture, Drawable icon, int position) {
        if (icon instanceof AdaptiveIconDrawable) {
            AdaptiveIconDrawable layered = (AdaptiveIconDrawable) icon;
            if (layered.getBackground() != null) {
                picture.addView(image(context, layered.getBackground()), matchParent());
            }
            if (layered.getForeground() != null) {
                int symbolSize = UiKit.dp(context, TILE_HEIGHT_DP * ADAPTIVE_SYMBOL_SCALE);
                picture.addView(fitted(context, layered.getForeground()),
                        new FrameLayout.LayoutParams(symbolSize, symbolSize, Gravity.CENTER));
            }
            return;
        }
        int[] colours = TILE_COLOURS[Math.floorMod(position, TILE_COLOURS.length)];
        picture.setBackground(UiKit.roundedGradient(context, colours[0], colours[1], TILE_RADIUS_DP));
        int iconSize = UiKit.dp(context, TILE_HEIGHT_DP * FLAT_ICON_SCALE);
        picture.addView(fitted(context, icon), new FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER));
    }

    static View allAppsTile(Context context, String description) {
        FrameLayout picture = roundedPicture(context, TILE_RADIUS_DP);
        picture.setBackground(UiKit.rounded(context, ALL_APPS_FILL, TILE_RADIUS_DP, 0, 0));
        ImageView icon = new ImageView(context);
        icon.setImageResource(R.drawable.ic_all_apps);
        int iconSize = UiKit.dp(context, ALL_APPS_ICON_DP);
        picture.addView(icon, new FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER));
        return holder(context, picture, TILE_RADIUS_DP, description);
    }

    /**
     * A 16:9 card: the show's picture (it carries the show's own title) and its progress bar, with
     * the app's symbol on the top-right corner. Season, episode and time left fade in only while the
     * card has focus. With no show picture, the title is written over {@code backdrop} (a blurred
     * app tile) or plain glass.
     */
    static View watchCard(Context context, WatchNextRules.Item item, String appName, Drawable showPicture,
            Drawable backdrop, Drawable appIcon) {
        FrameLayout picture = roundedPicture(context, CARD_RADIUS_DP);
        boolean hasPicture = showPicture != null;
        if (hasPicture) {
            picture.addView(image(context, showPicture), matchParent());
        } else if (backdrop != null) {
            picture.addView(image(context, backdrop), matchParent());
        } else {
            GradientDrawable glass = UiKit.roundedGradient(context, CARD_FALLBACK_TOP, CARD_FALLBACK_BOTTOM, CARD_RADIUS_DP);
            glass.setStroke(UiKit.dp(context, 1), UiKit.FROSTED_STROKE);
            picture.setBackground(glass);
        }
        View scrim = new View(context);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[] {SCRIM_BOTTOM, SCRIM_TOP}));
        float restingScrim = hasPicture ? 0f : SCRIM_RESTING_ALPHA;
        scrim.setAlpha(restingScrim);
        float scrimShare = hasPicture ? 0.42f : 0.7f;
        picture.addView(progressFade(context), new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                UiKit.dp(context, CARD_HEIGHT_DP * PROGRESS_FADE_SHARE), Gravity.BOTTOM));
        picture.addView(scrim, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                UiKit.dp(context, CARD_HEIGHT_DP * scrimShare), Gravity.BOTTOM));
        View details = infoRow(context, item);
        details.setAlpha(0f);
        picture.addView(cardFooter(context, item, details, hasPicture), new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
        FrameLayout holder = holder(context, picture, CARD_RADIUS_DP, item.title + ", " + appName);
        holder.setTag(R.id.card_title, WatchNextRules.cardTitle(item));
        addCornerBadge(context, holder, appIcon);
        holder.setOnFocusChangeListener((card, hasFocus) -> revealDetails(details, scrim, restingScrim, hasFocus));
        return holder;
    }

    private static View progressFade(Context context) {
        View fade = new View(context);
        fade.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[] {PROGRESS_FADE_BOTTOM, SCRIM_TOP}));
        return fade;
    }

    /** Season, episode and time left in while focused, out when focus moves on. */
    private static void revealDetails(View details, View scrim, float restingScrim, boolean hasFocus) {
        long duration = Motion.duration(details.getContext(), Motion.FADE_MS);
        details.animate().alpha(hasFocus ? 1f : 0f).setDuration(duration).start();
        scrim.animate().alpha(hasFocus ? 1f : restingScrim).setDuration(duration).start();
    }

    /**
     * The app's symbol sitting over the card's top-right corner, with nothing behind it. A modern
     * icon is two layers (symbol and background): only the symbol is used, enlarged because it is
     * drawn in the middle of a padded canvas. An older single-layer icon can't be split, so it is
     * cut to a circle instead.
     */
    private static void addCornerBadge(Context context, FrameLayout holder, Drawable appIcon) {
        if (appIcon == null) {
            return;
        }
        int size = UiKit.dp(context, BADGE_SIZE_DP);
        FrameLayout.LayoutParams corner = new FrameLayout.LayoutParams(size, size, Gravity.TOP | Gravity.END);
        int overhang = -UiKit.dp(context, BADGE_OVERHANG_DP);
        corner.setMargins(0, overhang, overhang, 0);
        holder.addView(badgeFor(context, appIcon), corner);
    }

    /**
     * A two-layer icon keeps its background when that background is colourful (JioHotstar's
     * gradient), shown as a full circle; a plain black or white background (Stremio) is dropped and
     * only the symbol shown. A single-layer icon has its plain surround erased instead.
     */
    private static ImageView badgeFor(Context context, Drawable appIcon) {
        if (appIcon instanceof AdaptiveIconDrawable && hasColourfulBackground((AdaptiveIconDrawable) appIcon)) {
            return roundIcon(context, appIcon);
        }
        if (appIcon instanceof AdaptiveIconDrawable && ((AdaptiveIconDrawable) appIcon).getForeground() != null) {
            ImageView symbol = fitted(context, ((AdaptiveIconDrawable) appIcon).getForeground());
            symbol.setScaleX(ADAPTIVE_SYMBOL_SCALE);
            symbol.setScaleY(ADAPTIVE_SYMBOL_SCALE);
            return symbol;
        }
        return fitted(context, new android.graphics.drawable.BitmapDrawable(context.getResources(), trimmedIcon(appIcon)));
    }

    private static boolean hasColourfulBackground(AdaptiveIconDrawable icon) {
        Drawable background = icon.getBackground();
        if (background == null) {
            return false;
        }
        android.graphics.Bitmap sample = render(background, SAMPLE_WIDTH_PX, SAMPLE_WIDTH_PX);
        int[] pixels = new int[SAMPLE_WIDTH_PX * SAMPLE_WIDTH_PX];
        sample.getPixels(pixels, 0, SAMPLE_WIDTH_PX, 0, 0, SAMPLE_WIDTH_PX, SAMPLE_WIDTH_PX);
        return IconTrim.isColourful(pixels);
    }

    private static ImageView roundIcon(Context context, Drawable icon) {
        ImageView round = image(context, icon);
        round.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        round.setClipToOutline(true);
        return round;
    }

    /** A single-layer icon with the background around it erased (see {@link IconTrim}). */
    private static android.graphics.Bitmap trimmedIcon(Drawable icon) {
        android.graphics.Bitmap bitmap = render(icon, BADGE_RENDER_PX, BADGE_RENDER_PX);
        int[] pixels = new int[BADGE_RENDER_PX * BADGE_RENDER_PX];
        bitmap.getPixels(pixels, 0, BADGE_RENDER_PX, 0, 0, BADGE_RENDER_PX, BADGE_RENDER_PX);
        IconTrim.clearEdgeBackground(pixels, BADGE_RENDER_PX, BADGE_RENDER_PX);
        bitmap.setPixels(pixels, 0, BADGE_RENDER_PX, 0, 0, BADGE_RENDER_PX, BADGE_RENDER_PX);
        return bitmap;
    }

    /**
     * A stand-in picture for a card whose app gives none (YouTube): a dark gradient in the most vivid
     * colour of the app's own TV tile (YouTube's red).
     */
    static Drawable brandBackdrop(Context context, Drawable banner) {
        if (banner == null) {
            return null;
        }
        android.graphics.Bitmap tiny = render(banner, SAMPLE_WIDTH_PX, SAMPLE_HEIGHT_PX);
        int[] pixels = new int[SAMPLE_WIDTH_PX * SAMPLE_HEIGHT_PX];
        tiny.getPixels(pixels, 0, SAMPLE_WIDTH_PX, 0, 0, SAMPLE_WIDTH_PX, SAMPLE_HEIGHT_PX);
        int brand = IconTrim.brandColour(pixels);
        return UiKit.roundedGradient(context, IconTrim.darken(brand, BACKDROP_LIGHT_SHARE),
                IconTrim.darken(brand, BACKDROP_DARK_SHARE), CARD_RADIUS_DP);
    }

    private static android.graphics.Bitmap render(Drawable drawable, int width, int height) {
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888);
        Drawable copy = drawable.getConstantState() == null ? drawable : drawable.getConstantState().newDrawable().mutate();
        copy.setBounds(0, 0, width, height);
        copy.draw(new android.graphics.Canvas(bitmap));
        return bitmap;
    }

    private static FrameLayout holder(Context context, View picture, float radiusDp, String description) {
        FrameLayout holder = new FrameLayout(context);
        int inset = UiKit.dp(context, FocusStyle.HOLDER_INSET_DP);
        holder.setPadding(inset, inset, inset, inset);
        holder.setFocusable(true);
        holder.setClickable(true);
        holder.setLongClickable(true);
        holder.setClipChildren(false);
        FocusStyle.roundCorners(holder, UiKit.dp(context, radiusDp));
        holder.setClipToOutline(false);
        holder.setContentDescription(description);
        holder.addView(picture, matchParent());
        return holder;
    }

    private static FrameLayout roundedPicture(Context context, float radiusDp) {
        FrameLayout picture = new FrameLayout(context);
        FocusStyle.roundCorners(picture, UiKit.dp(context, radiusDp));
        return picture;
    }

    private static ImageView image(Context context, Drawable drawable) {
        ImageView image = new ImageView(context);
        image.setImageDrawable(drawable);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        return image;
    }

    private static ImageView fitted(Context context, Drawable drawable) {
        ImageView image = new ImageView(context);
        image.setImageDrawable(drawable);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        return image;
    }

    private static LinearLayout cardFooter(Context context, WatchNextRules.Item item, View details, boolean hasPicture) {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        int sidePadding = UiKit.dp(context, 9);
        column.setPadding(sidePadding, 0, sidePadding, UiKit.dp(context, 7));
        if (!hasPicture) {
            TextView title = UiKit.text(context, item.title, 11f, CARD_TITLE);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            title.setSingleLine(true);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            title.setPadding(0, 0, 0, UiKit.dp(context, 3));
            column.addView(title);
        }
        column.addView(details);
        LinearLayout bar = progressBar(context, WatchNextRules.progress(item.positionMs, item.durationMs));
        ((LinearLayout.LayoutParams) bar.getLayoutParams()).topMargin = UiKit.dp(context, 5);
        column.addView(bar);
        return column;
    }

    /** "S2 · E5" on the left (series only); "21 min left" on the right. */
    private static LinearLayout infoRow(Context context, WatchNextRules.Item item) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView series = UiKit.text(context, WatchNextRules.seriesLabel(item.season, item.episode), 8f, CARD_TITLE);
        series.setTypeface(Typeface.DEFAULT_BOLD);
        series.setSingleLine(true);
        row.addView(series, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView timeLeft = UiKit.text(context, WatchNextRules.status(item.type, item.positionMs, item.durationMs), 7.5f, CARD_STATUS);
        timeLeft.setSingleLine(true);
        row.addView(timeLeft);
        return row;
    }

    private static LinearLayout progressBar(Context context, float progress) {
        LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackground(UiKit.rounded(context, PROGRESS_TRACK, 2f, 0, 0));
        bar.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, UiKit.dp(context, 3)));
        View filled = new View(context);
        filled.setBackground(UiKit.rounded(context, PROGRESS_FILL, 2f, 0, 0));
        bar.addView(filled, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, progress));
        bar.addView(new View(context), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f - progress));
        return bar;
    }

    private static FrameLayout.LayoutParams matchParent() {
        return new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
    }
}
