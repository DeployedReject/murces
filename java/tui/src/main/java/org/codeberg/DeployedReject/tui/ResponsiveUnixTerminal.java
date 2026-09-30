package org.codeberg.DeployedReject.tui;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.terminal.ansi.UnixTerminal;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

public class ResponsiveUnixTerminal extends UnixTerminal implements ResponsiveTerminal {

    public ResponsiveUnixTerminal() throws IOException {
        super();
    }

    public ResponsiveUnixTerminal(InputStream in, OutputStream out, Charset charset) throws IOException {
        super(in, out, charset);
    }

    @Override
    protected TerminalSize findTerminalSize() throws IOException {
        TerminalSize queried = TerminalResizeHelper.queryPhysicalTerminalSize();
        if (queried != null) {
            return queried;
        }
        return super.findTerminalSize();
    }

    @Override
    public void notifyResized(TerminalSize newSize) {
        if (newSize != null) {
            onResized(newSize);
        }
    }
}
