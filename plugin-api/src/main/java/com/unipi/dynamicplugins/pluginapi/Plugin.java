package com.unipi.dynamicplugins.pluginapi;

public interface Plugin {
    String getName();
    String getDescription();
    String execute(String input);
    default void onActivate() {}
    default void onDeactivate() {}
    default void setContext(PluginContext context) {}
}
