package org.codeberg.DeployedReject.tui.views;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.ActionListBox;
import com.googlecode.lanterna.gui2.Interactable;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;

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
        if (listener != null && getSelectedIndex() >= 0) {
            listener.onSelectionChanged(getSelectedIndex());
        }
    }

    private void notifySelection(int oldIdx, int newIdx) {
        if (oldIdx != newIdx && selectionListener != null) {
            selectionListener.onSelectionChanged(newIdx);
        }
    }

    @Override
    public synchronized MurcesListBox setSelectedIndex(int index) {
        if (getItemCount() == 0) {
            int old = getSelectedIndex();
            super.clearItems();
            notifySelection(old, -1);
            return this;
        }
        int clamped = Math.max(0, Math.min(index, getItemCount() - 1));
        int old = getSelectedIndex();
        super.setSelectedIndex(clamped);
        int cur = getSelectedIndex();
        notifySelection(old, cur);
        return this;
    }

    @Override
    public synchronized MurcesListBox clearItems() {
        int old = getSelectedIndex();
        super.clearItems();
        notifySelection(old, -1);
        return this;
    }

    @Override
    protected synchronized void afterEnterFocus(Interactable.FocusChangeDirection direction, Interactable previouslyFocusedElement) {
        int old = getSelectedIndex();
        super.afterEnterFocus(direction, previouslyFocusedElement);
        int cur = getSelectedIndex();
        notifySelection(old, cur);
    }

    @Override
    public synchronized Runnable getSelectedItem() {
        int idx = getSelectedIndex();
        if (idx < 0 || idx >= getItemCount()) {
            return null;
        }
        return super.getSelectedItem();
    }

    @Override
    public void runSelectedItem() {
        int idx = getSelectedIndex();
        if (idx < 0 || idx >= getItemCount()) {
            return;
        }
        super.runSelectedItem();
    }

    @Override
    public synchronized Runnable getItemAt(int index) {
        if (index < 0 || index >= getItemCount()) {
            return null;
        }
        return super.getItemAt(index);
    }

    @Override
    public synchronized Runnable removeItem(int index) {
        if (index < 0 || index >= getItemCount()) {
            return null;
        }
        return super.removeItem(index);
    }

    @Override
    public synchronized MurcesListBox addItem(String label, Runnable action) {
        super.addItem(label, action);
        return this;
    }

    @Override
    public synchronized Interactable.Result handleKeyStroke(KeyStroke keyStroke) {
        int old = getSelectedIndex();
        Interactable.Result res = internalHandleKeyStroke(keyStroke);
        int cur = getSelectedIndex();
        notifySelection(old, cur);
        return res;
    }

    private Interactable.Result internalHandleKeyStroke(KeyStroke keyStroke) {
        if (getItemCount() == 0) {
            if (keyStroke instanceof com.googlecode.lanterna.input.MouseAction) {
                return Interactable.Result.HANDLED;
            }
            if (isKeyboardActivationStroke(keyStroke)) {
                return Interactable.Result.HANDLED;
            }
        }

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

                return Interactable.Result.HANDLED;

            case ArrowUp:
                if (count > 0) {
                    int prev = Math.max(0, cur - 1);
                    if (prev != cur) {
                        setSelectedIndex(prev);
                    }
                }

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

                return Interactable.Result.HANDLED;

            case Tab:

                return Interactable.Result.MOVE_FOCUS_NEXT;

            case ReverseTab:

                return Interactable.Result.MOVE_FOCUS_PREVIOUS;

            default:
                if (count == 0) {
                    return Interactable.Result.HANDLED;
                }
                return super.handleKeyStroke(keyStroke);
        }
    }
}
