package com.unipi.dynamicplugins.plugincore.model;

import jakarta.persistence.*;

@Entity
@Table(name = "plugins")
public class PluginEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String name;
    private String description;
    @Column(nullable = false)
    private boolean active;
    @Column(nullable = false)
    private String jarPath;

    public PluginEntity() {
    }

    public PluginEntity(String name, String description, boolean active, String jarPath) {
        this.name = name;
        this.description = description;
        this.active = active;
        this.jarPath = jarPath;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getJarPath() {
        return jarPath;
    }

    public void setJarPath(String jarPath) {
        this.jarPath = jarPath;
    }
}