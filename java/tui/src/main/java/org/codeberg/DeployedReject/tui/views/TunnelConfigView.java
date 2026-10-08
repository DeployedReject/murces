package org.codeberg.DeployedReject.tui.views;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import org.codeberg.DeployedReject.tui.backend.OrchestratorBridge;
import org.codeberg.DeployedReject.tui.backend.ServerPropertiesManager;
import org.codeberg.DeployedReject.tui.config.ConfigManager;
import org.codeberg.DeployedReject.tui.config.TuiConfig;
import org.codeberg.DeployedReject.tui.theme.GlyphHelper;
import org.codeberg.DeployedReject.tui.theme.MinecraftTheme;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TunnelConfigView implements WorkspaceView {

    private final MainWindow mainWindow;
    private final Panel root;
    private final Label statusIndicatorLabel;
    private final Label daemonIndicatorLabel;
    private final Label claimCodeLabel;
    private final TextBox claimUrlBox;
    private final MurcesListBox routeListBox;
    private final Button claimBtn;
    private final Button daemonBtn;
    private final Button refreshBtn;
    private final Button syncPortBtn;
    private final Button resetBtn;
    private final Button backBtn;
    private final Label detailLabel;
    private final Map<Character, Runnable> hotkeys = new HashMap<>();

    public TunnelConfigView(MainWindow mainWindow) {
        this.mainWindow = mainWindow;
        this.root = new Panel(new LinearLayout(Direction.VERTICAL));

        Panel statusPanel = new Panel(new LinearLayout(Direction.VERTICAL));
        Panel row1 = new Panel(new LinearLayout(Direction.HORIZONTAL));
        row1.addComponent(new Label(GlyphHelper.apply(GlyphHelper.ICON_TUNNEL + " Playit Agent:  ")));
        statusIndicatorLabel = new Label("CHECKING...");
        row1.addComponent(statusIndicatorLabel);
        statusPanel.addComponent(row1);

        Panel rowDaemon = new Panel(new LinearLayout(Direction.HORIZONTAL));
        rowDaemon.addComponent(new Label(GlyphHelper.apply(GlyphHelper.ICON_TASKS + " Tunnel Daemon: ")));
        daemonIndicatorLabel = new Label("CHECKING...");
        rowDaemon.addComponent(daemonIndicatorLabel);
        statusPanel.addComponent(rowDaemon);

        Panel row2 = new Panel(new LinearLayout(Direction.HORIZONTAL));
        row2.addComponent(new Label(GlyphHelper.apply(GlyphHelper.ICON_OPTIONS + " [K] Claim Code: ")));
        claimCodeLabel = new Label("(press [C]laim to generate)");
        claimCodeLabel.setForegroundColor(MinecraftTheme.GOLD_YELLOW);
        row2.addComponent(claimCodeLabel);
        statusPanel.addComponent(row2);

        Panel row3 = new Panel(new LinearLayout(Direction.HORIZONTAL));
        row3.addComponent(new Label(GlyphHelper.apply(GlyphHelper.ICON_OPTIONS + " [U]rl Link:   ")));
        claimUrlBox = new TextBox(new TerminalSize(38, 1));
        row3.addComponent(claimUrlBox);
        statusPanel.addComponent(row3);

        root.addComponent(statusPanel.withBorder(Borders.singleLine(GlyphHelper.apply(GlyphHelper.ICON_TUNNEL + " Playit.gg Connection State"))));

        routeListBox = new MurcesListBox(new TerminalSize(48, 6));
        root.addComponent(routeListBox.withBorder(Borders.singleLine(GlyphHelper.apply(GlyphHelper.ICON_CONFIG + " Active Tunnel Routes [L]"))));

        detailLabel = new Label("Ready.");
        detailLabel.setForegroundColor(MinecraftTheme.GOLD_YELLOW);
        root.addComponent(detailLabel);

        Panel actionPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
        claimBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_PLAY + " [C]laim"), this::onClaim);
        daemonBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_CHECK + " [T]oggle Daemon"), this::onToggleDaemon);
        refreshBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_RESTART + " [R]efresh"), this::onRefresh);
        syncPortBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_CHECK + " [S]ync Port"), this::onSyncPort);
        resetBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_BACK + " Reset [X]"), this::onReset);
        actionPanel.addComponent(claimBtn);
        actionPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        actionPanel.addComponent(daemonBtn);
        actionPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        actionPanel.addComponent(refreshBtn);
        actionPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        actionPanel.addComponent(syncPortBtn);
        actionPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        actionPanel.addComponent(resetBtn);
        root.addComponent(actionPanel.withBorder(Borders.singleLine(GlyphHelper.apply(GlyphHelper.ICON_TASKS + " Tunnel Actions"))));

        backBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_BACK + " [B]ack to Main Menu"), mainWindow::showMainMenu);
        root.addComponent(backBtn);

        hotkeys.put('C', KeyboardNavigationHelper.action(claimBtn, this::onClaim));
        hotkeys.put('T', KeyboardNavigationHelper.action(daemonBtn, this::onToggleDaemon));
        hotkeys.put('R', KeyboardNavigationHelper.action(refreshBtn, this::onRefresh));
        hotkeys.put('S', KeyboardNavigationHelper.action(syncPortBtn, this::onSyncPort));
        hotkeys.put('X', KeyboardNavigationHelper.action(resetBtn, this::onReset));
        hotkeys.put('B', KeyboardNavigationHelper.action(backBtn, mainWindow::showMainMenu));
        hotkeys.put('L', routeListBox::takeFocus);
        hotkeys.put('U', claimUrlBox::takeFocus);

        OrchestratorBridge.getInstance().addListener(json -> {
            if (json.has("type") && "tunnel".equals(json.get("type").getAsString())) {
                mainWindow.getGui().getGUIThread().invokeLater(this::updateViewFromDisk);
                if (json.has("url")) {
                    String url = json.get("url").getAsString();
                    String code = json.has("code") ? json.get("code").getAsString() : "";
                    mainWindow.getGui().getGUIThread().invokeLater(() -> {
                        claimUrlBox.setText(url);
                        if (!code.isEmpty()) {
                            claimCodeLabel.setText(code);
                            detailLabel.setText("Match code: " + code + " on playit.gg, then click Add Agent.");
                        } else {
                            detailLabel.setText("Claim URL generated! Open in browser to link.");
                        }
                    });
                }
            }
        });
    }

    @Override
    public String getTitle() {
        return GlyphHelper.apply(GlyphHelper.ICON_TUNNEL + " Playit.gg Tunnel");
    }

    @Override
    public Component getComponent() {
        return root;
    }

    @Override
    public Map<Character, Runnable> getHotkeys() {
        return hotkeys;
    }

    @Override
    public Interactable getDefaultFocus() {
        return routeListBox.getItemCount() > 0 ? routeListBox : (claimBtn.isEnabled() ? claimBtn : refreshBtn);
    }

    @Override
    public void onResized(TerminalSize newSize) {
        if (newSize == null) return;
        int cols = newSize.getColumns();
        int rows = newSize.getRows();
        int actWidth = Math.max(28, Math.min(65, (cols * 35) / 100));
        int wsWidth = Math.max(44, cols - actWidth - 6);

        int listWidth = Math.max(40, wsWidth - 4);
        int listHeight = Math.max(5, Math.min(8, rows - 19));
        routeListBox.setPreferredSize(new TerminalSize(listWidth, listHeight));
        claimUrlBox.setPreferredSize(new TerminalSize(Math.max(20, listWidth - 18), 1));
    }

    @Override
    public void onActivated() {
        updateViewFromDisk();
        ActivityLogger.info("=== Playit.gg Tunnel Guide ===");
        ActivityLogger.log("[PLAYIT] 1. [C] Claim Agent: Generates claim URL. Open in browser to link.");
        ActivityLogger.log("[PLAYIT] 2. [T] Toggle Daemon: Starts or stops the background tunnel runner (playitd).");
        ActivityLogger.log("[PLAYIT] 3. [R] Refresh Routes: Fetches active tunnel routes and domains.");
        ActivityLogger.log("[PLAYIT] 4. [S] Sync Port: Sets server.properties port to match tunnel port.");
        ActivityLogger.log("[PLAYIT] 5. [X] Reset: Clears cached agent credentials (playitagent.txt).");
    }

    private void updateViewFromDisk() {
        boolean linked = OrchestratorBridge.isTunnelLinked();
        boolean running = OrchestratorBridge.isTunnelDaemonRunning();
        List<OrchestratorBridge.TunnelInfo> tunnels = OrchestratorBridge.getStoredTunnels();

        if (linked) {
            statusIndicatorLabel.setText(GlyphHelper.apply("● [LINKED] Registered (playitagent.txt)"));
            statusIndicatorLabel.setForegroundColor(MinecraftTheme.CREEPER_GREEN);
            claimBtn.setEnabled(false);
            daemonBtn.setEnabled(true);
            syncPortBtn.setEnabled(!tunnels.isEmpty());
        } else {
            statusIndicatorLabel.setText(GlyphHelper.apply("○ [NOT LINKED] Ready for setup"));
            statusIndicatorLabel.setForegroundColor(MinecraftTheme.REDSTONE_RED);
            claimBtn.setEnabled(true);
            daemonBtn.setEnabled(false);
            syncPortBtn.setEnabled(false);
        }

        if (running) {
            daemonIndicatorLabel.setText(GlyphHelper.apply("● [ONLINE (tmux: playit)] Forwarding Active"));
            daemonIndicatorLabel.setForegroundColor(MinecraftTheme.CREEPER_GREEN);
        } else {
            daemonIndicatorLabel.setText(GlyphHelper.apply("○ [OFFLINE] Stopped"));
            daemonIndicatorLabel.setForegroundColor(MinecraftTheme.REDSTONE_RED);
        }

        routeListBox.clearItems();
        if (tunnels.isEmpty()) {
            if (linked) {
                routeListBox.addItem("No active tunnels retrieved. Press [R] to query routes.", () -> {});
            } else {
                routeListBox.addItem("Agent unlinked. Press [C] to start claiming.", () -> {});
            }
        } else {
            for (OrchestratorBridge.TunnelInfo t : tunnels) {
                String entry = String.format("• [%s] %s -> localhost:%d", t.proto.toUpperCase(), t.publicAddress.isEmpty() ? "(assigning...)" : t.publicAddress, t.localPort);
                routeListBox.addItem(entry, () -> {
                    detailLabel.setText("Selected route: " + t.publicAddress + " (port " + t.localPort + ")");
                });
            }
        }
    }

    private void onClaim() {
        detailLabel.setText("Initiating claim handshake...");
        ActivityLogger.info("Initiating Playit agent setup claim...");
        new Thread(OrchestratorBridge::setupTunnel).start();
    }

    private void onToggleDaemon() {
        boolean running = OrchestratorBridge.isTunnelDaemonRunning();
        if (running) {
            detailLabel.setText("Stopping Playit tunnel daemon...");
            ActivityLogger.info("Stopping Playit tunnel daemon...");
            new Thread(() -> {
                OrchestratorBridge.stopTunnelDaemon();
                mainWindow.getGui().getGUIThread().invokeLater(this::updateViewFromDisk);
            }).start();
        } else {
            detailLabel.setText("Starting Playit tunnel daemon...");
            ActivityLogger.info("Starting Playit tunnel daemon...");
            new Thread(() -> {
                OrchestratorBridge.startTunnelDaemon();
                mainWindow.getGui().getGUIThread().invokeLater(this::updateViewFromDisk);
            }).start();
        }
    }

    private void onRefresh() {
        detailLabel.setText("Querying Playit agent status...");
        ActivityLogger.info("Refreshing Playit tunnel status...");
        new Thread(() -> {
            OrchestratorBridge.statusTunnel();
            mainWindow.getGui().getGUIThread().invokeLater(this::updateViewFromDisk);
        }).start();
    }

    private void onSyncPort() {
        List<OrchestratorBridge.TunnelInfo> tunnels = OrchestratorBridge.getStoredTunnels();
        if (tunnels.isEmpty()) {
            ActivityLogger.warn("No active tunnel found to sync port.");
            return;
        }
        int targetPort = tunnels.get(0).localPort;
        TuiConfig cfg = ConfigManager.getInstance().getConfig();
        ServerPropertiesManager spm = new ServerPropertiesManager(cfg.getServerDir());
        spm.set("server-port", String.valueOf(targetPort));
        try {
            spm.save();
            ActivityLogger.ok("Updated server.properties port to " + targetPort + " from tunnel route.");
            detailLabel.setText("Server port synced to " + targetPort + ".");
        } catch (Exception e) {
            ActivityLogger.err("Failed saving server.properties: " + e.getMessage());
        }
    }

    private void onReset() {
        ActivityLogger.info("Resetting Playit credentials...");
        new Thread(() -> {
            OrchestratorBridge.resetTunnel();
            mainWindow.getGui().getGUIThread().invokeLater(() -> {
                claimUrlBox.setText("");
                detailLabel.setText("Playit credentials reset.");
                updateViewFromDisk();
            });
        }).start();
    }
}
