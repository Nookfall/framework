package com.nookfall.framework.gradle;

import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

/** The {@code nookfall { }} block. */
public abstract class NookfallExtension {

    /** Plugin name in plugin.yml; also the jar base name. Defaults to the project name. */
    public abstract Property<String> getPluginName();

    /** Fully qualified main class extending JavaPlugin. Required. */
    public abstract Property<String> getMainClass();

    /** Plugin version in plugin.yml. Defaults to the project version. */
    public abstract Property<String> getPluginVersion();

    public abstract Property<String> getDescription();

    public abstract ListProperty<String> getAuthors();

    public abstract Property<String> getWebsite();

    /** {@code api-version} in plugin.yml. Defaults to {@code 1.21}. */
    public abstract Property<String> getApiVersion();

    /** Hard plugin dependencies (NookfallFramework is added automatically to the shared jar). */
    public abstract ListProperty<String> getDepend();

    public abstract ListProperty<String> getSoftDepend();

    public abstract ListProperty<String> getLoadBefore();

    /** Nookfall Framework version. Defaults to the version of this Gradle plugin. */
    public abstract Property<String> getFrameworkVersion();

    /** Paper API version compiled against. Defaults to the framework's baseline. */
    public abstract Property<String> getPaperVersion();

    /** Adds framework-storage (SQL helpers). */
    public abstract Property<Boolean> getStorage();

    /** Package that bundled libraries are moved to in the standalone jar. Defaults to {@code <main package>.libs}. */
    public abstract Property<String> getRelocationPrefix();

    /** Whether the Paper API and framework dependencies are added automatically. */
    public abstract Property<Boolean> getAddDependencies();

    /** Permissions written to plugin.yml with their descriptions, defaults and children. */
    public abstract NamedDomainObjectContainer<PermissionSpec> getPermissions();
}
