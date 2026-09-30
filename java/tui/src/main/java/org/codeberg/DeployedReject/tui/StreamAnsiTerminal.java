package org.codeberg.DeployedReject.tui;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.terminal.ansi.ANSITerminal;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

public class StreamAnsiTerminal extends ANSITerminal {

    public StreamAnsiTerminal(InputStream inputStream, OutputStream outputStream, Charset charset) {
        super(inputStream, outputStream, charset);
    }

    @Override
    protected TerminalSize findTerminalSize() throws IOException {
        String lines = System.getenv("LINES");
        String cols = System.getenv("COLUMNS");
        int w = 80;
        int h = 25;
        if (cols != null && lines != null) {
            try {
                w = Integer.parseInt(cols);
                h = Integer.parseInt(lines);
            } catch (NumberFormatException ignored) {}
        }
        return new TerminalSize(w, h);
    }
}
