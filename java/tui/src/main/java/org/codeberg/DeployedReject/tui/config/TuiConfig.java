package org.codeberg.DeployedReject.tui.config;

public class TuiConfig {

    private String theme = "Gruvbox Dark";
    private int transparencyPercent = 0;
    private boolean trueColor = true;
    private boolean enforceMinSize = true;
    private boolean pickaxeAnimation = true;
    private String nerdFontMode = "auto";

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

    public String getNerdFontMode() {
        return nerdFontMode != null && !nerdFontMode.trim().isEmpty() ? nerdFontMode : "auto";
    }

    public void setNerdFontMode(String nerdFontMode) {
        this.nerdFontMode = nerdFontMode;
    }
}
