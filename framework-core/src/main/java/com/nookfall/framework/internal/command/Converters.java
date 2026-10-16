package com.nookfall.framework.internal.command;

import com.nookfall.framework.command.ArgumentResolver;
import com.nookfall.framework.command.CommandFailure;
import com.nookfall.framework.message.Arg;
import com.nookfall.framework.util.item.Materials;
import com.nookfall.framework.util.player.Players;
import com.nookfall.framework.util.time.Durations;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Built-in and custom converters for word arguments. */
final class Converters {

    private final Map<Class<?>, ArgumentConverter> custom = new ConcurrentHashMap<>();

    <T> void register(Class<T> type, ArgumentResolver<T> resolver) {
        custom.put(type, new ArgumentConverter() {
            @Override
            public Object convert(CommandSender sender, String paramName, String input) {
                T value = resolver.parse(sender, input);
                if (value == null) {
                    throw CommandFailure.message(
                            "nookfall.command.invalid-argument", Arg.text("name", paramName), Arg.text("value", input));
                }
                return value;
            }

            @Override
            public List<String> suggest(CommandSender sender, String input) {
                return resolver.suggest(sender, input);
            }
        });
    }

    boolean hasCustom(Class<?> type) {
        return custom.containsKey(type);
    }

    /** Returns null if the type has no converter. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    ArgumentConverter find(Class<?> type) {
        ArgumentConverter converter = custom.get(type);
        if (converter != null) {
            return converter;
        }
        if (type.isEnum()) {
            return enumConverter((Class<? extends Enum>) type);
        }
        if (type == Duration.class) {
            return DURATION;
        }
        if (type == OfflinePlayer.class) {
            return OFFLINE_PLAYER;
        }
        if (type == Material.class) {
            return MATERIAL;
        }
        return null;
    }

    private static List<String> filter(List<String> options, String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(option);
            }
        }
        return out;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ArgumentConverter enumConverter(Class<? extends Enum> type) {
        List<String> names = Arrays.stream(type.getEnumConstants())
                .map(e -> ((Enum<?>) e).name().toLowerCase(Locale.ROOT))
                .toList();
        return new ArgumentConverter() {
            @Override
            public Object convert(CommandSender sender, String paramName, String input) {
                String normalized = input.toUpperCase(Locale.ROOT).replace('-', '_');
                for (Enum constant : type.getEnumConstants()) {
                    if (constant.name().equals(normalized)) {
                        return constant;
                    }
                }
                throw CommandFailure.message(
                        "nookfall.command.invalid-enum",
                        Arg.text("name", paramName),
                        Arg.text("value", input),
                        Arg.text("options", String.join(", ", names)));
            }

            @Override
            public List<String> suggest(CommandSender sender, String input) {
                return filter(names, input);
            }
        };
    }

    private static final ArgumentConverter DURATION = new ArgumentConverter() {
        @Override
        public Object convert(CommandSender sender, String paramName, String input) {
            try {
                return Durations.parse(input);
            } catch (IllegalArgumentException e) {
                throw CommandFailure.message("nookfall.command.invalid-duration", Arg.text("value", input));
            }
        }

        @Override
        public List<String> suggest(CommandSender sender, String input) {
            return filter(List.of("30s", "1m", "5m", "1h"), input);
        }
    };

    private static final ArgumentConverter OFFLINE_PLAYER = new ArgumentConverter() {
        @Override
        public Object convert(CommandSender sender, String paramName, String input) {
            Player online = Bukkit.getPlayerExact(input);
            if (online != null) {
                return online;
            }
            return Players.known(input)
                    .orElseThrow(() ->
                            CommandFailure.message("nookfall.command.player-not-found", Arg.text("value", input)));
        }

        @Override
        public List<String> suggest(CommandSender sender, String input) {
            return Players.namesStartingWith(input);
        }
    };

    private static final ArgumentConverter MATERIAL = new ArgumentConverter() {
        @Override
        public Object convert(CommandSender sender, String paramName, String input) {
            return Materials.findItem(input)
                    .orElseThrow(() -> CommandFailure.message(
                            "nookfall.command.invalid-argument",
                            Arg.text("name", paramName),
                            Arg.text("value", input)));
        }

        @Override
        public List<String> suggest(CommandSender sender, String input) {
            String lower = input.toLowerCase(Locale.ROOT);
            return Arrays.stream(Material.values())
                    .filter(m -> !m.isLegacy() && m.isItem())
                    .map(m -> m.name().toLowerCase(Locale.ROOT))
                    .filter(n -> n.startsWith(lower))
                    .limit(50)
                    .collect(Collectors.toList());
        }
    };
}
