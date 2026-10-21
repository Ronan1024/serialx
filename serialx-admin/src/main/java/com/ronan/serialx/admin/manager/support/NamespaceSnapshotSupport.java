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
        String configJson = writeJson(config);
        return NamespaceConfigDO.builder()
                .namespaceId(namespaceId).idMode(idMode)
                .version(0).configJson(configJson).configChecksum(calculateChecksum(configJson))
                .publishStatus(NamespacePublishStatusEnum.EDITING.getCode()).build();
    }

    /**
     * 生成 Namespace 快照。
     */
    public String snapshot(NamespaceDO namespace, NamespaceConfigDO config) {
        try {
            return objectMapper.writeValueAsString(new SnapshotView(namespace, config == null ? null : readJson(config.getConfigJson())));
        } catch (Exception ex) {
            throw new BizException(BusinessErrorCode.NAMESPACE_PUBLISH_FAILED.getCode(), "Namespace 快照序列化失败");
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
     * 解析配置 JSON，供管理响应与审计快照共用。
     *
     * <p>解析失败不降级为空配置，避免掩盖持久化内容异常；仅需忽略空白输入时使用
     * {@link #readJsonSafely(String)}。
     *
     * @param json 待解析的配置 JSON 文本
     * @return ObjectMapper 解析得到的 JSON 节点
     * @throws BizException 解析失败，错误码为 NAMESPACE_CONFIG_INVALID
     */
    public JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            throw new BizException(BusinessErrorCode.NAMESPACE_CONFIG_INVALID.getCode(), "Namespace 配置反序列化失败");
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
            throw new BizException(BusinessErrorCode.NAMESPACE_PUBLISH_FAILED.getCode(), "配置摘要算法不可用");
        }
    }

    /**
     * Namespace 快照视图。
     */
    private record SnapshotView(NamespaceDO namespace, JsonNode config) {
    }
}
