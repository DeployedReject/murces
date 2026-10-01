package org.codeberg.DeployedReject.tui;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.terminal.ansi.UnixTerminal;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

public class ResponsiveUnixTerminal extends UnixTerminal implements ResponsiveTerminal {

    public ResponsiveUnixTerminal() throws IOException {
        this(System.in, System.out, java.nio.charset.StandardCharsets.UTF_8);
    }

    public ResponsiveUnixTerminal(InputStream in, OutputStream out, Charset charset) throws IOException {
        super(in, out, charset);
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
