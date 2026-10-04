package org.codeberg.DeployedReject.tui.views;

import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TerminalTextUtils;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class WideDropDownHelper {

    public static void showWideDropDown(WindowBasedTextGUI gui, ComboBox<String> comboBox, int maxRows, Consumer<Integer> onSelected) {
        if (gui == null || comboBox == null || comboBox.getItemCount() == 0) {
            return;
        }

        int maxItemWidth = 10;
        for (int i = 0; i < comboBox.getItemCount(); i++) {
            String item = comboBox.getItem(i);
            if (item != null) {
                maxItemWidth = Math.max(maxItemWidth, TerminalTextUtils.getColumnWidth(item));
            }
        }

        TerminalSize termSize = gui.getScreen() != null ? gui.getScreen().getTerminalSize() : new TerminalSize(80, 24);
        int termCols = Math.max(40, termSize.getColumns());
        int termRows = Math.max(15, termSize.getRows());

        int comboWidth = comboBox.getSize() != null ? comboBox.getSize().getColumns() : 20;
        int popupWidth = Math.min(termCols - 4, Math.max(comboWidth + 2, maxItemWidth + 4));
        int popupRows = Math.min(maxRows, Math.max(1, comboBox.getItemCount()));

        TerminalPosition globalPos;
        try {
            globalPos = comboBox.toGlobal(new TerminalPosition(0, 1));
        } catch (Exception e) {
            globalPos = new TerminalPosition(2, 4);
        }

        int col = globalPos.getColumn();
        if (col + popupWidth > termCols - 1) {
            col = Math.max(1, termCols - popupWidth - 1);
        }

        int row = globalPos.getRow();
        if (row + popupRows + 1 > termRows) {
            row = Math.max(1, row - popupRows - 2);
        }

        BasicWindow window = new BasicWindow();
        window.setHints(Arrays.asList(Window.Hint.NO_FOCUS, Window.Hint.FIXED_POSITION, Window.Hint.MENU_POPUP));
        window.setPosition(new TerminalPosition(col, row));

        ActionListBox listBox = new ActionListBox(new TerminalSize(popupWidth, popupRows));
        for (int i = 0; i < comboBox.getItemCount(); i++) {
            final int idx = i;
            String text = comboBox.getItem(i);
            listBox.addItem(text != null ? text : "", () -> {
                comboBox.setSelectedIndex(idx);
                window.close();
                if (onSelected != null) {
                    onSelected.accept(idx);
                }
            });
        }

        int curSel = comboBox.getSelectedIndex();
        if (curSel >= 0 && curSel < comboBox.getItemCount()) {
            listBox.setSelectedIndex(curSel);
        }

        window.addWindowListener(new WindowListenerAdapter() {
            @Override
            public void onUnhandledInput(Window basePane, KeyStroke keyStroke, AtomicBoolean deliver) {
                if (keyStroke.getKeyType() == KeyType.Escape) {
                    deliver.set(false);
                    window.close();
                }
            }
        });

        window.setComponent(listBox);
        gui.addWindow(window);
        gui.setActiveWindow(window);
        listBox.takeFocus();
    }
}
