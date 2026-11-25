package local.tvhome;

import android.view.View;
import android.widget.FrameLayout;

/**
 * Owns every moving part of the sky (stars, comets, clouds, wisps, mist, birds, sun rays, rain,
 * lightning and the grey veil of bad weather) and switches each on or off from one
 * {@link SceneRules.Scene}. Everything stops while the home screen is not visible.
 */
final class SkyScene {

    private static final int GLOOM_COLOUR = 0xFF3A414D;

    private final View gloom;
    private final StarField stars;
    private final SunRays rays;
    private final DriftLayer wisps;
    private final DriftLayer clouds;
    private final DriftLayer mist;
    private final BirdLayer birds;
    private final RainLayer rain;

    /** Adds the layers to {@code root} right after whatever is already there (the sky). */
    SkyScene(FrameLayout root) {
        android.content.Context context = root.getContext();
        gloom = new View(context);
        gloom.setBackgroundColor(GLOOM_COLOUR);
        gloom.setAlpha(0f);
        stars = new StarField(context);
        rays = new SunRays(context);
        wisps = new DriftLayer(context, DriftLayer.WISP_SPECS, DriftLayer.WISP_HEIGHT_RATIO, DriftLayer::paintWisp);
        clouds = new DriftLayer(context, DriftLayer.CLOUD_SPECS, DriftLayer.CLOUD_HEIGHT_RATIO, DriftLayer::paintCloud);
        mist = new DriftLayer(context, DriftLayer.MIST_SPECS, DriftLayer.MIST_HEIGHT_RATIO, DriftLayer::paintMist);
        birds = new BirdLayer(context);
        rain = new RainLayer(context);
        for (View layer : new View[] {gloom, stars, rays, wisps, clouds, mist, birds, rain}) {
            root.addView(layer, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
        }
    }

    /** {@code sunPoint} is where the sun is on screen (pixels), or null when it is not up. */
    void apply(SceneRules.Scene scene, boolean visible, float[] sunPoint) {
        if (!visible || scene == null) {
            stopAll();
            return;
        }
        gloom.setAlpha(scene.gloomLevel);
        stars.show(scene.starLevel);
        rays.show(scene.sunRays, sunPoint);
        wisps.show(scene.wisps ? DriftLayer.WISP_SPECS.length : 0, 1f);
        clouds.show(scene.clouds, 1f);
        mist.show(scene.mistLevel > 0f ? DriftLayer.MIST_SPECS.length : 0, scene.mistLevel);
        birds.setActive(scene.birds);
        rain.show(scene.rainLevel, scene.lightning);
    }

    void stopAll() {
        stars.show(0f);
        rays.show(false, null);
        wisps.show(0, 0f);
        clouds.show(0, 0f);
        mist.show(0, 0f);
        birds.setActive(false);
        rain.show(0f, false);
    }
}
