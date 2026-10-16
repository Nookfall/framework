package com.nookfall.framework.processor;

import static com.nookfall.framework.processor.Fixtures.compile;
import static com.nookfall.framework.processor.Fixtures.source;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Each fixture must fail with a precise error at the offending declaration. */
class InvalidDeclarationsTest {

    private static List<String> errors(Fixtures.Source... sources) {
        Fixtures.Result result = compile(sources);
        assertThat(result.success()).as(result.output()).isFalse();
        return result.errors();
    }

    @Test
    void duplicateCommandNames() {
        List<String> errors = errors(source("demo.A", """
                        package demo;
                        @com.nookfall.framework.command.Command("homes")
                        public class A { @com.nookfall.framework.command.Execute void run() {} }
                        """), source("demo.B", """
                        package demo;
                        @com.nookfall.framework.command.Command("other")
                        @com.nookfall.framework.command.Aliases("homes")
                        public class B { @com.nookfall.framework.command.Execute void run() {} }
                        """));
        assertThat(errors)
                .anySatisfy(e -> assertThat(e).startsWith("B.java:3").contains("Duplicate command name 'homes'"));
    }

    @Test
    void unsupportedCommandArgument() {
        List<String> errors = errors(source("demo.C", """
                package demo;
                import com.nookfall.framework.command.*;
                @Command("c")
                public class C {
                    @Execute void run(org.bukkit.command.CommandSender sender,
                                      @Arg("map") java.util.Map<String, String> map) {}
                }
                """));
        assertThat(errors)
                .anySatisfy(e -> assertThat(e)
                        .startsWith("C.java:6")
                        .contains("Unsupported command argument type java.util.Map"));
    }

    @Test
    void parameterWithoutArgAnnotation() {
        List<String> errors = errors(source("demo.C", """
                package demo;
                import com.nookfall.framework.command.*;
                @Command("c")
                public class C {
                    @Execute void run(org.bukkit.entity.Player player, String name) {}
                }
                """));
        assertThat(errors).anySatisfy(e -> assertThat(e).contains("Parameter 'name' needs @Arg"));
    }

    @Test
    void requiredAfterOptionalAndBadDefaults() {
        List<String> errors = errors(source("demo.C", """
                package demo;
                import com.nookfall.framework.command.*;
                @Command("c")
                public class C {
                    @Execute void run(org.bukkit.command.CommandSender sender,
                                      @Arg(value = "a", optional = true) String a,
                                      @Arg("b") String b) {}
                    @Subcommand("x") void x(@Arg("n") @Default("ten") int n) {}
                    @Subcommand("y") void y(@Arg(value = "n", optional = true) int n) {}
                    @Subcommand("z") @Cooldown("soon") void z() {}
                }
                """));
        assertThat(errors)
                .anySatisfy(e -> assertThat(e).contains("Required argument 'b' cannot follow an optional argument"));
        assertThat(errors).anySatisfy(e -> assertThat(e).contains("Invalid @Default value 'ten' for int"));
        assertThat(errors).anySatisfy(e -> assertThat(e).contains("Optional primitive argument 'n' needs @Default"));
        assertThat(errors).anySatisfy(e -> assertThat(e).contains("Invalid cooldown 'soon'"));
    }

    @Test
    void conflictingHandlers() {
        // Two handlers at the same path whose arguments have the same types are ambiguous for Brigadier.
        List<String> errors = errors(source("demo.C", """
                package demo;
                import com.nookfall.framework.command.*;
                @Command("c")
                public class C {
                    @Subcommand("set") void a(@Arg("name") String name) {}
                    @Subcommand("set") void b(@Arg("other") String other) {}
                    @Subcommand("opt") void c(@Arg(value = "x", optional = true) String x) {}
                    @Subcommand("opt") void d() {}
                }
                """));
        assertThat(errors).anySatisfy(e -> assertThat(e).startsWith("C.java:6").contains("is handled by both a and b"));
        assertThat(errors).anySatisfy(e -> assertThat(e).startsWith("C.java:8").contains("is handled by both c and d"));
    }

    @Test
    void listenerMustImplementListener() {
        List<String> errors = errors(source("demo.L", """
                package demo;
                @com.nookfall.framework.listener.ListenerComponent
                public class L {}
                """));
        assertThat(errors)
                .anySatisfy(
                        e -> assertThat(e).startsWith("L.java:3").contains("must implement org.bukkit.event.Listener"));
    }

    @Test
    void invalidGuiSignaturesAndDuplicates() {
        List<String> errors = errors(source("demo.G", """
                package demo;
                import com.nookfall.framework.gui.*;
                @Gui("menu")
                public class G {
                    @GuiProvider("items") String items(GuiContext context) { return ""; }
                    @GuiAction("go") void go(GuiClickContext context) {}
                    @GuiAction("go") void go2(GuiClickContext context) {}
                    @GuiAction("close") void close(GuiClickContext context) {}
                    @GuiAction("bad") void bad(String text) {}
                    @GuiCondition("c") String c(GuiContext context) { return ""; }
                }
                """));
        assertThat(errors)
                .anySatisfy(
                        e -> assertThat(e).startsWith("G.java:5").contains("@GuiProvider methods must have signature"));
        assertThat(errors)
                .anySatisfy(e -> assertThat(e).startsWith("G.java:7").contains("Duplicate GUI action identifier 'go'"));
        assertThat(errors).anySatisfy(e -> assertThat(e).contains("conflicts with a built-in action"));
        assertThat(errors)
                .anySatisfy(e -> assertThat(e).contains("Unsupported @GuiAction parameter type java.lang.String"));
        assertThat(errors).anySatisfy(e -> assertThat(e).contains("@GuiCondition methods must have signature"));
    }

    @Test
    void duplicateGuiIds() {
        List<String> errors = errors(
                source("demo.G1", "package demo; @com.nookfall.framework.gui.Gui(\"menu\") public class G1 {}"),
                source("demo.G2", "package demo; @com.nookfall.framework.gui.Gui(\"menu\") public class G2 {}"));
        assertThat(errors).anySatisfy(e -> assertThat(e).contains("Duplicate GUI id 'menu'"));
    }

    @Test
    void invalidScheduling() {
        List<String> errors = errors(source("demo.S", """
                package demo;
                import com.nookfall.framework.task.Scheduled;
                public class S {
                    @Scheduled(every = "5 minutes") void a() {}
                    @Scheduled(every = "0s") void b() {}
                    @Scheduled(every = "1m") void c(String arg) {}
                    @Scheduled(every = "${}") void d() {}
                    @Scheduled(every = "1m") private void e() {}
                }
                """));
        assertThat(errors).anySatisfy(e -> assertThat(e).startsWith("S.java:4").contains("Invalid every '5 minutes'"));
        assertThat(errors)
                .anySatisfy(e -> assertThat(e).startsWith("S.java:5").contains("every must be greater than zero"));
        assertThat(errors).anySatisfy(e -> assertThat(e).startsWith("S.java:6").contains("cannot take parameters"));
        assertThat(errors).anySatisfy(e -> assertThat(e).startsWith("S.java:7").contains("must look like ${path}"));
        assertThat(errors)
                .anySatisfy(e -> assertThat(e).startsWith("S.java:8").contains("non-private instance methods"));
    }

    @Test
    void invalidConfigs() {
        List<String> errors = errors(
                source("demo.C1", """
                        package demo;
                        import com.nookfall.framework.config.*;
                        import com.nookfall.framework.constraint.*;
                        @Config("../escape.yml")
                        public class C1 {
                            private final int fixed = 1;
                            @Range(min = 1) private String text = "";
                            private Thread thread;
                            public C1(int x) {}
                        }
                        """),
                source("demo.C2", "package demo; @com.nookfall.framework.config.Config(\"a.yml\") public class C2 {}"),
                source("demo.C3", "package demo; @com.nookfall.framework.config.Config(\"a.yml\") public class C3 {}"));
        assertThat(errors).anySatisfy(e -> assertThat(e).contains("relative path ending in .yml"));
        assertThat(errors).anySatisfy(e -> assertThat(e).contains("'fixed' must not be final"));
        assertThat(errors).anySatisfy(e -> assertThat(e).contains("@Range is only allowed on numeric fields"));
        assertThat(errors).anySatisfy(e -> assertThat(e).contains("Unsupported configuration type java.lang.Thread"));
        assertThat(errors).anySatisfy(e -> assertThat(e).contains("needs a non-private no-argument constructor"));
        assertThat(errors).anySatisfy(e -> assertThat(e).contains("'a.yml' is already used by C2"));
    }

    @Test
    void invalidMessages() {
        List<String> errors = errors(source("demo.M", """
                package demo;
                import com.nookfall.framework.message.*;
                @Messages("x")
                public interface M {
                    void noRecipient(String value);
                    String wrongReturn(org.bukkit.entity.Player player);
                    void dup(org.bukkit.entity.Player player, @Param("a") String one, @Param("a") String two);
                }
                """));
        assertThat(errors).anySatisfy(e -> assertThat(e).startsWith("M.java:5").contains("needs the recipient"));
        assertThat(errors).anySatisfy(e -> assertThat(e).startsWith("M.java:6").contains("must return void"));
        assertThat(errors).anySatisfy(e -> assertThat(e).contains("Duplicate placeholder name 'a'"));
    }

    @Test
    void missingTranslationKeysAndInvalidLayouts(@TempDir Path resources) throws IOException {
        Files.createDirectories(resources.resolve("lang"));
        Files.writeString(resources.resolve("lang/en_US.yml"), "x:\n  present: hi\n");
        Files.createDirectories(resources.resolve("guis/menu"));
        Files.writeString(resources.resolve("guis/menu/layout.yml"), """
                rows: 2
                layout:
                  - "AAAAAAAA"
                  - "BBBBBBBBZ"
                elements:
                  a: { symbol: "A", type: button, action: missing_action, item: { material: STONE } }
                  b: { symbol: "B", type: collection, provider: nope, item-template: t }
                templates:
                  t: { material: STONE }
                """);
        Fixtures.Result result = compile(
                resources,
                source("demo.M", """
                        package demo;
                        @com.nookfall.framework.message.Messages("x")
                        public interface M {
                            void present(org.bukkit.entity.Player player);
                            void absent(org.bukkit.entity.Player player);
                        }
                        """),
                source("demo.G", "package demo; @com.nookfall.framework.gui.Gui(\"menu\") public class G {}"));
        List<String> errors = result.errors();
        assertThat(errors).anySatisfy(e -> assertThat(e).startsWith("M.java:5").contains("'x.absent' is missing"));
        assertThat(errors).anySatisfy(e -> assertThat(e).contains("layout row 1 must have exactly 9 characters"));
        assertThat(errors).anySatisfy(e -> assertThat(e).contains("undeclared symbol 'Z'"));
        assertThat(errors).anySatisfy(e -> assertThat(e).contains("'missing_action' is not declared in G"));
        assertThat(errors).anySatisfy(e -> assertThat(e).contains("'nope' is not declared in G"));
    }

    @Test
    void componentsWithoutPluginClass() {
        // Without a JavaPlugin in the compilation the index cannot be generated.
        Fixtures.Result result = Fixtures.compileWithoutPlugin(source("demo.C", """
                package demo;
                @com.nookfall.framework.command.Command("c")
                public class C { @com.nookfall.framework.command.Execute void run() {} }
                """));
        assertThat(result.errors()).anySatisfy(e -> assertThat(e).contains("no JavaPlugin subclass"));
    }
}
