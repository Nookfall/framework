package com.nookfall.framework.gradle;

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.process.CommandLineArgumentProvider;

/**
 * Configures a Paper plugin project for Nookfall: dependencies, annotation processors, plugin.yml generation and two
 * jars built from the same classes:
 *
 * <ul>
 *   <li>{@code standaloneJar}: {@code <name>-<version>.jar}, bundles and relocates the framework.
 *   <li>{@code sharedJar}: {@code <name>-<version>-shared.jar}, requires the NookfallFramework plugin.
 * </ul>
 */
public final class NookfallGradlePlugin implements Plugin<Project> {

    /** Package the NookfallFramework plugin relocates its own libraries to. Shared jars must use the same names. */
    static final String SHARED_LIBS = "com.nookfall.framework.libs";
    static final String FRAMEWORK_PLUGIN = "NookfallFramework";

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(JavaPlugin.class);
        project.getPluginManager().apply("com.gradleup.shadow");
        Properties versions = versions();

        NookfallExtension ext = project.getExtensions().create("nookfall", NookfallExtension.class);
        ext.getPluginName().convention(project.getName());
        ext.getPluginVersion().convention(project.provider(() -> project.getVersion().toString()));
        ext.getApiVersion().convention("1.21");
        ext.getFrameworkVersion().convention(versions.getProperty("framework"));
        ext.getPaperVersion().convention(versions.getProperty("paper"));
        ext.getStorage().convention(false);
        ext.getAddDependencies().convention(true);
        ext.getRelocationPrefix().convention(ext.getMainClass().map(main -> {
            int dot = main.lastIndexOf('.');
            return (dot < 0 ? "nookfall" : main.substring(0, dot)) + ".libs";
        }));

        JavaPluginExtension java = project.getExtensions().getByType(JavaPluginExtension.class);
        java.getToolchain().getLanguageVersion().convention(JavaLanguageVersion.of(21));

        addDependencies(project, ext, versions.getProperty("avaje"));
        configureCompiler(project, java);

        Provider<Directory> ymlRoot = project.getLayout().getBuildDirectory().dir("nookfall");
        TaskProvider<GeneratePluginYml> standaloneYml =
                registerYml(project, ext, "generateStandalonePluginYml", ymlRoot.map(d -> d.dir("standalone")), false);
        TaskProvider<GeneratePluginYml> sharedYml =
                registerYml(project, ext, "generateSharedPluginYml", ymlRoot.map(d -> d.dir("shared")), true);

        SourceSet main = java.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        Configuration runtime = project.getConfigurations().getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME);

        TaskProvider<ShadowJar> standalone = project.getTasks().register("standaloneJar", ShadowJar.class, jar -> {
            jar.setGroup("nookfall");
            jar.setDescription("Builds the plugin jar with the framework bundled and relocated.");
            jar.getArchiveBaseName().set(ext.getPluginName());
            jar.getArchiveVersion().set(ext.getPluginVersion());
            jar.getArchiveClassifier().set("");
            jar.from(main.getOutput());
            jar.from(standaloneYml);
            jar.getConfigurations().set(List.of(runtime));
            String prefix = ext.getRelocationPrefix().get();
            jar.relocate("com.nookfall.framework", prefix + ".nookfall", r -> {});
            jar.relocate("io.avaje", prefix + ".avaje", r -> {});
            jar.relocate("jakarta.inject", prefix + ".jakarta.inject", r -> {});
            jar.relocate("com.zaxxer.hikari", prefix + ".hikari", r -> {});
            jar.mergeServiceFiles();
            jar.dependencies(filter -> {
                filter.exclude(filter.dependency("org.jspecify:.*:.*"));
                filter.exclude(filter.dependency("org.slf4j:.*:.*"));
            });
            excludeMetadata(jar);
        });

        TaskProvider<ShadowJar> shared = project.getTasks().register("sharedJar", ShadowJar.class, jar -> {
            jar.setGroup("nookfall");
            jar.setDescription("Builds the plugin jar that uses the installed NookfallFramework plugin.");
            jar.getArchiveBaseName().set(ext.getPluginName());
            jar.getArchiveVersion().set(ext.getPluginVersion());
            jar.getArchiveClassifier().set("shared");
            jar.from(main.getOutput());
            jar.from(sharedYml);
            jar.getConfigurations().set(List.of(runtime));
            // Same names the NookfallFramework plugin uses for its bundled libraries.
            jar.relocate("io.avaje", SHARED_LIBS + ".avaje", r -> {});
            jar.relocate("jakarta.inject", SHARED_LIBS + ".jakarta.inject", r -> {});
            jar.relocate("com.zaxxer.hikari", SHARED_LIBS + ".hikari", r -> {});
            jar.mergeServiceFiles();
            jar.dependencies(filter -> {
                // Provided by the NookfallFramework plugin.
                for (String group : List.of("com.nookfall", "io.avaje", "jakarta.inject", "org.jspecify", "com.zaxxer",
                        "org.slf4j")) {
                    filter.exclude(filter.dependency(group + ":.*:.*"));
                }
            });
            excludeMetadata(jar);
        });

        project.getTasks().named("shadowJar").configure(task -> task.setEnabled(false));
        project.getTasks().named("assemble").configure(task -> task.dependsOn(standalone, shared));
        project.afterEvaluate(p -> {
            if (!ext.getMainClass().isPresent()) {
                throw new GradleException("Set nookfall.mainClass to your JavaPlugin class in " + p.getPath());
            }
        });
    }

    private static void excludeMetadata(ShadowJar jar) {
        // Service files from several jars must all reach the merging transformer.
        jar.setDuplicatesStrategy(org.gradle.api.file.DuplicatesStrategy.INCLUDE);
        // No server internals are used, so Paper does not need to remap the jar when loading it.
        jar.getManifest().getAttributes().put("paperweight-mappings-namespace", "mojang");
        jar.exclude("META-INF/maven/**", "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "module-info.class",
                "META-INF/versions/*/module-info.class", "META-INF/proguard/**", "META-INF/native-image/**");
    }

    private static TaskProvider<GeneratePluginYml> registerYml(
            Project project, NookfallExtension ext, String name, Provider<Directory> output, boolean shared) {
        return project.getTasks().register(name, GeneratePluginYml.class, task -> {
            task.setGroup("nookfall");
            task.getPluginName().set(ext.getPluginName());
            task.getMainClass().set(ext.getMainClass());
            task.getPluginVersion().set(ext.getPluginVersion());
            task.getPluginDescription().set(ext.getDescription());
            task.getAuthors().set(ext.getAuthors());
            task.getWebsite().set(ext.getWebsite());
            task.getApiVersion().set(ext.getApiVersion());
            task.getSoftDepend().set(ext.getSoftDepend());
            task.getLoadBefore().set(ext.getLoadBefore());
            task.getOutputDirectory().set(output);
            if (shared) {
                task.getDepend().set(ext.getDepend().map(list -> {
                    List<String> all = new ArrayList<>();
                    all.add(FRAMEWORK_PLUGIN);
                    for (String dependency : list) {
                        if (!dependency.equals(FRAMEWORK_PLUGIN)) {
                            all.add(dependency);
                        }
                    }
                    return all;
                }));
            } else {
                task.getDepend().set(ext.getDepend());
            }
            File manual = project.file("src/main/resources/plugin.yml");
            task.doFirst(t -> {
                if (manual.exists()) {
                    throw new GradleException(
                            "Remove src/main/resources/plugin.yml; the nookfall { } block generates it for each jar");
                }
            });
        });
    }

    private static void addDependencies(Project project, NookfallExtension ext, String avajeVersion) {
        var dependencies = project.getDependencies();
        project.getConfigurations().named("compileOnly").configure(c -> c.getDependencies().addAllLater(
                ext.getAddDependencies().zip(ext.getPaperVersion(), (add, paper) -> add
                        ? List.of(dependencies.create("io.papermc.paper:paper-api:" + paper))
                        : List.of())));
        project.getConfigurations().named("implementation").configure(c -> c.getDependencies().addAllLater(
                ext.getAddDependencies().zip(ext.getFrameworkVersion(), (add, version) -> add
                        ? List.of(dependencies.create("com.nookfall:framework-core:" + version))
                        : List.of())));
        project.getConfigurations().named("implementation").configure(c -> c.getDependencies().addAllLater(
                ext.getStorage().zip(ext.getFrameworkVersion(), (storage, version) -> storage
                        ? List.of(dependencies.create("com.nookfall:framework-storage:" + version))
                        : List.of())));
        project.getConfigurations().named("annotationProcessor").configure(c -> c.getDependencies().addAllLater(
                ext.getAddDependencies().zip(ext.getFrameworkVersion(), (add, version) -> add
                        ? List.of(
                                dependencies.create("com.nookfall:framework-processor:" + version),
                                dependencies.create("io.avaje:avaje-inject-generator:" + avajeVersion))
                        : List.of())));
    }

    private static void configureCompiler(Project project, JavaPluginExtension java) {
        SourceSet main = java.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        project.getTasks().named(main.getCompileJavaTaskName(), JavaCompile.class).configure(compile -> {
            ResourcesArgument argument = project.getObjects().newInstance(ResourcesArgument.class);
            argument.getResources().set(project.getLayout().getProjectDirectory().dir("src/main/resources"));
            compile.getOptions().getCompilerArgumentProviders().add(argument);
            compile.getOptions().getRelease().convention(21);
            compile.getOptions().setEncoding("UTF-8");
            compile.getOptions().getCompilerArgs().add("-parameters");
        });
    }

    /** Passes the resource directory to the processor so it can validate translations and GUI layouts. */
    public abstract static class ResourcesArgument implements CommandLineArgumentProvider {

        @InputFiles
        @PathSensitive(PathSensitivity.RELATIVE)
        public abstract DirectoryProperty getResources();

        @Override
        public Iterable<String> asArguments() {
            return List.of("-Anookfall.resources=" + getResources().get().getAsFile().getAbsolutePath());
        }
    }

    private static Properties versions() {
        try (InputStream in = NookfallGradlePlugin.class.getResourceAsStream("/META-INF/nookfall/gradle-plugin.properties")) {
            Properties properties = new Properties();
            properties.load(in);
            return properties;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
