package org.codeberg.DeployedReject.tui;

import com.googlecode.lanterna.TerminalSize;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

public class ResponsiveStreamTerminal extends StreamAnsiTerminal implements ResponsiveTerminal {

    public ResponsiveStreamTerminal(InputStream inputStream, OutputStream outputStream, Charset charset) {
        super(inputStream, outputStream, charset);
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
