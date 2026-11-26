package local.tvhome;

/**
 * Which sky effects run at a given moment, from the time of day and the weather. One place for
 * all the decisions, so the screen code only switches layers on and off. Pure logic.
 */
final class SceneRules {

    /** Dawn: the first stretch of morning, with a pink sky, the last stars and the most mist. */
    static final int DAWN_MINUTES = 70;
    /** Mist has burnt off by this time; wisps of cloud and sun rays take over after it. */
    static final int MIST_GONE_MINUTE = 9 * 60;

    /** What to show. Levels are 0..1. */
    static final class Scene {
        float starLevel;
        /** How much of the sun or moon shows through the cloud: 1 clear, 0 hidden (overcast or rain). */
        float bodyLevel;
        int clouds;
        boolean wisps;
        float mistLevel;
        boolean birds;
        boolean sunRays;
        float rainLevel;
        boolean lightning;
        /** A grey veil over the sky for heavy cloud and rain. */
        float gloomLevel;
    }

    private SceneRules() {
    }

    static boolean isDawn(SkyClock clock, int minuteOfDay) {
        int minute = SkyClock.normalise(minuteOfDay);
        int sunrise = clock.sunriseMinute();
        return minute >= sunrise && minute < sunrise + DAWN_MINUTES;
    }

    /** 0 at sunrise, 1 when dawn is over. */
    static float dawnProgress(SkyClock clock, int minuteOfDay) {
        int into = SkyClock.normalise(minuteOfDay) - clock.sunriseMinute();
        return clamp01(into / (float) DAWN_MINUTES);
    }

    /** {@code weather} may be null (no reading yet): then the sky follows the time of day alone. */
    static Scene decide(SkyClock clock, int minuteOfDay, WeatherRules.Report weather) {
        Scene scene = new Scene();
        int minute = SkyClock.normalise(minuteOfDay);
        SkyClock.Phase phase = clock.phaseAt(minute);
        boolean dark = clock.isDark(minute);
        boolean daytime = phase == SkyClock.Phase.MORNING || phase == SkyClock.Phase.AFTERNOON;
        boolean dawn = isDawn(clock, minute);
        WeatherRules.Condition condition = weather == null ? WeatherRules.Condition.UNKNOWN : weather.condition();
        int cover = weather == null ? -1 : weather.cloudCoverPercent;
        int thickCover = weather == null ? -1 : weather.thickCoverPercent;
        int highCover = weather == null ? -1 : weather.highCoverPercent;
        boolean clearish = condition == WeatherRules.Condition.CLEAR
                || condition == WeatherRules.Condition.PARTLY_CLOUDY || condition == WeatherRules.Condition.UNKNOWN;

        scene.rainLevel = rainLevel(condition);
        scene.starLevel = scene.rainLevel > 0f ? 0f : starLevel(phase, dark, dawn, clock, minute, cover);
        scene.bodyLevel = throughCloud(scene.rainLevel, thickCover) * throughHaze(highCover);
        scene.clouds = dark ? 0 : cloudCount(phase, dawn, cover);
        scene.wisps = phase == SkyClock.Phase.MORNING && minute >= MIST_GONE_MINUTE && clearish;
        scene.mistLevel = mistLevel(phase, clock, minute, condition);
        scene.lightning = condition == WeatherRules.Condition.THUNDER;
        scene.birds = daytime && scene.rainLevel == 0f && !dawn;
        scene.sunRays = phase == SkyClock.Phase.MORNING && clearish && scene.rainLevel == 0f;
        scene.gloomLevel = gloomLevel(condition, dark);
        return scene;
    }

    private static float starLevel(SkyClock.Phase phase, boolean dark, boolean dawn, SkyClock clock, int minute, int cover) {
        float level;
        if (dark || phase == SkyClock.Phase.EVENING) {
            level = 1f;
        } else if (dawn) {
            level = 1f - dawnProgress(clock, minute);
        } else {
            return 0f;
        }
        float hiddenByCloud = cover < 0 ? 0f : cover / 100f;
        return level * (1f - hiddenByCloud);
    }

    /** Cloud up to this much leaves the sun and moon clear; from here they fade. */
    static final int CLEAR_UP_TO_COVER = 40;
    /** By this much cloud (overcast) the sun and moon are hidden. */
    static final int HIDDEN_AT_COVER = 90;

    /** The sun and moon show clear through light cloud, fade as it thickens, and hide when overcast or raining. */
    static float throughCloud(float rainLevel, int cloudCoverPercent) {
        if (rainLevel > 0f) {
            return 0f;
        }
        if (cloudCoverPercent < 0) {
            return 1f;
        }
        float thickening = (cloudCoverPercent - CLEAR_UP_TO_COVER) / (float) (HIDDEN_AT_COVER - CLEAR_UP_TO_COVER);
        return 1f - clamp01(thickening);
    }

    /** Most clouds drawn at once, on a fully cloudy sky. */
    static final int MAX_CLOUDS = 7;
    /** Thin high cloud (cirrus) can take at most this much off the sun or moon; they still show through. */
    static final float HIGH_CLOUD_HAZE = 0.35f;

    /** How much of the sun or moon shows through thin high cloud: a light haze, never hidden. */
    static float throughHaze(int highCoverPercent) {
        return highCoverPercent < 0 ? 1f : 1f - HIGH_CLOUD_HAZE * clamp01(highCoverPercent / 100f);
    }

    private static int cloudCount(SkyClock.Phase phase, boolean dawn, int cover) {
        if (cover >= 0) {
            return Math.max(0, Math.min(MAX_CLOUDS, Math.round(cover * MAX_CLOUDS / 100f)));
        }
        if (phase == SkyClock.Phase.AFTERNOON) {
            return 3;
        }
        return phase == SkyClock.Phase.MORNING && !dawn ? 1 : 0;
    }

    private static float mistLevel(SkyClock.Phase phase, SkyClock clock, int minute, WeatherRules.Condition condition) {
        if (condition == WeatherRules.Condition.FOG && !clock.isDark(minute)) {
            return 1f;
        }
        if (phase != SkyClock.Phase.MORNING || minute >= MIST_GONE_MINUTE) {
            return 0f;
        }
        int sunrise = clock.sunriseMinute();
        return 1f - clamp01((minute - sunrise) / (float) (MIST_GONE_MINUTE - sunrise));
    }

    static float rainLevel(WeatherRules.Condition condition) {
        if (condition == null) {
            return 0f;
        }
        switch (condition) {
            case DRIZZLE:
                return 0.3f;
            case RAIN:
                return 0.6f;
            case HEAVY_RAIN:
            case THUNDER:
                return 1f;
            default:
                return 0f;
        }
    }

    private static float gloomLevel(WeatherRules.Condition condition, boolean dark) {
        float level;
        switch (condition) {
            case OVERCAST:
            case FOG:
                level = 0.18f;
                break;
            case DRIZZLE:
            case RAIN:
                level = 0.3f;
                break;
            case HEAVY_RAIN:
            case THUNDER:
                level = 0.42f;
                break;
            default:
                level = 0f;
        }
        return dark ? level * 0.5f : level;
    }

    private static float clamp01(float value) {
        if (Float.isNaN(value) || value < 0f) {
            return 0f;
        }
        return Math.min(1f, value);
    }
}
