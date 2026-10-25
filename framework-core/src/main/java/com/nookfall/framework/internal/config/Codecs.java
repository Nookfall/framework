package com.nookfall.framework.internal.config;

import com.nookfall.framework.config.ConfigCodec;
import com.nookfall.framework.util.item.Materials;
import com.nookfall.framework.util.location.StoredLocation;
import com.nookfall.framework.util.time.Durations;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

/** Scalar codecs. Collections and nested sections are handled by {@link ConfigBinder}. */
public final class Codecs {

    private final Map<Class<?>, ConfigCodec<?>> codecs = new ConcurrentHashMap<>();

    public Codecs() {
        register(String.class, codec(raw -> {
            if (raw instanceof Map<?, ?> || raw instanceof Iterable<?>) {
                throw new IllegalArgumentException("expected text");
            }
            return String.valueOf(raw);
        }));
        register(Boolean.class, codec(Codecs::bool));
        register(boolean.class, codec(Codecs::bool));
        register(Integer.class, codec(raw -> (int) integral(raw, Integer.MIN_VALUE, Integer.MAX_VALUE)));
        register(int.class, codec(raw -> (int) integral(raw, Integer.MIN_VALUE, Integer.MAX_VALUE)));
        register(Long.class, codec(raw -> integral(raw, Long.MIN_VALUE, Long.MAX_VALUE)));
        register(long.class, codec(raw -> integral(raw, Long.MIN_VALUE, Long.MAX_VALUE)));
        register(Short.class, codec(raw -> (short) integral(raw, Short.MIN_VALUE, Short.MAX_VALUE)));
        register(short.class, codec(raw -> (short) integral(raw, Short.MIN_VALUE, Short.MAX_VALUE)));
        register(Byte.class, codec(raw -> (byte) integral(raw, Byte.MIN_VALUE, Byte.MAX_VALUE)));
        register(byte.class, codec(raw -> (byte) integral(raw, Byte.MIN_VALUE, Byte.MAX_VALUE)));
        register(Double.class, codec(Codecs::decimal));
        register(double.class, codec(Codecs::decimal));
        register(Float.class, codec(raw -> (float) decimal(raw)));
        register(float.class, codec(raw -> (float) decimal(raw)));
        register(
                BigDecimal.class,
                codec(raw -> new BigDecimal(String.valueOf(raw).strip()), BigDecimal::toPlainString));
        register(Duration.class, codec(raw -> Durations.parse(String.valueOf(raw)), Durations::toCompact));
        register(UUID.class, codec(raw -> UUID.fromString(String.valueOf(raw).strip()), UUID::toString));
        register(Locale.class, codec(raw -> parseLocale(String.valueOf(raw)), Locale::toString));
        register(
                Material.class,
                codec(
                        raw -> Materials.find(String.valueOf(raw))
                                .orElseThrow(() -> new IllegalArgumentException("unknown material '" + raw + "'")),
                        Material::name));
        register(
                StoredLocation.class,
                codec(raw -> StoredLocation.parse(String.valueOf(raw)), StoredLocation::serialize));
        register(ItemStack.class, new ItemStackCodec());
        register(
                Enchantment.class,
                codec(
                        raw -> {
                            String name = String.valueOf(raw)
                                    .strip()
                                    .toLowerCase(Locale.ROOT)
                                    .replace(' ', '_');
                            NamespacedKey key = NamespacedKey.fromString(name);
                            Enchantment enchantment = key == null ? null : Registry.ENCHANTMENT.get(key);
                            if (enchantment == null) {
                                throw new IllegalArgumentException("unknown enchantment '" + raw + "'");
                            }
                            return enchantment;
                        },
                        enchantment -> enchantment.getKey().getKey().toUpperCase(Locale.ROOT)));
    }

    public <T> void register(Class<T> type, ConfigCodec<T> codec) {
        codecs.put(type, codec);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T> ConfigCodec<T> find(Class<T> type) {
        ConfigCodec<?> codec = codecs.get(type);
        if (codec == null) {
            com.nookfall.framework.config.Codec annotation =
                    type.getAnnotation(com.nookfall.framework.config.Codec.class);
            if (annotation != null) {
                codec = (ConfigCodec<?>) ConfigBinder.instantiate(annotation.value());
                codecs.put(type, codec);
            }
        }
        if (codec == null && type.isEnum()) {
            codec = enumCodec((Class<? extends Enum>) type);
        }
        return (ConfigCodec<T>) codec;
    }

    public static Locale parseLocale(String text) {
        String[] parts = text.strip().replace('-', '_').split("_");
        if (parts.length == 0 || parts[0].isEmpty()) {
            throw new IllegalArgumentException("invalid locale '" + text + "'");
        }
        return parts.length == 1
                ? Locale.of(parts[0].toLowerCase(Locale.ROOT))
                : Locale.of(parts[0].toLowerCase(Locale.ROOT), parts[1].toUpperCase(Locale.ROOT));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ConfigCodec<?> enumCodec(Class<? extends Enum> type) {
        return new ConfigCodec<Enum>() {
            @Override
            public Enum decode(Object raw) {
                String name = String.valueOf(raw)
                        .strip()
                        .toUpperCase(Locale.ROOT)
                        .replace('-', '_')
                        .replace(' ', '_');
                for (Enum constant : type.getEnumConstants()) {
                    if (constant.name().equals(name)) {
                        return constant;
                    }
                }
                StringBuilder options = new StringBuilder();
                for (Enum constant : type.getEnumConstants()) {
                    if (!options.isEmpty()) options.append(", ");
                    options.append(constant.name());
                }
                throw new IllegalArgumentException("'" + raw + "' is not one of " + options);
            }

            @Override
            public Object encode(Enum value) {
                return value.name();
            }
        };
    }

    private static boolean bool(Object raw) {
        if (raw instanceof Boolean b) {
            return b;
        }
        String text = String.valueOf(raw).strip().toLowerCase(Locale.ROOT);
        return switch (text) {
            case "true", "yes", "on" -> true;
            case "false", "no", "off" -> false;
            default -> throw new IllegalArgumentException("expected true or false but got '" + raw + "'");
        };
    }

    private static long integral(Object raw, long min, long max) {
        long value;
        if (raw instanceof Integer || raw instanceof Long || raw instanceof Short || raw instanceof Byte) {
            value = ((Number) raw).longValue();
        } else if (raw instanceof Number n) {
            double d = n.doubleValue();
            if (d != Math.rint(d)) {
                throw new IllegalArgumentException("expected a whole number but got " + raw);
            }
            value = (long) d;
        } else {
            try {
                value = Long.parseLong(String.valueOf(raw).strip());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("expected a whole number but got '" + raw + "'");
            }
        }
        if (value < min || value > max) {
            throw new IllegalArgumentException(value + " is out of range for this setting");
        }
        return value;
    }

    private static double decimal(Object raw) {
        if (raw instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(raw).strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("expected a number but got '" + raw + "'");
        }
    }

    private interface Decoder<T> {
        T decode(Object raw);
    }

    private interface Encoder<T> {
        Object encode(T value);
    }

    private static <T> ConfigCodec<T> codec(Decoder<T> decoder) {
        return codec(decoder, value -> value);
    }

    private static <T> ConfigCodec<T> codec(Decoder<T> decoder, Encoder<T> encoder) {
        return new ConfigCodec<>() {
            @Override
            public T decode(Object raw) {
                return decoder.decode(raw);
            }

            @Override
            public Object encode(T value) {
                return encoder.encode(value);
            }
        };
    }
}
