package org.codeberg.DeployedReject.tui.views;

import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.table.Table;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.input.MouseAction;
import com.googlecode.lanterna.input.MouseActionType;
import org.codeberg.DeployedReject.tui.backend.OrchestratorBridge;
import org.codeberg.DeployedReject.tui.config.ConfigManager;
import org.codeberg.DeployedReject.tui.config.TuiConfig;
import org.codeberg.DeployedReject.tui.theme.LazyVimTheme;
import org.codeberg.DeployedReject.tui.theme.MinecraftTheme;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Unified 3-Pane Dashboard Window.
 * Automatically expands to fill the entire terminal window in full-screen mode without dead space.
 * Panes:
 *  1. Center Workspace / Menu (Left/Center, fills remaining area)
 *  2. Activity & Diagnostics Window (Right/Side, color-coded, auto-wrapping, scrollable [A])
 *  3. Server Console Window (Bottom, full-width, auto-wrapping, scrollable [L])
 */
public class MainWindow extends BasicWindow {

    private final Panel root;
    private final Panel tooSmallPanel;
    private final Label tooSmallSizeLabel;
    private final Border tooSmallBordered;

    private final WindowBasedTextGUI gui;
    private final Panel workspaceContainer;
    private final Label workspaceTitleLabel;
    private final Border workspaceBordered;
    private final Border activityBordered;
    private final Border consoleBordered;
    private final ColoredLogView activityLogView;
    private final ColoredLogView consoleLogView;

    private final MainMenuView mainMenuView;
    private final ServerControlView serverControlView;
    private final InstallServerView installServerView;
    private final ConfigServerView configServerView;
    private final BackupView backupView;
    private final MigratePlayerView migratePlayerView;
    private final ModBrowseView modBrowseView;
    private final ModManageView modManageView;
    private final CustomizationView customizationView;

    private WorkspaceView currentView;
    private String lastConsoleOutput = "";
    private final ScheduledExecutorService poller = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ConsolePoller");
        t.setDaemon(true);
        return t;
    });

    public MainWindow(WindowBasedTextGUI gui) {
        super("Murces - Minecraft Server Manager");
        this.gui = gui;
        setHints(Arrays.asList(Hint.FULL_SCREEN, Hint.FIT_TERMINAL_WINDOW));

        // Too small warning panel overlay
        tooSmallPanel = new Panel(new LinearLayout(Direction.VERTICAL));
        Label warnTitle = new Label("⚠️  TERMINAL WINDOW TOO SMALL");
        warnTitle.setForegroundColor(LazyVimTheme.getErrorColor());
        tooSmallPanel.addComponent(warnTitle);
        tooSmallSizeLabel = new Label("Current: 0x0 | Required: >= 70x18");
        tooSmallSizeLabel.setForegroundColor(LazyVimTheme.getWarningColor());
        tooSmallPanel.addComponent(tooSmallSizeLabel);
        tooSmallPanel.addComponent(new Label("Please enlarge or maximize your terminal window."));
        tooSmallBordered = tooSmallPanel.withBorder(Borders.doubleLine("Display Warning"));

        this.root = new Panel(new BorderLayout());

        // 1. Top Header Tooltip (Location.TOP)
        Label tip = KeyboardNavigationHelper.createTooltip();
        tip.setLayoutData(BorderLayout.Location.TOP);
        root.addComponent(tip);

        // 2. Middle Row: Left/Center Workspace + Right Activity Window (Location.CENTER)
        Panel midPanel = new Panel(new BorderLayout());
        midPanel.setLayoutData(BorderLayout.Location.CENTER);

        // Center Workspace Pane
        Panel workspaceOuter = new Panel(new LinearLayout(Direction.VERTICAL));
        workspaceTitleLabel = new Label("=== Workspace: Main Menu ===");
        workspaceTitleLabel.setForegroundColor(MinecraftTheme.GOLD_YELLOW);
        workspaceOuter.addComponent(workspaceTitleLabel);

        workspaceContainer = new Panel(new LinearLayout(Direction.VERTICAL));
        workspaceContainer.setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.Fill, LinearLayout.GrowPolicy.CanGrow));
        workspaceOuter.addComponent(workspaceContainer);

        workspaceBordered = workspaceOuter.withBorder(Borders.singleLine("Workspace / Menu"));
        workspaceBordered.setLayoutData(BorderLayout.Location.CENTER);
        midPanel.addComponent(workspaceBordered);

        activityLogView = new ColoredLogView(false);
        activityBordered = activityLogView.withBorder(Borders.singleLine("Activity & Diagnostics [A]"));
        activityBordered.setLayoutData(BorderLayout.Location.RIGHT);
        midPanel.addComponent(activityBordered);

        root.addComponent(midPanel);

        // 3. Bottom Pane: Server Console (Location.BOTTOM)
        consoleLogView = new ColoredLogView(true);
        consoleLogView.setContent("[Server not started - Start server from Server Control [S] to view live output]");
        consoleBordered = consoleLogView.withBorder(Borders.singleLine("Server Console [L]"));
        consoleBordered.setLayoutData(BorderLayout.Location.BOTTOM);
        root.addComponent(consoleBordered);

        // Real-time live screen refresh callbacks for continuous log streaming
        activityLogView.setOnUpdate(() -> {
            activityBordered.invalidate();
            invalidate();
            try {
                gui.updateScreen();
            } catch (Exception ignored) {}
        });

        consoleLogView.setOnUpdate(() -> {
            consoleBordered.invalidate();
            invalidate();
            try {
                gui.updateScreen();
            } catch (Exception ignored) {}
        });

        setComponent(root);

        // Initial dynamic dimensions
        TerminalSize initialSize = gui.getScreen() != null ? gui.getScreen().getTerminalSize() : new TerminalSize(80, 24);
        updateLayoutDimensions(initialSize);

        // Instantiate workspace views
        this.mainMenuView = new MainMenuView(this);
        this.serverControlView = new ServerControlView(this);
        this.installServerView = new InstallServerView(this);
        this.configServerView = new ConfigServerView(this);
        this.backupView = new BackupView(this);
        this.migratePlayerView = new MigratePlayerView(this);
        this.modBrowseView = new ModBrowseView(this);
        this.modManageView = new ModManageView(this);
        this.customizationView = new CustomizationView(this);

        // Apply persistent theme and configuration
        applyConfig(ConfigManager.getInstance().getConfig());

        // Connect Activity Logger
        ActivityLogger.addListener(msg -> {
            gui.getGUIThread().invokeLater(() -> activityLogView.addLine(msg));
        });

        // Setup Console Poller (every 1 second)
        poller.scheduleWithFixedDelay(this::pollServerConsole, 500, 1000, TimeUnit.MILLISECONDS);

        // Setup Window Listener
        setupInputHandling();

        // Show Main Menu View by default
        showView(mainMenuView);
    }

    public void updateLayoutDimensions(TerminalSize termSize) {
        if (termSize == null) return;
        int cols = termSize.getColumns();
        int rows = termSize.getRows();

        boolean enforceMin = ConfigManager.getInstance().getConfig().isEnforceMinSize();
        if (enforceMin && (cols < 70 || rows < 18)) {
            tooSmallSizeLabel.setText(String.format("Current: %dx%d | Minimum Required: 70x18", cols, rows));
            if (getComponent() != tooSmallBordered) {
                setComponent(tooSmallBordered);
            }
            invalidate();
            return;
        } else {
            if (getComponent() != root) {
                setComponent(root);
            }
        }

        // Dynamic UI scaling on full screen
        // Console height: ~25% of rows (min 5, max 16 on full screen)
        int consHeight = Math.max(5, Math.min(16, rows / 4));
        consoleBordered.setPreferredSize(new TerminalSize(cols, consHeight));

        // Activity width: ~35% of cols (min 28, max 65 on full screen)
        int actWidth = Math.max(28, Math.min(65, (cols * 35) / 100));
        activityBordered.setPreferredSize(new TerminalSize(actWidth, Math.max(10, rows - consHeight - 4)));

        if (currentView != null) {
            currentView.onResized(termSize);
        }

        invalidate();
    }

    public WindowBasedTextGUI getGui() {
        return gui;
    }

    public void showView(WorkspaceView view) {
        if (view == null) return;
        if (currentView != null) {
            currentView.onDeactivated();
        }
        currentView = view;
        workspaceTitleLabel.setText("=== Workspace: " + view.getTitle() + " ===");
        workspaceContainer.removeAllComponents();
        workspaceContainer.addComponent(view.getComponent());

        view.onActivated();

        Interactable defFocus = view.getDefaultFocus();
        if (defFocus != null) {
            defFocus.takeFocus();
        }
    }

    public void showMainMenu() { showView(mainMenuView); }
    public void showServerControl() { showView(serverControlView); }
    public void showInstallServer() { showView(installServerView); }
    public void showConfigServer() { showView(configServerView); }
    public void showBackup() { showView(backupView); }
    public void showMigratePlayer() { showView(migratePlayerView); }
    public void showModBrowse() { showView(modBrowseView); }
    public void showModManage() { showView(modManageView); }
    public void showCustomization() { showView(customizationView); }

    public MainMenuView getMainMenuView() { return mainMenuView; }
    public ServerControlView getServerControlView() { return serverControlView; }
    public InstallServerView getInstallServerView() { return installServerView; }
    public ConfigServerView getConfigServerView() { return configServerView; }
    public BackupView getBackupView() { return backupView; }
    public MigratePlayerView getMigratePlayerView() { return migratePlayerView; }
    public ModBrowseView getModBrowseView() { return modBrowseView; }
    public ModManageView getModManageView() { return modManageView; }
    public CustomizationView getCustomizationView() { return customizationView; }

    public void applyConfig(TuiConfig config) {
        if (config == null) return;
        com.googlecode.lanterna.graphics.Theme theme = LazyVimTheme.createTheme(
                config.getTheme(),
                config.getTransparencyPercent(),
                config.isTrueColor()
        );
        gui.setTheme(theme);
        setTheme(theme);
        workspaceTitleLabel.setForegroundColor(LazyVimTheme.getAccentColor());
        activityBordered.invalidate();
        consoleBordered.invalidate();
        workspaceBordered.invalidate();
        activityLogView.invalidate();
        consoleLogView.invalidate();
        invalidate();
        try {
            if (gui.getScreen() != null) {
                gui.getScreen().clear();
            }
        } catch (Exception ignored) {}
        TerminalSize size = gui.getScreen() != null ? gui.getScreen().getTerminalSize() : new TerminalSize(80, 24);
        updateLayoutDimensions(size);
        try {
            gui.updateScreen();
        } catch (Exception ignored) {}
    }

    public void exit() {
        poller.shutdownNow();
        close();
    }

    private void pollServerConsole() {
        try {
            String output = OrchestratorBridge.getLiveConsoleOutput(12);
            if (!Objects.equals(output, lastConsoleOutput)) {
                lastConsoleOutput = output;
                gui.getGUIThread().invokeLater(() -> consoleLogView.setContent(output));
            }
        } catch (Exception ignored) {}
    }

    private boolean isInside(Component comp, TerminalPosition pos) {
        if (comp == null || pos == null) return false;
        TerminalPosition origin;
        try {
            origin = comp.toGlobal(TerminalPosition.TOP_LEFT_CORNER);
        } catch (Exception e) {
            return false;
        }
        TerminalSize size = comp.getSize();
        if (origin == null || size == null) return false;
        return pos.getColumn() >= origin.getColumn() &&
               pos.getColumn() < origin.getColumn() + size.getColumns() &&
               pos.getRow() >= origin.getRow() &&
               pos.getRow() < origin.getRow() + size.getRows();
    }

    private void setupInputHandling() {
        setEnableDirectionBasedMovements(false);
        addWindowListener(new WindowListenerAdapter() {
            @Override
            public void onInput(Window basePane, KeyStroke keyStroke, AtomicBoolean deliver) {
                // 0. CTRL+C CLEAN TERMINATION
                if (keyStroke.isCtrlDown() && (keyStroke.getCharacter() == 'c' || keyStroke.getCharacter() == 'C')) {
                    deliver.set(false);
                    poller.shutdownNow();
                    System.exit(0);
                    return;
                }

                // 0a. MOUSE WHEEL SCROLLING & CLICK FOCUS FOR LOG PANES
                if (keyStroke instanceof MouseAction) {
                    MouseAction ma = (MouseAction) keyStroke;
                    TerminalPosition pos = ma.getPosition();
                    if (pos != null) {
                        if (ma.getActionType() == MouseActionType.SCROLL_UP || ma.getActionType() == MouseActionType.SCROLL_DOWN) {
                            if (isInside(activityBordered, pos) || isInside(activityLogView, pos)) {
                                if (ma.getActionType() == MouseActionType.SCROLL_UP) activityLogView.scrollUp(3);
                                else activityLogView.scrollDown(3);
                                deliver.set(false);
                                return;
                            } else if (isInside(consoleBordered, pos) || isInside(consoleLogView, pos)) {
                                if (ma.getActionType() == MouseActionType.SCROLL_UP) consoleLogView.scrollUp(3);
                                else consoleLogView.scrollDown(3);
                                deliver.set(false);
                                return;
                            }
                        } else if (ma.getActionType() == MouseActionType.CLICK_DOWN) {
                            if (isInside(activityBordered, pos) || isInside(activityLogView, pos)) {
                                activityLogView.takeFocus();
                                deliver.set(false);
                                return;
                            } else if (isInside(consoleBordered, pos) || isInside(consoleLogView, pos)) {
                                consoleLogView.takeFocus();
                                deliver.set(false);
                                return;
                            }
                        }
                    }
                }

                KeyType type = keyStroke.getKeyType();
                Interactable focused = basePane.getFocusedInteractable();
                boolean isEditableText = (focused instanceof TextBox) && !((TextBox) focused).isReadOnly();

                // 1. ESCAPE KEY: Universal Un-focus / Navigation
                if (type == KeyType.Escape) {
                    if (focused != null) {
                        basePane.setFocusedInteractable(null);
                        deliver.set(false);
                        return;
                    } else if (currentView != mainMenuView) {
                        deliver.set(false);
                        showMainMenu();
                        return;
                    }
                }

                // 2. SUPPRESS ARROWS ON BUTTONS (Allow on lists, tables, textboxes, and scrollable log views)
                if (type == KeyType.ArrowDown || type == KeyType.ArrowUp ||
                    type == KeyType.ArrowLeft || type == KeyType.ArrowRight) {
                    if (focused instanceof ComboBox) {
                        ComboBox<?> cb = (ComboBox<?>) focused;
                        if (cb.getItemCount() > 0) {
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
                    }

                    boolean isScrollableOrText = (focused instanceof ActionListBox) ||
                                                 (focused instanceof Table) ||
                                                 (focused instanceof TextBox) ||
                                                 (focused instanceof ColoredLogView);
                    if (!isScrollableOrText) {
                        deliver.set(false);
                        return;
                    }
                }

                // 3. HOTKEYS DISPATCH
                if (!isEditableText) {
                    Character c = null;
                    if (type == KeyType.Character && keyStroke.getCharacter() != null) {
                        c = Character.toUpperCase(keyStroke.getCharacter());
                    }
                    if (c != null) {
                        // Global focus shortcuts: [A] Activity Log, [L] Server Console
                        if (c == 'A') {
                            deliver.set(false);
                            activityLogView.takeFocus();
                            return;
                        } else if (c == 'L' && (currentView == null || currentView.getHotkeys() == null || !currentView.getHotkeys().containsKey('L'))) {
                            deliver.set(false);
                            consoleLogView.takeFocus();
                            return;
                        }

                        // Global subview Back action
                        if (c == 'B' && currentView != mainMenuView) {
                            deliver.set(false);
                            showMainMenu();
                            return;
                        }

                        // Global customization shortcut [Z]
                        if (c == 'Z' && currentView != customizationView) {
                            deliver.set(false);
                            showCustomization();
                            return;
                        }

                        // Check current view hotkeys
                        if (currentView != null && currentView.getHotkeys() != null) {
                            Map<Character, Runnable> hotkeys = currentView.getHotkeys();
                            if (hotkeys.containsKey(c)) {
                                deliver.set(false);
                                hotkeys.get(c).run();
                                return;
                            }
                        }
                    }
                }
            }
        });
    }
}
