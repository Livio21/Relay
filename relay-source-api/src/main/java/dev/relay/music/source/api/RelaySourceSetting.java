package dev.relay.music.source.api;

import java.util.Collections;
import java.util.List;

/**
 * One user-visible source preference, rendered by Relay's own settings UI. Values are plain
 * strings ("true"/"false" for TOGGLE, one of {@code choices} for CHOICE) and are handed back
 * through {@code RelaySource.applySettings} after loading and whenever the user edits them.
 * Secrets do not belong here; account flows will use platform secure storage.
 */
public final class RelaySourceSetting {
    public enum Type { TEXT, TOGGLE, CHOICE }

    private final String id;
    private final String label;
    private final Type type;
    private final String defaultValue;
    private final List<String> choices;

    public RelaySourceSetting(String id, String label, Type type, String defaultValue) {
        this(id, label, type, defaultValue, Collections.emptyList());
    }

    public RelaySourceSetting(String id, String label, Type type, String defaultValue, List<String> choices) {
        this.id = id;
        this.label = label;
        this.type = type;
        this.defaultValue = defaultValue;
        this.choices = choices == null ? Collections.emptyList() : choices;
    }

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public Type getType() {
        return type;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public List<String> getChoices() {
        return choices;
    }
}
