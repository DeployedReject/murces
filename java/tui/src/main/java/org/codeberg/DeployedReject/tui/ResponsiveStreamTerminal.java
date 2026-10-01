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

    private char highSurrogate = 0;

    @Override
    public void putCharacter(char c) throws IOException {
        if (Character.isHighSurrogate(c)) {
            highSurrogate = c;
            return;
        }
        if (Character.isLowSurrogate(c) && highSurrogate != 0) {
            String s = new String(new char[]{highSurrogate, c});
            highSurrogate = 0;
            writeToTerminal(s.getBytes(getCharset()));
            return;
        }
        highSurrogate = 0;
        super.putCharacter(c);
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
