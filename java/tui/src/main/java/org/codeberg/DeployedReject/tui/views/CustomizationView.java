package org.codeberg.DeployedReject.tui.views;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import org.codeberg.DeployedReject.tui.config.ConfigManager;
import org.codeberg.DeployedReject.tui.config.TuiConfig;
import org.codeberg.DeployedReject.tui.theme.GlyphHelper;
import org.codeberg.DeployedReject.tui.theme.LazyVimTheme;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dedicated customization workspace view allowing users to select LazyVim themes,
 * adjust background transparency levels, toggle 24-bit TrueColor, minimum size enforcement,
 * Minecraft pickaxe loading animations, and Nerd Font glyph fallback settings.
 */
public class CustomizationView implements WorkspaceView {

    private final MainWindow mainWindow;
    private final Panel root;
    private final Label header;
    private final ComboBox<String> themeCombo;
    private final ComboBox<String> transparencyCombo;
    private final ComboBox<String> nerdFontCombo;
    private final CheckBox trueColorCheck;
    private final CheckBox minSizeCheck;
    private final CheckBox animationCheck;
    private final Label statusLabel;
    private final Map<Character, Runnable> hotkeys = new HashMap<>();

    private static final String[] TRANSPARENCY_OPTIONS = {
            "0%  (Opaque - Theme solid background)",
            "25% (Low - Transparent terminal canvas)",
            "50% (Medium - Transparent panels & cards)",
            "75% (High - Transparent controls)",
            "100% (Full - Maximum terminal transparency)"
    };

    public CustomizationView(MainWindow mainWindow) {
        this.mainWindow = mainWindow;
        this.root = new Panel(new LinearLayout(Direction.VERTICAL));

        header = new Label(GlyphHelper.apply("󰏘 Theme & Interface Customization"));
        header.setForegroundColor(LazyVimTheme.getAccentColor());
        root.addComponent(header);
        root.addComponent(new Label("Select from LazyVim themes and terminal transparency levels:"));
        root.addComponent(new EmptySpace(new TerminalSize(1, 1)));

        // Form layout
        Panel formPanel = new Panel(new GridLayout(2));

        // 1. Theme
        formPanel.addComponent(new Label(GlyphHelper.apply("󰏘 [T]heme: ")));
        themeCombo = new ComboBox<>();
        List<String> themeNames = LazyVimTheme.getAvailableThemeNames();
        for (String t : themeNames) {
            themeCombo.addItem(t);
        }
        formPanel.addComponent(themeCombo);

        // 2. Transparency
        formPanel.addComponent(new Label(GlyphHelper.apply("󰏘 Trans[p]arency: ")));
        transparencyCombo = new ComboBox<>();
        for (String opt : TRANSPARENCY_OPTIONS) {
            transparencyCombo.addItem(opt);
        }
        formPanel.addComponent(transparencyCombo);

        // 3. Nerd Font Glyphs
        formPanel.addComponent(new Label(GlyphHelper.apply("󰏘 [G]lyphs: ")));
        nerdFontCombo = new ComboBox<>("Auto-detect", "Force Nerd Fonts", "Basic (Fallback)");
        formPanel.addComponent(nerdFontCombo);

        root.addComponent(formPanel.withBorder(Borders.singleLine(GlyphHelper.apply("󰏘 Appearance Settings"))));

        // 4. Toggles
        Panel togglePanel = new Panel(new LinearLayout(Direction.VERTICAL));
        trueColorCheck = new CheckBox("Enable 24-bit TrueColor (ANSI RGB)");
        minSizeCheck = new CheckBox("Enforce Minimum Screen Size (>= 70x18)");
        animationCheck = new CheckBox("Minecraft pickaxe dirt-breaking loading animation");

        togglePanel.addComponent(trueColorCheck);
        togglePanel.addComponent(minSizeCheck);
        togglePanel.addComponent(animationCheck);

        root.addComponent(togglePanel.withBorder(Borders.singleLine(GlyphHelper.apply("󰒓 Options & Animations"))));

        statusLabel = new Label(GlyphHelper.apply("󰄬 [OK] Ready."));
        statusLabel.setForegroundColor(LazyVimTheme.getLogSuccessColor());
        root.addComponent(statusLabel);

        // 5. Actions
        Panel actionPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
        Button saveBtn = new Button(GlyphHelper.apply("󰆓 [S]ave & Apply"), this::onSaveAndApply);
        Button resetBtn = new Button(GlyphHelper.apply("󰁯 [R]eset Defaults"), this::onResetDefaults);
        Button backBtn = new Button(GlyphHelper.apply("󰁯 [B]ack"), mainWindow::showMainMenu);

        actionPanel.addComponent(saveBtn);
        actionPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        actionPanel.addComponent(resetBtn);
        actionPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));
        actionPanel.addComponent(backBtn);

        root.addComponent(actionPanel);

        // Hotkeys
        hotkeys.put('S', this::onSaveAndApply);
        hotkeys.put('R', this::onResetDefaults);
        hotkeys.put('B', mainWindow::showMainMenu);
        hotkeys.put('T', themeCombo::takeFocus);
        hotkeys.put('P', transparencyCombo::takeFocus);
        hotkeys.put('G', nerdFontCombo::takeFocus);

        loadCurrentConfig();
    }

    private void loadCurrentConfig() {
        TuiConfig config = ConfigManager.getInstance().getConfig();

        // Theme
        String currentTheme = config.getTheme();
        for (int i = 0; i < themeCombo.getItemCount(); i++) {
            if (themeCombo.getItem(i).equalsIgnoreCase(currentTheme)) {
                themeCombo.setSelectedIndex(i);
                break;
            }
        }

        // Transparency
        int currentTrans = config.getTransparencyPercent();
        if (currentTrans <= 0) transparencyCombo.setSelectedIndex(0);
        else if (currentTrans <= 25) transparencyCombo.setSelectedIndex(1);
        else if (currentTrans <= 50) transparencyCombo.setSelectedIndex(2);
        else if (currentTrans <= 75) transparencyCombo.setSelectedIndex(3);
        else transparencyCombo.setSelectedIndex(4);

        // Nerd Font Glyphs
        String mode = config.getNerdFontMode();
        if ("enabled".equalsIgnoreCase(mode)) nerdFontCombo.setSelectedIndex(1);
        else if ("disabled".equalsIgnoreCase(mode)) nerdFontCombo.setSelectedIndex(2);
        else nerdFontCombo.setSelectedIndex(0);

        // Toggles
        trueColorCheck.setChecked(config.isTrueColor());
        minSizeCheck.setChecked(config.isEnforceMinSize());
        animationCheck.setChecked(config.isPickaxeAnimation());
    }

    private void onSaveAndApply() {
        TuiConfig config = ConfigManager.getInstance().getConfig();

        String selectedTheme = themeCombo.getSelectedItem();
        config.setTheme(selectedTheme != null ? selectedTheme : "Gruvbox Dark");

        int transIdx = transparencyCombo.getSelectedIndex();
        int transPercent = 0;
        switch (transIdx) {
            case 1: transPercent = 25; break;
            case 2: transPercent = 50; break;
            case 3: transPercent = 75; break;
            case 4: transPercent = 100; break;
            default: transPercent = 0; break;
        }
        config.setTransparencyPercent(transPercent);

        int gIdx = nerdFontCombo.getSelectedIndex();
        if (gIdx == 1) config.setNerdFontMode("enabled");
        else if (gIdx == 2) config.setNerdFontMode("disabled");
        else config.setNerdFontMode("auto");
        GlyphHelper.invalidateCache();

        config.setTrueColor(trueColorCheck.isChecked());
        config.setEnforceMinSize(minSizeCheck.isChecked());
        config.setPickaxeAnimation(animationCheck.isChecked());

        ConfigManager.getInstance().save();

        mainWindow.applyConfig(config);
        header.setForegroundColor(LazyVimTheme.getAccentColor());
        statusLabel.setText(GlyphHelper.apply("󰄬 [OK] Saved and applied theme: " + config.getTheme() + " (" + transPercent + "% trans)"));
        statusLabel.setForegroundColor(LazyVimTheme.getLogSuccessColor());
        ActivityLogger.ok("Applied theme: " + config.getTheme() + " (" + transPercent + "% trans)");
    }

    private void onResetDefaults() {
        TuiConfig def = new TuiConfig();
        ConfigManager.getInstance().setConfig(def);
        GlyphHelper.invalidateCache();
        loadCurrentConfig();
        mainWindow.applyConfig(def);
        header.setForegroundColor(LazyVimTheme.getAccentColor());
        statusLabel.setText(GlyphHelper.apply("󰄬 [OK] Reset to default configuration (Gruvbox Dark)."));
        statusLabel.setForegroundColor(LazyVimTheme.getLogSuccessColor());
        ActivityLogger.ok("Reset configuration to default Gruvbox Dark.");
    }

    @Override
    public String getTitle() {
        return GlyphHelper.apply("󰏘 Customization & Themes");
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
        return themeCombo;
    }

    @Override
    public void onResized(TerminalSize newSize) {
        if (newSize == null) return;
        int cols = newSize.getColumns();
        int actWidth = Math.max(28, Math.min(65, (cols * 35) / 100));
        int wsWidth = Math.max(44, cols - actWidth - 6);

        int comboWidth = Math.max(28, wsWidth - 20);
        themeCombo.setPreferredSize(new TerminalSize(comboWidth, 1));
        transparencyCombo.setPreferredSize(new TerminalSize(comboWidth, 1));
        nerdFontCombo.setPreferredSize(new TerminalSize(comboWidth, 1));
    }

    @Override
    public void onActivated() {
        loadCurrentConfig();
    }
}
