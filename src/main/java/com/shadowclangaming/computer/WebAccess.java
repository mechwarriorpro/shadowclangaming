package com.shadowclangaming.computer;

import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The web card. Fetches text from the internet and hands it back to the terminal.
 *
 * THREE GUARDS, ALL OF THEM DELIBERATE:
 *
 *  1. ALLOWLIST. Nothing is reachable unless a host is listed in config/shadowclangaming/web.txt. The file
 *     ships empty, which means the card fetches nothing until a server owner opts in by name. This is
 *     the difference between "a computer that can read a wiki page" and "a computer that can be used
 *     to attack somebody else's server from yours".
 *
 *  2. NO LOCAL NETWORK. Hosts that resolve to loopback, link-local, site-local or any-address are
 *     refused even if allowlisted, so the card cannot be pointed at a router admin page, a cloud
 *     metadata endpoint, or another service on the same machine.
 *
 *  3. BOUNDED. 5 second timeout, 200 KB cap, plain text only. A terminal is not a downloader.
 *
 * The fetch is asynchronous and hands its result back on the server thread, so a slow site can never
 * stall a tick.
 */
public final class WebAccess {

    public static final int MAX_BYTES = 200_000;
    public static final Duration TIMEOUT = Duration.ofSeconds(5);
    /** Lines of response a terminal will keep. */
    public static final int MAX_LINES = 12;

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static volatile Set<String> allowlist;
    private static volatile long allowlistLoadedAt = -1L;

    private WebAccess() {
    }

    public static Path configFile() {
        for (String candidate : new String[]{"config", "run/config"}) {
            Path dir = Paths.get(candidate);
            if (Files.isDirectory(dir)) {
                return dir.resolve("shadowclangaming").resolve("web.txt");
            }
        }
        return Paths.get("config", "shadowclangaming", "web.txt");
    }

    /** Reads (and creates) the allowlist. Cached for 10 seconds so /term does not hit the disk. */
    public static Set<String> allowlist() {
        long now = System.currentTimeMillis();
        if (allowlist != null && now - allowlistLoadedAt < 10_000L) {
            return allowlist;
        }
        Set<String> hosts = new HashSet<>();
        Path file = configFile();
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, """
                        # ShadowClanGaming: hosts the terminal's web card may read.
                        # One host per line, no scheme, no path. Lines starting with # are ignored.
                        # The card is completely inert while this list is empty.
                        #
                        # example:
                        # wiki.example.org
                        #
                        """, StandardCharsets.UTF_8);
            }
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (String line : lines) {
                String host = line.trim().toLowerCase(Locale.ROOT);
                if (host.isEmpty() || host.startsWith("#")) {
                    continue;
                }
                hosts.add(host);
            }
        } catch (IOException e) {
            // A broken config file means no access, never "allow everything".
            return Set.of();
        }
        allowlist = hosts;
        allowlistLoadedAt = now;
        return hosts;
    }

    /** Why a URL cannot be fetched, or null if it can. */
    public static String refusal(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            return "malformed url";
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            return "only http and https";
        }
        String host = uri.getHost();
        if (host == null) {
            return "no host in url";
        }
        if (!allowlist().contains(host.toLowerCase(Locale.ROOT))) {
            return "host not in " + configFile();
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (address.isLoopbackAddress() || address.isSiteLocalAddress()
                        || address.isLinkLocalAddress() || address.isAnyLocalAddress()) {
                    return "refused: private address";
                }
            }
        } catch (IOException e) {
            return "cannot resolve host";
        }
        return null;
    }

    /**
     * Fetch a URL and hand the body - or an error line - to {@code callback}.
     * The callback always runs on the server thread.
     */
    public static void fetch(MinecraftServer server, String url, Consumer<List<String>> callback) {
        String refusal = refusal(url);
        if (refusal != null) {
            callback.accept(List.of("net: " + refusal));
            return;
        }
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("User-Agent", "ShadowClanGaming-Terminal/1.0")
                    .GET()
                    .build();
        } catch (IllegalArgumentException e) {
            callback.accept(List.of("net: malformed url"));
            return;
        }

        CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .orTimeout(TIMEOUT.toSeconds() + 2L, java.util.concurrent.TimeUnit.SECONDS)
                .whenComplete((response, error) -> {
                    List<String> lines;
                    if (error != null || response == null) {
                        lines = List.of("net: " + (error == null ? "no response" : error.getClass().getSimpleName()));
                    } else if (response.statusCode() / 100 != 2) {
                        lines = List.of("net: HTTP " + response.statusCode());
                    } else {
                        byte[] body = response.body();
                        int length = Math.min(body.length, MAX_BYTES);
                        String text = new String(body, 0, length, StandardCharsets.UTF_8);
                        lines = summarise(text);
                    }
                    final List<String> result = lines;
                    server.execute(() -> callback.accept(result));
                });
    }

    /** Cuts HTML down to readable text and keeps the first few lines of it. */
    static List<String> summarise(String body) {
        String text = body
                .replaceAll("(?is)<script.*?</script>", " ")
                .replaceAll("(?is)<style.*?</style>", " ")
                .replaceAll("(?s)<[^>]*>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replaceAll("\\s+", " ")
                .trim();

        java.util.List<String> out = new java.util.ArrayList<>();
        int index = 0;
        while (index < text.length() && out.size() < MAX_LINES) {
            out.add(text.substring(index, Math.min(text.length(), index + 68)));
            index += 68;
        }
        if (out.isEmpty()) {
            out.add("net: empty response");
        }
        return out;
    }
}
