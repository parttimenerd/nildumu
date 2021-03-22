package nildumu.mih;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * A basic scheme that defines the properties (with their possible default values) for each
 * handler class
 */
public class PropertyScheme {
    final char SEPARATOR = ';';
    private final Map<String, String> defaultValues;

    PropertyScheme() {
        defaultValues = new HashMap<>();
    }

    public PropertyScheme add(String param, String defaultValue) {
        defaultValues.put(param, defaultValue);
        return this;
    }

    public PropertyScheme add(String param) {
        return add(param, null);
    }

    public Properties parse(String props) {
        return parse(props, false);
    }

    public Properties parse(String props, boolean allowAnyProps) {
        if (!props.contains("=")) {
            props = String.format("handler=%s", props);
        }
        Properties properties = new PropertiesParser(props).parse();
        for (Map.Entry<String, String> defaulValEntry : defaultValues.entrySet()) {
            if (!properties.containsKey(defaulValEntry.getKey())) {
                if (defaulValEntry.getValue() == null) {
                    throw new MethodInvocationHandlerInitializationError(String.format("for string \"%s\": property %s not set", props, defaulValEntry.getKey()));
                }
                properties.setProperty(defaulValEntry.getKey(), defaulValEntry.getValue());
            }
        }
        if (!allowAnyProps) {
            for (String prop : properties.stringPropertyNames()) {
                if (!defaultValues.containsKey(prop)) {
                    throw new MethodInvocationHandlerInitializationError(String.format("for string \"%s\": property %s unknown, valid properties are: %s", props, prop, defaultValues.keySet().stream().sorted().collect(Collectors.joining(", "))));
                }
            }
        }
        return properties;
    }
}
