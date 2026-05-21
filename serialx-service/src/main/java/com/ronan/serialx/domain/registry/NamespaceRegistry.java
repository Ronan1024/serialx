package com.ronan.serialx.domain.registry;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.ronan.serialx.domain.runtime.NamespaceRuntime;
import org.springframework.stereotype.Component;

/**
 * Namespace 本地运行时注册表。
 */
@Component
public class NamespaceRegistry {

    private final ConcurrentMap<String, NamespaceRuntime> runtimeMap = new ConcurrentHashMap<>();

    /**
     * 替换 Namespace 运行时。
     */
    public void replace(String namespaceCode, NamespaceRuntime runtime) {
        runtimeMap.put(namespaceCode, runtime);
    }

    /**
     * 删除 Namespace 运行时。
     */
    public void remove(String namespaceCode) {
        runtimeMap.remove(namespaceCode);
    }

    /**
     * 查询 Namespace 运行时。
     */
    public Optional<NamespaceRuntime> get(String namespaceCode) {
        return Optional.ofNullable(runtimeMap.get(namespaceCode));
    }

    /**
     * 查询所有 Namespace 运行时。
     */
    public Collection<NamespaceRuntime> listAll() {
        return runtimeMap.values();
    }
}
