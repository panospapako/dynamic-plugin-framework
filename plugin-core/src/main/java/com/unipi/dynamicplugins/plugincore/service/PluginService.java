package com.unipi.dynamicplugins.plugincore.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unipi.dynamicplugins.pluginapi.Plugin;
import com.unipi.dynamicplugins.pluginapi.PluginAction;
import com.unipi.dynamicplugins.pluginapi.PluginContext;
import com.unipi.dynamicplugins.plugincore.model.PluginEntity;
import com.unipi.dynamicplugins.plugincore.repository.PluginRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;


import java.io.*;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PluginService {
    private static final String PLUGIN_DIR = "plugins";
    final Map<Long, LoadedPlugin> loadedPlugins = new HashMap<>();

    @Autowired
    private PluginRepository pluginRepository;

    @Autowired
    ObjectMapper objectMapper;

    record LoadedPlugin(Plugin instance, ClassLoader classLoader) {}

    @PostConstruct
    public void loadActivePlugins() {
        new File(PLUGIN_DIR).mkdirs();
        pluginRepository.findByActiveTrue().forEach(plugin -> {
            try {
                LoadedPlugin loaded = loadPlugin(plugin);
                loadedPlugins.put(plugin.getId(), loaded);
                loaded.instance().onActivate();
            } catch (Exception e) {
                plugin.setActive(false);
                pluginRepository.save(plugin);
            }
        });
    }

    public PluginEntity uploadPlugin(MultipartFile file) throws IOException {
        File pluginDir = new File(PLUGIN_DIR);
        pluginDir.mkdirs();

        String originalFilename = Objects.requireNonNullElse(file.getOriginalFilename(), "plugin.jar");
        File savedFile = new File(pluginDir, "upload_" + System.currentTimeMillis() + "_" + originalFilename);
        try (FileOutputStream fos = new FileOutputStream(savedFile)) {
            fos.write(file.getBytes());
        }
        PluginEntity pluginEntity = new PluginEntity(originalFilename, "", false, savedFile.getPath());
        pluginRepository.save(pluginEntity);
        File finalFile = new File(pluginDir, "plugin-" + pluginEntity.getId() + "-" + originalFilename);
        if (savedFile.renameTo(finalFile)) {
            pluginEntity.setJarPath(finalFile.getPath());
            pluginRepository.save(pluginEntity);
        }
        return pluginEntity;
    }

    public PluginEntity activatePlugin(Long id) throws Exception {
        PluginEntity pluginEntity = pluginRepository.findById(id).orElseThrow();
        if (pluginEntity.isActive()) throw new Exception("Already active");
        LoadedPlugin loaded = loadPlugin(pluginEntity);
        loadedPlugins.put(id, loaded);
        Plugin plugin = loaded.instance();
        plugin.setContext(new PluginContextImpl());
        pluginEntity.setName(plugin.getName());
        pluginEntity.setDescription(plugin.getDescription());
        pluginEntity.setActive(true);
        plugin.onActivate();
        pluginRepository.save(pluginEntity);
        return pluginEntity;
    }

    public PluginEntity deactivatePlugin(Long id) throws Exception {
        PluginEntity pluginEntity = pluginRepository.findById(id).orElseThrow();
        if (!pluginEntity.isActive()) throw new Exception("Not active");
        LoadedPlugin loaded = loadedPlugins.remove(id);
        if (loaded != null) loaded.instance().onDeactivate();
        pluginEntity.setActive(false);
        pluginRepository.save(pluginEntity);
        return pluginEntity;
    }

    public void removePlugin(Long id) throws Exception {
        PluginEntity pluginEntity = pluginRepository.findById(id).orElseThrow();
        if (pluginEntity.isActive()) throw new Exception("Must deactivate first");
        new File(pluginEntity.getJarPath()).delete();
        pluginRepository.delete(pluginEntity);
    }

    public List<PluginEntity> listPlugins() {
        return pluginRepository.findAll();
    }

    // 2. List ALL public methods
    public List<String> listPluginMethods(Long pluginId) throws Exception {
        PluginEntity pluginEntity = pluginRepository.findById(pluginId).orElseThrow();
        File jar = new File(pluginEntity.getJarPath());
        URLClassLoader loader = new URLClassLoader(new URL[]{jar.toURI().toURL()}, Plugin.class.getClassLoader());

        try (var jf = new java.util.jar.JarFile(jar)) {
            Enumeration<java.util.jar.JarEntry> entries = jf.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (entry.getName().endsWith(".class")) {
                    String className = entry.getName().replace('/', '.').replace(".class", "");
                    Class<?> cls = loader.loadClass(className);

                    if (Plugin.class.isAssignableFrom(cls) && !cls.isInterface()) {
                        List<String> methodList = new ArrayList<>();
                        for (Method m : cls.getDeclaredMethods()) {
                            if (!m.isSynthetic() && Modifier.isPublic(m.getModifiers())) {
                                String params = Arrays.stream(m.getParameters())
                                        .map(p -> p.getType().getSimpleName() + " " + p.getName())
                                        .collect(Collectors.joining(", "));
                                methodList.add(m.getName() + "(" + params + ") → " + m.getReturnType().getSimpleName());
                            }
                        }
                        return methodList;
                    }
                }
            }
        }
        throw new ClassNotFoundException("No Plugin implementation found.");
    }

    // 3. List only @PluginAction annotated methods (safe)
    public List<Map<String, Object>> listSafePluginMethods(Long pluginId) throws Exception {
        PluginEntity pluginEntity = pluginRepository.findById(pluginId).orElseThrow();
        if (!pluginEntity.isActive()) throw new IllegalStateException("Plugin must be active");

        Plugin plugin = loadedPlugins.get(pluginId).instance();
        Class<?> cls = plugin.getClass();

        List<Map<String, Object>> methods = new ArrayList<>();

        for (Method method : cls.getDeclaredMethods()) {
            if (!method.isAnnotationPresent(PluginAction.class)) continue;

            PluginAction action = method.getAnnotation(PluginAction.class);
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("name", method.getName());
            metadata.put("description", action.description());

            List<String> paramTypes = Arrays.stream(method.getParameters())
                    .map(Parameter::getType)
                    .map(Class::getSimpleName)
                    .toList();

            metadata.put("parameters", paramTypes);

            methods.add(metadata);
        }

        return methods;
    }

    // 4. The simplest: classic Plugin.execute(String)
    public String executePlugin(Long id, String input) throws Exception {
        PluginEntity pluginEntity = pluginRepository.findById(id).orElseThrow();
        if (!pluginEntity.isActive()) throw new Exception("Plugin not active");
        Plugin plugin = loadedPlugins.get(id).instance();
        return plugin.execute(input);
    }

    // 5. Reflection-based: invoke any public method
    public Object invokePluginMethod(Long pluginId, String method, List<?> args) throws Exception {
        LoadedPlugin loaded = loadedPlugins.get(pluginId);
        if (loaded == null) throw new Exception("Plugin not active");
        Object instance = loaded.instance();

        for (Method m : instance.getClass().getMethods()) {
            if (m.getName().equals(method) && m.getParameterCount() == args.size()) {
                Class<?>[] paramTypes = m.getParameterTypes();
                Object[] convertedArgs = new Object[args.size()];
                for (int i = 0; i < args.size(); i++) {
                    convertedArgs[i] = objectMapper.convertValue(args.get(i), paramTypes[i]);
                }
                return m.invoke(instance, convertedArgs);
            }
        }
        throw new NoSuchMethodException("No such method: " + method + " with " + args.size() + " args");
    }

    // 6. Reflection-based: invoke only @PluginAction annotated methods (safe)
    public Object invokeSafePluginMethod(Long pluginId, String method, List<Object> args) throws Exception {
        LoadedPlugin loaded = loadedPlugins.get(pluginId);
        if (loaded == null) throw new Exception("Plugin not active");
        Object instance = loaded.instance();

        for (Method m : instance.getClass().getMethods()) {
            if (m.getName().equals(method)
                    && m.isAnnotationPresent(PluginAction.class)
                    && m.getParameterCount() == args.size()) {

                Class<?>[] paramTypes = m.getParameterTypes();
                Object[] convertedArgs = new Object[args.size()];
                for (int i = 0; i < args.size(); i++) {
                    convertedArgs[i] = objectMapper.convertValue(args.get(i), paramTypes[i]);
                }
                return m.invoke(instance, convertedArgs);
            }
        }
        throw new NoSuchMethodException("No such annotated method: " + method + " with " + args.size() + " args");
    }

    // 7. Abstract/flexible: accepts string, array, or object as args (JsonNode)
    public Object executeById(Long id, JsonNode input) throws Exception {
        LoadedPlugin loaded = loadedPlugins.get(id);
        if (loaded == null) throw new Exception("Plugin not active");
        Plugin instance = loaded.instance();

        // If input is textual or null, treat as simple string for legacy 'execute'
        if (input == null || input.isTextual()) {
            String arg = (input == null) ? "" : input.asText();
            return instance.execute(arg);
        }
        // If input is object: { "method": "...", "args": [...] }
        else if (input.isObject()) {
            String method = input.has("method") ? input.get("method").asText() : "execute";
            JsonNode argsNode = input.has("args") ? input.get("args") : null;
            List<Object> args = jsonNodeToList(argsNode);
            return invokePluginMethod(id, method, args);
        } else if (input.isArray()) {
            // e.g., call execute with joined args, or handle differently
            List<Object> args = jsonNodeToList(input);
            return invokePluginMethod(id, "execute", args);
        }
        throw new IllegalArgumentException("Unsupported input");
    }

    // 8. Universal endpoint: accepts JSON { pluginId|plugin, method, args }
    public Object universalExecute(JsonNode input) throws Exception {
        if (input.isObject()) {
            Long id = input.has("pluginId") ? input.get("pluginId").asLong() : null;
            String pluginName = input.has("plugin") ? input.get("plugin").asText() : null;
            String method = input.has("method") ? input.get("method").asText() : "execute";
            JsonNode argsNode = input.has("args") ? input.get("args") : null;

            if (id == null && pluginName != null) {
                id = getPluginIdByName(pluginName);
            }
            if (id == null) throw new Exception("pluginId or plugin name required");
            List<Object> args = jsonNodeToList(argsNode);
            return invokePluginMethod(id, method, args);
        } else if (input.isTextual()) {
            return defaultPluginExecute(input.asText());
        }
        throw new IllegalArgumentException("Unsupported input");
    }

    // Helper: Find plugin by name (case-insensitive)
    private Long getPluginIdByName(String name) {
        for (var entry : loadedPlugins.entrySet()) {
            if (entry.getValue().instance().getName().equalsIgnoreCase(name)) {
                return entry.getKey();
            }
        }
        return null;
    }

    // Helper: Convert JSON node to list of arguments (supports array or scalar)
    private List<Object> jsonNodeToList(JsonNode argsNode) {
        List<Object> list = new ArrayList<>();
        if (argsNode == null) return list;
        if (argsNode.isArray()) {
            for (JsonNode n : argsNode) list.add(jsonNodeToObject(n));
        } else {
            list.add(jsonNodeToObject(argsNode));
        }
        return list;
    }

    // Helper: Convert JsonNode to object
    private Object jsonNodeToObject(JsonNode n) {
        if (n == null) return null;
        if (n.isTextual()) return n.asText();
        if (n.isInt() || n.isLong()) return n.asLong();
        if (n.isDouble() || n.isFloat()) return n.asDouble();
        if (n.isBoolean()) return n.asBoolean();
        if (n.isNull()) return null;
        return n.toString();
    }

    private Object defaultPluginExecute(String arg) {
        // Fallback - could call a default plugin or return message
        return "Please specify plugin and method in JSON";
    }

    // Plugin loading logic
    private LoadedPlugin loadPlugin(PluginEntity pluginEntity) throws Exception {
        File jar = new File(pluginEntity.getJarPath());
        URLClassLoader loader = new URLClassLoader(new URL[]{jar.toURI().toURL()}, Plugin.class.getClassLoader());
        try (var jf = new java.util.jar.JarFile(jar)) {
            Enumeration<java.util.jar.JarEntry> entries = jf.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (entry.getName().endsWith(".class")) {
                    String className = entry.getName().replace('/', '.').replace(".class", "");
                    Class<?> cls = loader.loadClass(className);
                    if (Plugin.class.isAssignableFrom(cls) && !cls.isInterface()) {
                        Plugin instance = (Plugin) cls.getDeclaredConstructor().newInstance();
                        return new LoadedPlugin(instance, loader);
                    }
                }
            }
        }
        throw new ClassNotFoundException("No Plugin implementation found in jar");
    }

    private class PluginContextImpl implements PluginContext {
        public Object callPluginMethod(Long pluginId, String methodName, List<String> args) throws Exception {
            return invokePluginMethod(pluginId, methodName, args);
        }

        @Override
        public Long getPluginIdByName(String name) {
            return PluginService.this.getPluginIdByName(name);
        }
    }

}
