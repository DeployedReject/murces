package org.codeberg.DeployedReject.tui;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.terminal.Terminal;

public interface ResponsiveTerminal extends Terminal {
    void notifyResized(TerminalSize newSize);
}
