package com.nookfall.framework.processor;

import io.avaje.inject.generator.InjectProcessor;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Compiles fixture sources with the real javac, the Nookfall processor and Avaje's processor, writing to a temporary
 * directory as a plugin build would.
 */
final class Fixtures {

    static final String PLUGIN = """
            package demo;

            public final class DemoPlugin extends org.bukkit.plugin.java.JavaPlugin {
            }
            """;

    record Source(String name, String code) {}

    /** Result of a compilation; {@code output} holds javac's diagnostics as printed on the command line. */
    record Result(boolean success, String output, Path generated, Path classes) {

        /** Error lines as {@code File.java:line: message}. */
        List<String> errors() {
            List<String> out = new ArrayList<>();
            for (String line : output.split("\\R")) {
                int marker = line.indexOf(": error: ");
                if (marker > 0) {
                    String location = line.substring(0, marker);
                    out.add(Path.of(location.substring(0, location.lastIndexOf(':')))
                                    .getFileName() + location.substring(location.lastIndexOf(':')) + ": "
                            + line.substring(marker + 9));
                }
            }
            return out;
        }

        boolean hasGenerated(String qualifiedName) {
            return Files.exists(generated.resolve(qualifiedName.replace('.', '/') + ".java"));
        }

        String generatedSource(String qualifiedName) {
            try {
                return Files.readString(generated.resolve(qualifiedName.replace('.', '/') + ".java"));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private Fixtures() {}

    static Source source(String name, String code) {
        return new Source(name, code);
    }

    static Result compile(Source... sources) {
        return compile(null, sources);
    }

    static Result compile(Path resources, Source... sources) {
        return compile(resources, true, sources);
    }

    static Result compileWithoutPlugin(Source... sources) {
        return compile(null, false, sources);
    }

    private static Result compile(Path resources, boolean withPlugin, Source... sources) {
        try {
            Path root = Files.createTempDirectory("nookfall-fixture");
            Path src = root.resolve("src");
            Path generated = root.resolve("generated");
            Path classes = root.resolve("classes");
            Files.createDirectories(generated);
            Files.createDirectories(classes);
            List<Source> all = new ArrayList<>(List.of(sources));
            if (withPlugin) {
                all.add(source("demo.DemoPlugin", PLUGIN));
            }
            List<Path> files = new ArrayList<>();
            for (Source source : all) {
                Path file = src.resolve(source.name().replace('.', '/') + ".java");
                Files.createDirectories(file.getParent());
                Files.writeString(file, source.code());
                files.add(file);
            }
            String classpath = System.getProperty("java.class.path");
            List<String> args = new ArrayList<>(List.of(
                    "-proc:full",
                    "-classpath",
                    classpath,
                    "-processorpath",
                    classpath,
                    "-processor",
                    NookfallProcessor.class.getName() + "," + InjectProcessor.class.getName(),
                    "-d",
                    classes.toString(),
                    "-s",
                    generated.toString(),
                    "--release",
                    "21",
                    "-Xmaxerrs",
                    "1000"));
            if (resources != null) {
                args.add("-Anookfall.resources=" + resources);
            }
            for (Path file : files) {
                args.add(file.toString());
            }
            // A separate javac process per compilation: Avaje's processor keeps static state, and a build tool
            // also gives each compilation fresh processor class loaders.
            Path argFile = root.resolve("javac.args");
            List<String> quoted = new ArrayList<>();
            for (String arg : args) {
                quoted.add("\"" + arg.replace("\\", "\\\\").replace("\"", "\\\"") + "\"");
            }
            Files.write(argFile, quoted);
            Path javac = Path.of(System.getProperty("java.home"), "bin", "javac");
            Process process = new ProcessBuilder(javac.toString(), "@" + argFile)
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int status;
            try {
                status = process.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            return new Result(status == 0, output, generated, classes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static List<String> generatedFiles(Result result) {
        try (Stream<Path> files = Files.walk(result.generated())) {
            return files.filter(Files::isRegularFile)
                    .map(p -> result.generated().relativize(p).toString())
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
