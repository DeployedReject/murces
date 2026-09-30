package org.codeberg.DeployedReject.tui.views;

import com.googlecode.lanterna.gui2.Component;
import com.googlecode.lanterna.gui2.Interactable;

import java.util.Map;

/**
 * Represents a view component that can be hosted inside the dashboard's center workspace pane.
 */
public interface WorkspaceView {

    /**
     * Title displayed in the workspace border header.
     */
    String getTitle();

    /**
     * Root component for this workspace view.
     */
    Component getComponent();

    /**
     * Active hotkeys for this view.
     */
    Map<Character, Runnable> getHotkeys();

    /**
     * Interactable component to receive focus upon view activation.
     */
    Interactable getDefaultFocus();

    /**
     * Called when this view becomes active in the workspace.
     */
    default void onActivated() {}

    /**
     * Called when this view is replaced by another view.
     */
    default void onDeactivated() {}
}
