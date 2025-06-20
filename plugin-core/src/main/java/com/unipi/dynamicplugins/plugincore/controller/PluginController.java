package com.unipi.dynamicplugins.plugincore.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.unipi.dynamicplugins.plugincore.model.PluginEntity;
import com.unipi.dynamicplugins.plugincore.service.PluginService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/plugins")
public class PluginController {

    @Autowired
    private PluginService service;

    // 1. Upload and manage plugins (CRUD)
    @PostMapping
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file) {
        try {
            PluginEntity p = service.uploadPlugin(file);
            return ResponseEntity.ok(Map.of("id", p.getId(), "name", p.getName(), "active", p.isActive()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<?> activate(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(service.activatePlugin(id));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<?> deactivate(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(service.deactivatePlugin(id));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> remove(@PathVariable Long id) {
        try {
            service.removePlugin(id);
            return ResponseEntity.ok(Map.of("status", "deleted"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<?> list() {
        List<Map<String, Object>> list = service.listPlugins().stream()
                .map(p -> Map.<String, Object>of(
                        "id", (Object) p.getId(),
                        "name", p.getName(),
                        "active", p.isActive()
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    // 2. List ALL public methods (for plugin discovery/demonstration)
    @GetMapping("/{id}/methods")
    public ResponseEntity<?> listPluginMethods(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(service.listPluginMethods(id));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // 3. List only @PluginAction methods (for safety)
    @GetMapping("/{id}/safeMethods")
    public ResponseEntity<?> listSafePluginMethods(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(service.listSafePluginMethods(id));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // 4. Plain: Single 'execute' (string) for classic compatibility
    @PostMapping("/{id}/executePlain")
    public ResponseEntity<?> executePlain(@PathVariable Long id, @RequestBody(required = false) String input) {
        try {
            return ResponseEntity.ok(Map.of("result", service.executePlugin(id, input == null ? "" : input)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // 5. Reflection - invoke any method (method name + List<String> args)
    @PostMapping("/{id}/invoke")
    public ResponseEntity<?> invokePluginMethod(
            @PathVariable Long id,
            @RequestParam String method,
            @RequestBody List<String> args
    ) {
        try {
            Object result = service.invokePluginMethod(id, method, args);
            return ResponseEntity.ok(Map.of("result", result));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // 6. Reflection - invoke only annotated methods (for safety)
    @PostMapping("/{id}/safeInvoke")
    public ResponseEntity<?> invokeSafePluginMethod(
            @PathVariable Long id,
            @RequestParam String method,
            @RequestBody List<Object> args
    ) {
        try {
            Object result = service.invokeSafePluginMethod(id, method, args);
            return ResponseEntity.ok(Map.of("result", result));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // 7. More abstract - accepts anything as args (Object), still needs method name
    @PostMapping("/{id}/invokeAbstract")
    public ResponseEntity<?> invokeAbstractPluginMethod(
            @PathVariable Long id,
            @RequestParam String method,
            @RequestBody Object args
    ) {
        try {
            List<Object> argList;
            if (args == null) {
                argList = Collections.emptyList();
            } else if (args instanceof List<?> list) {
                argList = new ArrayList<>(list);
            } else {
                argList = List.of(args);
            }
            Object result = service.invokeSafePluginMethod(id, method, argList);
            return ResponseEntity.ok(Map.of("result", result));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // 8. Reflection with flexible JSON body: POST /plugins/{id}/execute (JsonNode: string, array, or object)
    @PostMapping("/{id}/execute")
    public ResponseEntity<?> executeById(@PathVariable Long id, @RequestBody(required = false) JsonNode input) {
        try {
            Object result = service.executeById(id, input);
            return ResponseEntity.ok(Map.of("result", result));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // 9. Super universal endpoint: POST /plugins/execute (no ID in path, info in JSON)
    @PostMapping("/execute")
    public ResponseEntity<?> universalExecute(@RequestBody JsonNode input) {
        try {
            Object result = service.universalExecute(input);
            return ResponseEntity.ok(Map.of("result", result));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
