package com.unipi.dynamicplugins.plugincore.repository;

import com.unipi.dynamicplugins.plugincore.model.PluginEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PluginRepository extends JpaRepository<PluginEntity, Long> {
    List<PluginEntity> findByActiveTrue();
}
