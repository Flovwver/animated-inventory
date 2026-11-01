package ru.example.inventorygif.client;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import net.neoforged.fml.loading.FMLPaths;
import java.util.LinkedHashMap;
import java.util.Map;

/** Typed client settings backed by the existing TOML file. */
public final class ClientConfig {
    private final Map<String, ConfigValue<?>> values;
    private final CommentedFileConfig file;
    private Map<String, Object> draftSnapshot;
    private ClientConfig(Map<String, ConfigValue<?>> values) {
        this.values = values;
        file = CommentedFileConfig.builder(FMLPaths.CONFIGDIR.get()
                .resolve("inventorygif-client.toml")).sync().build();
        file.load();
        values.forEach((key, value) -> value.load(file.get(key)));
        save();
    }
    public void beginEdit() {
        if (draftSnapshot != null) return;
        draftSnapshot = new LinkedHashMap<>();
        values.forEach((key, value) -> draftSnapshot.put(key, value.get()));
    }
    public boolean hasPendingEdits() {
        return draftSnapshot != null && values.entrySet().stream()
                .anyMatch(entry -> !java.util.Objects.equals(entry.getValue().get(), draftSnapshot.get(entry.getKey())));
    }
    public void cancelEdit() {
        if (draftSnapshot == null) return;
        draftSnapshot.forEach((key, value) -> values.get(key).load(value));
        draftSnapshot = null;
    }
    public void commitEdit() {
        draftSnapshot = null;
        save();
    }
    public void save() {
        values.forEach((key, value) -> file.set(key, value.get()));
        file.save();
    }
    public static class ConfigValue<T> {
        private T value;
        ConfigValue(T value) { this.value = value; }
        public T get() { return value; }
        public void set(T value) { this.value = value; }
        @SuppressWarnings("unchecked")
        void load(Object raw) {
            if (raw == null) return;
            if (value instanceof Integer && raw instanceof Number n) set((T) Integer.valueOf(n.intValue()));
            else if (value instanceof Double && raw instanceof Number n) set((T) Double.valueOf(n.doubleValue()));
            else if (value.getClass().isInstance(raw)) set((T) raw);
        }
    }
    public static final class IntValue extends ConfigValue<Integer> {
        private final int min, max;
        IntValue(int value, int min, int max) { super(value); this.min = min; this.max = max; }
        @Override public void set(Integer value) { super.set(Math.clamp(value, min, max)); }
    }
    public static final class DoubleValue extends ConfigValue<Double> {
        private final double min, max;
        DoubleValue(double value, double min, double max) { super(value); this.min = min; this.max = max; }
        @Override public void set(Double value) { if (Double.isFinite(value)) super.set(Math.clamp(value, min, max)); }
    }
    public static final class BooleanValue extends ConfigValue<Boolean> {
        BooleanValue(boolean value) { super(value); }
    }
    public static final class Builder {
        private final Map<String, ConfigValue<?>> values = new LinkedHashMap<>();
        public Builder comment(String... text) { return this; }
        public IntValue defineInRange(String key, int value, int min, int max) {
            IntValue setting = new IntValue(value, min, max); values.put(key, setting); return setting;
        }
        public DoubleValue defineInRange(String key, double value, double min, double max) {
            DoubleValue setting = new DoubleValue(value, min, max); values.put(key, setting); return setting;
        }
        public BooleanValue define(String key, boolean value) {
            BooleanValue setting = new BooleanValue(value); values.put(key, setting); return setting;
        }
        public ConfigValue<String> define(String key, String value) {
            ConfigValue<String> setting = new ConfigValue<>(value); values.put(key, setting); return setting;
        }
        public ClientConfig build() { return new ClientConfig(values); }
    }
}
