package org.codeberg.DeployedReject.tui.views;

import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.table.Table;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import org.codeberg.DeployedReject.tui.theme.MinecraftTheme;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class KeyboardNavigationHelper {

    public static Label createTooltip() {
        Label tip = new Label("[TIP] [A] Activity Log | [L] Server Console | [TAB] Cycle Focus | [ESC] Un-focus / Back");
        tip.setForegroundColor(MinecraftTheme.GOLD_YELLOW);
        return tip;
    }

    /**
     * Wraps an action so that triggering the shortcut also puts the target button/component into focus.
     */
    public static Runnable focus(Interactable target, Runnable action) {
        return () -> {
            if (target != null) {
                target.takeFocus();
            }
            if (action != null) {
                action.run();
            }
        };
    }

    public static Runnable focus(Interactable target) {
        return () -> {
            if (target != null) {
                target.takeFocus();
            }
        };
    }

    public static void attach(Window window, Map<Character, Runnable> hotkeys) {
        window.setEnableDirectionBasedMovements(false);
        window.addWindowListener(new WindowListenerAdapter() {
            @Override
            public void onInput(Window basePane, KeyStroke keyStroke, AtomicBoolean deliver) {
                // 0. CTRL+C CLEAN TERMINATION IN RAW MODE
                if (keyStroke.isCtrlDown() && (keyStroke.getCharacter() == 'c' || keyStroke.getCharacter() == 'C')) {
                    deliver.set(false);
                    System.exit(0);
                    return;
                }

                KeyType type = keyStroke.getKeyType();
                Interactable focused = basePane.getFocusedInteractable();
                boolean isEditableText = (focused instanceof TextBox) && !((TextBox) focused).isReadOnly();

                // 1. ESCAPE KEY: Unfocus any currently focused component
                if (type == KeyType.Escape) {
                    if (focused != null) {
                        basePane.setFocusedInteractable(null);
                        deliver.set(false);
                        return;
                    }
                }

                // 2. NO ARROW KEYS for button/option navigation.
                // Arrow keys are ONLY allowed if currently focused component is a list, table, or text box.
                if (type == KeyType.ArrowDown || type == KeyType.ArrowUp ||
                    type == KeyType.ArrowLeft || type == KeyType.ArrowRight) {

                    // If focus is on a ComboBox, let Arrow keys cycle items directly without jumping focus
                    if (focused instanceof ComboBox) {
                        ComboBox<?> cb = (ComboBox<?>) focused;
                        if (type == KeyType.ArrowDown) {
                            int next = (cb.getSelectedIndex() + 1) % cb.getItemCount();
                            cb.setSelectedIndex(next);
                            deliver.set(false);
                            return;
                        } else if (type == KeyType.ArrowUp) {
                            int prev = (cb.getSelectedIndex() - 1 + cb.getItemCount()) % cb.getItemCount();
                            cb.setSelectedIndex(prev);
                            deliver.set(false);
                            return;
                        }
                    }

                    boolean isListOrText = (focused instanceof ActionListBox) ||
                                           (focused instanceof Table) ||
                                           (focused instanceof TextBox);

                    if (!isListOrText) {
                        // Suppress arrow keys on buttons and other components!
                        deliver.set(false);
                        return;
                    }
                }

                // 3. CAPITALIZED / SPECIAL HOTKEY SELECTION
                if (hotkeys != null) {
                    // Do not trigger hotkeys if user is actively typing in an editable TextBox
                    if (!isEditableText) {
                        Character c = null;
                        if (type == KeyType.Character && keyStroke.getCharacter() != null) {
                            c = Character.toUpperCase(keyStroke.getCharacter());
                        }
                        if (c != null && hotkeys.containsKey(c)) {
                            deliver.set(false);
                            hotkeys.get(c).run();
                            return;
                        }
                    }
                }
            }

            @Override
            public void onUnhandledInput(Window basePane, KeyStroke keyStroke, AtomicBoolean hasBeenHandled) {
                if (keyStroke.getKeyType() == KeyType.Escape) {
                    basePane.close();
                    hasBeenHandled.set(true);
                }
            }
        });
    }
}
