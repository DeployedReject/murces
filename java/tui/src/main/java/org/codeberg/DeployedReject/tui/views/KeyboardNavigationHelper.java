package org.codeberg.DeployedReject.tui.views;

import com.googlecode.lanterna.gui2.Button;
import com.googlecode.lanterna.gui2.Interactable;
import com.googlecode.lanterna.gui2.Label;
import org.codeberg.DeployedReject.tui.theme.GlyphHelper;
import org.codeberg.DeployedReject.tui.theme.MinecraftTheme;

public class KeyboardNavigationHelper {

    public static Label createTooltip() {
        Label tip = new Label(GlyphHelper.apply(GlyphHelper.ICON_INFO + " [TIP] " + GlyphHelper.ICON_FILE + " [A] Activity Log │ " + GlyphHelper.ICON_TERMINAL + " [L] Server Console │ " + GlyphHelper.ICON_TASKS + " [J] Active Tasks │ " + GlyphHelper.ICON_OPTIONS + " [TAB/ENTER] Navigate │ " + GlyphHelper.ICON_BACK + " [ESC] Back"));
        tip.setForegroundColor(MinecraftTheme.GOLD_YELLOW);
        return tip;
    }

    /**
     * Executes an action on an interactable (e.g. Button) without shifting focus to it.
     * If the target is disabled, the action is ignored.
     */
    public static Runnable action(Interactable target, Runnable action) {
        return () -> {
            if (target != null && !target.isEnabled()) {
                return;
            }
            if (action != null) {
                action.run();
            }
        };
    }

    /**
     * Executes an action and shifts focus to the target.
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

