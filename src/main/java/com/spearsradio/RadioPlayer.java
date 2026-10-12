package com.spearsradio;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sound.sampled.*;
import javazoom.jl.decoder.*;
import net.fabricmc.loader.api.FabricLoader;

/** MP3 stream / file player (own audio line, own volume). Accepts direct MP3 URLs and .m3u/.pls playlist URLs. */
public final class RadioPlayer {
    public record Entry(String name, String url) {}
    public static final List<Entry> list = new ArrayList<>();
    public static volatile int index = -1;
    public static volatile float volume = 0.5f;
    public static volatile String status = "Stopped";
    /** 0 = repeat off, 1 = repeat all, 2 = repeat one */
    public static volatile int repeat = 1;
    public static String repeatName() { return repeat == 0 ? "Off" : repeat == 1 ? "All" : "One"; }
    public static void cycleRepeat() { repeat = (repeat + 1) % 3; save(); }

    private static final AtomicInteger GEN = new AtomicInteger();
    private static volatile SourceDataLine line;
    private static volatile boolean playing;

    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("spears_radio.txt"); }

    public static synchronized void load() {
        try {
            for (String l : Files.readAllLines(file(), StandardCharsets.UTF_8)) {
                if (l.startsWith("#volume=")) { volume = Float.parseFloat(l.substring(8)); continue; }
                if (l.startsWith("#repeat=")) { repeat = Integer.parseInt(l.substring(8)) % 3; continue; }
                int i = l.indexOf('|');
                if (i > 0) list.add(new Entry(l.substring(0, i), l.substring(i + 1)));
            }
        } catch (Exception ignored) {}
    }

    public static synchronized void save() {
        try {
            List<String> out = new ArrayList<>();
            out.add("#volume=" + volume);
            out.add("#repeat=" + repeat);
            for (Entry e : list) out.add(e.name() + "|" + e.url());
            Files.write(file(), out, StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
    }

    public static boolean isPlaying() { return playing; }
    public static String nowPlaying() { return index >= 0 && index < list.size() ? list.get(index).name() : "-"; }

    /** Add a URL. Returns a message for the user. Runs network work off the render thread. */
    public static void addUrl(String raw, Runnable done) {
        String url = raw.trim();
        String low = url.toLowerCase(Locale.ROOT);
        if (url.isEmpty()) { status = "Paste a URL first"; if (done != null) done.run(); return; }
        if (low.contains("spotify.com") || low.contains("youtube.com") || low.contains("youtu.be")) {
            status = "Spotify/YouTube links can't be played. Use a direct MP3 stream or .m3u/.pls link.";
            if (done != null) done.run(); return;
        }
        Thread t = new Thread(() -> {
            try {
                if (low.endsWith(".m3u") || low.endsWith(".pls")) {
                    int n = 0;
                    for (String line : fetchText(url).split("\\R")) {
                        String u = line.trim();
                        if (u.regionMatches(true, 0, "File", 0, 4) && u.contains("=")) u = u.substring(u.indexOf('=') + 1).trim();
                        if (u.startsWith("http://") || u.startsWith("https://")) { list.add(new Entry(nameOf(u), u)); n++; }
                    }
                    status = n > 0 ? "Added " + n + " entries" : "No stream URLs found in that playlist";
                } else {
                    list.add(new Entry(nameOf(url), url));
                    status = "Added";
                }
                save();
            } catch (Exception e) { status = "Could not add: " + e.getMessage(); }
            if (done != null) done.run();
        }, "SpearsRadio-add");
        t.setDaemon(true); t.start();
    }

    private static String nameOf(String u) {
        try { URI x = new URI(u); String p = x.getPath() == null ? "" : x.getPath();
            return x.getHost() + (p.length() > 1 ? p : ""); } catch (Exception e) { return u; }
    }

    private static String fetchText(String u) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setRequestProperty("User-Agent", "SpearsRadio/1.0"); c.setConnectTimeout(8000); c.setReadTimeout(8000);
        try (InputStream in = c.getInputStream()) { return new String(in.readNBytes(1 << 20), StandardCharsets.UTF_8); }
    }

    public static synchronized void remove(int i) {
        if (i < 0 || i >= list.size()) return;
        boolean wasCurrent = i == index;
        list.remove(i);
        if (wasCurrent) stop(); else if (i < index) index--;
        save();
    }

    public static void play(int i) {
        if (list.isEmpty() || i < 0) return;
        i = i % list.size();
        index = i;
        stopAudio();
        final int gen = GEN.incrementAndGet();
        final Entry e = list.get(i);
        playing = true; status = "Connecting: " + e.name();
        Thread t = new Thread(() -> run(e, gen), "SpearsRadio-play");
        t.setDaemon(true); t.start();
    }

    public static void next() { if (!list.isEmpty()) play(index + 1); }
    public static void prev() { if (!list.isEmpty()) play(index <= 0 ? list.size() - 1 : index - 1); }

    public static void toggle() {
        if (playing) { stop(); status = "Paused"; }
        else if (!list.isEmpty()) play(Math.max(index, 0));
    }

    public static void stop() { GEN.incrementAndGet(); stopAudio(); playing = false; status = "Stopped"; }

    private static void stopAudio() {
        SourceDataLine l = line; line = null;
        if (l != null) { try { l.stop(); l.flush(); l.close(); } catch (Exception ignored) {} }
    }

    public static void setVolume(float v) {
        volume = Math.max(0f, Math.min(1f, v));
        applyVolume(line);
    }

    private static void applyVolume(SourceDataLine l) {
        if (l == null) return;
        try {
            FloatControl g = (FloatControl) l.getControl(FloatControl.Type.MASTER_GAIN);
            float db = volume < 0.0001f ? g.getMinimum() : (float) (20.0 * Math.log10(volume));
            g.setValue(Math.max(g.getMinimum(), Math.min(g.getMaximum(), db)));
        } catch (Exception ignored) {}
    }

    private static void run(Entry e, int gen) {
        SourceDataLine ln = null;
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(e.url()).openConnection();
            c.setRequestProperty("User-Agent", "SpearsRadio/1.0");
            c.setConnectTimeout(8000); c.setReadTimeout(15000); c.setInstanceFollowRedirects(true);
            try (InputStream in = new BufferedInputStream(c.getInputStream(), 1 << 16)) {
                Bitstream bs = new Bitstream(in);
                Decoder dec = new Decoder();
                while (gen == GEN.get()) {
                    Header h = bs.readFrame();
                    if (h == null) break;
                    SampleBuffer sb = (SampleBuffer) dec.decodeFrame(h, bs);
                    if (ln == null) {
                        AudioFormat f = new AudioFormat(dec.getOutputFrequency(), 16, dec.getOutputChannels(), true, false);
                        ln = AudioSystem.getSourceDataLine(f);
                        ln.open(f, 1 << 16); ln.start();
                        line = ln; applyVolume(ln);
                        status = "Playing: " + e.name();
                    }
                    short[] s = sb.getBuffer(); int n = sb.getBufferLength();
                    byte[] b = new byte[n * 2];
                    for (int i = 0; i < n; i++) { b[2*i] = (byte) (s[i] & 0xff); b[2*i+1] = (byte) ((s[i] >> 8) & 0xff); }
                    ln.write(b, 0, b.length);
                    bs.closeFrame();
                }
            }
            if (gen == GEN.get()) {                       // track/stream ended on its own -> next entry
                if (ln != null) ln.drain();
                if (repeat == 2) play(index);                         // repeat one
                else if (repeat == 1) next();                         // repeat all (wraps to the start)
                else if (index + 1 < list.size()) play(index + 1);    // off: stop after the last entry
                else { playing = false; status = "Finished"; }
            }
        } catch (Exception ex) {
            if (gen == GEN.get()) { playing = false; status = "Error: " + ex.getMessage() + " (MP3 streams only)"; }
        } finally {
            if (ln != null) { try { ln.close(); } catch (Exception ignored) {} }
        }
    }
}
