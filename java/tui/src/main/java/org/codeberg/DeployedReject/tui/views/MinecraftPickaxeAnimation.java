package org.codeberg.DeployedReject.tui.views;

import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.AbstractComponent;
import com.googlecode.lanterna.gui2.ComponentRenderer;
import com.googlecode.lanterna.gui2.TextGUIGraphics;
import org.codeberg.DeployedReject.tui.theme.LazyVimTheme;

/**
 * A Minecraft-themed loading animation that renders a diamond pickaxe breaking
 * a row of dirt blocks through cracking stages and breaking particles.
 */
public class MinecraftPickaxeAnimation extends AbstractComponent<MinecraftPickaxeAnimation> {

    private static final int TOTAL_BLOCKS = 5;
    private static final TextColor DIRT_BROWN = new TextColor.RGB(133, 82, 43);
    private static final TextColor CRACK_GRAY = new TextColor.RGB(170, 170, 170);
    private static final TextColor DIAMOND_TOOL = new TextColor.RGB(85, 255, 255);
    private static final TextColor PARTICLE_GOLD = new TextColor.RGB(255, 170, 0);

    private double progress = -1; // -1 for looping indeterminate, 0..100 for progress-bound
    private int tick = 0;
    private String customMessage = null;

    public MinecraftPickaxeAnimation() {
    }

    public synchronized void setProgress(double progress) {
        this.progress = progress;
        this.tick++;
        invalidate();
    }

    public synchronized double getProgress() {
        return progress;
    }

    public synchronized void tick() {
        this.tick++;
        invalidate();
    }

    public synchronized void setCustomMessage(String customMessage) {
        this.customMessage = customMessage;
        invalidate();
    }

    @Override
    protected ComponentRenderer<MinecraftPickaxeAnimation> createDefaultRenderer() {
        return new ComponentRenderer<>() {
            @Override
            public TerminalSize getPreferredSize(MinecraftPickaxeAnimation component) {
                return new TerminalSize(36, 3);
            }

            @Override
            public void drawComponent(TextGUIGraphics graphics, MinecraftPickaxeAnimation comp) {
                TerminalSize size = graphics.getSize();
                int width = Math.max(30, size.getColumns());
                int height = Math.max(1, size.getRows());

                // Clear background with log background or default
                graphics.setBackgroundColor(LazyVimTheme.getLogBackgroundColor());
                graphics.fill(' ');

                double effectiveProgress;
                int currentTick;
                String msg;

                synchronized (comp) {
                    currentTick = comp.tick;
                    msg = comp.customMessage;
                    if (comp.progress < 0) {
                        // Indeterminate mode: loop smoothly 0..100
                        effectiveProgress = (currentTick * 4) % 101;
                    } else {
                        effectiveProgress = Math.max(0, Math.min(100, comp.progress));
                    }
                }

                // Compute number of blocks that fit the full available width
                int blockWidth = 5; // "[██] " is 5 chars
                int totalBlocks = Math.max(5, (width - 1) / blockWidth);

                double blockProgress = (effectiveProgress / 100.0) * totalBlocks;
                int activeBlock = Math.min(totalBlocks - 1, (int) blockProgress);
                double subProgress = blockProgress - activeBlock;

                // Pickaxe swing frames: 0 = high, 1 = hitting, 2 = follow-through
                String toolIcon = org.codeberg.DeployedReject.tui.theme.GlyphHelper.isNerdFontEnabled() ? org.codeberg.DeployedReject.tui.theme.GlyphHelper.ICON_TOOL : "/";
                String[] swingIcons = {toolIcon + " \\", toolIcon + " |", toolIcon + " /"};
                String swing = swingIcons[Math.abs(currentTick) % 3];

                // Line 0: Pickaxe swing above block (fixed position at start)
                if (height >= 1) {
                    int pickCol = 0; // always start at column 0
                    if (pickCol < width) {
                        graphics.setForegroundColor(DIAMOND_TOOL);
                        graphics.putString(pickCol, 0, toolIcon);
                        graphics.setForegroundColor(LazyVimTheme.getAccentColor());
                        if (pickCol + 2 < width) {
                            graphics.putString(pickCol + 2, 0, swing.substring(toolIcon.length() + 1));
                        }
                    }
                }

                // Line 1: Row of dirt blocks filling the entire width
                if (height >= 2) {
                    for (int b = 0; b < totalBlocks; b++) {
                        int col = b * blockWidth;
                        if (col + 4 > width) {
                            break;
                        }
                        if (b < activeBlock) {
                            // Already broken
                            graphics.setForegroundColor(LazyVimTheme.getMutedColor());
                            graphics.putString(col, 1, "[  ]");
                        } else if (b > activeBlock) {
                            // Intact dirt
                            graphics.setForegroundColor(DIRT_BROWN);
                            graphics.putString(col, 1, "[██]");
                        } else {
                            // Currently breaking dirt block
                            graphics.setForegroundColor(DIRT_BROWN);
                            graphics.putString(col, 1, "[");
                            graphics.putString(col + 3, 1, "]");

                            if (subProgress < 0.25) {
                                graphics.setForegroundColor(DIRT_BROWN);
                                graphics.putString(col + 1, 1, "██");
                            } else if (subProgress < 0.50) {
                                graphics.setForegroundColor(CRACK_GRAY);
                                graphics.putString(col + 1, 1, "▓▓");
                            } else if (subProgress < 0.75) {
                                graphics.setForegroundColor(CRACK_GRAY);
                                graphics.putString(col + 1, 1, "▒▒");
                            } else if (subProgress < 0.95) {
                                graphics.setForegroundColor(CRACK_GRAY);
                                graphics.putString(col + 1, 1, "░░");
                            } else {
                                graphics.setForegroundColor(PARTICLE_GOLD);
                                graphics.putString(col, 1, "*░ *");
                            }
                        }
                    }
                }

                // Line 2: Status label / ETA
                if (height >= 3) {
                    graphics.setForegroundColor(LazyVimTheme.getActivePalette().fg);
                    String display;
                    if (msg != null && !msg.isEmpty()) {
                        display = org.codeberg.DeployedReject.tui.theme.GlyphHelper.apply(msg);
                    } else {
                        display = String.format("Breaking dirt... %5.2f%% [Block %d/%d]", effectiveProgress, activeBlock + 1, totalBlocks);
                    }
                    if (display.length() > width) {
                        display = display.substring(0, width);
                    }
                    graphics.putString(0, 2, display);
                }
            }
        };
    }
}
