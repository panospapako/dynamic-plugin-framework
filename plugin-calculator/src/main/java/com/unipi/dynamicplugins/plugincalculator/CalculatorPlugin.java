package com.unipi.dynamicplugins.plugincalculator;

import com.unipi.dynamicplugins.pluginapi.Plugin;
import com.unipi.dynamicplugins.pluginapi.PluginAction;

/**
 * Supports classic string-based operation and reflection-based (typed) method invocation.
 */
public class CalculatorPlugin implements Plugin {

    @Override
    public String getName() { return "Calculator Plugin"; }

    @Override
    public String getDescription() { return "Performs basic arithmetic operations"; }

    /**
     * Classic mode: "add 1 2"
     */
    @Override
    public String execute(String input) {
        String[] parts = input.split("\\s+");
        if (parts.length != 3) return "Invalid format. Use: add 1 2";
        double a, b;
        try {
            a = Double.parseDouble(parts[1]);
            b = Double.parseDouble(parts[2]);
        } catch (NumberFormatException e) {
            return "Invalid numbers: " + e.getMessage();
        }
        return switch (parts[0]) {
            case "add" -> String.valueOf(add(a, b));
            case "sub" -> String.valueOf(subtract(a, b));
            case "mul" -> String.valueOf(multiply(a, b));
            case "div" -> (b != 0) ? String.valueOf(divide(a, b)) : "Divide by zero";
            default -> "Unknown operation";
        };
    }

    /** Annotated as safe for reflection endpoint demo */
    @PluginAction(description = "Adds two numbers")
    public double add(double a, double b) { return a + b; }

    @PluginAction(description = "Subtracts second number from first")
    public double subtract(double a, double b) { return a - b; }

    @PluginAction(description = "Multiplies two numbers")
    public double multiply(double a, double b) { return a * b; }

    @PluginAction(description = "Divides first number by second")
    public double divide(double a, double b) { return b == 0 ? Double.NaN : a / b; }
}
