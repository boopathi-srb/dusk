package local.tvhome;

import android.app.Activity;
import android.graphics.Typeface;
import android.media.session.MediaController;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The control panel: a frosted sheet that slides in from the right with what is playing, a
 * brightness slider, quick toggles, and shortcuts into Fire TV's Settings. While it is open, focus stays inside it;
 * Back or the remote's menu button closes it.
 */
final class ControlPanel {

    private static final float WIDTH_DP = 340f;
    private static final float EDGE_DP = 24f;
    private static final float RADIUS_DP = 28f;
    private static final float PADDING_DP = 22f;
    private static final float SECTION_GAP_DP = 20f;
    private static final int TILES_PER_ROW = 3;
    private static final float TILE_HEIGHT_DP = 84f;
    private static final float TILE_GAP_DP = 8f;
    private static final float TILE_RADIUS_DP = 16f;
    private static final float ART_DP = 44f;
    private static final float SLIDER_HEIGHT_DP = 30f;
    private static final int PANEL_FILL = 0xF5262730;
    private static final int PANEL_STROKE = 0x40FFFFFF;
    private static final int SECONDARY_TEXT = 0xFFB8B9C2;
    private static final long SLIDE_MS = 280L;

    /** What the panel asks of the home screen. */
    interface Host {
        /** Plays the sky through a whole day; the panel has already closed. */
        void startSkyPreview();
    }

    private final Activity activity;
    private final Host host;
    /** Runs after the greeting name changes, so the home screen shows it at once. */
    private final Runnable onNameChanged;
    private final android.widget.EditText nameField;
    private final FrameLayout root;
    private final java.util.List<PanelTile> tiles = new java.util.ArrayList<>();
    /** The timer is set on the Home service a moment later, so only this tile re-reads it after a press. */
    private PanelTile sleepTile;
    private final LinearLayout sheet;
    private final TextView wifiLine;
    private final LinearLayout nowPlayingCard;
    private final ImageView nowPlayingArt;
    private final TextView nowPlayingTitle;
    private final TextView nowPlayingState;
    private final BrightnessBar brightness;
    private final TextView brightnessValue;
    private MediaController playing;
    private View focusBeforeOpening;
    /** Home screen controls switched off while the panel is open, to switch back on when it closes. */
    private final java.util.List<View> madeUnfocusable = new java.util.ArrayList<>();
    private boolean open;

    private ControlPanel(Activity activity, FrameLayout root, Host host, Runnable onNameChanged) {
        this.activity = activity;
        this.host = host;
        this.onNameChanged = onNameChanged;
        this.nameField = new android.widget.EditText(activity);
        this.root = root;
        sheet = new LinearLayout(activity);
        wifiLine = secondaryText("");
        nowPlayingCard = new LinearLayout(activity);
        nowPlayingArt = new ImageView(activity);
        nowPlayingTitle = UiKit.text(activity, "", 15f, 0xFFFFFFFF);
        nowPlayingState = secondaryText("");
        brightnessValue = secondaryText("");
        brightness = new BrightnessBar(activity, SystemControls.readBrightness(activity), this::applyBrightness);
    }

    /** Builds the panel, hidden, on top of everything else in {@code root}. */
    static ControlPanel attach(Activity activity, FrameLayout root, Host host, Runnable onNameChanged) {
        ControlPanel panel = new ControlPanel(activity, root, host, onNameChanged);
        panel.buildSheet();
        return panel;
    }

    boolean isOpen() {
        return open;
    }

    void toggle() {
        if (open) {
            close();
        } else {
            show();
        }
    }

    void show() {
        if (open) {
            return;
        }
        open = true;
        focusBeforeOpening = root.findFocus();
        refresh();
        setOthersFocusable(false);
        sheet.setVisibility(View.VISIBLE);
        sheet.setTranslationX(slideDistance());
        sheet.setAlpha(0f);
        sheet.animate().translationX(0f).alpha(1f).setDuration(Motion.duration(activity, SLIDE_MS))
                .setInterpolator(Motion.GLIDE).start();
        sheet.findViewById(R.id.control_first).requestFocus();
        Log.i(HomeState.LOG_TAG, "ControlPanel.show: opened, nowPlaying=" + (playing == null ? "none" : playing.getPackageName()));
    }

    void close() {
        if (!open) {
            return;
        }
        open = false;
        setOthersFocusable(true);
        sheet.animate().translationX(slideDistance()).alpha(0f).setDuration(Motion.duration(activity, SLIDE_MS))
                .setInterpolator(Motion.GLIDE).withEndAction(() -> sheet.setVisibility(View.GONE)).start();
        if (focusBeforeOpening != null && focusBeforeOpening.isAttachedToWindow()) {
            focusBeforeOpening.requestFocus();
        }
        Log.i(HomeState.LOG_TAG, "ControlPanel.close: closed");
    }

    private void buildSheet() {
        sheet.setOrientation(LinearLayout.VERTICAL);
        int padding = UiKit.dp(activity, PADDING_DP);
        sheet.setPadding(padding, padding, padding, padding);
        sheet.setBackground(UiKit.rounded(activity, PANEL_FILL, RADIUS_DP, PANEL_STROKE, 1f));
        sheet.setVisibility(View.GONE);
        sheet.setClipChildren(false);
        sheet.setClipToPadding(false);
        sheet.addView(buildTitleRow());
        sheet.addView(buildNowPlaying(), sectionParams());
        sheet.addView(buildBrightness(), sectionParams());
        sheet.addView(buildNameField(), sectionParams());
        sheet.addView(buildShortcuts(), sectionParams());
        int edge = UiKit.dp(activity, EDGE_DP);
        FrameLayout.LayoutParams place = new FrameLayout.LayoutParams(UiKit.dp(activity, WIDTH_DP),
                ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END);
        place.setMargins(edge, edge, edge, edge);
        root.addView(sheet, place);
    }

    private View buildTitleRow() {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = UiKit.text(activity, "Controls", 20f, 0xFFFFFFFF);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(wifiLine);
        return row;
    }

    private View buildNowPlaying() {
        nowPlayingCard.setGravity(Gravity.CENTER_VERTICAL);
        int inner = UiKit.dp(activity, 10f);
        nowPlayingCard.setPadding(inner, inner, inner, inner);
        nowPlayingCard.setBackground(UiKit.frostedButton(activity, TILE_RADIUS_DP));
        nowPlayingCard.setFocusable(true);
        nowPlayingCard.setClickable(true);
        nowPlayingCard.setId(R.id.control_first);
        UiKit.growOnFocus(nowPlayingCard);
        int art = UiKit.dp(activity, ART_DP);
        nowPlayingArt.setScaleType(ImageView.ScaleType.FIT_CENTER);
        nowPlayingCard.addView(nowPlayingArt, new LinearLayout.LayoutParams(art, art));
        LinearLayout words = new LinearLayout(activity);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setPadding(inner, 0, 0, 0);
        nowPlayingTitle.setSingleLine(true);
        nowPlayingTitle.setEllipsize(TextUtils.TruncateAt.END);
        words.addView(nowPlayingTitle);
        words.addView(nowPlayingState);
        nowPlayingCard.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        nowPlayingCard.setOnClickListener(clicked -> togglePlayback());
        return nowPlayingCard;
    }

    /**
     * The greeting's name: select it and press OK to type with the TV's keyboard; Done saves it.
     * Left empty, the greeting uses the owner's name from the TV's own name ("Sam's Fire TV").
     */
    private View buildNameField() {
        LinearLayout section = new LinearLayout(activity);
        section.setOrientation(LinearLayout.VERTICAL);
        section.addView(secondaryText("Greeting name"));
        nameField.setSingleLine(true);
        nameField.setTextColor(0xFFFFFFFF);
        nameField.setHintTextColor(SECONDARY_TEXT);
        nameField.setHint("From your TV's name");
        nameField.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f);
        nameField.setFilters(new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(SettingsRules.MAX_NAME_LENGTH)});
        nameField.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        nameField.setBackground(UiKit.toggleButton(activity, TILE_RADIUS_DP));
        int inner = UiKit.dp(activity, 14f);
        nameField.setPadding(inner, UiKit.dp(activity, 10f), inner, UiKit.dp(activity, 10f));
        nameField.setOnEditorActionListener((field, action, event) -> {
            saveName();
            return false;
        });
        nameField.setOnFocusChangeListener((field, hasFocus) -> {
            if (!hasFocus && open) {
                saveName();
            }
        });
        LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        place.topMargin = UiKit.dp(activity, 6f);
        section.addView(nameField, place);
        return section;
    }

    /** Saves only a real change, so moving through the box without typing does nothing. */
    private void saveName() {
        String typed = SettingsRules.cleanName(nameField.getText().toString());
        if (typed.equals(HomeSettings.savedViewerName(activity))) {
            return;
        }
        HomeSettings.saveViewerName(activity, typed);
        onNameChanged.run();
    }

    private View buildBrightness() {
        LinearLayout section = new LinearLayout(activity);
        section.setOrientation(LinearLayout.VERTICAL);
        LinearLayout labels = new LinearLayout(activity);
        labels.addView(secondaryText("Brightness"), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        labels.addView(brightnessValue);
        section.addView(labels);
        section.addView(brightness, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                UiKit.dp(activity, SLIDER_HEIGHT_DP)));
        return section;
    }

    /** Three rows: getting around (Wi-Fi, Bluetooth, apps), the TV itself, and the screen. */
    private View buildShortcuts() {
        tiles.add(PanelTile.withState(activity, R.drawable.ic_wifi, this::wifiTileLabel,
                () -> SystemControls.isOnWifi(activity), () -> openPage(Settings.ACTION_WIFI_SETTINGS, "Wi-Fi settings")));
        tiles.add(PanelTile.plain(activity, R.drawable.ic_bluetooth, "Bluetooth", () -> openPage(
                SystemControls.ACTION_REMOTES_AND_BLUETOOTH, "Bluetooth settings")));
        tiles.add(PanelTile.plain(activity, R.drawable.ic_all_apps, "Apps", () -> openPage(
                Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS, "Apps settings")));
        tiles.add(PanelTile.withState(activity, R.drawable.ic_adb, () -> ControlRules.adbLabel(SystemControls.isAdbOn(activity)),
                () -> SystemControls.isAdbOn(activity), () -> SystemControls.setAdb(activity, !SystemControls.isAdbOn(activity))));
        PanelTile memory = PanelTile.plain(activity, R.drawable.ic_memory, "Free memory", () -> { });
        memory.view().setOnClickListener(clicked -> freeMemory(memory));
        tiles.add(memory);
        sleepTile = PanelTile.withState(activity, R.drawable.ic_sleep,
                () -> ControlRules.sleepLabel(HomeTakeoverService.sleepMinutesLeft()),
                () -> HomeTakeoverService.sleepMinutesLeft() > 0, this::stepSleepTimer);
        tiles.add(sleepTile);
        tiles.add(PanelTile.plain(activity, R.drawable.ic_screensaver, "Screensaver", () -> {
            close();
            SystemControls.startScreensaver(activity);
        }));
        tiles.add(PanelTile.plain(activity, R.drawable.ic_sky, "Sky preview", () -> {
            close();
            host.startSkyPreview();
        }));
        tiles.add(PanelTile.plain(activity, R.drawable.ic_settings, "Settings", () -> {
            close();
            Launch.openSettings(activity);
        }));
        return tileGrid();
    }

    private View tileGrid() {
        LinearLayout grid = new LinearLayout(activity);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        for (int start = 0; start < tiles.size(); start += TILES_PER_ROW) {
            LinearLayout row = new LinearLayout(activity);
            row.setClipChildren(false);
            for (int index = start; index < start + TILES_PER_ROW; index++) {
                LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(0, UiKit.dp(activity, TILE_HEIGHT_DP), 1f);
                place.leftMargin = index == start ? 0 : UiKit.dp(activity, TILE_GAP_DP);
                row.addView(index < tiles.size() ? tiles.get(index).view() : new View(activity), place);
            }
            LinearLayout.LayoutParams rowPlace = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            rowPlace.topMargin = start == 0 ? 0 : UiKit.dp(activity, TILE_GAP_DP);
            grid.addView(row, rowPlace);
        }
        return grid;
    }

    /** The connected network's name when Android shares it, else plain "Wi-Fi". */
    private String wifiTileLabel() {
        String name = SystemControls.wifiNetworkName(activity);
        return name == null ? "Wi-Fi" : name;
    }

    private void openPage(String action, String what) {
        close();
        SystemControls.openPage(activity, action, SystemControls.FIRE_TV_SETTINGS, what);
    }

    private void stepSleepTimer() {
        int current = HomeTakeoverService.sleepMinutesLeft();
        int chosen = ControlRules.nextSleepChoice(current > 0 ? roundToChoice(current) : 0);
        HomeTakeoverService.setSleepTimer(chosen);
        sleepTile.view().postDelayed(sleepTile::refresh, 100L);
    }

    /** A running timer counts down (e.g. 27 min); pressing again moves on from the choice it started as. */
    private static int roundToChoice(int minutesLeft) {
        for (int choice : ControlRules.SLEEP_CHOICES_MINUTES) {
            if (choice >= minutesLeft) {
                return choice;
            }
        }
        return 0;
    }

    private void freeMemory(PanelTile tile) {
        tile.say("Freeing…");
        new Thread(() -> {
            String result = SystemControls.freeMemory(activity);
            tile.view().post(() -> tile.say(result));
        }, "free-memory").start();
    }

    /** Reads what is playing, Wi-Fi and brightness fresh each time the panel opens. */
    private void refresh() {
        nameField.setText(HomeSettings.savedViewerName(activity));
        wifiLine.setText(SystemControls.wifiLabel(activity));
        int current = SystemControls.readBrightness(activity);
        brightness.setValue(current);
        brightnessValue.setText(ControlRules.brightnessPercent(current) + "%");
        tiles.forEach(PanelTile::refresh);
        playing = SystemControls.nowPlaying(activity);
        showNowPlaying();
    }

    private void showNowPlaying() {
        if (playing == null) {
            nowPlayingCard.setVisibility(View.GONE);
            nowPlayingCard.setId(View.NO_ID);
            brightness.setId(R.id.control_first);
            return;
        }
        brightness.setId(View.NO_ID);
        nowPlayingCard.setId(R.id.control_first);
        nowPlayingCard.setVisibility(View.VISIBLE);
        AppCatalog.LaunchableApp app = AppCatalog.load(activity.getPackageManager(), playing.getPackageName());
        nowPlayingArt.setImageDrawable(app == null ? null : app.icon);
        nowPlayingTitle.setText(SystemControls.titleOf(playing));
        nowPlayingState.setText(ControlRules.playbackLabel(app == null ? null : app.label, SystemControls.stateOf(playing)));
    }

    private void togglePlayback() {
        if (playing == null) {
            return;
        }
        SystemControls.togglePlayback(playing);
        nowPlayingCard.postDelayed(this::showNowPlaying, 400L);
    }

    private void applyBrightness(int value) {
        if (SystemControls.writeBrightness(activity, value)) {
            brightnessValue.setText(ControlRules.brightnessPercent(value) + "%");
        }
    }

    /** Everything on the home screen except the panel stops taking focus while it is open. */
    private void setOthersFocusable(boolean focusable) {
        if (focusable) {
            for (View blocked : madeUnfocusable) {
                blocked.setFocusable(true);
            }
            madeUnfocusable.clear();
        }
        int mode = focusable ? ViewGroup.FOCUS_AFTER_DESCENDANTS : ViewGroup.FOCUS_BLOCK_DESCENDANTS;
        for (int index = 0; index < root.getChildCount(); index++) {
            View child = root.getChildAt(index);
            if (child == sheet) {
                continue;
            }
            if (child instanceof ViewGroup) {
                ((ViewGroup) child).setDescendantFocusability(mode);
            }
            if (!focusable && child.isFocusable()) {
                child.setFocusable(false);
                madeUnfocusable.add(child);
            }
        }
    }

    private float slideDistance() {
        return UiKit.dp(activity, WIDTH_DP + EDGE_DP);
    }

    private TextView secondaryText(String value) {
        return UiKit.text(activity, value, 13f, SECONDARY_TEXT);
    }

    private LinearLayout.LayoutParams sectionParams() {
        LinearLayout.LayoutParams place = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        place.topMargin = UiKit.dp(activity, SECTION_GAP_DP);
        return place;
    }

}
