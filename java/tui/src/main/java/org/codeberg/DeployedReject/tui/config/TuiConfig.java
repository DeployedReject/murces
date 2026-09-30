package org.codeberg.DeployedReject.tui.config;

/**
 * Data model for persistent TUI preferences and customization options.
 */
public class TuiConfig {

    private String theme = "Gruvbox Dark";
    private int transparencyPercent = 0; // 0, 25, 50, 75, 100
    private boolean trueColor = true;
    private boolean enforceMinSize = true;
    private boolean pickaxeAnimation = true;

    public TuiConfig() {}

    public String getTheme() {
        return theme != null && !theme.trim().isEmpty() ? theme : "Gruvbox Dark";
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public int getTransparencyPercent() {
        return Math.max(0, Math.min(100, transparencyPercent));
    }

    public void setTransparencyPercent(int transparencyPercent) {
        this.transparencyPercent = Math.max(0, Math.min(100, transparencyPercent));
    }

    public boolean isTrueColor() {
        return trueColor;
    }

    public void setTrueColor(boolean trueColor) {
        this.trueColor = trueColor;
    }

    public boolean isEnforceMinSize() {
        return enforceMinSize;
    }

    public void setEnforceMinSize(boolean enforceMinSize) {
        this.enforceMinSize = enforceMinSize;
    }

    public boolean isPickaxeAnimation() {
        return pickaxeAnimation;
    }

    public void setPickaxeAnimation(boolean pickaxeAnimation) {
        this.pickaxeAnimation = pickaxeAnimation;
    }
}
