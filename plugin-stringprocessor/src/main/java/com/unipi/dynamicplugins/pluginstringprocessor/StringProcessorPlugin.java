package com.unipi.dynamicplugins.pluginstringprocessor;

import com.unipi.dynamicplugins.pluginapi.Plugin;
import com.unipi.dynamicplugins.pluginapi.PluginAction;

/**
 * String operations: classic and reflection-enabled.
 */
public class StringProcessorPlugin implements Plugin {

    @Override
    public String getName() { return "String Processor Plugin"; }

    @Override
    public String getDescription() { return "Performs string operations"; }

    /**
     * Classic: "upper hello world"
     */
    @Override
    public String execute(String input) {
        String[] parts = input.split("\\s+", 2);
        if (parts.length < 2) return "Invalid format. Use: upper text";
        return switch (parts[0]) {
            case "upper" -> upper(parts[1]);
            case "lower" -> lower(parts[1]);
            case "reverse" -> reverse(parts[1]);
            case "count" -> String.valueOf(count(parts[1]));
            case "join" -> join(parts[1].split("\\s+"));
            default -> "Unknown command";
        };
    }

    @PluginAction(description = "Uppercases a string")
    public String upper(String input) { return input.toUpperCase(); }

    @PluginAction(description = "Lowercases a string")
    public String lower(String input) { return input.toLowerCase(); }

    @PluginAction(description = "Reverses a string")
    public String reverse(String input) { return new StringBuilder(input).reverse().toString(); }

    @PluginAction(description = "Counts the number of characters in a string")
    public int count(String input) { return input.length(); }

    @PluginAction(description = "Joins an array of strings with a space")
    public String join(String[] parts) { return String.join(" ", parts); }
}
