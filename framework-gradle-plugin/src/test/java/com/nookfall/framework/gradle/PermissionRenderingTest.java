package com.nookfall.framework.gradle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.gradle.api.GradleException;
import org.junit.jupiter.api.Test;

class PermissionRenderingTest {

    @Test
    void rendersDescriptionsDefaultsAndChildren() {
        String yaml = GeneratePluginYml.renderPermissions(List.of(
                new GeneratePluginYml.Permission("homes.use", "Use \"homes\"", "true", Map.of()),
                new GeneratePluginYml.Permission(
                        "homes.admin", null, "op", new TreeMap<>(Map.of("homes.use", true, "homes.limit.5", false)))));
        assertThat(yaml).isEqualTo("""
                permissions:
                  "homes.use":
                    description: "Use \\"homes\\""
                    default: "true"
                  "homes.admin":
                    default: "op"
                    children:
                      "homes.limit.5": false
                      "homes.use": true
                """);
    }

    @Test
    void noPermissionsRenderNothing() {
        assertThat(GeneratePluginYml.renderPermissions(List.of())).isEmpty();
    }

    @Test
    void rejectsUnknownDefaultsAndInvalidNames() {
        assertThatThrownBy(() -> GeneratePluginYml.renderPermissions(
                        List.of(new GeneratePluginYml.Permission("a.b", null, "yes", Map.of()))))
                .isInstanceOf(GradleException.class)
                .hasMessageContaining("use true, false, op or not op");
        assertThatThrownBy(() -> GeneratePluginYml.renderPermissions(
                        List.of(new GeneratePluginYml.Permission("A B", null, "op", Map.of()))))
                .isInstanceOf(GradleException.class)
                .hasMessageContaining("may only contain");
    }
}
