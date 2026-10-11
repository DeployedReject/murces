package org.codeberg.DeployedReject.tui;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ResponsiveStreamTerminal extends StreamAnsiTerminal implements ResponsiveTerminal {

    private final BlockingQueue<KeyStroke> decodedKeyQueue = new LinkedBlockingQueue<>();
    private final Thread inputPumpThread;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public ResponsiveStreamTerminal(InputStream inputStream, OutputStream outputStream, Charset charset) {
        super(inputStream, outputStream, charset);
        try {
            getInputDecoder().setTimeoutUnits(0);
        } catch (Exception ignored) {}

        this.inputPumpThread = new Thread(this::pumpInput, "MurcesInputPump");
        this.inputPumpThread.setDaemon(true);
        this.inputPumpThread.start();
    }

    private void pumpInput() {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                // Call super.readInput() which performs blocking read on the input stream
                KeyStroke ks = super.readInput();
                if (ks != null) {
                    if (ks.getKeyType() == KeyType.EOF) {
                        break;
                    }
                    decodedKeyQueue.offer(ks);
                }
            } catch (java.io.EOFException eof) {
                break;
            } catch (Throwable t) {
                if (!running.get()) {
                    break;
                }
                try {
                    Thread.sleep(10);
                } catch (InterruptedException ie) {
                    break;
                }
            }
        }
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
        return new TerminalSize(80, 25);
    }

    @Override
    public KeyStroke readInput() throws IOException {
        try {
            return decodedKeyQueue.poll(20, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    @Override
    public KeyStroke pollInput() throws IOException {
        return decodedKeyQueue.poll();
    }

    @Override
    public void notifyResized(TerminalSize newSize) {
        if (newSize != null) {
            onResized(newSize);
        }
    }

    @Override
    public void close() throws IOException {
        running.set(false);
        if (inputPumpThread != null) {
            inputPumpThread.interrupt();
        }
        super.close();
    }
}
