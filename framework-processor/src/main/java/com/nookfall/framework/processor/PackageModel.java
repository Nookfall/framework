package com.nookfall.framework.processor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.Element;

/** Generated registrations of one package, emitted into {@code <package>.Nookfall_Components}. */
final class PackageModel {

    final String pkg;
    final List<String> listeners = new ArrayList<>();
    final List<String> commands = new ArrayList<>();
    final List<String> arguments = new ArrayList<>();
    final List<String> configs = new ArrayList<>();
    final List<String> guis = new ArrayList<>();
    final List<String> scheduled = new ArrayList<>();
    final List<String> integrations = new ArrayList<>();
    final Set<String> singletonImports = new LinkedHashSet<>();
    final Set<String> prototypeImports = new LinkedHashSet<>();
    final List<String> configTypes = new ArrayList<>();
    final List<Element> origins = new ArrayList<>();

    PackageModel(String pkg) {
        this.pkg = pkg;
    }

    String qualified(String simpleName) {
        return pkg.isEmpty() ? simpleName : pkg + "." + simpleName;
    }

    boolean isEmpty() {
        return listeners.isEmpty()
                && commands.isEmpty()
                && arguments.isEmpty()
                && configs.isEmpty()
                && guis.isEmpty()
                && scheduled.isEmpty()
                && integrations.isEmpty()
                && singletonImports.isEmpty()
                && prototypeImports.isEmpty();
    }
}
