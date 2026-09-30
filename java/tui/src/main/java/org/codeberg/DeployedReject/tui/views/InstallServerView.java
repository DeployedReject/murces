package org.codeberg.DeployedReject.tui.views;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import org.codeberg.DeployedReject.tui.backend.OrchestratorBridge;
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
    private final Label progressLabel;
    private final Button installOnlyBtn;
    private final Button installStartBtn;
    private final Button backBtn;
    private final Map<Character, Runnable> hotkeys = new HashMap<>();

    public InstallServerView(MainWindow mainWindow) {
        this.mainWindow = mainWindow;
        this.root = new Panel(new LinearLayout(Direction.VERTICAL));

        // Options form
        Panel formPanel = new Panel(new GridLayout(2));

        formPanel.addComponent(new Label("[E]ngine:"));
        engineComboBox = new ComboBox<>("Fabric", "Paper", "Spigot", "Vanilla", "Forge");
        engineComboBox.setPreferredSize(new TerminalSize(16, 1));
        formPanel.addComponent(engineComboBox);

        formPanel.addComponent(new Label("Game [V]ersion:"));
        gameVersionComboBox = MinecraftVersionHelper.createVersionComboBox(mainWindow.getGui(), new TerminalSize(16, 1));
        formPanel.addComponent(gameVersionComboBox);

        formPanel.addComponent(new Label("Loader [L] Version:"));
        loaderVersionBox = new TextBox(new TerminalSize(16, 1), "0.16.5");
        formPanel.addComponent(loaderVersionBox);

        formPanel.addComponent(new Label("[R]AM Allocation:"));
        ramComboBox = new ComboBox<>("2G", "4G", "6G", "8G", "12G", "16G", "1G");
        ramComboBox.setPreferredSize(new TerminalSize(16, 1));
        ramComboBox.setSelectedIndex(1); // 4G default
        formPanel.addComponent(ramComboBox);

        root.addComponent(formPanel.withBorder(Borders.singleLine("Server Configuration")));

        // Action buttons
        Panel actionPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
        installOnlyBtn = new Button("[I]nstall Only", () -> runInstall(0));
        installStartBtn = new Button("[S]tart & Install", () -> runInstall(1));
        actionPanel.addComponent(installOnlyBtn);
        actionPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        actionPanel.addComponent(installStartBtn);
        root.addComponent(actionPanel);

        // Progress label
        progressLabel = new Label("Ready to install.");
        progressLabel.setForegroundColor(MinecraftTheme.GOLD_YELLOW);
        root.addComponent(progressLabel);

        root.addComponent(new EmptySpace(new TerminalSize(1, 1)));

        // Footer
        backBtn = new Button("[B]ack to Main Menu", mainWindow::showMainMenu);
        root.addComponent(backBtn);

        // Hotkeys
        hotkeys.put('I', KeyboardNavigationHelper.focus(installOnlyBtn, () -> runInstall(0)));
        hotkeys.put('S', KeyboardNavigationHelper.focus(installStartBtn, () -> runInstall(1)));
        hotkeys.put('E', KeyboardNavigationHelper.focus(engineComboBox, this::cycleEngine));
        hotkeys.put('V', KeyboardNavigationHelper.focus(gameVersionComboBox, () -> MinecraftVersionHelper.cycleVersion(gameVersionComboBox)));
        hotkeys.put('L', loaderVersionBox::takeFocus);
        hotkeys.put('R', KeyboardNavigationHelper.focus(ramComboBox, this::cycleRam));
        hotkeys.put('B', KeyboardNavigationHelper.focus(backBtn, mainWindow::showMainMenu));
    }

    @Override
    public String getTitle() {
        return "Install Server Engine";
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
    }

    private void cycleEngine() {
        int next = (engineComboBox.getSelectedIndex() + 1) % engineComboBox.getItemCount();
        engineComboBox.setSelectedIndex(next);
    }

    private void cycleRam() {
        int next = (ramComboBox.getSelectedIndex() + 1) % ramComboBox.getItemCount();
        ramComboBox.setSelectedIndex(next);
    }

    private void runInstall(int job) {
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

        ActivityLogger.info("Starting installation of " + engine + " " + gameVer + " (RAM: " + ramVal + "G)...");
        progressLabel.setText("[BUSY] Installing " + engine + " " + gameVer + "...");

        final int finalRam = ramVal;
        new Thread(() -> {
            try {
                java.util.concurrent.atomic.AtomicInteger lastProg = new java.util.concurrent.atomic.AtomicInteger(-1);
                OrchestratorBridge.getInstance().installServer(engine, gameVer, loaderVer, finalRam, job, msg -> {
                    if (msg != null && msg.contains("%")) {
                        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)%").matcher(msg);
                        if (m.find()) {
                            int pct = Integer.parseInt(m.group(1));
                            int prev = lastProg.get();
                            if (prev != -1 && pct < 100 && pct < prev + 5) {
                                return;
                            }
                            lastProg.set(pct);
                        }
                    }
                    mainWindow.getGui().getGUIThread().invokeLater(() -> {
                        progressLabel.setText("[BUSY] " + msg);
                        ActivityLogger.log("[STATUS] " + msg);
                    });
                }).get();

                mainWindow.getGui().getGUIThread().invokeLater(() -> {
                    progressLabel.setText("[OK] Server installation completed!");
                    ActivityLogger.ok("Server installation finished successfully!");
                });
            } catch (Exception e) {
                mainWindow.getGui().getGUIThread().invokeLater(() -> {
                    progressLabel.setText("[ERR] Installation failed: " + e.getMessage());
                    ActivityLogger.err("Installation failed: " + e.getMessage());
                });
            }
        }).start();
    }
}
