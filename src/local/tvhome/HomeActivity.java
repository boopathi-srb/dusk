package local.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** The home screen: sky, greeting, the app row, and Continue watching. */
public final class HomeActivity extends Activity {

    /** As many 16:9 cards as fit across the screen without scrolling. */
    private static final int MAX_WATCH_CARDS = 5;

    /** About four frames at 60 Hz after focus: this screen is on the panel. */
    private static final long DRAWN_AFTER_FOCUS_MS = 64L;
    /** Home pressed while this screen was already in front: focus never changes. */
    private static final long NO_FOCUS_CHANGE_FALLBACK_MS = 400L;

    /** Layout positions as fractions of the screen, matching the approved design. */
    /** The greeting block's top: inside the arc, clear of its dashed top (at HORIZON_Y - RADIUS_Y = 0.18). */
    private static final float GREETING_TOP = 0.25f;
    /** The clock sits in the top-left corner, level with the buttons on the right. */
    private static final float CLOCK_LEFT = 0.075f;
    private static final float PILL_RIGHT = 0.075f;
    private static final float PILL_TOP = 0.125f;
    /** Just below the horizon line drawn by the sky. */
    private static final float ROWS_TOP = ArcGeometry.HORIZON_Y - 0.035f;

    /** Space on each side of a tile: the focused tile grows into it without touching its neighbours. */
    private static final float TILE_GAP_DP = 7f;
    /** Space from the glass to the resting tiles: the same on every side, and it never changes. */
    private static final float DOCK_SIDE_PADDING_DP = 14f;
    private static final float DOCK_VERTICAL_PADDING_DP = 14f;
    /** Glass corners follow the tiles' curve: tile radius plus the padding around it. */
    private static final float DOCK_RADIUS_DP = TileFactory.TILE_RADIUS_DP + DOCK_VERTICAL_PADDING_DP;
    /** Room above the cards for their corner badges to hang into, clear of the label. */
    private static final float CARD_ROW_TOP_PADDING_DP = 14f;
    private static final float CARD_ROW_BOTTOM_PADDING_DP = 8f;
    /** Space between the apps bar and Continue watching. */
    private static final float CONTINUE_GAP_DP = 18f;
    private static final float SETTINGS_BUTTON_DP = 44f;
    private static final float SETTINGS_ICON_DP = 24f;
    /** Space between the controls button and the gear. */
    private static final float ROUND_BUTTON_GAP_DP = 14f;

    private static final String ALL_APPS_LABEL = "All apps";

    /** Sky preview: the next sunrise round to the sunrise after it, in 30 seconds, about 12 frames a second. */
    private static final long SKY_PREVIEW_MS = 30_000L;
    private static final long SKY_PREVIEW_FRAME_MS = 80L;

    private final Handler mainThread = new Handler(Looper.getMainLooper());
    /** Continue watching loads run one at a time, so a clear can never race a load that read the old list. */
    private final java.util.concurrent.ExecutorService watchLoader = java.util.concurrent.Executors.newSingleThreadExecutor(
            work -> new Thread(work, "watch-next-reader"));
    private final BroadcastReceiver clockReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshClock();
            fetchWeatherIfDue();
        }
    };

    private SkyClock skyClock;
    private ControlPanel controls;
    private final Runnable skyPreviewStep = this::stepSkyPreview;
    private long skyPreviewStartedAt = -1L;
    /** The sunrise the preview starts from (date and time); the date line follows it, through midnight. */
    private LocalDateTime skyPreviewSunrise;
    /** What the date line shows while previewing; null shows the real time. */
    private LocalDateTime previewTime;
    private SkyView sky;
    private SkyScene scene;
    /** Sky effects run only while the home screen is on screen. */
    private boolean visible;
    /** Set only by {@link #EXTRA_PREVIEW_MINUTE} from adb, to look at another time of day; -1 = now. */
    private int previewMinute = -1;

    /** adb: am start -n local.tvhome/.HomeActivity --ei local.tvhome.PREVIEW_MINUTE 900 (15:00). */
    static final String EXTRA_PREVIEW_MINUTE = "local.tvhome.PREVIEW_MINUTE";

    /** adb, to see a kind of weather: --ei local.tvhome.PREVIEW_WEATHER 63 (WMO code; 63 = rain). */
    static final String EXTRA_PREVIEW_WEATHER = "local.tvhome.PREVIEW_WEATHER";
    private int previewWeatherCode = -1;
    /** Set only by {@link #EXTRA_PREVIEW_DAYS} from adb, to see another day's moon; 0 = today. */
    static final String EXTRA_PREVIEW_DAYS = "local.tvhome.PREVIEW_DAYS";
    /** Set only from adb: empty Continue watching (YouTube history cleared, the apps' current cards hidden). */
    static final String EXTRA_CLEAR_CONTINUE = "local.tvhome.CLEAR_CONTINUE";
    private volatile boolean clearContinueRequested;
    /** Set from adb (see tv.sh): the name the greeting uses, and the city's latitude and longitude. */
    static final String EXTRA_SET_NAME = "local.tvhome.SET_NAME";
    static final String EXTRA_SET_LATITUDE = "local.tvhome.SET_LATITUDE";
    static final String EXTRA_SET_LONGITUDE = "local.tvhome.SET_LONGITUDE";
    private int previewDays;
    private DockLabel dockLabel;
    /** The show's title under the focused Continue watching card. */
    private DockLabel cardLabel;
    private GlassBar glass;
    private TextView dayLine;
    private TextView timeLine;
    private ImageView clockIcon;
    private TextView greeting;
    private TextView subtitle;
    private LinearLayout dockRow;
    private LinearLayout continueSection;
    private LinearLayout continueRow;
    private TextView continueLabel;
    private String packageToFocus;
    private int watchLoadGeneration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        skyClock = HomeSettings.skyClock(this);
        setContentView(buildScreen());
        recordLaunch(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        overridePendingTransition(0, 0);
        recordLaunch(intent);
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter clockChanges = new IntentFilter(Intent.ACTION_TIME_TICK);
        clockChanges.addAction(Intent.ACTION_TIME_CHANGED);
        clockChanges.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(clockReceiver, clockChanges);
        visible = true;
        refreshClock();
        fetchWeatherIfDue();
    }

    @Override
    protected void onStop() {
        controls.close();
        unregisterReceiver(clockReceiver);
        visible = false;
        scene.stopAll();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        watchLoader.shutdownNow();
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        rebuildDock();
        loadContinueWatching();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            mainThread.postDelayed(HomeState::notifyHomeReady, DRAWN_AFTER_FOCUS_MS);
        }
    }

    @Override
    public void onEnterAnimationComplete() {
        super.onEnterAnimationComplete();
        HomeState.notifyHomeReady();
    }

    /** This is home: Back only closes the control panel; otherwise it has nowhere further to go. */
    @Override
    public void onBackPressed() {
        if (controls.isOpen()) {
            controls.close();
            return;
        }
        Log.i(HomeState.LOG_TAG, "onBackPressed: ignored on the home screen");
    }

    /** The remote's menu button opens and closes the control panel. */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            controls.toggle();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void recordLaunch(Intent intent) {
        previewMinute = intent == null ? -1 : intent.getIntExtra(EXTRA_PREVIEW_MINUTE, -1);
        previewWeatherCode = intent == null ? -1 : intent.getIntExtra(EXTRA_PREVIEW_WEATHER, -1);
        previewDays = intent == null ? 0 : intent.getIntExtra(EXTRA_PREVIEW_DAYS, 0);
        previewTime = previewMinute < 0 ? null : LocalDateTime.now().toLocalDate().plusDays(previewDays)
                .atTime(previewMinute / 60, previewMinute % 60);
        applySettingsFrom(intent);
        if (intent != null && intent.getBooleanExtra(EXTRA_CLEAR_CONTINUE, false)) {
            clearContinueRequested = true;
            Log.i(HomeState.LOG_TAG, "recordLaunch: clearing Continue watching on the next load (onResume)");
        }
        if (previewMinute >= 0 || previewWeatherCode >= 0) {
            Log.i(HomeState.LOG_TAG, "recordLaunch: previewing minute=" + previewMinute + " weather=" + previewWeatherCode);
            if (sky != null && visible) {
                refreshClock();
            }
        }
        String reason = intent == null ? null : intent.getStringExtra(HomeState.EXTRA_LAUNCH_REASON);
        Log.i(HomeState.LOG_TAG, "recordLaunch: home shown, reason=" + (reason == null ? "opened-by-hand" : reason));
        mainThread.postDelayed(HomeState::notifyHomeReady, NO_FOCUS_CHANGE_FALLBACK_MS);
    }

    /** Saves a name or city sent over adb; a new city drops the old weather so the next refresh fetches it. */
    private void applySettingsFrom(Intent intent) {
        if (intent == null) {
            return;
        }
        String name = intent.getStringExtra(EXTRA_SET_NAME);
        if (name != null) {
            HomeSettings.saveViewerName(this, name);
        }
        String latitude = intent.getStringExtra(EXTRA_SET_LATITUDE);
        String longitude = intent.getStringExtra(EXTRA_SET_LONGITUDE);
        if ((latitude != null || longitude != null) && HomeSettings.saveLocation(this, latitude, longitude)) {
            WeatherClient.forget(this);
        }
        if ((name != null || latitude != null) && greeting != null) {
            refreshClock();
            fetchWeatherIfDue();
        }
    }

    private FrameLayout buildScreen() {
        FrameLayout root = new FrameLayout(this);
        root.setClipChildren(false);
        sky = new SkyView(this, skyClock);
        root.addView(sky, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        sky.setDrawStillStars(false);
        scene = new SkyScene(root);
        int width = getResources().getDisplayMetrics().widthPixels;
        int height = getResources().getDisplayMetrics().heightPixels;
        root.addView(buildGreeting(), topCentre((int) (height * GREETING_TOP)));
        root.addView(buildClock(), topLeftAt((int) (width * CLOCK_LEFT), (int) (height * PILL_TOP)));
        root.addView(buildButtons(), topRight((int) (width * PILL_RIGHT), (int) (height * PILL_TOP)));
        root.addView(buildRows(), topCentre((int) (height * ROWS_TOP)));
        dockLabel = DockLabel.attach(root, DockLabel.Side.ABOVE);
        cardLabel = DockLabel.attach(root, DockLabel.Side.BELOW);
        controls = ControlPanel.attach(this, root, this::startSkyPreview, this::refreshClock);
        return root;
    }

    /** The apps row and Continue watching share one column, so both start at the same left edge. */
    private LinearLayout buildRows() {
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setClipChildren(false);
        rows.addView(buildDock());
        LinearLayout.LayoutParams fullWidth = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        fullWidth.setMargins(0, UiKit.dp(this, CONTINUE_GAP_DP), 0, 0);
        rows.addView(buildContinueSection(), fullWidth);
        return rows;
    }

    /** "Hey Sam!" centred, with the temperature and weather under it. */
    private LinearLayout buildGreeting() {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setGravity(Gravity.CENTER_HORIZONTAL);
        greeting = UiKit.text(this, "", 46f, 0xFFFFFFFF);
        greeting.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        greeting.setLetterSpacing(-0.01f);
        greeting.setGravity(Gravity.CENTER_HORIZONTAL);
        block.addView(greeting);
        subtitle = UiKit.text(this, "", 18f, 0xFFFFFFFF);
        subtitle.setPadding(0, UiKit.dp(this, 10), 0, 0);
        subtitle.setGravity(Gravity.CENTER_HORIZONTAL);
        block.addView(subtitle);
        return block;
    }

    /** Top right, in one row: the controls and settings buttons. */
    private LinearLayout buildButtons() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClipChildren(false);
        int buttonSize = UiKit.dp(this, SETTINGS_BUTTON_DP);
        LinearLayout.LayoutParams controlsPlace = new LinearLayout.LayoutParams(buttonSize, buttonSize);
        row.addView(roundButton(R.drawable.ic_controls, "Controls", clicked -> controls.toggle()), controlsPlace);
        LinearLayout.LayoutParams settingsPlace = new LinearLayout.LayoutParams(buttonSize, buttonSize);
        settingsPlace.leftMargin = UiKit.dp(this, ROUND_BUTTON_GAP_DP);
        row.addView(roundButton(R.drawable.ic_settings, "Settings", clicked -> Launch.openSettings(this)), settingsPlace);
        return row;
    }

    /** A clock icon in a frosted circle (like the buttons on the right, but not pressable), then the day and date over the time. */
    private LinearLayout buildClock() {
        LinearLayout clock = new LinearLayout(this);
        clock.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout iconCircle = new FrameLayout(this);
        iconCircle.setBackground(UiKit.rounded(this, UiKit.FROSTED_FILL, SETTINGS_BUTTON_DP / 2f, UiKit.FROSTED_STROKE, 1f));
        clockIcon = new ImageView(this);
        clockIcon.setImageResource(R.drawable.ic_clock);
        int iconSize = UiKit.dp(this, SETTINGS_ICON_DP);
        iconCircle.addView(clockIcon, new FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER));
        int circleSize = UiKit.dp(this, SETTINGS_BUTTON_DP);
        clock.addView(iconCircle, new LinearLayout.LayoutParams(circleSize, circleSize));
        LinearLayout lines = new LinearLayout(this);
        lines.setOrientation(LinearLayout.VERTICAL);
        lines.setPadding(UiKit.dp(this, 14), 0, 0, 0);
        dayLine = UiKit.text(this, "", 12f, 0xFFFFFFFF);
        dayLine.setLetterSpacing(0.2f);
        dayLine.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        lines.addView(dayLine);
        timeLine = UiKit.text(this, "", 20f, 0xFFFFFFFF);
        timeLine.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        timeLine.setPadding(0, UiKit.dp(this, 3), 0, 0);
        lines.addView(timeLine);
        clock.addView(lines);
        return clock;
    }

    /** A round frosted button with just an icon: the gear, and the control panel's sliders. */
    private FrameLayout roundButton(int iconResource, String description, View.OnClickListener onClick) {
        FrameLayout button = new FrameLayout(this);
        button.setBackground(UiKit.frostedButton(this, SETTINGS_BUTTON_DP / 2f));
        button.setFocusable(true);
        button.setClickable(true);
        button.setContentDescription(description);
        UiKit.growOnFocus(button);
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconResource);
        int iconSize = UiKit.dp(this, SETTINGS_ICON_DP);
        button.addView(icon, new FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER));
        button.setOnClickListener(onClick);
        return button;
    }

    private FrameLayout buildDock() {
        FrameLayout dock = new FrameLayout(this);
        glass = new GlassBar(this, DOCK_RADIUS_DP);
        dock.setBackground(glass);
        int side = UiKit.dp(this, DOCK_SIDE_PADDING_DP - TILE_GAP_DP);
        int vertical = UiKit.dp(this, DOCK_VERTICAL_PADDING_DP);
        dock.setPadding(side, vertical, side, vertical);
        dock.setClipChildren(false);
        dock.setClipToPadding(false);
        dockRow = new LinearLayout(this);
        dockRow.setOrientation(LinearLayout.HORIZONTAL);
        dockRow.setClipChildren(false);
        dockRow.setClipToPadding(false);
        dock.addView(dockRow);
        return dock;
    }

    private LinearLayout buildContinueSection() {
        continueSection = new LinearLayout(this);
        continueSection.setOrientation(LinearLayout.VERTICAL);
        continueSection.setGravity(Gravity.START);
        continueSection.setClipChildren(false);
        continueSection.setVisibility(View.GONE);
        int pictureEdge = UiKit.dp(this, DOCK_SIDE_PADDING_DP);
        continueLabel = UiKit.text(this, "CONTINUE WATCHING", 10f, 0xFFFFFFFF);
        continueLabel.setLetterSpacing(0.3f);
        continueLabel.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        continueLabel.setPadding(pictureEdge, 0, 0, 0);
        continueSection.addView(continueLabel);
        continueRow = new LinearLayout(this);
        continueRow.setOrientation(LinearLayout.HORIZONTAL);
        continueRow.setClipChildren(false);
        continueRow.setClipToPadding(false);
        continueRow.setPadding(UiKit.dp(this, DOCK_SIDE_PADDING_DP - TILE_GAP_DP),
                UiKit.dp(this, CARD_ROW_TOP_PADDING_DP), 0, UiKit.dp(this, CARD_ROW_BOTTOM_PADDING_DP));
        continueSection.addView(continueRow);
        return continueSection;
    }

    private void refreshClock() {
        int minute = previewMinute >= 0 ? previewMinute : HomeSettings.minuteOfDayNow();
        sky.setMinuteOfDay(minute);
        SkyPalette palette = sky.palette();
        boolean use24Hour = DateFormat.is24HourFormat(this);
        SkyClock.Phase phase = skyClock.phaseAt(minute);
        WeatherRules.Report weather = currentWeather();
        greeting.setText(SkyClock.hey(HomeSettings.viewerName(this)));
        String weatherLine = WeatherRules.subtitle(weather, null);
        subtitle.setText(weatherLine == null ? "" : weatherLine);
        subtitle.setVisibility(weatherLine == null || weatherLine.isEmpty() ? View.GONE : View.VISIBLE);
        LocalDateTime shownTime = previewTime != null ? previewTime : LocalDateTime.now();
        LocalDateTime moonTime = shownTime;
        sky.setMoonPhase(MoonRules.phaseAt(moonTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()));
        dayLine.setText(DateLine.dayAndDate(shownTime));
        timeLine.setText(DateLine.time(shownTime, use24Hour));
        SceneRules.Scene decided = SceneRules.decide(skyClock, minute, weather);
        sky.setMoonLevel(decided.moonLevel);
        scene.apply(decided, visible, sky.sunPoint());
        greeting.setTextColor(palette.textPrimary);
        dayLine.setTextColor(palette.textSecondary);
        timeLine.setTextColor(palette.textPrimary);
        clockIcon.setColorFilter(palette.textPrimary);
        subtitle.setTextColor(palette.textSecondary);
        continueLabel.setTextColor(palette.textSecondary);
    }

    private void startSkyPreview() {
        int sunrise = skyClock.sunriseMinute();
        int daysAhead = ControlRules.previewStartDaysAhead(HomeSettings.minuteOfDayNow(), sunrise);
        skyPreviewSunrise = LocalDateTime.now().toLocalDate().plusDays(daysAhead).atTime(sunrise / 60, sunrise % 60);
        skyPreviewStartedAt = SystemClock.uptimeMillis();
        mainThread.removeCallbacks(skyPreviewStep);
        mainThread.post(skyPreviewStep);
        Log.i(HomeState.LOG_TAG, "startSkyPreview: from " + skyPreviewSunrise + " to the next sunrise in "
                + SKY_PREVIEW_MS + " ms");
    }

    /** Moves the previewed time on a frame; at the end (or once the screen is hidden) goes back to now. */
    private void stepSkyPreview() {
        long elapsed = SystemClock.uptimeMillis() - skyPreviewStartedAt;
        if (elapsed >= SKY_PREVIEW_MS || !visible) {
            skyPreviewStartedAt = -1L;
            previewMinute = -1;
            previewTime = null;
            refreshClock();
            Log.i(HomeState.LOG_TAG, "stepSkyPreview: finished, back to the real time");
            return;
        }
        int offset = ControlRules.previewOffsetMinutes(elapsed, SKY_PREVIEW_MS, ControlRules.PREVIEW_SPAN_MINUTES);
        previewTime = skyPreviewSunrise.plusMinutes(offset);
        previewMinute = previewTime.getHour() * 60 + previewTime.getMinute();
        refreshClock();
        mainThread.postDelayed(skyPreviewStep, SKY_PREVIEW_FRAME_MS);
    }

    /** The kept weather reading if it is recent enough, or a made-up one while previewing over adb. */
    private WeatherRules.Report currentWeather() {
        if (skyPreviewStartedAt >= 0 && previewWeatherCode < 0) {
            return clearSky();
        }
        if (previewWeatherCode >= 0) {
            int cover = WeatherRules.typicalCover(WeatherRules.fromCode(previewWeatherCode));
            return new WeatherRules.Report(24, previewWeatherCode, cover,
                    skyClock.sunriseMinute(), skyClock.sunsetMinute(), System.currentTimeMillis());
        }
        WeatherRules.Report kept = WeatherClient.cached(this);
        return WeatherRules.isUsable(kept, System.currentTimeMillis()) ? kept : null;
    }

    /** Asks for new weather when the kept reading is over 30 minutes old; cheap to call every minute. */
    /** Sky preview shows off the whole day and night, so it plays under a clear sky, not tonight's cloud. */
    private WeatherRules.Report clearSky() {
        WeatherRules.Report real = WeatherClient.cached(this);
        double temperature = real == null ? 24 : real.temperatureC;
        return new WeatherRules.Report(temperature, 0, 0, skyClock.sunriseMinute(), skyClock.sunsetMinute(),
                System.currentTimeMillis());
    }

    private void fetchWeatherIfDue() {
        CityLookup.refreshIfNeeded(this, this::fetchWeatherIfDue);
        WeatherClient.refreshIfNeeded(this, report -> {
            skyClock = HomeSettings.skyClockFor(report);
            sky.setClock(skyClock);
            if (visible) {
                refreshClock();
            }
        });
    }

    private void rebuildDock() {
        dockRow.removeAllViews();
        List<AppCatalog.LaunchableApp> apps = pinnedApps();
        View tileToFocus = null;
        for (int position = 0; position < apps.size(); position++) {
            AppCatalog.LaunchableApp app = apps.get(position);
            View tile = TileFactory.appTile(this, app, position);
            tile.setOnClickListener(clicked -> Launch.start(this, app.launchIntent, app.label, clicked));
            tile.setOnLongClickListener(held -> {
                showTileMenu(app);
                return true;
            });
            dockRow.addView(tile, tileMargins());
            if (app.packageName.equals(packageToFocus) || (tileToFocus == null && packageToFocus == null)) {
                tileToFocus = tile;
            }
        }
        View allApps = TileFactory.allAppsTile(this, ALL_APPS_LABEL);
        allApps.setOnClickListener(clicked -> startActivity(new Intent(this, AllAppsActivity.class)));
        dockRow.addView(allApps, sizedWithGap(TileFactory.ALL_APPS_SIZE_DP, TileFactory.ALL_APPS_SIZE_DP));
        FocusStyle.magnifyRow(dockRow, DockMagnifier.DOCK, true, (tile, scale, shift, lift, spread) -> {
            dockLabel.follow(tile, scale, shift, lift);
            glass.stretchTo(spread);
        });
        packageToFocus = null;
        (tileToFocus != null ? tileToFocus : allApps).requestFocus();
    }

    private List<AppCatalog.LaunchableApp> pinnedApps() {
        List<AppCatalog.LaunchableApp> apps = new ArrayList<>();
        for (String packageName : HomeSettings.pinned(this)) {
            AppCatalog.LaunchableApp app = AppCatalog.load(getPackageManager(), packageName);
            if (app != null) {
                apps.add(app);
            }
        }
        return apps;
    }

    private void showTileMenu(AppCatalog.LaunchableApp app) {
        String[] choices = {"Move left", "Move right", "Remove from home"};
        new AlertDialog.Builder(this)
                .setTitle(app.label)
                .setItems(choices, (dialog, which) -> applyTileChoice(app.packageName, which))
                .show();
    }

    private void applyTileChoice(String packageName, int choice) {
        List<String> pinned = HomeSettings.pinned(this);
        List<String> updated;
        if (choice == 0) {
            updated = PinnedApps.move(pinned, packageName, -1);
        } else if (choice == 1) {
            updated = PinnedApps.move(pinned, packageName, 1);
        } else {
            updated = PinnedApps.remove(pinned, packageName);
        }
        Log.i(HomeState.LOG_TAG, "applyTileChoice: package=" + packageName + " choice=" + choice
                + " pinned=" + PinnedApps.serialise(updated));
        HomeSettings.savePinned(this, updated);
        packageToFocus = choice == 2 ? null : packageName;
        rebuildDock();
    }

    /** Reads the TV's Watch Next list off the main thread; only the newest request may update the row. */
    private void loadContinueWatching() {
        int generation = ++watchLoadGeneration;
        Context appContext = getApplicationContext();
        watchLoader.execute(() -> {
            List<WatchNextRules.Item> appLists = new ArrayList<>(WatchNextReader.readAll(appContext));
            appLists.addAll(ContinueChannelReader.readAll(appContext));
            if (clearContinueRequested) {
                clearContinueRequested = false;
                HiddenCards.save(appContext, ContinueHiding.fingerprints(appLists));
                MediaHistory.clearAll(appContext);
            }
            List<WatchNextRules.Item> everything = ContinueHiding.withoutHidden(appLists, HiddenCards.read(appContext));
            everything.addAll(WatchNextRules.onlyAppsWithoutList(MediaHistory.readAll(appContext), appLists));
            List<WatchNextRules.Item> picked = WatchNextRules.pick(everything, System.currentTimeMillis(), MAX_WATCH_CARDS);
            List<Bitmap> pictures = new ArrayList<>();
            for (WatchNextRules.Item item : picked) {
                pictures.add(ArtLoader.load(appContext, item.artUri));
            }
            mainThread.post(() -> showContinueWatching(generation, picked, pictures));
        });
    }

    /** {@code pictures} lines up with {@code items}; a missing picture leaves the card's plain background. */
    private void showContinueWatching(int generation, List<WatchNextRules.Item> items, List<Bitmap> pictures) {
        if (generation != watchLoadGeneration || isDestroyed()) {
            return;
        }
        continueRow.removeAllViews();
        for (int index = 0; index < items.size(); index++) {
            WatchNextRules.Item item = items.get(index);
            AppCatalog.LaunchableApp app = AppCatalog.load(getPackageManager(), item.packageName);
            Bitmap picture = pictures.get(index);
            Drawable art = picture == null ? null : new BitmapDrawable(getResources(), picture);
            String appName = app == null ? item.packageName : app.label;
            Drawable backdrop = art == null && app != null ? TileFactory.brandBackdrop(this, app.banner) : null;
            View card = TileFactory.watchCard(this, item, appName, art, backdrop, app == null ? null : app.icon);
            card.setOnClickListener(clicked -> openWatchItem(item, clicked));
            continueRow.addView(card, cardMargins());
        }
        cardLabel.setLeftLimit(continueLabel);
        FocusStyle.magnifyRow(continueRow, DockMagnifier.CARDS, false, (card, scale, shift, lift, spread) ->
                cardLabel.follow(card, scale, shift, lift, card == null ? null : (String) card.getTag(R.id.card_title)));
        boolean hasItems = !items.isEmpty();
        continueSection.setVisibility(hasItems ? View.VISIBLE : View.GONE);
        Log.i(HomeState.LOG_TAG, "showContinueWatching: cards=" + items.size());
    }

    /** The app's own link to that exact episode, else just the app. */
    private void openWatchItem(WatchNextRules.Item item, View card) {
        Intent exact = Launch.fromUri(item.intentUri, item.title);
        if (exact != null && Launch.start(this, exact, item.title, card)) {
            return;
        }
        AppCatalog.LaunchableApp app = AppCatalog.load(getPackageManager(), item.packageName);
        Launch.start(this, app == null ? null : app.launchIntent, "app for " + item.title, card);
    }

    private LinearLayout.LayoutParams tileMargins() {
        return sizedWithGap(TileFactory.TILE_WIDTH_DP, TileFactory.TILE_HEIGHT_DP);
    }

    private LinearLayout.LayoutParams cardMargins() {
        return sizedWithGap(TileFactory.CARD_WIDTH_DP, TileFactory.CARD_HEIGHT_DP);
    }

    /** Sizes a holder for a picture of the given size, plus a small gap either side. */
    private LinearLayout.LayoutParams sizedWithGap(float pictureWidthDp, float pictureHeightDp) {
        LinearLayout.LayoutParams margins = new LinearLayout.LayoutParams(
                UiKit.dp(this, TileFactory.holderSize(pictureWidthDp)),
                UiKit.dp(this, TileFactory.holderSize(pictureHeightDp)));
        int gap = UiKit.dp(this, TILE_GAP_DP);
        margins.setMargins(gap, 0, gap, 0);
        return margins;
    }

    private static FrameLayout.LayoutParams topLeftAt(int left, int top) {
        FrameLayout.LayoutParams place = wrap(Gravity.TOP | Gravity.START);
        place.leftMargin = left;
        place.topMargin = top;
        return place;
    }

    private static FrameLayout.LayoutParams topRight(int right, int top) {
        FrameLayout.LayoutParams params = wrap(Gravity.TOP | Gravity.END);
        params.setMargins(0, top, right, 0);
        return params;
    }

    private static FrameLayout.LayoutParams topCentre(int top) {
        FrameLayout.LayoutParams params = wrap(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        params.setMargins(0, top, 0, 0);
        return params;
    }

    private static FrameLayout.LayoutParams wrap(int gravity) {
        return new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, gravity);
    }
}
