package com.ronan.serialx.infra.support;

import java.util.Map;
import java.util.LinkedHashMap;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Locale;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapperImpl;

/**
 * 平铺状态对象与 Redis 字符串 Hash 的边界转换，不影响 HTTP 或全局 JSON 配置。
 * 时间统一写入毫秒文本，读取兼容旧 ISO 格式，不进行时区换算。
 */
public final class RedisSnapshotCodec {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter
            .ofPattern("uuuu-MM-dd HH:mm:ss.SSS", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);
    private RedisSnapshotCodec() { }

    /** 以对象属性名作为 Hash 字段名，省略 null，时间写入时截取毫秒精度。 */
    public static Map<String, String> toHash(Object snapshot) {
        var bean = new BeanWrapperImpl(snapshot);
        Map<String, String> hash = new LinkedHashMap<>();
        for (var property : bean.getPropertyDescriptors()) {
            String name = property.getName();
            if ("class".equals(name) || property.getReadMethod() == null) continue;
            Object value = bean.getPropertyValue(name);
            if (value == null) continue;
            if (value instanceof LocalDateTime time) hash.put(name, TIME_FORMAT.format(time));
            else if (value instanceof CharSequence || value instanceof Number || value instanceof Boolean) {
                hash.put(name, value.toString());
            } else throw new IllegalArgumentException("Redis 状态快照必须为平铺对象，字段=" + name);
        }
        return hash;
    }

    /**
     * 转为指定状态对象，忽略未知旧字段；空白非字符串字段视为 null。
     * 仅使用可写属性名，不接受嵌套属性路径；数字和日期格式错误继续抛出。
     */
    public static <T> T fromHash(Map<?, ?> hash, Class<T> type) {
        T snapshot = BeanUtils.instantiateClass(type);
        var bean = new BeanWrapperImpl(snapshot);
        for (var property : bean.getPropertyDescriptors()) {
            String name = property.getName();
            if (property.getWriteMethod() == null || !hash.containsKey(name)) continue;
            Object raw = hash.get(name);
            String value = raw == null ? null : raw.toString();
            if (property.getPropertyType() != String.class && (value == null || value.isBlank())) {
                bean.setPropertyValue(name, null);
            } else if (property.getPropertyType() == LocalDateTime.class) {
                bean.setPropertyValue(name, value.contains("T") ? LocalDateTime.parse(value)
                        : LocalDateTime.parse(value, TIME_FORMAT));
            } else bean.setPropertyValue(name, value);
        }
        return snapshot;
    }
}
