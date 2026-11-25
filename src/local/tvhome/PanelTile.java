package local.tvhome;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * One square tile in the control panel: an icon over a label. A tile with a state (ADB, sleep
 * timer) reads its label and on/off look fresh each time the panel refreshes; "on" tiles glow warm.
 */
final class PanelTile {

    private static final float ICON_DP = 24f;
    private static final float LABEL_SP = 11f;
    private static final float RADIUS_DP = 16f;

    private final LinearLayout view;
    private final TextView label;
    private final Supplier<String> labelText;
    private final BooleanSupplier isOn;

    private PanelTile(LinearLayout view, TextView label, Supplier<String> labelText, BooleanSupplier isOn) {
        this.view = view;
        this.label = label;
        this.labelText = labelText;
        this.isOn = isOn;
    }

    /** A tile whose label never changes and has no on/off state. */
    static PanelTile plain(Context context, int icon, String text, Runnable onPress) {
        return withState(context, icon, () -> text, () -> false, onPress);
    }

    static PanelTile withState(Context context, int icon, Supplier<String> labelText, BooleanSupplier isOn, Runnable onPress) {
        LinearLayout tile = new LinearLayout(context);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setBackground(UiKit.toggleButton(context, RADIUS_DP));
        tile.setFocusable(true);
        tile.setClickable(true);
        UiKit.growOnFocus(tile);
        ImageView image = new ImageView(context);
        image.setImageResource(icon);
        int iconSize = UiKit.dp(context, ICON_DP);
        tile.addView(image, new LinearLayout.LayoutParams(iconSize, iconSize));
        TextView label = UiKit.text(context, "", LABEL_SP, 0xFFFFFFFF);
        label.setGravity(Gravity.CENTER_HORIZONTAL);
        label.setMaxLines(2);
        label.setPadding(0, UiKit.dp(context, 5f), 0, 0);
        tile.addView(label);
        PanelTile panelTile = new PanelTile(tile, label, labelText, isOn);
        tile.setOnClickListener(clicked -> {
            onPress.run();
            panelTile.refresh();
        });
        panelTile.refresh();
        return panelTile;
    }

    View view() {
        return view;
    }

    void refresh() {
        String text = labelText.get();
        label.setText(text);
        view.setContentDescription(text);
        view.setSelected(isOn.getAsBoolean());
    }

    /** Shows a passing message (e.g. "Freed 120 MB") until the next refresh. */
    void say(String message) {
        label.setText(message);
    }
}
