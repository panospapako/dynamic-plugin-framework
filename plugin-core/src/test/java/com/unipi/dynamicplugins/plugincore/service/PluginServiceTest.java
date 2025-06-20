package com.unipi.dynamicplugins.plugincore.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unipi.dynamicplugins.plugincore.model.PluginEntity;
import com.unipi.dynamicplugins.plugincore.repository.PluginRepository;
import com.unipi.dynamicplugins.pluginapi.Plugin;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PluginServiceTest {

    @Mock private PluginRepository pluginRepository;
    @InjectMocks private PluginService pluginService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        pluginService.objectMapper = new ObjectMapper();
    }

    // Registration
    @Test
    void uploadPlugin_savesPluginEntity() throws IOException {
        MultipartFile mockFile = mock(MultipartFile.class);
        when(mockFile.getOriginalFilename()).thenReturn("test.jar");
        when(mockFile.getBytes()).thenReturn(new byte[]{1, 2, 3});
        when(pluginRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        PluginEntity result = pluginService.uploadPlugin(mockFile);
        assertNotNull(result);
        verify(pluginRepository, atLeastOnce()).save(any());
    }

    @Test
    void uploadPlugin_duplicateRegistration_throws() {
        MultipartFile mockFile = mock(MultipartFile.class);
        when(mockFile.getOriginalFilename()).thenReturn("test.jar");
        when(pluginRepository.save(any())).thenThrow(new RuntimeException("Duplicate entry"));
        assertThrows(RuntimeException.class, () -> pluginService.uploadPlugin(mockFile));
    }

    // Activate/Deactivate
    @Test
    void activateDeactivate_blocksInvocation() throws Exception {
        PluginEntity entity = new PluginEntity("Demo", "desc", false, "path");
        when(pluginRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(pluginRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        PluginService.LoadedPlugin loaded = mock(PluginService.LoadedPlugin.class);
        Plugin mockPlugin = mock(MockPlugin.class);
        when(loaded.instance()).thenReturn(mockPlugin);
        pluginService.loadedPlugins.put(1L, loaded);
        entity.setActive(true);
        pluginService.deactivatePlugin(1L);
        entity.setActive(false);
        when(entity.isActive()).thenReturn(false);
        assertThrows(Exception.class, () -> pluginService.executePlugin(1L, "input"));
    }

    // Remove
    @Test
    void removePlugin_successAndFail() throws Exception {
        PluginEntity entity = new PluginEntity("Demo", "desc", false, "path");
        when(pluginRepository.findById(1L)).thenReturn(Optional.of(entity));
        doNothing().when(pluginRepository).delete(entity);
        assertDoesNotThrow(() -> pluginService.removePlugin(1L));
        when(pluginRepository.findById(2L)).thenReturn(Optional.empty());
        assertThrows(Exception.class, () -> pluginService.removePlugin(2L));
    }

    // Invoke plugin method: valid/invalid
    @Test
    void invokePluginMethod_successAndFail() throws Exception {
        Plugin mockPlugin = new MockPlugin();
        PluginService.LoadedPlugin loaded = new PluginService.LoadedPlugin(mockPlugin, getClass().getClassLoader());
        pluginService.loadedPlugins.put(1L, loaded);

        // Happy path
        Object result = pluginService.invokePluginMethod(1L, "echo", List.of("hello"));
        assertEquals("hello", result);

        // Wrong method name
        assertThrows(NoSuchMethodException.class, () -> pluginService.invokePluginMethod(1L, "nope", List.of("x")));

        // Wrong arg count
        assertThrows(NoSuchMethodException.class, () -> pluginService.invokePluginMethod(1L, "echo", List.of()));
    }

    static class MockPlugin implements Plugin {
        public String getName() { return "Mock"; }
        public String getDescription() { return ""; }
        public String execute(String input) { return input; }
        public String echo(String s) { return s; }
    }

    // List all
    @Test
    void listPlugins_returnsAll() {
        List<PluginEntity> entities = List.of(new PluginEntity("A", "desc", false, "path"));
        when(pluginRepository.findAll()).thenReturn(entities);
        assertEquals(1, pluginService.listPlugins().size());
    }

    @Test
    void invokePluginMethod_concurrentAccess() throws Exception {
        PluginService.LoadedPlugin loaded = mock(PluginService.LoadedPlugin.class);
        Plugin plugin = new PluginServiceTest.MockPlugin();
        when(loaded.instance()).thenReturn(plugin);
        pluginService.loadedPlugins.put(1L, loaded);

        Runnable task = () -> {
            try {
                Object result = pluginService.invokePluginMethod(1L, "echo", List.of("concurrent"));
                assertEquals("concurrent", result);
            } catch (Exception e) {
                fail("Exception in concurrent invoke: " + e.getMessage());
            }
        };

        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 10; i++) threads.add(new Thread(task));
        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();
    }

    @Test
    void performance_reflection_vs_direct() throws Exception {
        Plugin plugin = new PluginServiceTest.MockPlugin();
        int iterations = 100_000;
        long t1 = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            ((MockPlugin) plugin).echo("foo");
        }
        long t2 = System.nanoTime();
        java.lang.reflect.Method m = plugin.getClass().getMethod("echo", String.class);
        long t3 = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            m.invoke(plugin, "foo");
        }
        long t4 = System.nanoTime();
        System.out.println("Direct: " + (t2 - t1) / 1_000_000 + "ms, Reflection: " + (t4 - t3) / 1_000_000 + "ms");
        // Reflection should be slower but not catastrophically so
    }

}
