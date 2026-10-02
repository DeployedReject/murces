package org.codeberg.DeployedReject.tui.views;

import com.googlecode.lanterna.gui2.Component;
import com.googlecode.lanterna.gui2.Interactable;

import java.util.Map;

public interface WorkspaceView {

    String getTitle();

    Component getComponent();

    Map<Character, Runnable> getHotkeys();

    Interactable getDefaultFocus();

    default void onActivated() {}

    default void onDeactivated() {}

    default void onResized(com.googlecode.lanterna.TerminalSize newSize) {}
}
