package local.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Every installed app over the same sky; hold OK on one to add it to or remove it from home. */
public final class AllAppsActivity extends Activity {

    private static final int COLUMNS = 7;
    private static final int DIM_OVERLAY = 0x8C000000;

    private GridLayout grid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildScreen());
    }

    @Override
    protected void onResume() {
        super.onResume();
        fillGrid();
    }

    private FrameLayout buildScreen() {
        FrameLayout root = new FrameLayout(this);
        root.addView(new SkyView(this, HomeSettings.skyClock(this)), matchParent());
        View dim = new View(this);
        dim.setBackgroundColor(DIM_OVERLAY);
        root.addView(dim, matchParent());
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        int sidePadding = UiKit.dp(this, 48);
        column.setPadding(sidePadding, UiKit.dp(this, 36), sidePadding, 0);
        TextView title = UiKit.text(this, "All apps", 28f, 0xFFFFFFFF);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        column.addView(title);
        ScrollView scroller = new ScrollView(this);
        scroller.setClipToPadding(false);
        scroller.setPadding(0, UiKit.dp(this, 20), 0, UiKit.dp(this, 24));
        grid = new GridLayout(this);
        grid.setColumnCount(COLUMNS);
        grid.setClipChildren(false);
        scroller.addView(grid);
        column.addView(scroller);
        root.addView(column, matchParent());
        return root;
    }

    private void fillGrid() {
        grid.removeAllViews();
        List<AppCatalog.LaunchableApp> apps = AppCatalog.loadAll(this);
        List<String> pinned = HomeSettings.pinned(this);
        for (int position = 0; position < apps.size(); position++) {
            AppCatalog.LaunchableApp app = apps.get(position);
            View tile = TileFactory.appTile(this, app, position);
            tile.setOnClickListener(clicked -> Launch.start(this, app.launchIntent, app.label, clicked));
            tile.setOnLongClickListener(held -> {
                showPinMenu(app, pinned.contains(app.packageName));
                return true;
            });
            grid.addView(tile, cellMargins());
        }
        FocusStyle.zoomGrid(grid);
        if (grid.getChildCount() > 0) {
            grid.getChildAt(0).requestFocus();
        }
        Log.i(HomeState.LOG_TAG, "fillGrid: apps=" + apps.size());
    }

    private void showPinMenu(AppCatalog.LaunchableApp app, boolean isPinned) {
        String choice = isPinned ? "Remove from home" : "Add to home";
        new AlertDialog.Builder(this)
                .setTitle(app.label)
                .setItems(new String[] {choice}, (dialog, which) -> togglePinned(app.packageName, isPinned))
                .show();
    }

    private void togglePinned(String packageName, boolean isPinned) {
        List<String> pinned = HomeSettings.pinned(this);
        List<String> updated = isPinned ? PinnedApps.remove(pinned, packageName) : PinnedApps.add(pinned, packageName);
        HomeSettings.savePinned(this, updated);
        Log.i(HomeState.LOG_TAG, "togglePinned: package=" + packageName + " wasPinned=" + isPinned
                + " pinned=" + PinnedApps.serialise(updated));
        fillGrid();
    }

    private GridLayout.LayoutParams cellMargins() {
        GridLayout.LayoutParams margins = new GridLayout.LayoutParams();
        margins.width = UiKit.dp(this, TileFactory.holderSize(TileFactory.TILE_WIDTH_DP));
        margins.height = UiKit.dp(this, TileFactory.holderSize(TileFactory.TILE_HEIGHT_DP));
        int gap = UiKit.dp(this, 3);
        margins.setMargins(gap, gap, gap, gap);
        return margins;
    }

    private static FrameLayout.LayoutParams matchParent() {
        return new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
    }
}
