package org.codeberg.DeployedReject.tui.views;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import org.codeberg.DeployedReject.tui.backend.OrchestratorBridge;
import org.codeberg.DeployedReject.tui.theme.GlyphHelper;
import org.codeberg.DeployedReject.tui.theme.MinecraftTheme;

import java.util.HashMap;
import java.util.Map;

public class InstallServerView implements WorkspaceView {

    private final MainWindow mainWindow;
    private final Panel root;
    private final ComboBox<String> engineComboBox;
    private final ComboBox<String> gameVersionComboBox;
    private final TextBox loaderVersionBox;
    private final ComboBox<String> ramComboBox;
    private final ProgressBar progressBar;
    private final Label progressLabel;
    private final Button installOnlyBtn;
    private final Button installStartBtn;
    private final Button cancelBtn;
    private final Button backBtn;
    private final Map<Character, Runnable> hotkeys = new HashMap<>();

    public InstallServerView(MainWindow mainWindow) {
        this.mainWindow = mainWindow;
        this.root = new Panel(new LinearLayout(Direction.VERTICAL));

        // Options form
        Panel formPanel = new Panel(new GridLayout(2));

        formPanel.addComponent(new Label(GlyphHelper.apply(GlyphHelper.ICON_SERVER + " [E]ngine:")));
        engineComboBox = new ComboBox<>("Fabric", "Paper", "Spigot", "Vanilla", "Forge");
        engineComboBox.setPreferredSize(new TerminalSize(16, 1));
        formPanel.addComponent(engineComboBox);

        formPanel.addComponent(new Label(GlyphHelper.apply(GlyphHelper.ICON_PACKAGE + " Game [V]ersion:")));
        gameVersionComboBox = MinecraftVersionHelper.createVersionComboBox(mainWindow.getGui(), new TerminalSize(16, 1));
        formPanel.addComponent(gameVersionComboBox);

        formPanel.addComponent(new Label(GlyphHelper.apply(GlyphHelper.ICON_CONFIG + " Loader [L] Version:")));
        loaderVersionBox = new TextBox(new TerminalSize(16, 1), "0.16.5");
        formPanel.addComponent(loaderVersionBox);

        formPanel.addComponent(new Label(GlyphHelper.apply(GlyphHelper.ICON_OPTIONS + " [R]AM Allocation:")));
        ramComboBox = new ComboBox<>("2G", "4G", "6G", "8G", "12G", "16G", "1G");
        ramComboBox.setPreferredSize(new TerminalSize(16, 1));
        ramComboBox.setSelectedIndex(1); // 4G default
        formPanel.addComponent(ramComboBox);

        root.addComponent(formPanel.withBorder(Borders.singleLine(GlyphHelper.apply(GlyphHelper.ICON_CONFIG + " Server Configuration"))));

        // Action buttons
        Panel actionPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
        installOnlyBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_PACKAGE + " [I]nstall Only"), () -> runInstall(0));
        installStartBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_PLAY + " [S]tart & Install"), () -> runInstall(1));
        cancelBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_CROSS + " [X] Cancel Installation"), this::cancelInstallation);
        actionPanel.addComponent(installOnlyBtn);
        actionPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        actionPanel.addComponent(installStartBtn);
        actionPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        actionPanel.addComponent(cancelBtn);
        root.addComponent(actionPanel);

        // Progress bar
        progressBar = new ProgressBar(0, 100);
        progressBar.setPreferredSize(new TerminalSize(36, 1));
        progressBar.setValue(0);
        root.addComponent(progressBar);

        // Progress label
        progressLabel = new Label("Ready to install.");
        progressLabel.setForegroundColor(MinecraftTheme.GOLD_YELLOW);
        root.addComponent(progressLabel);

        root.addComponent(new EmptySpace(new TerminalSize(1, 1)));

        // Footer
        backBtn = new Button(GlyphHelper.apply(GlyphHelper.ICON_BACK + " [B]ack to Main Menu"), mainWindow::showMainMenu);
        root.addComponent(backBtn);

        // Hotkeys
        hotkeys.put('I', KeyboardNavigationHelper.focus(installOnlyBtn, () -> runInstall(0)));
        hotkeys.put('S', KeyboardNavigationHelper.focus(installStartBtn, () -> runInstall(1)));
        hotkeys.put('X', KeyboardNavigationHelper.focus(cancelBtn, this::cancelInstallation));
        hotkeys.put('E', KeyboardNavigationHelper.focus(engineComboBox, this::cycleEngine));
        hotkeys.put('V', KeyboardNavigationHelper.focus(gameVersionComboBox, () -> MinecraftVersionHelper.cycleVersion(gameVersionComboBox)));
        hotkeys.put('L', loaderVersionBox::takeFocus);
        hotkeys.put('R', KeyboardNavigationHelper.focus(ramComboBox, this::cycleRam));
        hotkeys.put('B', KeyboardNavigationHelper.focus(backBtn, mainWindow::showMainMenu));
    }

    @Override
    public String getTitle() {
        return GlyphHelper.apply(GlyphHelper.ICON_CONFIG + " Install Server Engine");
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
        return installOnlyBtn;
    }

    @Override
    public void onResized(TerminalSize newSize) {
        if (newSize == null) return;
        int cols = newSize.getColumns();
        int actWidth = Math.max(28, Math.min(65, (cols * 35) / 100));
        int wsWidth = Math.max(44, cols - actWidth - 6);

        int inputWidth = Math.max(16, Math.min(36, wsWidth - 24));
        engineComboBox.setPreferredSize(new TerminalSize(inputWidth, 1));
        gameVersionComboBox.setPreferredSize(new TerminalSize(inputWidth, 1));
        loaderVersionBox.setPreferredSize(new TerminalSize(inputWidth, 1));
        ramComboBox.setPreferredSize(new TerminalSize(inputWidth, 1));
        progressBar.setPreferredSize(new TerminalSize(Math.max(20, wsWidth - 6), 1));
    }

    private void cycleEngine() {
        int next = (engineComboBox.getSelectedIndex() + 1) % engineComboBox.getItemCount();
        engineComboBox.setSelectedIndex(next);
    }

    private void cycleRam() {
        int next = (ramComboBox.getSelectedIndex() + 1) % ramComboBox.getItemCount();
        ramComboBox.setSelectedIndex(next);
    }

    public void cancelInstallation() {
        if (!OrchestratorBridge.isServerDownloading()) {
            ActivityLogger.info("No server installation is currently running.");
            return;
        }
        ActivityLogger.warn("Cancelling active server installation...");
        org.codeberg.DeployedReject.tui.backend.JobTracker.getInstance().getActiveJobs().forEach(j -> {
            if ("Server".equalsIgnoreCase(j.getType())) {
                j.cancel();
            }
        });
        installOnlyBtn.setEnabled(true);
        installStartBtn.setEnabled(true);
        progressBar.setValue(0);
        progressLabel.setText("[CANCELLED] Server installation aborted and cleaned up.");
        mainWindow.invalidate();
    }

    private void runInstall(int job) {
        if (OrchestratorBridge.isServerDownloading()) {
            ActivityLogger.warn("Server installation or download is already running! Press [X] to cancel it first.");
            progressLabel.setText("[BUSY] An installation is already running!");
            return;
        }

        String engine = engineComboBox.getSelectedItem();
        String gameVer = MinecraftVersionHelper.getSelectedVersion(gameVersionComboBox);
        String loaderVer = loaderVersionBox.getText().trim();
        String ramStr = ramComboBox.getSelectedItem().replace("G", "").trim();
        int ramVal = 4;
        try {
            ramVal = Integer.parseInt(ramStr);
        } catch (NumberFormatException ignored) {}

        if (gameVer == null || gameVer.isEmpty()) {
            ActivityLogger.warn("Invalid version: Minecraft game version cannot be empty.");
            progressLabel.setText("[WARN] Game version cannot be empty!");
            return;
        }

        installOnlyBtn.setEnabled(false);
        installStartBtn.setEnabled(false);
        progressBar.setValue(0);

        ActivityLogger.info("Starting installation of " + engine + " " + gameVer + " (RAM: " + ramVal + "G)...");
        progressLabel.setText("[BUSY] Installing " + engine + " " + gameVer + "...");

        final int finalRam = ramVal;
        new Thread(() -> {
            try {
                java.util.concurrent.atomic.AtomicInteger lastProg = new java.util.concurrent.atomic.AtomicInteger(-1);
                OrchestratorBridge.getInstance().installServer(engine, gameVer, loaderVer, finalRam, job, msg -> {
                    if (msg != null && msg.contains("%")) {
                        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+(\\.\\d+)?)%").matcher(msg);
                        if (m.find()) {
                            try {
                                double pVal = Double.parseDouble(m.group(1));
                                int pct = (int) Math.round(pVal);
                                int prev = lastProg.get();
                                if (prev == -1 || pct >= prev + 1 || pct >= 100) {
                                    lastProg.set(pct);
                                    mainWindow.getGui().getGUIThread().invokeLater(() -> {
                                        progressBar.setValue(Math.min(100, Math.max(0, pct)));
                                    });
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                    mainWindow.getGui().getGUIThread().invokeLater(() -> {
                        progressLabel.setText("[BUSY] " + msg);
                        ActivityLogger.log("[STATUS] " + msg);
                    });
                }).get();

                mainWindow.getGui().getGUIThread().invokeLater(() -> {
                    progressBar.setValue(100);
                    installOnlyBtn.setEnabled(true);
                    installStartBtn.setEnabled(true);
                    progressLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_CHECK + " [OK] Server installation completed!"));
                    ActivityLogger.ok("Server installation finished successfully!");
                });
            } catch (Exception e) {
                mainWindow.getGui().getGUIThread().invokeLater(() -> {
                    installOnlyBtn.setEnabled(true);
                    installStartBtn.setEnabled(true);
                    progressLabel.setText(GlyphHelper.apply(GlyphHelper.ICON_CROSS + " [ERR] Installation stopped: " + e.getMessage()));
                    ActivityLogger.err("Installation stopped: " + e.getMessage());
                });
            }
        }).start();
    }
}
