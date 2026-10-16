package com.nookfall.harness;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

/**
 * Boots real Paper servers with the probe plugins and checks the results they record. Each scenario starts a fresh
 * plugins folder on a per-version server directory.
 */
public final class Harness {

    private static final String USER_AGENT = "nookfall-framework-tests (https://nookfall.com)";
    private static final Duration SCENARIO_TIMEOUT = Duration.ofMinutes(4);

    record Scenario(
            String name,
            List<String> jars,
            List<String> probes,
            Map<String, String> files,
            List<String> logMustContain,
            List<String> extraPasses) {}

    record Outcome(String version, String scenario, boolean ok, List<String> problems, int passes, Duration time) {}

    private final Map<String, String> options;
    private final Path work;

    private Harness(Map<String, String> options) {
        this.options = options;
        this.work = Path.of(options.get("work"));
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> options = new LinkedHashMap<>();
        for (String arg : args) {
            int eq = arg.indexOf('=');
            options.put(arg.substring(2, eq), arg.substring(eq + 1));
        }
        System.exit(new Harness(options).run() ? 0 : 1);
    }

    private List<Scenario> scenarios() {
        String brokenConfig = "answer: [unclosed\n";
        List<Scenario> all = List.of(
                new Scenario("standalone-pair", List.of("alpha.standalone", "beta.standalone"),
                        List.of("ProbeAlpha", "ProbeBeta"), Map.of(), List.of(),
                        List.of("cycle-disable-listeners", "cycle-reenable-handled")),
                new Scenario("shared-pair", List.of("framework", "alpha.shared", "beta.shared"),
                        List.of("ProbeAlpha", "ProbeBeta"), Map.of(),
                        List.of("[NookfallFramework] Nookfall " + options.getOrDefault("frameworkVersion", "")),
                        List.of("cycle-disable-listeners", "cycle-reenable-handled", "shared-registry")),
                new Scenario("mixed", List.of("framework", "alpha.shared", "beta.standalone"),
                        List.of("ProbeAlpha", "ProbeBeta"), Map.of(), List.of(), List.of()),
                new Scenario("shared-missing-runtime", List.of("alpha.shared"), List.of(), Map.of(),
                        List.of("NookfallFramework"), List.of()),
                new Scenario("shared-incompatible", List.of("framework", "incompatible.shared"), List.of(), Map.of(),
                        List.of("ProbeIncompatible was built for Nookfall Framework 0.9.0"), List.of()),
                new Scenario("startup-failure", List.of("alpha.standalone", "beta.standalone"), List.of("ProbeAlpha"),
                        Map.of("ProbeBeta/config.yml", brokenConfig),
                        List.of("ProbeBeta could not start", "config.yml is not valid YAML"),
                        List.of("failed-startup-cleanup")));
        String selected = options.getOrDefault("scenarios", "all");
        if (selected.equals("all")) {
            return all;
        }
        List<String> names = List.of(selected.split(","));
        return all.stream().filter(s -> names.contains(s.name())).toList();
    }

    private boolean run() throws Exception {
        List<String> packaging = verifyPackaging();
        List<Outcome> outcomes = new ArrayList<>();
        for (String version : options.get("versions").split(",")) {
            Path server = prepareServer(version.strip());
            for (Scenario scenario : scenarios()) {
                Outcome outcome = runScenario(version.strip(), server, scenario);
                outcomes.add(outcome);
                System.out.printf("[%s] %-24s %s (%d checks, %ds)%s%n", version, scenario.name(),
                        outcome.ok() ? "PASS" : "FAIL", outcome.passes(), outcome.time().toSeconds(),
                        outcome.ok() ? "" : " " + outcome.problems());
            }
        }
        return report(packaging, outcomes);
    }

    // ---- packaging ----

    /** Checks jar contents: plugin.yml, relocation, no duplicated framework classes, resolvable service entries. */
    private List<String> verifyPackaging() throws IOException {
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, String> entry : options.entrySet()) {
            if (!entry.getKey().startsWith("jar.")) {
                continue;
            }
            String id = entry.getKey().substring(4);
            Path jar = Path.of(entry.getValue());
            try (ZipFile zip = new ZipFile(jar.toFile())) {
                List<String> names = zip.stream().map(e -> e.getName()).toList();
                String yml = new String(zip.getInputStream(zip.getEntry("plugin.yml")).readAllBytes(), StandardCharsets.UTF_8);
                boolean shared = id.endsWith(".shared");
                if (shared != yml.contains("NookfallFramework\"]") && !id.equals("framework")) {
                    problems.add(id + ": plugin.yml depend does not match the packaging mode");
                }
                if (shared && names.stream().anyMatch(n -> n.startsWith("com/nookfall/framework/") || n.startsWith("io/avaje/"))) {
                    problems.add(id + ": shared jar contains framework or Avaje classes");
                }
                if (id.endsWith(".standalone")) {
                    if (names.stream().anyMatch(n -> n.startsWith("com/nookfall/framework/") || n.startsWith("io/avaje/")
                            || n.startsWith("jakarta/inject/"))) {
                        problems.add(id + ": standalone jar contains unrelocated framework classes");
                    }
                    if (names.stream().noneMatch(n -> n.contains("/libs/nookfall/Nookfall.class"))) {
                        problems.add(id + ": standalone jar does not bundle the relocated framework");
                    }
                }
                for (String name : names) {
                    if (name.startsWith("META-INF/services/") && !name.endsWith("/")) {
                        String content = new String(zip.getInputStream(zip.getEntry(name)).readAllBytes(), StandardCharsets.UTF_8);
                        for (String provider : content.split("\\R")) {
                            provider = provider.strip();
                            if (provider.isEmpty() || provider.startsWith("#")) {
                                continue;
                            }
                            String classFile = provider.replace('.', '/') + ".class";
                            boolean local = names.contains(classFile);
                            boolean fromFramework = shared && provider.startsWith("com.nookfall.framework.");
                            if (!local && !fromFramework) {
                                problems.add(id + ": " + name + " lists " + provider + " which is not in the jar");
                            }
                        }
                    }
                }
                System.out.printf("packaging %-20s %8d bytes, %d entries%n", id, Files.size(jar), names.size());
            }
        }
        return problems;
    }

    // ---- servers ----

    private Path prepareServer(String version) throws Exception {
        Path dir = work.resolve(version);
        Files.createDirectories(dir.resolve("plugins"));
        Path jar = dir.resolve("paper.jar");
        if (!Files.exists(jar)) {
            download(version, jar);
        }
        Files.writeString(dir.resolve("eula.txt"), "eula=true\n");
        Files.writeString(dir.resolve("server.properties"), """
                online-mode=false
                level-type=minecraft\\:flat
                generate-structures=false
                spawn-protection=0
                max-players=4
                view-distance=3
                simulation-distance=3
                enable-rcon=false
                enable-query=false
                motd=Nookfall integration test
                server-port=%d
                """.formatted(freePort()));
        return dir;
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private void download(String version, Path target) throws Exception {
        HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
        String api = "https://fill.papermc.io/v3/projects/paper/versions/" + version + "/builds/latest";
        String json = http.send(request(api), HttpResponse.BodyHandlers.ofString()).body();
        Matcher url = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        Matcher sha = Pattern.compile("\"sha256\"\\s*:\\s*\"([0-9a-f]{64})\"").matcher(json);
        if (!url.find() || !sha.find()) {
            throw new IllegalStateException("No Paper build for " + version + ": " + json);
        }
        System.out.println("Downloading Paper " + version + " from " + url.group(1));
        byte[] bytes = http.send(request(url.group(1)), HttpResponse.BodyHandlers.ofByteArray()).body();
        String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        if (!actual.equals(sha.group(1))) {
            throw new IllegalStateException("Checksum mismatch for Paper " + version);
        }
        Files.write(target, bytes);
    }

    private static HttpRequest request(String url) {
        return HttpRequest.newBuilder(URI.create(url)).header("User-Agent", USER_AGENT).build();
    }

    private String javaFor(String version) {
        return version.startsWith("1.") ? options.get("java21") : options.get("java25");
    }

    private Outcome runScenario(String version, Path server, Scenario scenario) throws Exception {
        Instant start = Instant.now();
        Path plugins = server.resolve("plugins");
        cleanPlugins(plugins);
        for (String id : scenario.jars()) {
            Path jar = Path.of(options.get("jar." + id));
            Files.copy(jar, plugins.resolve(jar.getFileName()));
        }
        for (Map.Entry<String, String> file : scenario.files().entrySet()) {
            Path target = plugins.resolve(file.getKey());
            Files.createDirectories(target.getParent());
            Files.writeString(target, file.getValue());
        }
        Path log = server.resolve("logs-" + scenario.name() + ".txt");
        Process process = new ProcessBuilder(javaFor(version), "-Xms512M", "-Xmx1536M",
                        "-Dcom.mojang.eula.agree=true", "-jar", "paper.jar", "--nogui")
                .directory(server.toFile())
                .redirectErrorStream(true)
                .start();
        StringBuilder output = new StringBuilder();
        Thread reader = Thread.ofPlatform().daemon().start(() -> copy(process.getInputStream(), output, log));
        PrintWriter console = new PrintWriter(process.getOutputStream(), true, StandardCharsets.UTF_8);

        Instant deadline = Instant.now().plus(SCENARIO_TIMEOUT);
        boolean started = false;
        boolean finished = false;
        while (Instant.now().isBefore(deadline) && process.isAlive()) {
            Thread.sleep(500);
            String text;
            synchronized (output) {
                text = output.toString();
            }
            if (!started && text.contains("Done (")) {
                started = true;
            }
            if (started && probesDone(plugins, scenario)) {
                finished = true;
                break;
            }
            if (started && scenario.probes().isEmpty() && Duration.between(start, Instant.now()).toSeconds() > 5) {
                finished = true;
                break;
            }
        }
        console.println("stop");
        if (!process.waitFor(90, java.util.concurrent.TimeUnit.SECONDS)) {
            process.destroyForcibly();
        }
        reader.join(5000);
        String text;
        synchronized (output) {
            text = output.toString();
        }
        List<String> problems = new ArrayList<>();
        if (!started) {
            problems.add("server did not finish starting");
        }
        if (!finished) {
            problems.add("timed out waiting for probes");
        }
        int passes = 0;
        for (String probe : scenario.probes()) {
            Path results = plugins.resolve(probe).resolve("results.txt");
            if (!Files.exists(results)) {
                problems.add(probe + ": no results");
                continue;
            }
            for (String line : Files.readAllLines(results)) {
                if (line.startsWith("FAIL")) {
                    problems.add(probe + ": " + line);
                } else if (line.startsWith("PASS")) {
                    passes++;
                } else if (line.startsWith("DISABLE") && !line.contains("listeners=0")) {
                    problems.add(probe + ": " + line);
                } else if (line.startsWith("DISABLE") && line.contains("runtime=true") && !line.contains("tasks=0")) {
                    problems.add(probe + ": " + line);
                }
            }
            String all = Files.readString(results);
            String expectedMode = scenario.jars().stream().filter(j -> j.startsWith(probe.substring(5).toLowerCase()))
                    .findFirst().map(j -> j.endsWith(".shared") ? "SHARED" : "STANDALONE").orElse("?");
            if (!all.contains("INFO mode: " + expectedMode)) {
                problems.add(probe + ": expected mode " + expectedMode);
            }
        }
        for (String pass : scenario.extraPasses()) {
            boolean found = false;
            for (String probe : List.of("ProbeAlpha", "ProbeBeta")) {
                Path results = plugins.resolve(probe).resolve("results.txt");
                if (Files.exists(results) && Files.readString(results).contains("PASS " + pass)) {
                    found = true;
                }
            }
            if (!found) {
                problems.add("missing PASS " + pass);
            }
        }
        for (String expected : scenario.logMustContain()) {
            if (!text.contains(expected)) {
                problems.add("log lacks: " + expected);
            }
        }
        for (String forbidden : List.of("ServiceConfigurationError", "NoClassDefFoundError", "ClassCastException",
                "LinkageError", "IllegalAccessError", "ClassNotFoundException")) {
            if (text.contains(forbidden)) {
                problems.add("log contains " + forbidden);
            }
        }
        if (scenario.name().equals("shared-missing-runtime") && Files.exists(plugins.resolve("ProbeAlpha/results.txt"))) {
            problems.add("ProbeAlpha ran without the framework plugin");
        }
        if (scenario.extraPasses().contains("cycle-reenable-handled")) {
            // Wait for the cycle performed by ProbeAlpha after its own checks.
            Path alpha = plugins.resolve("ProbeAlpha/results.txt");
            if (!Files.exists(alpha) || !Files.readString(alpha).contains("INFO cycle-reenable")) {
                problems.add("ProbeAlpha did not run the disable/enable cycle");
            }
        }
        return new Outcome(version, scenario.name(), problems.isEmpty(), problems, passes,
                Duration.between(start, Instant.now()));
    }

    private static boolean probesDone(Path plugins, Scenario scenario) throws IOException {
        for (String probe : scenario.probes()) {
            Path results = plugins.resolve(probe).resolve("results.txt");
            if (!Files.exists(results)) {
                return false;
            }
            String text = Files.readString(results);
            if (!text.contains("DONE run=1")) {
                return false;
            }
        }
        return true;
    }

    private static void cleanPlugins(Path plugins) throws IOException {
        try (Stream<Path> entries = Files.list(plugins)) {
            for (Path entry : entries.toList()) {
                if (entry.getFileName().toString().startsWith(".paper-remapped")) {
                    continue;
                }
                deleteRecursively(entry);
            }
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (Stream<Path> walk = Files.walk(path)) {
                for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(p);
                }
            }
        } else {
            Files.deleteIfExists(path);
        }
    }

    private static void copy(InputStream in, StringBuilder output, Path log) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                OutputStream file = Files.newOutputStream(log)) {
            String line;
            while ((line = reader.readLine()) != null) {
                synchronized (output) {
                    output.append(line).append('\n');
                }
                file.write((line + "\n").getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private boolean report(List<String> packaging, List<Outcome> outcomes) throws IOException {
        StringBuilder md = new StringBuilder("# Paper integration results\n\n");
        md.append("Generated ").append(Instant.now()).append("\n\n## Packaging\n\n");
        md.append(packaging.isEmpty() ? "All jars passed packaging checks.\n" : String.join("\n", packaging) + "\n");
        md.append("\n## Scenarios\n\n| Paper | Scenario | Result | Checks passed | Time |\n|---|---|---|---|---|\n");
        boolean ok = packaging.isEmpty();
        for (Outcome outcome : outcomes) {
            ok &= outcome.ok();
            md.append("| ").append(outcome.version()).append(" | ").append(outcome.scenario()).append(" | ")
                    .append(outcome.ok() ? "pass" : "FAIL: " + String.join("; ", outcome.problems()).replace("|", "\\|"))
                    .append(" | ").append(outcome.passes()).append(" | ").append(outcome.time().toSeconds()).append("s |\n");
        }
        Path file = Path.of(options.get("report"));
        Files.createDirectories(file.getParent());
        Files.writeString(file, md);
        System.out.println(md);
        packaging.forEach(p -> System.out.println("PACKAGING PROBLEM: " + p));
        return ok;
    }
}
