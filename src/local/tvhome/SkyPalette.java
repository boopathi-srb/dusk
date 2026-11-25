package local.tvhome;

/** Colours for each time of day. Pure data (ARGB ints), no Android types. */
final class SkyPalette {

    /** Where each sky colour sits, top (0) to bottom (1). Shared by every phase. */
    static final float[] SKY_STOPS = {0f, 0.38f, 0.60f, 0.66f, 0.85f, 1f};

    /** Dawn: deep blue overhead, rose and peach near the ground, the last stars still out. */
    static final SkyPalette DAWN = new SkyPalette(
            new int[] {0xFF1F2F5C, 0xFF3D4C82, 0xFF7A6FA0, 0xFFC58AA0, 0xFFF0A88C, 0xFFF7C79A},
            0xFFFFFFFF, 0xFFF6E6EE, 0x80FFFFFF, true);

    static final SkyPalette MORNING = new SkyPalette(
            new int[] {0xFF2E5C9E, 0xFF4F80BF, 0xFF86A9D6, 0xFFC9C6CF, 0xFFF0C79A, 0xFFE6A877},
            0xFFFFFFFF, 0xFFF6ECE0, 0x80FFFFFF, false);

    static final SkyPalette AFTERNOON = new SkyPalette(
            new int[] {0xFF1E5AA8, 0xFF2F72C2, 0xFF5A95D8, 0xFF8DB7E6, 0xFFB8D3EE, 0xFF7F9BB5},
            0xFFFFFFFF, 0xFFE8F1FB, 0x80FFFFFF, false);

    static final SkyPalette EVENING = new SkyPalette(
            new int[] {0xFF090B1C, 0xFF17183B, 0xFF2E2248, 0xFF4A2B47, 0xFF8E4B3C, 0xFFCC8644},
            0xFFFBF3E8, 0xFFE2CDB8, 0x73D6CDE8, true);

    /** Charcoal, cool and even: no warm glow at the bottom once dusk is over. */
    static final SkyPalette NIGHT = new SkyPalette(
            new int[] {0xFF07080B, 0xFF0D0F13, 0xFF13151A, 0xFF16181E, 0xFF1A1C22, 0xFF1E2027},
            0xFFEDEFF3, 0xFFAEB4C0, 0x4DB0B6C2, true);

    final int[] skyColors;
    final int textPrimary;
    final int textSecondary;
    /** Dotted arc and horizon line. */
    final int lineColor;
    final boolean showStars;

    private SkyPalette(int[] skyColors,
            int textPrimary, int textSecondary, int lineColor, boolean showStars) {
        this.skyColors = skyColors;
        this.textPrimary = textPrimary;
        this.textSecondary = textSecondary;
        this.lineColor = lineColor;
        this.showStars = showStars;
    }

    /** The palette for a moment: dark (charcoal) from the end of dusk, else by time of day. */
    static SkyPalette forMinute(SkyClock clock, int minuteOfDay) {
        if (clock == null) {
            return NIGHT;
        }
        if (clock.isDark(minuteOfDay)) {
            return NIGHT;
        }
        return SceneRules.isDawn(clock, minuteOfDay) ? DAWN : forPhase(clock.phaseAt(minuteOfDay));
    }

    static SkyPalette forPhase(SkyClock.Phase phase) {
        if (phase == null) {
            return NIGHT;
        }
        switch (phase) {
            case MORNING:
                return MORNING;
            case AFTERNOON:
                return AFTERNOON;
            case EVENING:
                return EVENING;
            default:
                return NIGHT;
        }
    }
}
