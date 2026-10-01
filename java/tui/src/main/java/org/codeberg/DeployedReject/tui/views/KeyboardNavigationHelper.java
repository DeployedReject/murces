package org.codeberg.DeployedReject.tui.views;

import com.googlecode.lanterna.gui2.Interactable;
import com.googlecode.lanterna.gui2.Label;
import org.codeberg.DeployedReject.tui.theme.GlyphHelper;
import org.codeberg.DeployedReject.tui.theme.MinecraftTheme;

public class KeyboardNavigationHelper {

    public static Label createTooltip() {
        Label tip = new Label(GlyphHelper.apply("󰋽 [TIP] 󰈚 [A] Activity Log │  [L] Server Console │ 󰓅 [J] Active Tasks │ 󰌒 [TAB] Cycle Focus │ 󰁯 [ESC] Back"));
        tip.setForegroundColor(MinecraftTheme.GOLD_YELLOW);
        return tip;
    }

    /**
     * Wraps an action so that triggering the shortcut also puts the target button/component into focus.
     * If the target component is disabled, shortcut execution is suppressed.
     */
    public static Runnable focus(Interactable target, Runnable action) {
        return () -> {
            if (target != null && !target.isEnabled()) {
                return;
            }
            if (target != null) {
                target.takeFocus();
            }
            if (action != null) {
                action.run();
            }
        };
    }
}
