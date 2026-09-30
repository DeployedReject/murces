package org.codeberg.DeployedReject.tui.views;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.ActionListBox;
import com.googlecode.lanterna.gui2.Interactable;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;

/**
 * A robust ActionListBox that strictly clamps arrow key navigation within list bounds.
 * Prevents escaping out of bounds, focus jumping, or TUI glitches when arrow keys
 * are held down continuously.
 */
public class MurcesListBox extends ActionListBox {

    public interface SelectionListener {
        void onSelectionChanged(int newIndex);
    }

    private SelectionListener selectionListener;

    public MurcesListBox() {
        super();
    }

    public MurcesListBox(TerminalSize size) {
        super(size);
    }

    public void setSelectionListener(SelectionListener listener) {
        this.selectionListener = listener;
    }

    @Override
    public synchronized MurcesListBox setSelectedIndex(int index) {
        int old = getSelectedIndex();
        super.setSelectedIndex(index);
        int cur = getSelectedIndex();
        if (old != cur && selectionListener != null) {
            selectionListener.onSelectionChanged(cur);
        }
        return this;
    }

    @Override
    public synchronized MurcesListBox addItem(String label, Runnable action) {
        super.addItem(label, action);
        return this;
    }

    @Override
    public synchronized Interactable.Result handleKeyStroke(KeyStroke keyStroke) {
        if (isKeyboardActivationStroke(keyStroke)) {
            runSelectedItem();
            return Interactable.Result.HANDLED;
        }

        KeyType type = keyStroke.getKeyType();
        int count = getItemCount();
        int cur = getSelectedIndex();

        switch (type) {
            case ArrowDown:
                if (count > 0) {
                    int next = Math.min(count - 1, cur + 1);
                    if (next != cur) {
                        setSelectedIndex(next);
                    }
                }
                // ALWAYS return HANDLED - never leak MOVE_FOCUS_DOWN to prevent escaping/glitching!
                return Interactable.Result.HANDLED;

            case ArrowUp:
                if (count > 0) {
                    int prev = Math.max(0, cur - 1);
                    if (prev != cur) {
                        setSelectedIndex(prev);
                    }
                }
                // ALWAYS return HANDLED - never leak MOVE_FOCUS_UP to prevent escaping/glitching!
                return Interactable.Result.HANDLED;

            case PageDown:
                if (count > 0) {
                    int pageSize = Math.max(1, (getPreferredSize() != null ? getPreferredSize().getRows() : 8) - 2);
                    int next = Math.min(count - 1, cur + pageSize);
                    setSelectedIndex(next);
                }
                return Interactable.Result.HANDLED;

            case PageUp:
                if (count > 0) {
                    int pageSize = Math.max(1, (getPreferredSize() != null ? getPreferredSize().getRows() : 8) - 2);
                    int prev = Math.max(0, cur - pageSize);
                    setSelectedIndex(prev);
                }
                return Interactable.Result.HANDLED;

            case Home:
                if (count > 0) {
                    setSelectedIndex(0);
                }
                return Interactable.Result.HANDLED;

            case End:
                if (count > 0) {
                    setSelectedIndex(count - 1);
                }
                return Interactable.Result.HANDLED;

            case ArrowLeft:
            case ArrowRight:
                // Strictly stay inside the list; do NOT jump focus left or right
                return Interactable.Result.HANDLED;

            case Tab:
                // User can deliberately Tab to the next control
                return Interactable.Result.MOVE_FOCUS_NEXT;

            case ReverseTab:
                // User can deliberately Shift-Tab to the previous control
                return Interactable.Result.MOVE_FOCUS_PREVIOUS;

            default:
                return super.handleKeyStroke(keyStroke);
        }
    }
}
