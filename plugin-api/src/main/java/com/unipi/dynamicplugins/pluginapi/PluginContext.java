package com.unipi.dynamicplugins.pluginapi;

import java.util.List;

public interface PluginContext {
    Object callPluginMethod(Long pluginId, String methodName, List<String> args) throws Exception;
    Long getPluginIdByName(String name);
}
