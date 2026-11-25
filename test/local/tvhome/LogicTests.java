package local.tvhome;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Plain-Java tests for every pure class. No test library, by design: run with `java`,
 * exits non-zero if any check fails. Log lines are copied from this TV (Fire OS 8.1.8.5).
 */
public final class LogicTests {

    private static final int SUNRISE = 6 * 60 + 20;
    private static final int SUNSET = 17 * 60 + 50;
    private static final SkyClock CLOCK = new SkyClock(SUNRISE, SUNSET);
    private static final long NOW = 1_795_616_173_565L;
    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    private static int failedChecks;
    private static int totalChecks;

    public static void main(String[] args) {
        skyClockPhases();
        skyClockValidation();
        skyClockSunAndMoon();
        skyPalettes();
        arcGeometry();
        dateLine();
        heyGreeting();
        settingsRules();
        geoRules();
        watchNextTimes();
        watchNextPick();
        watchNextStatusAndProgress();
        watchNextArt();
        seriesLabels();
        continueSources();
        iconTrim();
        moonRules();
        continueHiding();
        cardTitles();
        youtubeRules();
        controlRules();
        dockMagnifier();
        motionTiming();
        skyNotes();
        starTwinkle();
        comets();
        bodyColours();
        weatherRules();
        sceneRules();
        birdsAndRain();
        pinnedApps();
        appRules();
        homePressLog();
        homeState();
        System.out.println(totalChecks + " checks, " + failedChecks + " failed");
        if (failedChecks > 0) {
            System.exit(1);
        }
    }

    private static void skyClockPhases() {
        check("midnight is night", CLOCK.phaseAt(0) == SkyClock.Phase.NIGHT);
        check("one minute before sunrise is night", CLOCK.phaseAt(SUNRISE - 1) == SkyClock.Phase.NIGHT);
        check("sunrise is morning", CLOCK.phaseAt(SUNRISE) == SkyClock.Phase.MORNING);
        check("11:59 is morning", CLOCK.phaseAt(SkyClock.NOON - 1) == SkyClock.Phase.MORNING);
        check("noon is afternoon", CLOCK.phaseAt(SkyClock.NOON) == SkyClock.Phase.AFTERNOON);
        check("sunset is evening", CLOCK.phaseAt(SUNSET) == SkyClock.Phase.EVENING);
        check("21:59 is evening", CLOCK.phaseAt(SkyClock.NIGHT_START - 1) == SkyClock.Phase.EVENING);
        check("22:00 is night", CLOCK.phaseAt(SkyClock.NIGHT_START) == SkyClock.Phase.NIGHT);
        check("23:59 is night", CLOCK.phaseAt(SkyClock.MINUTES_PER_DAY - 1) == SkyClock.Phase.NIGHT);
        check("negative minute wraps to previous day", CLOCK.phaseAt(-1) == SkyClock.Phase.NIGHT);
        check("minute past a day wraps", CLOCK.phaseAt(SkyClock.MINUTES_PER_DAY + SkyClock.NOON) == SkyClock.Phase.AFTERNOON);
        check("normalise -1 is 1439", SkyClock.normalise(-1) == 1439);
        check("normalise 1440 is 0", SkyClock.normalise(1440) == 0);
        check("greeting morning", "Good morning".equals(SkyClock.greeting(SkyClock.Phase.MORNING)));
        check("greeting afternoon", "Good afternoon".equals(SkyClock.greeting(SkyClock.Phase.AFTERNOON)));
        check("greeting evening", "Good evening".equals(SkyClock.greeting(SkyClock.Phase.EVENING)));
        check("greeting night", "Good night".equals(SkyClock.greeting(SkyClock.Phase.NIGHT)));
        check("greeting with name", "Good evening Sam!".equals(SkyClock.greeting(SkyClock.Phase.EVENING, "Sam")));
        check("greeting name is trimmed", "Good morning Sam!".equals(SkyClock.greeting(SkyClock.Phase.MORNING, " Sam ")));
        check("greeting with null name", "Good night".equals(SkyClock.greeting(SkyClock.Phase.NIGHT, null)));
        check("greeting with blank name", "Good afternoon".equals(SkyClock.greeting(SkyClock.Phase.AFTERNOON, "  ")));
    }

    private static void skyClockValidation() {
        check("sunrise after noon is refused", throwsIllegalArgument(() -> new SkyClock(SkyClock.NOON, SUNSET)));
        check("negative sunrise is refused", throwsIllegalArgument(() -> new SkyClock(-1, SUNSET)));
        check("sunset at noon is refused", throwsIllegalArgument(() -> new SkyClock(SUNRISE, SkyClock.NOON)));
        check("sunset at 22:00 is refused", throwsIllegalArgument(() -> new SkyClock(SUNRISE, SkyClock.NIGHT_START)));
        check("valid times are accepted", !throwsIllegalArgument(() -> new SkyClock(0, SkyClock.NIGHT_START - SkyClock.DUSK_MINUTES)));
        check("sunset too late for a full dusk is refused", throwsIllegalArgument(() -> new SkyClock(SUNRISE, SkyClock.NIGHT_START - SkyClock.DUSK_MINUTES + 1)));
    }

    private static void skyClockSunAndMoon() {
        check("sun at sunrise is 0", near(CLOCK.sunProgressAt(SUNRISE), 0f));
        float midday = (SUNRISE + SUNSET) / 2f;
        check("sun halfway through the day is halfway to its sunset point",
                near(CLOCK.sunProgressAt((int) midday), SkyClock.DUSK_PROGRESS / 2f, 0.002f));
        check("sun at sunset is at its sunset point", near(CLOCK.sunProgressAt(SUNSET), SkyClock.DUSK_PROGRESS));
        check("sun one minute before sunset is just short of it",
                CLOCK.sunProgressAt(SUNSET - 1) < SkyClock.DUSK_PROGRESS && CLOCK.sunProgressAt(SUNSET - 1) > SkyClock.DUSK_PROGRESS - 0.01f);
        check("sun keeps sinking through dusk, never jumping back", CLOCK.sunProgressAt(SUNSET + 30) > SkyClock.DUSK_PROGRESS
                && CLOCK.sunProgressAt(SUNSET + 30) < 1f
                && CLOCK.sunProgressAt(SUNSET + SkyClock.DUSK_MINUTES - 1) > CLOCK.sunProgressAt(SUNSET + 30));
        boolean neverBack = true;
        for (int minute = SUNRISE; minute < SUNSET + SkyClock.DUSK_MINUTES - 1; minute++) {
            neverBack &= CLOCK.sunProgressAt(minute + 1) >= CLOCK.sunProgressAt(minute);
        }
        check("sun only ever moves right, sunrise to the end of dusk", neverBack);
        check("no sun once dusk is over", CLOCK.sunProgressAt(SUNSET + SkyClock.DUSK_MINUTES) == SkyClock.NOT_IN_SKY);
        check("no sun at night", CLOCK.sunProgressAt(SkyClock.NIGHT_START) == SkyClock.NOT_IN_SKY);
        check("dark from dusk end", CLOCK.isDark(SUNSET + SkyClock.DUSK_MINUTES) && !CLOCK.isDark(SUNSET + SkyClock.DUSK_MINUTES - 1));
        check("dark until sunrise", CLOCK.isDark(SUNRISE - 1) && !CLOCK.isDark(SUNRISE));
        check("8 PM uses the charcoal sky", SkyPalette.forMinute(CLOCK, 20 * 60) == SkyPalette.NIGHT);
        check("dusk keeps the golden sky", SkyPalette.forMinute(CLOCK, SUNSET + 10) == SkyPalette.EVENING);
        check("afternoon palette by day", SkyPalette.forMinute(CLOCK, 15 * 60) == SkyPalette.AFTERNOON);
        check("no clock falls back to night", SkyPalette.forMinute(null, 0) == SkyPalette.NIGHT);
        check("greeting still says evening at 8 PM", "Good evening".equals(SkyClock.greeting(CLOCK.phaseAt(20 * 60))));
    }

    private static void skyPalettes() {
        for (SkyClock.Phase phase : SkyClock.Phase.values()) {
            SkyPalette palette = SkyPalette.forPhase(phase);
            check(phase + " palette has one colour per stop", palette.skyColors.length == SkyPalette.SKY_STOPS.length);
        }
        check("stops run from 0 to 1", SkyPalette.SKY_STOPS[0] == 0f
                && SkyPalette.SKY_STOPS[SkyPalette.SKY_STOPS.length - 1] == 1f);
        boolean ascending = true;
        for (int index = 1; index < SkyPalette.SKY_STOPS.length; index++) {
            ascending &= SkyPalette.SKY_STOPS[index] > SkyPalette.SKY_STOPS[index - 1];
        }
        check("stops ascend", ascending);
        check("stars at night", SkyPalette.forPhase(SkyClock.Phase.NIGHT).showStars);
        check("no stars in the afternoon", !SkyPalette.forPhase(SkyClock.Phase.AFTERNOON).showStars);
        check("null phase falls back to night", SkyPalette.forPhase(null) == SkyPalette.NIGHT);
        check("each phase has its own palette", SkyPalette.forPhase(SkyClock.Phase.EVENING) == SkyPalette.EVENING);
    }

    private static void arcGeometry() {
        float[] left = ArcGeometry.pointAt(0f, 1600, 900);
        float[] top = ArcGeometry.pointAt(0.5f, 1600, 900);
        float[] right = ArcGeometry.pointAt(1f, 1600, 900);
        check("left end x", near(left[0], 1600 * (ArcGeometry.CENTRE_X - ArcGeometry.RADIUS_X), 0.5f));
        check("left end sits on the horizon", near(left[1], 900 * ArcGeometry.HORIZON_Y, 0.5f));
        check("top is centred", near(top[0], 1600 * ArcGeometry.CENTRE_X, 0.5f));
        check("arc is centred on the screen, like the dock", near(top[0], 800f, 0.5f)
                && near(left[0] + right[0], 1600f, 0.5f));
        check("top is the highest point", near(top[1], 900 * (ArcGeometry.HORIZON_Y - ArcGeometry.RADIUS_Y), 0.5f));
        check("right end x", near(right[0], 1600 * (ArcGeometry.CENTRE_X + ArcGeometry.RADIUS_X), 0.5f));
        check("progress below 0 clamps to the left end", near(ArcGeometry.pointAt(-3f, 1600, 900)[0], left[0], 0.01f));
        check("progress above 1 clamps to the right end", near(ArcGeometry.pointAt(5f, 1600, 900)[0], right[0], 0.01f));
        check("NaN clamps to 0", ArcGeometry.clamp01(Float.NaN) == 0f);
        check("zero-size screen gives the origin", near(ArcGeometry.pointAt(0.5f, 0, 0)[0], 0f));
    }

    private static void dateLine() {
        LocalDateTime evening = LocalDateTime.of(2026, 11, 25, 20, 15);
        check("12-hour line", "WEDNESDAY  ·  8:15 PM".equals(DateLine.format(evening, false)));
        check("24-hour line", "WEDNESDAY  ·  20:15".equals(DateLine.format(evening, true)));
        check("midnight in 12-hour", DateLine.format(LocalDateTime.of(2026, 11, 26, 0, 5), false).endsWith("12:05 AM"));
        check("null time is empty", "".equals(DateLine.format(null, false)));
    }

    private static void watchNextTimes() {
        check("seconds are turned into milliseconds", WatchNextRules.toMillis(1_795_616_173L) == 1_795_616_173_000L);
        check("milliseconds stay as they are", WatchNextRules.toMillis(NOW) == NOW);
        check("zero stays zero", WatchNextRules.toMillis(0) == 0);
        check("negative becomes zero", WatchNextRules.toMillis(-5) == 0);
        check("cutoff itself counts as milliseconds",
                WatchNextRules.toMillis(WatchNextRules.SECONDS_CUTOFF) == WatchNextRules.SECONDS_CUTOFF);
    }

    private static void watchNextPick() {
        WatchNextRules.Item older = item("com.netflix.ninja", "Show A", WatchNextRules.TYPE_CONTINUE, NOW - 2 * DAY_MS);
        WatchNextRules.Item newer = item("in.startv.hotstar", "Film B", WatchNextRules.TYPE_CONTINUE, NOW - 1000);
        WatchNextRules.Item duplicate = item("com.netflix.ninja", "Show A", WatchNextRules.TYPE_NEXT, NOW - 3 * DAY_MS);
        WatchNextRules.Item saved = item("com.suntv.sunnxt", "Saved", WatchNextRules.TYPE_WATCHLIST, NOW);
        WatchNextRules.Item stale = item("com.fancode.tv", "Old match", WatchNextRules.TYPE_CONTINUE, NOW - 61 * DAY_MS);
        WatchNextRules.Item untitled = item("com.stremio.one", "  ", WatchNextRules.TYPE_CONTINUE, NOW);
        WatchNextRules.Item noTime = item("com.stremio.one", "No time", WatchNextRules.TYPE_CONTINUE, 0);
        List<WatchNextRules.Item> picked = WatchNextRules.pick(
                Arrays.asList(older, newer, duplicate, saved, stale, untitled, noTime, null), NOW, 6);
        check("newest first", picked.get(0) == newer);
        check("older one second", picked.get(1) == older);
        check("item without a time is kept, last", picked.size() == 3 && picked.get(2) == noTime);
        check("duplicate app and title dropped", !picked.contains(duplicate));
        check("watchlist dropped", !picked.contains(saved));
        check("stale dropped", !picked.contains(stale));
        check("blank title dropped", !picked.contains(untitled));
        check("limit respected", WatchNextRules.pick(Arrays.asList(older, newer), NOW, 1).size() == 1);
        check("null list gives empty", WatchNextRules.pick(null, NOW, 6).isEmpty());
        check("empty list gives empty", WatchNextRules.pick(new ArrayList<>(), NOW, 6).isEmpty());
        check("zero limit gives empty", WatchNextRules.pick(Arrays.asList(newer), NOW, 0).isEmpty());
        check("exactly 60 days old is kept", WatchNextRules.isWorthShowing(
                item("p", "t", WatchNextRules.TYPE_CONTINUE, NOW - WatchNextRules.STALE_AFTER_MS), NOW));
        check("null item is not shown", !WatchNextRules.isWorthShowing(null, NOW));
    }

    private static void watchNextStatusAndProgress() {
        long minute = 60_000L;
        check("progress halfway", near(WatchNextRules.progress(30 * minute, 60 * minute), 0.5f));
        check("progress unknown duration", WatchNextRules.progress(30 * minute, 0) == 0f);
        check("progress unknown position", WatchNextRules.progress(0, 60 * minute) == 0f);
        check("progress past the end is capped", WatchNextRules.progress(90 * minute, 60 * minute) == 1f);
        check("24 min left", "24 min left".equals(WatchNextRules.status(0, 36 * minute, 60 * minute)));
        check("part minutes round up", "1 min left".equals(WatchNextRules.status(0, 60 * minute - 1, 60 * minute)));
        check("exactly 1 h left", "1 h left".equals(WatchNextRules.status(0, 30 * minute, 90 * minute)));
        check("1 h 5 min left", "1 h 5 min left".equals(WatchNextRules.status(0, 10 * minute, 75 * minute)));
        check("59 min left stays in minutes", "59 min left".equals(WatchNextRules.status(0, minute, 60 * minute)));
        check("next episode", "Next episode".equals(WatchNextRules.status(WatchNextRules.TYPE_NEXT, 0, 0)));
        check("new", "New".equals(WatchNextRules.status(WatchNextRules.TYPE_NEW, 0, 0)));
        check("unknown times say continue", "Continue".equals(WatchNextRules.status(0, 0, 0)));
        check("finished says continue", "Continue".equals(WatchNextRules.status(0, 60 * minute, 60 * minute)));
    }

    private static void pinnedApps() {
        List<String> three = PinnedApps.parse("a, b ,c");
        check("parse trims", three.equals(Arrays.asList("a", "b", "c")));
        check("parse drops blanks and duplicates", PinnedApps.parse("a,,a, ,b").equals(Arrays.asList("a", "b")));
        check("parse null is empty", PinnedApps.parse(null).isEmpty());
        check("parse empty is empty", PinnedApps.parse("").isEmpty());
        check("serialise round-trips", PinnedApps.parse(PinnedApps.serialise(three)).equals(three));
        check("serialise null is empty", "".equals(PinnedApps.serialise(null)));
        check("add appends", PinnedApps.add(three, "d").equals(Arrays.asList("a", "b", "c", "d")));
        check("add ignores duplicates", PinnedApps.add(three, "a").equals(three));
        check("add ignores blank", PinnedApps.add(three, " ").equals(three));
        check("add to null list", PinnedApps.add(null, "a").equals(Arrays.asList("a")));
        check("remove", PinnedApps.remove(three, "b").equals(Arrays.asList("a", "c")));
        check("remove missing is no-op", PinnedApps.remove(three, "z").equals(three));
        check("move right", PinnedApps.move(three, "a", 1).equals(Arrays.asList("b", "a", "c")));
        check("move left", PinnedApps.move(three, "c", -1).equals(Arrays.asList("a", "c", "b")));
        check("move past the left end is no-op", PinnedApps.move(three, "a", -1).equals(three));
        check("move past the right end is no-op", PinnedApps.move(three, "c", 1).equals(three));
        check("move missing is no-op", PinnedApps.move(three, "z", 1).equals(three));
        check("operations leave the input alone", three.equals(Arrays.asList("a", "b", "c")));
        check("keep installed", PinnedApps.keepInstalled(three, Arrays.asList("c", "a")).equals(Arrays.asList("a", "c")));
        check("keep installed with null", PinnedApps.keepInstalled(null, three).isEmpty());
    }

    private static void appRules() {
        check("operator portal hidden", AppRules.isHidden("com.mediatek.opapp", "local.tvhome"));
        check("player shell hidden", AppRules.isHidden("com.vewd.core.tvapp", "local.tvhome"));
        check("Amazon home hidden", AppRules.isHidden("com.amazon.tv.launcher", "local.tvhome"));
        check("ourselves hidden", AppRules.isHidden("local.tvhome", "local.tvhome"));
        check("Netflix shown", !AppRules.isHidden("com.netflix.ninja", "local.tvhome"));
        check("Appstore shown", !AppRules.isHidden("com.amazon.venezia", "local.tvhome"));
        check("null hidden", AppRules.isHidden(null, "local.tvhome"));
        check("empty hidden", AppRules.isHidden("", "local.tvhome"));
    }

    private static void homePressLog() {
        String prefix = "I/ActivityTaskManager(  934): ";
        String press = prefix + "START u0 {act=android.intent.action.MAIN cat=[android.intent.category.HOME]"
                + " flg=0x10000100 cmp=com.amazon.tv.launcher/.ui.HomeActivity_vNext (has extras)} from uid 0";
        String bySettings = prefix + "START u0 {act=android.intent.action.MAIN cat=[android.intent.category.HOME]"
                + " flg=0x14000000 cmp=com.amazon.tv.launcher/.ui.HomeActivity_vNext} from uid 10183";
        String boot = prefix + "START u0 {act=android.intent.action.MAIN cat=[android.intent.category.HOME]"
                + " flg=0x10000100 cmp=com.amazon.tv.settings.v2/.system.FallbackHome} from uid 0";
        check("remote Home press recognised", HomePressLog.isHomePressLine(press));
        check("trailing whitespace tolerated", HomePressLog.isHomePressLine(press + " \r"));
        check("Settings opening home is not a press", !HomePressLog.isHomePressLine(bySettings));
        check("boot FallbackHome is not a press", !HomePressLog.isHomePressLine(boot));
        String bySleep = prefix + "START u0 {act=android.intent.action.MAIN cat=[android.intent.category.HOME]"
                + " flg=0x10200000 cmp=com.amazon.tv.launcher/.ui.HomeActivity_vNext (has extras)} from uid 1000";
        check("Fire OS sending the TV home at sleep is taken over", HomePressLog.isHomePressLine(bySleep));
        check("uid 1000 followed by digits is not", !HomePressLog.isHomePressLine(bySleep + "5"));
        check("uid 0 followed by digits is not a press", !HomePressLog.isHomePressLine(press + "5"));
        check("null is not a press", !HomePressLog.isHomePressLine(null));
        check("empty is not a press", !HomePressLog.isHomePressLine(""));
        String[] command = HomePressLog.logcatCommandSince("11-25 19:46:03.757");
        check("command runs logcat on the system buffer", "logcat".equals(command[0])
                && String.join("|", command).contains("-b|system"));
        check("timestamp stays one argument", Arrays.asList(command).contains("11-25 19:46:03.757"));
    }

    private static void homeState() {
        int[] timesRun = {0};
        HomeState.setHomeReadyListener(null);
        HomeState.notifyHomeReady();
        check("notify with no listener does nothing", timesRun[0] == 0);
        HomeState.setHomeReadyListener(() -> timesRun[0]++);
        HomeState.notifyHomeReady();
        HomeState.notifyHomeReady();
        check("listener runs on each notify", timesRun[0] == 2);
        HomeState.setHomeReadyListener(null);
        HomeState.notifyHomeReady();
        check("cleared listener is not run", timesRun[0] == 2);
        HomeState.setLogWatcherRunning(true);
        check("watcher flag on", HomeState.isLogWatcherRunning());
        HomeState.setLogWatcherRunning(false);
        check("watcher flag off", !HomeState.isLogWatcherRunning());
    }

    private static WatchNextRules.Item item(String packageName, String title, int type, long engagedAtMs) {
        return new WatchNextRules.Item(packageName, title, type, engagedAtMs, 0, 0, null, null, null, null);
    }

    private static void skyNotes() {
        check("morning names the sunset", "Sunset at 5:50 PM".equals(CLOCK.skyNote(9 * 60, false)));
        check("afternoon counts down", "Sunset in 2 h 50 min".equals(CLOCK.skyNote(15 * 60, false)));
        check("afternoon last minutes", "Sunset in 5 min".equals(CLOCK.skyNote(SUNSET - 5, false)));
        check("evening looks back", "The sun set at 5:50 PM".equals(CLOCK.skyNote(20 * 60, false)));
        check("night names the sunrise", "Sunrise at 6:20 AM".equals(CLOCK.skyNote(23 * 60, false)));
        check("24-hour form", "Sunrise at 06:20".equals(CLOCK.skyNote(1 * 60, true)));
        check("noon in 12-hour", "12:00 PM".equals(SkyClock.clockTime(SkyClock.NOON, false)));
        check("midnight in 12-hour", "12:00 AM".equals(SkyClock.clockTime(0, false)));
        check("wrapped minute", "12:05 AM".equals(SkyClock.clockTime(SkyClock.MINUTES_PER_DAY + 5, false)));
        check("whole hours", "2 h".equals(SkyClock.duration(120)));
        check("negative duration is zero", "0 min".equals(SkyClock.duration(-4)));
    }

    private static void weatherRules() {
        check("clear sky", WeatherRules.fromCode(0) == WeatherRules.Condition.CLEAR);
        check("mainly clear counts as clear", WeatherRules.fromCode(1) == WeatherRules.Condition.CLEAR);
        check("partly cloudy", WeatherRules.fromCode(2) == WeatherRules.Condition.PARTLY_CLOUDY);
        check("overcast", WeatherRules.fromCode(3) == WeatherRules.Condition.OVERCAST);
        check("fog", WeatherRules.fromCode(45) == WeatherRules.Condition.FOG);
        check("drizzle", WeatherRules.fromCode(53) == WeatherRules.Condition.DRIZZLE);
        check("rain", WeatherRules.fromCode(63) == WeatherRules.Condition.RAIN);
        check("showers are rain", WeatherRules.fromCode(80) == WeatherRules.Condition.RAIN);
        check("heavy rain", WeatherRules.fromCode(65) == WeatherRules.Condition.HEAVY_RAIN);
        check("violent showers are heavy", WeatherRules.fromCode(82) == WeatherRules.Condition.HEAVY_RAIN);
        check("thunderstorm", WeatherRules.fromCode(95) == WeatherRules.Condition.THUNDER);
        check("snow shows as cloud", WeatherRules.fromCode(73) == WeatherRules.Condition.OVERCAST);
        check("unknown code", WeatherRules.fromCode(42) == WeatherRules.Condition.UNKNOWN);
        check("negative code", WeatherRules.fromCode(-1) == WeatherRules.Condition.UNKNOWN);
        check("describe cloudy", "Cloudy".equals(WeatherRules.describe(WeatherRules.Condition.OVERCAST)));
        check("describe unknown is empty", "".equals(WeatherRules.describe(WeatherRules.Condition.UNKNOWN)));
        check("describe null is empty", "".equals(WeatherRules.describe(null)));
        check("sunrise minute", WeatherRules.minuteOf("2026-11-25T06:22") == 382);
        check("sunset minute", WeatherRules.minuteOf("2026-11-25T17:50") == 1070);
        check("minute with seconds", WeatherRules.minuteOf("2026-11-25T17:50:30") == 1070);
        check("no time part", WeatherRules.minuteOf("2026-11-25") == -1);
        check("bad hour", WeatherRules.minuteOf("2026-11-25T25:00") == -1);
        check("letters", WeatherRules.minuteOf("2026-11-25Tab:cd") == -1);
        check("null time", WeatherRules.minuteOf(null) == -1);
        check("cut short", WeatherRules.minuteOf("2026-11-25T06") == -1);
        WeatherRules.Report report = new WeatherRules.Report(20.7, 3, 100, 382, 1070, NOW);
        check("subtitle joins weather and sky", "21\u00B0  \u00B7  Cloudy  \u00B7  Sunrise at 6:22 AM".equals(
                WeatherRules.subtitle(report, "Sunrise at 6:22 AM")));
        check("subtitle without a reading", "Sunrise at 6:22 AM".equals(WeatherRules.subtitle(null, "Sunrise at 6:22 AM")));
        check("subtitle with unknown weather", "21\u00B0  \u00B7  Sunset at 5:50 PM".equals(
                WeatherRules.subtitle(new WeatherRules.Report(20.7, 42, 0, 382, 1070, NOW), "Sunset at 5:50 PM")));
        check("subtitle with empty sky note", "21\u00B0  \u00B7  Cloudy".equals(WeatherRules.subtitle(report, "")));
        check("fresh reading is kept", !WeatherRules.needsRefresh(report, NOW + 60_000));
        check("old reading is refreshed", WeatherRules.needsRefresh(report, NOW + WeatherRules.REFRESH_AFTER_MS));
        check("refresh every 10 minutes", WeatherRules.REFRESH_AFTER_MS == 10L * 60L * 1000L
                && !WeatherRules.needsRefresh(report, NOW + 9L * 60L * 1000L));
        check("missing reading is fetched", WeatherRules.needsRefresh(null, NOW));
        check("reading from the future is refreshed", WeatherRules.needsRefresh(report, NOW - 1000));
        check("recent reading is usable", WeatherRules.isUsable(report, NOW + 60_000));
        check("day-old reading is not usable", !WeatherRules.isUsable(report, NOW + WeatherRules.DISCARD_AFTER_MS));
        check("null reading is not usable", !WeatherRules.isUsable(null, NOW));
    }

    private static void birdsAndRain() {
        FlockPath flock = new FlockPath(0.2f, 0.04f);
        float[] entering = flock.birdAt(0, 0);
        float[] leaving = flock.birdAt(0, FlockPath.CROSSING_MS);
        check("flock enters off the left edge", entering[0] < 0f);
        check("flock leaves off the right edge", leaving[0] > 1f);
        check("flock drifts by its set amount", near(leaving[1] - entering[1], 0.04f, 0.01f));
        check("followers trail the leader", flock.birdAt(3, 5000)[0] < flock.birdAt(0, 5000)[0]);
        check("bird index wraps", near(flock.birdAt(5, 5000)[0], flock.birdAt(0, 5000)[0]));
        check("wings stay in range", Math.abs(FlockPath.wingAt(2, 1234)) <= 1f);
        check("wings beat", FlockPath.wingAt(0, 0) != FlockPath.wingAt(0, FlockPath.WING_BEAT_MS / 4));
        check("crossing ends", FlockPath.isOver(FlockPath.CROSSING_MS) && !FlockPath.isOver(FlockPath.CROSSING_MS - 1));
        java.util.Random seeded = new java.util.Random(11);
        boolean flocksInSky = true;
        boolean gapsInRange = true;
        for (int round = 0; round < 100; round++) {
            FlockPath random = FlockPath.random(seeded);
            flocksInSky &= random.startY >= 0.1f && random.startY <= 0.32f && Math.abs(random.driftY) <= 0.04f;
            long first = FlockPath.delayMs(seeded, true);
            long later = FlockPath.delayMs(seeded, false);
            gapsInRange &= first >= FlockPath.FIRST_MIN_MS && first <= FlockPath.FIRST_MAX_MS
                    && later >= FlockPath.NEXT_MIN_MS && later <= FlockPath.NEXT_MAX_MS;
        }
        check("flocks fly in the upper sky", flocksInSky);
        check("flock gaps stay in range", gapsInRange);

        check("no rain, no drops", RainField.dropCount(0f) == 0);
        check("light rain shows a few", RainField.dropCount(0.01f) >= RainField.MIN_DROPS);
        check("heavy rain shows them all", RainField.dropCount(1f) == RainField.MAX_DROPS);
        check("rain level is capped", RainField.dropCount(5f) == RainField.MAX_DROPS);
        check("NaN rain level, no drops", RainField.dropCount(Float.NaN) == 0);
        RainField field = new RainField();
        boolean onScreen = true;
        for (int index = 0; index < RainField.MAX_DROPS; index++) {
            float[] drop = field.dropAt(index, 987_654L);
            onScreen &= drop[1] >= 0f && drop[1] < 1f;
        }
        check("drops wrap back onto the screen", onScreen);
        check("drops fall", field.dropAt(0, 100)[1] != field.dropAt(0, 200)[1]);
        check("lightning starts bright", RainField.flashLevel(0) == 1f);
        check("lightning flickers", RainField.flashLevel((long) (RainField.FLASH_MS * 0.25)) < 0.3f);
        check("lightning is over", RainField.flashLevel(RainField.FLASH_MS) == 0f && RainField.flashLevel(-1) == 0f);
        long gap = RainField.nextFlashGapMs(new java.util.Random(3));
        check("lightning gap in range", gap >= RainField.FLASH_MIN_GAP_MS && gap <= RainField.FLASH_MAX_GAP_MS);

        check("https picture is web art", WatchNextRules.isWebArt("https://img.hotstar.com/a.jpg"));
        check("plain http is refused", !WatchNextRules.isWebArt("http://img.example/a.jpg"));
        check("null is not web art", !WatchNextRules.isWebArt(null));
        check("sample city times are valid", SkyClock.isValid(382, 1070));
        check("missing sunrise is invalid", !SkyClock.isValid(-1, 1070));
        check("sunset too late is invalid", !SkyClock.isValid(382, 21 * 60 + 30));
    }

    private static void sceneRules() {
        WeatherRules.Report clear = new WeatherRules.Report(25, 0, 0, SUNRISE, SUNSET, NOW);
        WeatherRules.Report overcast = new WeatherRules.Report(21, 3, 100, SUNRISE, SUNSET, NOW);
        WeatherRules.Report rain = new WeatherRules.Report(21, 63, 100, SUNRISE, SUNSET, NOW);
        WeatherRules.Report storm = new WeatherRules.Report(21, 95, 100, SUNRISE, SUNSET, NOW);
        WeatherRules.Report fog = new WeatherRules.Report(18, 45, 60, SUNRISE, SUNSET, NOW);

        SceneRules.Scene dawn = SceneRules.decide(CLOCK, SUNRISE + 10, clear);
        check("dawn keeps fading stars", dawn.starLevel > 0f && dawn.starLevel < 1f);
        check("dawn has the most mist", dawn.mistLevel > 0.9f);
        check("no birds at dawn", !dawn.birds);
        check("dawn has sun rays", dawn.sunRays);
        check("dawn sky", SkyPalette.forMinute(CLOCK, SUNRISE + 10) == SkyPalette.DAWN);
        check("dawn is over after 70 minutes", !SceneRules.isDawn(CLOCK, SUNRISE + SceneRules.DAWN_MINUTES));
        check("dawn progress at sunrise", SceneRules.dawnProgress(CLOCK, SUNRISE) == 0f);
        check("dawn progress clamps", SceneRules.dawnProgress(CLOCK, SkyClock.NOON) == 1f);

        SceneRules.Scene earlyMorning = SceneRules.decide(CLOCK, 8 * 60, clear);
        check("mist thins as the morning goes on", earlyMorning.mistLevel > 0f && earlyMorning.mistLevel < dawn.mistLevel);
        check("birds in the morning", earlyMorning.birds);
        check("no wisps before nine", !earlyMorning.wisps);
        SceneRules.Scene lateMorning = SceneRules.decide(CLOCK, 10 * 60, clear);
        check("mist gone by nine", lateMorning.mistLevel == 0f);
        check("wisps late in a clear morning", lateMorning.wisps);
        check("clear sky has no clouds", lateMorning.clouds == 0);
        check("no stars by day", lateMorning.starLevel == 0f);

        SceneRules.Scene cloudyAfternoon = SceneRules.decide(CLOCK, 15 * 60, overcast);
        check("full cover gives four clouds", cloudyAfternoon.clouds == 4);
        check("overcast greys the sky", cloudyAfternoon.gloomLevel > 0f);
        check("no sun rays in the afternoon", !cloudyAfternoon.sunRays);
        SceneRules.Scene unknownAfternoon = SceneRules.decide(CLOCK, 15 * 60, null);
        check("afternoon without weather keeps three clouds", unknownAfternoon.clouds == 3);
        check("no weather, no rain", unknownAfternoon.rainLevel == 0f && unknownAfternoon.gloomLevel == 0f);

        SceneRules.Scene rainyMorning = SceneRules.decide(CLOCK, 10 * 60, rain);
        check("rain falls", rainyMorning.rainLevel > 0f);
        check("no birds in rain", !rainyMorning.birds);
        check("no wisps in rain", !rainyMorning.wisps);
        check("no sun rays in rain", !rainyMorning.sunRays);
        SceneRules.Scene stormNight = SceneRules.decide(CLOCK, 23 * 60, storm);
        check("storm brings lightning", stormNight.lightning && stormNight.rainLevel == 1f);
        check("no stars while it rains", stormNight.starLevel == 0f);
        check("night gloom is lighter", stormNight.gloomLevel < SceneRules.decide(CLOCK, 15 * 60, storm).gloomLevel);
        check("no clouds drawn at night", stormNight.clouds == 0);

        SceneRules.Scene clearNight = SceneRules.decide(CLOCK, 23 * 60, clear);
        SceneRules.Scene cloudyNight = SceneRules.decide(CLOCK, 23 * 60, overcast);
        check("clear night, full stars", clearNight.starLevel == 1f);
        check("overcast night (100% cloud), no stars", cloudyNight.starLevel == 0f);
        WeatherRules.Report halfCloud = new WeatherRules.Report(22, 2, 50, SUNRISE, SUNSET, NOW);
        check("half-cloudy night, half the stars", near(SceneRules.decide(CLOCK, 23 * 60, halfCloud).starLevel, 0.5f));
        check("sun and moon: clear sky shows them fully", clearNight.bodyLevel == 1f);
        check("sun and moon: hidden when overcast", cloudyNight.bodyLevel == 0f);
        check("sun and moon: hidden in rain", stormNight.bodyLevel == 0f
                && SceneRules.decide(CLOCK, 15 * 60, rain).bodyLevel == 0f);
        check("sun and moon: light cloud leaves them clear", SceneRules.throughCloud(0f, SceneRules.CLEAR_UP_TO_COVER) == 1f);
        check("sun and moon: fade as cloud thickens", near(SceneRules.throughCloud(0f, 65), 0.5f));
        check("sun and moon: no weather reading shows them", SceneRules.throughCloud(0f, -1) == 1f);
        check("staged cover: clear has none, rain is full, unknown stays unknown",
                WeatherRules.typicalCover(WeatherRules.Condition.CLEAR) == 0
                && WeatherRules.typicalCover(WeatherRules.Condition.RAIN) == 100
                && WeatherRules.typicalCover(WeatherRules.Condition.PARTLY_CLOUDY) == 40
                && WeatherRules.typicalCover(WeatherRules.Condition.UNKNOWN) == -1);
        check("fog brings mist by day", SceneRules.decide(CLOCK, 15 * 60, fog).mistLevel == 1f);
        check("fog at night shows no mist", SceneRules.decide(CLOCK, 23 * 60, fog).mistLevel == 0f);
        check("drizzle is light", SceneRules.rainLevel(WeatherRules.Condition.DRIZZLE) < SceneRules.rainLevel(WeatherRules.Condition.RAIN));
        check("null condition, no rain", SceneRules.rainLevel(null) == 0f);
    }

    private static void bodyColours() {
        BodyLook sunrise = BodyLook.at(CLOCK, SUNRISE);
        BodyLook beforeNoon = BodyLook.at(CLOCK, SkyClock.NOON - 1);
        BodyLook afternoon = BodyLook.at(CLOCK, 15 * 60);
        BodyLook dusk = BodyLook.at(CLOCK, SUNSET + 10);
        BodyLook night = BodyLook.at(CLOCK, 21 * 60);
        check("sunrise sun is orange", sunrise.body == 0xFFFF9A3C);
        check("sun is nearly white by noon", ((beforeNoon.body >> 8) & 0xFF) > 0xF0);
        check("afternoon sun is white", afternoon.body == 0xFFFFF8EC);
        BodyLook goldenStart = BodyLook.at(CLOCK, SUNSET - BodyLook.GOLDEN_MINUTES);
        BodyLook goldenMiddle = BodyLook.at(CLOCK, SUNSET - BodyLook.GOLDEN_MINUTES / 2);
        BodyLook lastMinute = BodyLook.at(CLOCK, SUNSET - 1);
        check("golden hour starts white", goldenStart.body == 0xFFFFF8EC);
        check("halfway through golden hour the sun is orange", goldenMiddle.body == 0xFFFF9A3C);
        check("the minute before sunset is already nearly dusk red",
                Math.abs(((lastMinute.body >> 8) & 0xFF) - ((dusk.body >> 8) & 0xFF)) <= 4);
        check("dusk sun is red", ((dusk.body >> 16) & 0xFF) == 0xFF && ((dusk.body >> 8) & 0xFF) < 0x60);
        check("night shows a white moon", night.body == 0xFFE3E7EF);
        check("moon glow is smaller", night.glowScale < 1f && afternoon.glowScale == 1f);
        check("moon glow is faint", ((night.glowCore >>> 24) & 0xFF) < ((afternoon.glowCore >>> 24) & 0xFF));
        check("glow edge is clear", (night.glowEdge >>> 24) == 0 && (night.glowEdge & 0xFFFFFF) == (night.glowCore & 0xFFFFFF));
        check("no clock gives the moon", BodyLook.at(null, 0).glowScale < 1f);
        BodyLook halfHidden = afternoon.dimmed(0.5f);
        check("dimmed: half the alpha, same colour", ((halfHidden.body >>> 24) & 0xFF) == 128
                && (halfHidden.body & 0xFFFFFF) == (afternoon.body & 0xFFFFFF) && halfHidden.glowScale == afternoon.glowScale);
        check("dimmed: fully hidden and clamped", ((afternoon.dimmed(0f).body >>> 24) & 0xFF) == 0
                && afternoon.dimmed(3f).body == afternoon.body && ((afternoon.dimmed(Float.NaN).glowCore >>> 24) & 0xFF) == 0);
        check("blend start", BodyLook.blend(0xFF000000, 0xFFFFFFFF, 0f) == 0xFF000000);
        check("blend end", BodyLook.blend(0xFF000000, 0xFFFFFFFF, 1f) == 0xFFFFFFFF);
        check("blend middle", BodyLook.blend(0x00000000, 0xFEFEFEFE, 0.5f) == 0x7F7F7F7F);
        check("blend clamps", BodyLook.blend(0xFF000000, 0xFFFFFFFF, 3f) == 0xFFFFFFFF);
        check("blend NaN", BodyLook.blend(0xFF102030, 0xFFFFFFFF, Float.NaN) == 0xFF102030);
    }

    private static void comets() {
        Comet comet = new Comet(0.8f, 0.1f, -0.3f, 0.1f);
        float[] start = comet.headAt(0);
        float[] end = comet.headAt(Comet.FLIGHT_MS);
        check("comet starts where it was placed", near(start[0], 0.8f) && near(start[1], 0.1f));
        check("comet ends after its full travel", near(end[0], 0.5f) && near(end[1], 0.2f));
        check("comet speeds up", Comet.progress(Comet.FLIGHT_MS / 2) < 0.5f);
        check("progress is clamped past the end", Comet.progress(Comet.FLIGHT_MS * 3) == 1f);
        check("progress is clamped before the start", Comet.progress(-50) == 0f);
        check("comet starts invisible", Comet.brightness(0) == 0f);
        check("comet is fully bright mid-flight", Comet.brightness(Comet.FLIGHT_MS / 2) == 1f);
        check("comet fades out at the end", Comet.brightness(Comet.FLIGHT_MS) == 0f);
        check("comet is over after its flight", Comet.isOver(Comet.FLIGHT_MS) && !Comet.isOver(Comet.FLIGHT_MS - 1));
        java.util.Random seeded = new java.util.Random(7);
        boolean placedWell = true;
        boolean delaysInRange = true;
        for (int round = 0; round < 200; round++) {
            Comet random = Comet.random(seeded);
            placedWell &= random.startX >= 0.4f && random.startX <= 0.95f && random.startY <= 0.28f
                    && random.travelX < 0f && random.travelY > 0f;
            long first = Comet.delayMs(seeded, true);
            long later = Comet.delayMs(seeded, false);
            delaysInRange &= first >= Comet.FIRST_MIN_MS && first <= Comet.FIRST_MAX_MS
                    && later >= Comet.NEXT_MIN_MS && later <= Comet.NEXT_MAX_MS;
        }
        check("comets start high on the right and fall left", placedWell);
        check("comet waits stay in range", delaysInRange);
    }

    private static void starTwinkle() {
        StarMap sky = StarMap.shared();
        boolean sizesInRange = true;
        for (float starRadius : sky.radius) {
            sizesInRange &= starRadius >= StarMap.SMALLEST_RADIUS && starRadius <= StarMap.LARGEST_RADIUS;
        }
        check("star sizes stay between smallest and largest", sizesInRange);
        StarMap stars = StarMap.shared();
        check("same sky every time", stars == StarMap.shared() && stars.x.length == StarMap.STAR_COUNT);
        boolean onScreen = true;
        boolean lowerAreFaint = true;
        boolean someBelowApps = false;
        for (int index = 0; index < StarMap.STAR_COUNT; index++) {
            onScreen &= stars.x[index] >= 0f && stars.x[index] <= 1f && stars.y[index] >= 0f && stars.y[index] <= 1f;
            if (stars.y[index] >= StarMap.LOWER_SKY_START) {
                someBelowApps = true;
                lowerAreFaint &= stars.alpha[index] <= 90;
            }
        }
        check("stars cover the whole screen", onScreen && someBelowApps);
        check("stars below the apps are faint", lowerAreFaint);
        int brightest = 0;
        int dimmest = 255;
        for (long time = 0; time < 4000; time += 50) {
            int alpha = StarMap.twinkleAlpha(200, 2000f, 0f, time);
            brightest = Math.max(brightest, alpha);
            dimmest = Math.min(dimmest, alpha);
        }
        check("twinkle reaches full brightness", brightest >= 198);
        check("twinkle dims to a quarter", dimmest >= 48 && dimmest <= 52);
        check("zero period holds steady", StarMap.twinkleAlpha(200, 0f, 1f, 1234) == 200);
        check("alpha is clamped", StarMap.twinkleAlpha(400, 0f, 0f, 0) == 255);
    }

    private static void motionTiming() {
        check("half-speed TV doubles the duration", MotionTiming.compensate(320, 0.5f) == 640);
        check("normal speed keeps it", MotionTiming.compensate(320, 1f) == 320);
        check("slow TV shortens it", MotionTiming.compensate(320, 2f) == 160);
        check("animations off are respected", MotionTiming.compensate(320, 0f) == 320);
        check("NaN scale keeps it", MotionTiming.compensate(320, Float.NaN) == 320);
        check("zero duration stays zero", MotionTiming.compensate(0, 0.5f) == 0);
        check("negative duration becomes zero", MotionTiming.compensate(-5, 0.5f) == 0);
    }

    private static void youtubeRules() {
        check("youtube: Fire TV app", YouTubeRules.isYouTube("com.amazon.firetv.youtube"));
        check("youtube: other app is not", !YouTubeRules.isYouTube("in.startv.hotstar") && !YouTubeRules.isYouTube(null));
        check("youtube: valid id", YouTubeRules.isVideoId("wZxmrDDH7N4") && YouTubeRules.isVideoId("a-b_c123XYZ"));
        check("youtube: bad ids", !YouTubeRules.isVideoId(null) && !YouTubeRules.isVideoId("")
                && !YouTubeRules.isVideoId("wZxmrDDH7N") && !YouTubeRules.isVideoId("wZxmrDDH7N4x")
                && !YouTubeRules.isVideoId("wZxmrDD\"7N4"));
        check("youtube: search url has title and channel", "https://www.youtube.com/results?search_query=Kia+Syros+%7C+EP+07+MotoWagon"
                .equals(YouTubeRules.searchUrl(" Kia Syros | EP 07 ", "MotoWagon")));
        check("youtube: search url without channel", "https://www.youtube.com/results?search_query=Kia"
                .equals(YouTubeRules.searchUrl("Kia", null)));
        check("youtube: no title no search", YouTubeRules.searchUrl(null, "x") == null && YouTubeRules.searchUrl("  ", "x") == null);
        check("youtube: thumbnail", "https://i.ytimg.com/vi/wZxmrDDH7N4/mqdefault.jpg".equals(YouTubeRules.thumbnailUrl("wZxmrDDH7N4")));
        check("youtube: no thumbnail for bad id", YouTubeRules.thumbnailUrl(null) == null && YouTubeRules.thumbnailUrl("x") == null);
        check("youtube: link opens video at the second", ("intent://www.youtube.com/watch?v=wZxmrDDH7N4&t=1109s#Intent;scheme=https;"
                + "action=android.intent.action.VIEW;package=com.amazon.firetv.youtube;end")
                .equals(YouTubeRules.watchIntentUri("com.amazon.firetv.youtube", "wZxmrDDH7N4", 1_109_007)));
        check("youtube: negative position starts at 0", YouTubeRules.watchIntentUri("p", "wZxmrDDH7N4", -5).contains("&t=0s"));
        check("youtube: no link without id or app", YouTubeRules.watchIntentUri("p", null, 0) == null
                && YouTubeRules.watchIntentUri(null, "wZxmrDDH7N4", 0) == null);
        check("youtube: normalise", "should you buy kia syros ev ep 07 motowagon"
                .equals(YouTubeRules.normalise("Should You Buy Kia Syros EV? | EP - 07 | MotoWagon.")));
        check("youtube: normalise null and punctuation only", YouTubeRules.normalise(null).isEmpty()
                && YouTubeRules.normalise(" |?. ").isEmpty());
        check("youtube: text with escapes", "Tom & \"Jerry\" é".equals(
                YouTubeRules.textAfter("{\"text\":\"Tom \\u0026 \\\"Jerry\\\" \\u00e9\"}", "\"text\":\"")));
        check("youtube: text missing", YouTubeRules.textAfter("{}", "\"text\":\"") == null
                && YouTubeRules.textAfter(null, "\"text\":\"") == null);
        check("youtube: unterminated text", YouTubeRules.textAfter("\"text\":\"abc", "\"text\":\"") == null);

        String sessionTitle = "Should You Buy Kia Syros EV for Long Drives? | Miles by MotoWagon EP - 07 | MotoWagon";
        String page = "var ytInitialData = {"
                + videoEntry("2DZEQDicfFw", "Kia Syros EV Review", "MotoWagon")
                + videoEntry("RtHoLecmMSc", sessionTitle, "Some Other Channel")
                + videoEntry("wZxmrDDH7N4", "Should You Buy Kia Syros EV for Long Drives? | Miles by MotoWagon EP - 07 | MotoWagon.", "MotoWagon")
                + "};";
        check("youtube: exact title and channel picked over earlier results", "wZxmrDDH7N4"
                .equals(YouTubeRules.findVideoId(page, sessionTitle, "MotoWagon")));
        check("youtube: title alone when channel unknown", "RtHoLecmMSc".equals(YouTubeRules.findVideoId(page, sessionTitle, null)));
        check("youtube: wrong channel finds nothing", YouTubeRules.findVideoId(page, sessionTitle, "Nobody") == null);
        check("youtube: near title is not taken", YouTubeRules.findVideoId(page, "Kia Syros EV", "MotoWagon") == null);
        check("youtube: empty inputs", YouTubeRules.findVideoId(null, sessionTitle, "MotoWagon") == null
                && YouTubeRules.findVideoId("", sessionTitle, "MotoWagon") == null
                && YouTubeRules.findVideoId(page, null, "MotoWagon") == null);
        check("youtube: short id at page end ignored", YouTubeRules.findVideoId(
                "\"videoRenderer\":{\"videoId\":\"abc", "abc", null) == null);
    }

    /** One search result the way YouTube's page writes it (trimmed to the fields the rules read). */
    private static String videoEntry(String videoId, String title, String channel) {
        return "\"videoRenderer\":{\"videoId\":\"" + videoId + "\",\"thumbnail\":{\"thumbnails\":[]},"
                + "\"title\":{\"runs\":[{\"text\":\"" + title.replace("\"", "\\\"") + "\"}]},"
                + "\"longBylineText\":{\"runs\":[{\"text\":\"" + channel + "\"}]}},";
    }

    private static void controlRules() {
        check("brightness: one step brighter", ControlRules.stepBrightness(128, 1) == 153);
        check("brightness: one step dimmer", ControlRules.stepBrightness(128, -1) == 103);
        check("brightness: never below the floor", ControlRules.stepBrightness(20, -1) == ControlRules.MIN_BRIGHTNESS
                && ControlRules.stepBrightness(0, -1) == ControlRules.MIN_BRIGHTNESS);
        check("brightness: never above full", ControlRules.stepBrightness(250, 1) == ControlRules.MAX_BRIGHTNESS
                && ControlRules.stepBrightness(999, 0) == ControlRules.MAX_BRIGHTNESS);
        check("brightness: big direction counts as one step", ControlRules.stepBrightness(128, 7) == 153);
        check("brightness: zero direction keeps value", ControlRules.stepBrightness(128, 0) == 128);
        check("brightness: percent ends", ControlRules.brightnessPercent(ControlRules.MIN_BRIGHTNESS) == 0
                && ControlRules.brightnessPercent(255) == 100 && ControlRules.brightnessPercent(-5) == 0);
        check("brightness: middle percent", ControlRules.brightnessPercent(128) == 48);
        check("wifi: offline", "Wi-Fi · offline".equals(ControlRules.wifiLabel(false, -40)));
        check("wifi: strong at -55", "Wi-Fi · strong".equals(ControlRules.wifiLabel(true, -55)));
        check("wifi: boundaries", "Wi-Fi · strong".equals(ControlRules.wifiLabel(true, -60))
                && "Wi-Fi · good".equals(ControlRules.wifiLabel(true, -61))
                && "Wi-Fi · good".equals(ControlRules.wifiLabel(true, -70))
                && "Wi-Fi · weak".equals(ControlRules.wifiLabel(true, -71)));
        check("wifi name: quotes removed", "Home Net".equals(ControlRules.networkName("\"Home Net\"")));
        check("wifi name: unquoted kept", "HomeNet".equals(ControlRules.networkName("HomeNet")));
        check("wifi name: unknown or empty", ControlRules.networkName("<unknown ssid>") == null
                && ControlRules.networkName(null) == null && ControlRules.networkName("\"\"") == null
                && ControlRules.networkName("  ") == null);
        check("now playing: Spotify counts", ControlRules.isNowPlayingApp("com.spotify.tv.android"));
        check("now playing: Alexa and this launcher do not", !ControlRules.isNowPlayingApp("com.amazon.alexamediaplayer.runtime.ftv")
                && !ControlRules.isNowPlayingApp("local.tvhome") && !ControlRules.isNowPlayingApp(null)
                && !ControlRules.isNowPlayingApp(""));
        check("now playing: playing beats paused", ControlRules.pickSession(
                new int[] {ControlRules.STATE_PAUSED, ControlRules.STATE_PLAYING}, new boolean[] {true, true}) == 1);
        check("now playing: paused when nothing plays", ControlRules.pickSession(
                new int[] {0, ControlRules.STATE_PAUSED}, new boolean[] {true, true}) == 1);
        check("now playing: no title is skipped", ControlRules.pickSession(
                new int[] {ControlRules.STATE_PLAYING, ControlRules.STATE_PAUSED}, new boolean[] {false, true}) == 1);
        check("now playing: none", ControlRules.pickSession(new int[] {0}, new boolean[] {true}) == ControlRules.NO_SESSION
                && ControlRules.pickSession(new int[0], new boolean[0]) == ControlRules.NO_SESSION
                && ControlRules.pickSession(null, null) == ControlRules.NO_SESSION
                && ControlRules.pickSession(new int[] {3}, new boolean[0]) == ControlRules.NO_SESSION);
        check("sleep: cycles off, 15, 30, 60, off", ControlRules.nextSleepChoice(0) == 15 && ControlRules.nextSleepChoice(15) == 30
                && ControlRules.nextSleepChoice(30) == 60 && ControlRules.nextSleepChoice(60) == 0);
        check("sleep: unknown restarts at 15", ControlRules.nextSleepChoice(7) == 15);
        check("sleep: labels", "Sleep off".equals(ControlRules.sleepLabel(0)) && "Sleep 25 min".equals(ControlRules.sleepLabel(25))
                && "Sleep off".equals(ControlRules.sleepLabel(-3)));
        check("adb: labels", "ADB on".equals(ControlRules.adbLabel(true)) && "ADB off".equals(ControlRules.adbLabel(false)));
        long mb = 1024L * 1024L;
        check("memory: freed amount", "Freed 120 MB".equals(ControlRules.freedLabel(500 * mb, 620 * mb)));
        check("memory: tiny or negative gain", "Already free".equals(ControlRules.freedLabel(500 * mb, 502 * mb))
                && "Already free".equals(ControlRules.freedLabel(500 * mb, 400 * mb)));
        int span = ControlRules.PREVIEW_SPAN_MINUTES;
        check("sky preview: starts at the sunrise", ControlRules.previewOffsetMinutes(0, 30_000, span) == 0);
        check("sky preview: halfway is 12 hours on", ControlRules.previewOffsetMinutes(15_000, 30_000, span) == 720);
        check("sky preview: ends at the next sunrise", ControlRules.previewOffsetMinutes(30_000, 30_000, span) == span);
        check("sky preview: held at the ends", ControlRules.previewOffsetMinutes(-5, 30_000, span) == 0
                && ControlRules.previewOffsetMinutes(99_000, 30_000, span) == span);
        check("sky preview: zero duration or span", ControlRules.previewOffsetMinutes(10, 0, span) == 0
                && ControlRules.previewOffsetMinutes(10, 30_000, 0) == 0);
        int sunriseMinute = 6 * 60 + 22;
        check("sky preview: before sunrise starts today", ControlRules.previewStartDaysAhead(5 * 60, sunriseMinute) == 0);
        check("sky preview: at or after sunrise starts tomorrow", ControlRules.previewStartDaysAhead(sunriseMinute, sunriseMinute) == 1
                && ControlRules.previewStartDaysAhead(22 * 60 + 50, sunriseMinute) == 1);
        check("now playing: labels", "YouTube · Playing".equals(ControlRules.playbackLabel("YouTube", ControlRules.STATE_PLAYING))
                && "Paused".equals(ControlRules.playbackLabel(null, ControlRules.STATE_PAUSED)));
    }

    private static void moonRules() {
        long day = 24L * 60 * 60 * 1000;
        check("moon: reference is a new moon", near(MoonRules.phaseAt(MoonRules.REFERENCE_NEW_MOON_MS), 0f));
        long halfCycle = Math.round(MoonRules.SYNODIC_DAYS / 2 * day);
        check("moon: half a cycle later is full", near(MoonRules.phaseAt(MoonRules.REFERENCE_NEW_MOON_MS + halfCycle), 0.5f, 0.001f));
        check("moon: before the reference wraps round", MoonRules.phaseAt(MoonRules.REFERENCE_NEW_MOON_MS - day) > 0.9f);
        long fullMoonJan2024 = 1_706_205_240_000L;
        check("moon: 25 Jan 2024 17:54 UTC was a full moon", near(MoonRules.phaseAt(fullMoonJan2024), 0.5f, 0.03f));
        check("moon: lit share", near(MoonRules.litShare(0f), 0f) && near(MoonRules.litShare(0.5f), 1f)
                && near(MoonRules.litShare(0.25f), 0.5f, 0.001f) && near(MoonRules.litShare(0.75f), 0.5f, 0.001f));
        check("moon: growing then shrinking", MoonRules.isWaxing(0.2f) && !MoonRules.isWaxing(0.7f));
        check("moon: crescent vs gibbous", MoonRules.isCrescent(0.1f) && !MoonRules.isCrescent(0.4f) && MoonRules.isCrescent(0.9f));
        check("moon: full moon rises at the left 12 hours after sunrise (just after sunset in winter)",
                near(MoonRules.progressAt(CLOCK, SUNRISE + 720, 0.5f), 0f, 0.01f)
                && MoonRules.progressAt(CLOCK, SUNRISE + 719, 0.5f) == SkyClock.NOT_IN_SKY);
        check("moon: full moon is up at midnight", MoonRules.progressAt(CLOCK, 0, 0.5f) > 0.3f && MoonRules.progressAt(CLOCK, 0, 0.5f) < 0.7f);
        check("moon: full moon is down at noon", MoonRules.progressAt(CLOCK, SkyClock.NOON, 0.5f) == SkyClock.NOT_IN_SKY);
        check("moon: half moon is high at sunset", MoonRules.progressAt(CLOCK, SUNSET, 0.25f) > 0.4f
                && MoonRules.progressAt(CLOCK, SUNSET, 0.25f) < 0.6f);
        check("moon: last-quarter moon is up in the morning sky", MoonRules.progressAt(CLOCK, 9 * 60, 0.75f) != SkyClock.NOT_IN_SKY);
        check("moon: new moon is not drawn", MoonRules.progressAt(CLOCK, SkyClock.NOON, 0.005f) == SkyClock.NOT_IN_SKY);
        check("moon: no clock, no moon", MoonRules.progressAt(null, 0, 0.5f) == SkyClock.NOT_IN_SKY);
        check("moon: bright at night, pale by day", MoonRules.brightnessAt(CLOCK, 23 * 60) == 1f
                && MoonRules.brightnessAt(CLOCK, SkyClock.NOON) == MoonRules.DAYTIME_BRIGHTNESS);
        check("moon: pale moon look", ((BodyLook.moon(MoonRules.DAYTIME_BRIGHTNESS).body >>> 24) & 0xFF) < 0xA0
                && ((BodyLook.moon(1f).body >>> 24) & 0xFF) == 0xFF);
    }

    private static void heyGreeting() {
        check("hey: with a name", "Hey Sam!".equals(SkyClock.hey("Sam")) && "Hey Sam!".equals(SkyClock.hey("  Sam ")));
        check("hey: no name", "Hey there!".equals(SkyClock.hey(null)) && "Hey there!".equals(SkyClock.hey("  ")));
        LocalDateTime wednesdayNight = LocalDateTime.of(2026, 11, 25, 23, 2);
        check("day line: weekday in capitals", "WEDNESDAY".equals(DateLine.day(wednesdayNight)));
        check("time line: 12 and 24 hour", "11:02 PM".equals(DateLine.time(wednesdayNight, false))
                && "23:02".equals(DateLine.time(wednesdayNight, true)));
        check("day and time: empty without a time", DateLine.day(null).isEmpty() && DateLine.time(null, false).isEmpty());
        check("day and date", "WEDNESDAY  ·  25 NOV".equals(DateLine.dayAndDate(wednesdayNight))
                && "FRIDAY  ·  1 JAN".equals(DateLine.dayAndDate(LocalDateTime.of(2027, 1, 1, 9, 0)))
                && DateLine.dayAndDate(null).isEmpty());
        WeatherRules.Report cloudy = new WeatherRules.Report(20.8, 3, 100, SUNRISE, SUNSET, NOW);
        check("weather line: temperature and conditions only", "21°  ·  Cloudy".equals(WeatherRules.subtitle(cloudy, null)));
        check("weather line: none without a reading", WeatherRules.subtitle(null, null) == null);
    }

    private static void continueHiding() {
        WatchNextRules.Item got = new WatchNextRules.Item("in.startv.hotstar", "Game Of Thrones", 0, 5L, 1_000_000, 4_431_000,
                "hotstar://x", "https://img/got.jpg", "8", "4");
        WatchNextRules.Item gotWatchedMore = new WatchNextRules.Item("in.startv.hotstar", "Game Of Thrones", 0, 9L, 1_600_000,
                4_431_000, "hotstar://x", "https://img/got.jpg", "8", "4");
        WatchNextRules.Item stremio = new WatchNextRules.Item("com.stremio.one", "Frieren", 0, 0L, 300_000, 1_440_000,
                "stremio://x", "https://img/f.jpg", null, null);
        List<String> hidden = ContinueHiding.fingerprints(Arrays.asList(got, stremio));
        check("hiding: current cards are hidden", ContinueHiding.withoutHidden(Arrays.asList(got, stremio), hidden).isEmpty());
        check("hiding: watching again brings it back", ContinueHiding.withoutHidden(Arrays.asList(gotWatchedMore), hidden).size() == 1);
        WatchNextRules.Item nextEpisode = new WatchNextRules.Item("in.startv.hotstar", "Game Of Thrones", 0, 9L, 1_000_000,
                4_431_000, "hotstar://x", "https://img/got.jpg", "8", "5");
        check("hiding: a new episode shows", ContinueHiding.withoutHidden(Arrays.asList(nextEpisode), hidden).size() == 1);
        check("hiding: nothing hidden shows all", ContinueHiding.withoutHidden(Arrays.asList(got, stremio), null).size() == 2
                && ContinueHiding.withoutHidden(Arrays.asList(got), new ArrayList<>()).size() == 1);
        check("hiding: empty inputs", ContinueHiding.withoutHidden(null, hidden).isEmpty() && ContinueHiding.fingerprints(null).isEmpty());
    }

    private static void settingsRules() {
        check("name: trimmed", "Sam".equals(SettingsRules.cleanName("  Sam ")));
        check("name: none", SettingsRules.cleanName(null).isEmpty() && SettingsRules.cleanName("   ").isEmpty());
        check("name: cut to the limit", SettingsRules.cleanName("A very long name that keeps going").length()
                == SettingsRules.MAX_NAME_LENGTH);
        check("latitude: valid", SettingsRules.latitude("51.51") == 51.51 && SettingsRules.latitude(" -33.9 ") == -33.9);
        check("latitude: out of range or junk", Double.isNaN(SettingsRules.latitude("91"))
                && Double.isNaN(SettingsRules.latitude("abc")) && Double.isNaN(SettingsRules.latitude(null))
                && Double.isNaN(SettingsRules.latitude("")));
        check("device name: owner's first name", "Sam".equals(SettingsRules.nameFromDeviceName("Sam's Fire TV"))
                && "Sam".equals(SettingsRules.nameFromDeviceName("Sam Lee\u2019s Fire TV Stick")));
        check("device name: not someone's", SettingsRules.nameFromDeviceName("Living Room").isEmpty()
                && SettingsRules.nameFromDeviceName("Fire TV").isEmpty() && SettingsRules.nameFromDeviceName("'s TV").isEmpty()
                && SettingsRules.nameFromDeviceName(null).isEmpty());
        check("longitude: allows ±180", SettingsRules.longitude("-180") == -180 && Double.isNaN(SettingsRules.longitude("180.5")));
    }

    private static void geoRules() {
        check("area: payload after the prefix", "eyJhIjoxfQ==".equals(GeoRules.areaPayload("A/eyJhIjoxfQ==")));
        check("area: missing or other form", GeoRules.areaPayload(null) == null && GeoRules.areaPayload("A/") == null
                && GeoRules.areaPayload("eyJhIjoxfQ==") == null);
        check("postcode: valid", GeoRules.isPostcode("110001") && GeoRules.isPostcode("SW1A 1AA") && GeoRules.isPostcode("10115"));
        check("postcode: invalid", !GeoRules.isPostcode(null) && !GeoRules.isPostcode(" ") && !GeoRules.isPostcode("12345678901")
                && !GeoRules.isPostcode("56&2130"));
        check("country: two letters", GeoRules.isCountryCode("IN") && !GeoRules.isCountryCode("IND") && !GeoRules.isCountryCode("1N")
                && !GeoRules.isCountryCode(null));
        check("search url", "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=1&postalcode=SW1A+1AA&countrycodes=gb"
                .equals(GeoRules.searchUrl("SW1A 1AA", "GB")));
        check("search url: nothing for bad input", GeoRules.searchUrl("x&y", "IN") == null && GeoRules.searchUrl("110001", "") == null);
    }

    private static void cardTitles() {
        WatchNextRules.Item episode = new WatchNextRules.Item("in.startv.hotstar", "Game Of Thrones", 0, 1L, 1, 2, null, null, "8", "4");
        WatchNextRules.Item film = new WatchNextRules.Item("in.startv.hotstar", "Moana 2", 0, 1L, 1, 2, null, null, null, null);
        check("card title: series with episode", "Game Of Thrones \u00B7 S8 E4".equals(WatchNextRules.cardTitle(episode)));
        check("card title: film is just the title", "Moana 2".equals(WatchNextRules.cardTitle(film)));
        WatchNextRules.Item longVideo = new WatchNextRules.Item("com.amazon.firetv.youtube",
                "Should You Buy Kia Syros EV for Long Drives? | Miles by MotoWagon EP - 07", 0, 1L, 1, 2, null, null, null, null);
        String cut = WatchNextRules.cardTitle(longVideo);
        check("card title: long titles end in an ellipsis", cut.length() <= WatchNextRules.CARD_TITLE_MAX_CHARS && cut.endsWith("\u2026"));
        WatchNextRules.Item longEpisode = new WatchNextRules.Item("x", "A Very Long Series Name That Goes On And On", 0, 1L, 1, 2,
                null, null, "12", "10");
        String cutEpisode = WatchNextRules.cardTitle(longEpisode);
        check("card title: the episode survives the cut", cutEpisode.endsWith("S12 E10")
                && cutEpisode.length() <= WatchNextRules.CARD_TITLE_MAX_CHARS);
        check("card title: none", WatchNextRules.cardTitle(null).isEmpty());
    }

    private static void dockMagnifier() {
        DockMagnifier.Profile profile = new DockMagnifier.Profile(new float[] {1.3f, 1.1f}, new float[] {12f, 3f}, 0.8f, true);
        check("focused tile gets the peak", DockMagnifier.scaleAt(3, 3, profile) == 1.3f);
        check("neighbour on the left", DockMagnifier.scaleAt(2, 3, profile) == 1.1f);
        check("neighbour on the right", DockMagnifier.scaleAt(4, 3, profile) == 1.1f);
        check("beyond the profile shrinks to resting", DockMagnifier.scaleAt(0, 3, profile) == 0.8f);
        check("no focus keeps full size", DockMagnifier.scaleAt(3, DockMagnifier.NO_FOCUS, profile) == 1f);
        check("null profile keeps full size", DockMagnifier.scaleAt(0, 0, null) == 1f);
        check("focused tile rises most", DockMagnifier.liftAt(3, 3, profile) == 12f);
        check("neighbour rises a little", DockMagnifier.liftAt(4, 3, profile) == 3f);
        check("far tile does not rise", DockMagnifier.liftAt(0, 3, profile) == 0f);
        check("no focus, no rise", DockMagnifier.liftAt(3, DockMagnifier.NO_FOCUS, profile) == 0f);
        check("null profile, no rise", DockMagnifier.liftAt(0, 0, null) == 0f);
        check("scales for an empty row", DockMagnifier.scales(0, 0, profile).length == 0);
        check("scales for a negative count", DockMagnifier.scales(-2, 0, profile).length == 0);
        check("dock: focused largest, neighbours a little larger", DockMagnifier.DOCK.scales[0] > DockMagnifier.DOCK.scales[1] && DockMagnifier.DOCK.scales[1] > 1f);
        check("dock: other tiles keep normal size", DockMagnifier.DOCK.restingScale == 1f && DockMagnifier.DOCK.spread);
        check("spread each side is half the growth", near(DockMagnifier.spreadEachSide(new float[] {100f, 100f}, new float[] {1.2f, 1.1f}), 15f, 0.001f));
        check("no growth, no spread", DockMagnifier.spreadEachSide(new float[] {100f}, new float[] {1f}) == 0f);
        check("mismatched arrays, no spread", DockMagnifier.spreadEachSide(new float[] {100f}, new float[] {1f, 1f}) == 0f);
        check("null arrays, no spread", DockMagnifier.spreadEachSide(null, null) == 0f);

        float[] lefts = {0f, 110f, 220f};
        float[] widths = {100f, 100f, 100f};
        float[] atRest = DockMagnifier.shifts(lefts, widths, new float[] {1f, 1f, 1f});
        check("no growth means no shift", near(atRest[0], 0f) && near(atRest[1], 0f) && near(atRest[2], 0f));
        float[] middleGrown = DockMagnifier.shifts(lefts, widths, new float[] {1f, 1.2f, 1f});
        check("middle tile stays centred", near(middleGrown[1], 0f));
        check("left tile moves left by half the growth", near(middleGrown[0], -10f));
        check("right tile moves right by half the growth", near(middleGrown[2], 10f));
        float[] firstGrown = DockMagnifier.shifts(lefts, widths, new float[] {1.2f, 1f, 1f});
        float grownRight = lefts[0] + 50f + firstGrown[0] + 60f;
        float neighbourLeft = lefts[1] + firstGrown[1];
        check("grown tile keeps the original gap to its neighbour", near(neighbourLeft - grownRight, 10f, 0.01f));
        check("mismatched arrays give no shift", DockMagnifier.shifts(lefts, new float[] {1f}, widths)[0] == 0f);
        check("empty row gives no shifts", DockMagnifier.shifts(new float[0], new float[0], new float[0]).length == 0);
    }

    private static void iconTrim() {
        int white = 0xFFFFFFFF;
        int red = 0xFFFF0000;
        int[] icon = new int[7 * 7];
        java.util.Arrays.fill(icon, white);
        for (int y = 1; y <= 5; y++) {
            for (int x = 1; x <= 5; x++) {
                icon[y * 7 + x] = red;
            }
        }
        icon[3 * 7 + 3] = white;
        IconTrim.clearEdgeBackground(icon, 7, 7);
        check("outer white is cleared", icon[0] == 0 && icon[6] == 0 && icon[48] == 0);
        check("red symbol stays", icon[1 * 7 + 1] == red);
        check("white inside the symbol stays", icon[3 * 7 + 3] == white);
        int[] seeThrough = {0x00000000, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF};
        IconTrim.clearEdgeBackground(seeThrough, 2, 2);
        check("already transparent icon is left alone", seeThrough[1] == 0xFFFFFFFF);
        int[] nearWhite = {0xFFFFFFFF, 0xFFF0F0F0};
        IconTrim.clearEdgeBackground(nearWhite, 2, 1);
        check("near-white counts as background", nearWhite[1] == 0);
        IconTrim.clearEdgeBackground(null, 2, 2);
        IconTrim.clearEdgeBackground(new int[1], 2, 2);
        check("bad input is ignored", true);
        check("brand colour picks the vivid red", IconTrim.brandColour(new int[] {0xFFFFFFFF, 0xFFEEEEEE, 0xFFFF0000}) == 0xFFFF0000);
        check("grey tile falls back to the average", IconTrim.brandColour(new int[] {0xFF000000, 0xFF646464}) == 0xFF323232);
        check("empty tile has a default", IconTrim.brandColour(new int[0]) != 0);
        check("colourful gradient", IconTrim.isColourful(new int[] {0xFF1E90FF, 0xFFE0218A, 0xFF7B2FF7}));
        check("plain black is not colourful", !IconTrim.isColourful(new int[] {0xFF000000, 0xFF101010, 0xFF050505}));
        check("plain white is not colourful", !IconTrim.isColourful(new int[] {0xFFFFFFFF, 0xFFF4F4F4}));
        check("empty is not colourful", !IconTrim.isColourful(new int[0]) && !IconTrim.isColourful(null));
        check("cards: focused card is largest", DockMagnifier.scaleAt(2, 2, DockMagnifier.CARDS) > 1f);
        check("cards: neighbour between focused and far", DockMagnifier.scaleAt(3, 2, DockMagnifier.CARDS) < 1f
                && DockMagnifier.scaleAt(3, 2, DockMagnifier.CARDS) > DockMagnifier.scaleAt(4, 2, DockMagnifier.CARDS));
        check("cards: no focus means full size", DockMagnifier.scaleAt(0, DockMagnifier.NO_FOCUS, DockMagnifier.CARDS) == 1f);
        WatchNextRules.Item gotList = new WatchNextRules.Item("in.startv.hotstar", "Game Of Thrones", 0, 1L, 1_000_000, 4_431_000,
                "hotstar://in/shows/game-of-thrones/1/watch", "https://img/got.jpg", "8", "4");
        WatchNextRules.Item gotPlaying = new WatchNextRules.Item("in.startv.hotstar", "S8 E4 The Last of the Starks", 0, 2L,
                1_000_000, 4_431_000, null, null, null, null);
        WatchNextRules.Item youtubePlaying = new WatchNextRules.Item("com.amazon.firetv.youtube", "Kia Syros EV", 0, 3L,
                1_045_345, 1_431_741, null, null, null, null);
        List<WatchNextRules.Item> playingOnly = WatchNextRules.onlyAppsWithoutList(
                java.util.Arrays.asList(gotPlaying, youtubePlaying), java.util.Collections.singletonList(gotList));
        check("now playing: app with its own list is dropped", !playingOnly.contains(gotPlaying));
        check("now playing: app without a list is kept", playingOnly.contains(youtubePlaying) && playingOnly.size() == 1);
        check("now playing: no lists keeps all", WatchNextRules.onlyAppsWithoutList(
                java.util.Collections.singletonList(gotPlaying), java.util.Collections.emptyList()).size() == 1);
        check("now playing: null inputs", WatchNextRules.onlyAppsWithoutList(null, null).isEmpty()
                && WatchNextRules.onlyAppsWithoutList(java.util.Collections.singletonList(youtubePlaying), null).size() == 1);
        check("darken halves", IconTrim.darken(0xFF804020, 0.5f) == 0xFF402010);
        check("darken clamps", IconTrim.darken(0xFF804020, 3f) == 0xFF804020 && IconTrim.darken(0xFF804020, -1f) == 0xFF000000);
    }

    private static void continueSources() {
        check("Stremio's channel name", WatchNextRules.isContinueChannel("Continue Watching"));
        check("lower-case name", WatchNextRules.isContinueChannel("continue watching"));
        check("keep watching", WatchNextRules.isContinueChannel("Keep Watching"));
        check("resume", WatchNextRules.isContinueChannel("Resume"));
        check("recommendations are not", !WatchNextRules.isContinueChannel("Recommended for you"));
        check("null name", !WatchNextRules.isContinueChannel(null));

        check("YouTube is remembered", !MediaHistoryRules.isSkippedApp("com.amazon.firetv.youtube"));
        check("Spotify is skipped", MediaHistoryRules.isSkippedApp("com.spotify.tv.android"));
        check("Alexa is skipped", MediaHistoryRules.isSkippedApp("com.amazon.alexamediaplayer.runtime.ftv"));
        check("this launcher is skipped", MediaHistoryRules.isSkippedApp("local.tvhome"));
        check("no package is skipped", MediaHistoryRules.isSkippedApp(null) && MediaHistoryRules.isSkippedApp(""));
        String youtube = "com.amazon.firetv.youtube";
        check("a watched video is kept", MediaHistoryRules.isWorthKeeping(youtube, "Kia Syros EV", 1_002_240, 1_500_000));
        check("a peek is not kept", !MediaHistoryRules.isWorthKeeping(youtube, "Kia Syros EV", 5_000, 1_500_000));
        check("a finished video is not kept", !MediaHistoryRules.isWorthKeeping(youtube, "Kia Syros EV", 1_450_000, 1_500_000));
        check("unknown length is kept", MediaHistoryRules.isWorthKeeping(youtube, "Live stream", 60_000, 0));
        check("no title is not kept (Netflix)", !MediaHistoryRules.isWorthKeeping("com.netflix.ninja", null, 60_000, 0));
        check("blank title is not kept", !MediaHistoryRules.isWorthKeeping(youtube, "  ", 60_000, 0));
        check("finished at 95%", MediaHistoryRules.isFinished(95, 100) && !MediaHistoryRules.isFinished(94, 100));
        check("no length is never finished", !MediaHistoryRules.isFinished(1_000_000, 0));
        check("paused position stays", MediaHistoryRules.positionNow(1000, 0, 5000, 1f, false) == 1000);
        check("playing position moves on", MediaHistoryRules.positionNow(1000, 0, 5000, 1f, true) == 6000);
        check("double speed", MediaHistoryRules.positionNow(1000, 0, 5000, 2f, true) == 11000);
        check("clock before report", MediaHistoryRules.positionNow(1000, 5000, 1000, 1f, true) == 1000);
        check("negative position is zero", MediaHistoryRules.positionNow(-5, 0, 0, 1f, false) == 0);
    }

    private static void seriesLabels() {
        check("season and episode", "S2 \u00B7 E5".equals(WatchNextRules.seriesLabel("2", "5")));
        check("numbers are trimmed", "S1 \u00B7 E10".equals(WatchNextRules.seriesLabel(" 1 ", "10 ")));
        check("episode only", "E7".equals(WatchNextRules.seriesLabel(null, "7")));
        check("season only", "S3".equals(WatchNextRules.seriesLabel("3", "")));
        check("film has no label", "".equals(WatchNextRules.seriesLabel(null, null)));
        check("blank values have no label", "".equals(WatchNextRules.seriesLabel(" ", "  ")));
    }

    private static void watchNextArt() {
        check("thumbnail preferred", "t".equals(WatchNextRules.preferredArt("t", "p")));
        check("poster when no thumbnail", "p".equals(WatchNextRules.preferredArt(null, "p")));
        check("poster when thumbnail blank", "p".equals(WatchNextRules.preferredArt("  ", "p")));
        check("null when neither", WatchNextRules.preferredArt(null, " ") == null);
        check("content uri is local", WatchNextRules.isLocalArt("content://media/1"));
        check("resource uri is local", WatchNextRules.isLocalArt("android.resource://pkg/1"));
        check("file uri is local", WatchNextRules.isLocalArt("FILE:///sdcard/a.png"));
        check("web picture is not local", !WatchNextRules.isLocalArt("https://img.example/a.jpg"));
        check("null is not local", !WatchNextRules.isLocalArt(null));
        check("scheme of web link", "https".equals(WatchNextRules.artScheme("HTTPS://x/y")));
        check("scheme of missing", "none".equals(WatchNextRules.artScheme(null)));
        check("scheme of garbage", "unknown".equals(WatchNextRules.artScheme("no-colon")));
    }

    private static boolean throwsIllegalArgument(Runnable action) {
        try {
            action.run();
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }

    private static boolean near(float actual, float expected) {
        return near(actual, expected, 0.0001f);
    }

    private static boolean near(float actual, float expected, float tolerance) {
        return Math.abs(actual - expected) <= tolerance;
    }

    private static void check(String description, boolean passed) {
        totalChecks++;
        if (!passed) {
            failedChecks++;
            System.out.println("FAIL: " + description);
        }
    }
}
