package com.ronan.serialx.admin.manager.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ronan.serialx.common.enums.NamespacePublishStatusEnum;
import com.ronan.serialx.common.error.BusinessErrorCode;
import com.ronan.serialx.common.exception.BizException;
import com.ronan.serialx.infra.entity.NamespaceConfigDO;
import com.ronan.serialx.infra.entity.NamespaceDO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Namespace 快照与配置序列化支持。
 */
@Component
@RequiredArgsConstructor
public class NamespaceSnapshotSupport {

    /**
     * JSON 序列化组件。
     */
    private final ObjectMapper objectMapper;

    /**
     * 构建草稿配置实体。
     */
    public NamespaceConfigDO buildDraftConfig(Long namespaceId, Integer idMode, JsonNode config) {
        return NamespaceConfigDO.builder()
                .namespaceId(namespaceId).idMode(idMode)
                .version(0).configChecksum(calculateChecksum(writeJson(config)))
                .publishStatus(NamespacePublishStatusEnum.EDITING.getCode()).build();
    }

    /**
     * 生成 Namespace 快照。
     */
    public String snapshot(NamespaceDO namespace, NamespaceConfigDO config) {
        try {
            return objectMapper.writeValueAsString(new SnapshotView(namespace, config == null ? null : readJson(config.getConfigJson())));
        } catch (Exception ex) {
            throw new BizException(BusinessErrorCode.NAMESPACE_PUBLISH_FAILED.getCode(), "serialize namespace snapshot failed");
        }
    }

    /**
     * 将 JSON 节点写为字符串。
     */
    public String writeJson(JsonNode jsonNode) {
        try {
            return objectMapper.writeValueAsString(jsonNode);
        } catch (Exception ex) {
            throw new BizException(BusinessErrorCode.NAMESPACE_CONFIG_INVALID.getCode(), "命名空间配置序列化失败");
        }
    }

    /**
     * 将 JSON 字符串解析为节点。
     */
    public JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            throw new BizException(BusinessErrorCode.NAMESPACE_CONFIG_INVALID.getCode(), "namespace config deserialize failed");
        }
    }

    /**
     * 安全读取 JSON 字符串。
     */
    public JsonNode readJsonSafely(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        return readJson(json);
    }

    /**
     * 计算配置内容摘要。
     */
    public String calculateChecksum(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new BizException(BusinessErrorCode.NAMESPACE_PUBLISH_FAILED.getCode(), "checksum algorithm unavailable");
        }
    }

    /**
     * Namespace 快照视图。
     */
    private record SnapshotView(NamespaceDO namespace, JsonNode config) {
    }
}
