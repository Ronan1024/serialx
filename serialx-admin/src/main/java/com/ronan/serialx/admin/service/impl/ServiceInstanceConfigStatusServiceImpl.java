package com.ronan.serialx.admin.service.impl;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.ronan.serialx.admin.service.ServiceInstanceConfigStatusService;
import com.ronan.serialx.common.constant.ServiceInstanceRedisConstants;
import com.ronan.serialx.infra.entity.ServiceInstanceConfigStatusDO;
import com.ronan.serialx.common.model.ServiceInstanceSnapshot;
import com.ronan.serialx.infra.support.RedisSnapshotCodec;
import org.springframework.data.redis.connection.DataType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Service 实例配置状态 Redis 查询服务实现。
 *
 * <p>汇总实例快照、Namespace 状态 Hash 及旧版状态 JSON，按实例和 Namespace 去重。
 * 这些数据只用于控制面展示，不作为节点租约有效性或实时发号能力的权威依据；
 * 查询过程中不修改 Redis，也不写入 MySQL。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceInstanceConfigStatusServiceImpl implements ServiceInstanceConfigStatusService {

    /** Redis SCAN 每次迭代的数量提示，不是单次返回数量或总结果数量的硬上限。 */
    private static final long SCAN_COUNT = 200L;

    /**
     * Redis 字符串模板，用于扫描快照、读取 Hash/旧版 JSON 和检查心跳键。
     */
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * JSON 转换组件，仅用于兼容旧版字符串快照；Hash 使用独立对象转换器。
     */
    private final ObjectMapper objectMapper;

    /**
     * 无心跳时估算离线时间的窗口，默认 90 秒。
     * 实际在线判定只检查心跳键是否存在，不通过此窗口比较上报时间。
     */
    @Value("${serialx.admin.instance-status.alive-timeout:90s}")
    private Duration aliveTimeout = Duration.ofSeconds(90);

    /**
     * 汇总三种来源的状态，过滤无法解析的 JSON，按实例与 Namespace 去重，
     * 再补充离线展示状态并按最后上报时间降序排列。
     * 缺少上报时间的比较返回相等，不承诺这些记录固定排在列表末尾。
     *
     * @return 汇总状态列表；实例级记录的 namespaceCode 为 null
     */
    @Override
    public List<ServiceInstanceConfigStatusDO> listAll() {
        List<ServiceInstanceConfigStatusDO> statuses = new ArrayList<>();
        statuses.addAll(listInstanceHeartbeatStatuses());
        statuses.addAll(listByStatusKeyPattern(ServiceInstanceRedisConstants.INSTANCE_NAMESPACE_KEY_PATTERN));
        statuses.addAll(listByStatusKeyPattern(ServiceInstanceRedisConstants.LEGACY_INSTANCE_STATUS_KEY_PATTERN));
        return statuses.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(
                                this::buildStatusKey,
                                status -> status,
                                this::chooseLatestStatus,
                                LinkedHashMap::new),
                        item -> new ArrayList<>(item.values())))
                .stream()
                .map(this::markOfflineIfNeeded)
                .sorted((left, right) -> {
                    if (left.getLastReportTime() == null || right.getLastReportTime() == null) {
                        return 0;
                    }
                    return right.getLastReportTime().compareTo(left.getLastReportTime());
                })
                .toList();
    }

    /**
     * 按在线状态查询实例的预留接口，当前尚未实现过滤逻辑，始终返回空列表。
     *
     * @param active 预期的在线状态条件；当前实现未使用该参数
     * @return 当前固定为空列表，不能据此判断系统没有对应状态的实例
     */
    @Override
    public List<ServiceInstanceConfigStatusDO> listByActive(Boolean active) {
        return List.of();
    }

    /**
     * 从完整汇总结果中按 Namespace 编码精确筛选，不直接缩小 Redis 扫描范围。
     *
     * @param namespaceCode Namespace 编码；null 匹配未设置编码的实例级记录
     * @return 匹配的状态列表，保留汇总结果的顺序
     */
    @Override
    public List<ServiceInstanceConfigStatusDO> listByNamespaceCode(String namespaceCode) {
        return listAll().stream()
                .filter(status -> Objects.equals(namespaceCode, status.getNamespaceCode()))
                .toList();
    }

    /**
     * 扫描指定模式的状态键并逐个读取，跳过已过期、无数据或 JSON 解析失败的记录。
     *
     * @param keyPattern 当前 Namespace 状态或旧版状态键的匹配模式
     * @return 成功读取的状态列表，无匹配键时为空
     */
    private List<ServiceInstanceConfigStatusDO> listByStatusKeyPattern(String keyPattern) {
        Set<String> keys = scanKeys(keyPattern);
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        return keys.stream()
                .map(this::readStatusByKey)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 扫描实例快照键并转换为实例级状态记录，不扫描心跳键的内容。
     * 心跳是否存活在合并后的离线判定阶段另行检查。
     *
     * @return 不含 Namespace 编码的实例状态列表
     */
    private List<ServiceInstanceConfigStatusDO> listInstanceHeartbeatStatuses() {
        Set<String> keys = scanKeys(ServiceInstanceRedisConstants.INSTANCE_KEY_PATTERN);

        if (CollectionUtils.isEmpty(keys)) {
            return List.of();
        }

        return keys.stream().map(this::readInstanceInfoByKey).filter(Objects::nonNull)
                .map(this::toNodeStatus).toList();
    }

    /**
     * 优先读取状态 Hash，Hash 为空时尝试旧版字符串 JSON。
     * Redis 操作异常不会在此处吞掉，包括键类型不匹配导致的异常。
     *
     * @param key 状态键
     * @return 状态对象，无字符串内容或 JSON 解析失败时为 null
     */
    private ServiceInstanceConfigStatusDO readStatusByKey(String key) {
        Map<Object, Object> hash = readHash(key);
        if (!hash.isEmpty()) {
            return RedisSnapshotCodec.fromHash(hash, ServiceInstanceConfigStatusDO.class);
        }
        String statusJson = stringRedisTemplate.opsForValue().get(key);
        if (!StringUtils.hasText(statusJson)) {
            return null;
        }
        return readStatus(statusJson);
    }

    /**
     * 优先将实例 Hash 转换为共享快照对象，Hash 为空时读取旧版实例 JSON。
     *
     * @param key 实例快照键
     * @return 实例快照对象，无内容或 JSON 解析失败时为 null
     */
    private ServiceInstanceSnapshot readInstanceInfoByKey(String key) {
        Map<Object, Object> hash = readHash(key);
        if (!hash.isEmpty()) {
            return RedisSnapshotCodec.fromHash(hash, ServiceInstanceSnapshot.class);
        }
        String instanceJson = stringRedisTemplate.opsForValue().get(key);
        if (!StringUtils.hasText(instanceJson)) {
            return null;
        }
        return readInstanceInfo(instanceJson);
    }

    /**
     * 读取 Hash 全部字段；操作对象或返回值为 null 时统一为空 Map。
     * 字符串键交由旧版 JSON 分支读取；不捕获 Redis 连接或其他键类型异常。
     *
     * @param key Redis 键
     * @return 非 null 的字段 Map
     */
    private Map<Object, Object> readHash(String key) {
        // 旧版字符串键不能执行 HGETALL，先识别类型避免 WRONGTYPE。
        if (stringRedisTemplate.type(key) == DataType.STRING) return Map.of();
        var hashOperations = stringRedisTemplate.opsForHash();
        if (hashOperations == null) {
            return Map.of();
        }
        Map<Object, Object> hash = hashOperations.entries(key);
        return hash == null ? Map.of() : hash;
    }

    /**
     * 反序列化旧版状态 JSON；解析失败记录告警并跳过该条记录。
     *
     * @param statusJson 旧版状态 JSON 字符串
     * @return 状态对象，解析失败时为 null
     */
    private ServiceInstanceConfigStatusDO readStatus(String statusJson) {
        try {
            return objectMapper.readValue(statusJson, ServiceInstanceConfigStatusDO.class);
        } catch (JsonProcessingException ex) {
            log.warn("deserialize service instance status failed, statusJson={}", statusJson, ex);
            return null;
        }
    }

    /**
     * 解析旧版实例 JSON；解析失败记录告警并跳过该条记录。
     *
     * @param instanceJson 旧版实例快照 JSON 字符串
     * @return 实例快照对象，解析失败时为 null
     */
    private ServiceInstanceSnapshot readInstanceInfo(String instanceJson) {
        try {
            return objectMapper.readerFor(ServiceInstanceSnapshot.class)
                    .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(instanceJson);
        } catch (JsonProcessingException ex) {
            log.warn("deserialize service instance info failed, instanceJson={}", instanceJson, ex);
            return null;
        }
    }

    /** 将实例对象映射为节点级展示状态，不设置 Namespace 编码或版本。 */
    private ServiceInstanceConfigStatusDO toNodeStatus(ServiceInstanceSnapshot instanceInfo) {
        ServiceInstanceConfigStatusDO status = new ServiceInstanceConfigStatusDO();
        status.setInstanceId(instanceInfo.getInstanceId());
        status.setHost(buildHost(instanceInfo));
        status.setRuntimeStatus(instanceInfo.getStatus());
        status.setLastReportTime(instanceInfo.getLastReportTime());
        status.setLastOfflineTime(instanceInfo.getLastOfflineTime());
        return status;
    }

    /**
     * 有心跳时保留原状态，无心跳时仅修改本次查询对象为 OFFLINE。
     * 已有离线时间不覆盖；缺失时使用最后上报时间加窗口估算，或使用当前时间。
     * 估算值不是进程真实停止时刻，也不会持久化回 Redis。
     *
     * @param status 合并后的状态对象
     * @return 同一对象，必要时补充离线展示字段
     */
    private ServiceInstanceConfigStatusDO markOfflineIfNeeded(ServiceInstanceConfigStatusDO status) {
        if (isHeartbeatAlive(status.getInstanceId())) {
            return status;
        }
        LocalDateTime lastReportTime = status.getLastReportTime();
        status.setRuntimeStatus(ServiceInstanceRedisConstants.OFFLINE);
        if (status.getLastOfflineTime() == null) {
            status.setLastOfflineTime(lastReportTime == null ? LocalDateTime.now() : lastReportTime.plus(aliveTimeout));
        }
        return status;
    }

    /**
     * 按实例 ID 检查 Redis 心跳键是否存在，不验证键内 runtimeId 或主库节点租约。
     *
     * @param instanceId 实例标识，空白时视为无心跳
     * @return 心跳键存在时为 true
     */
    private boolean isHeartbeatAlive(String instanceId) {
        if (!StringUtils.hasText(instanceId)) {
            return false;
        }
        String heartbeatKey = String.format(ServiceInstanceRedisConstants.INSTANCE_HEARTBEAT_KEY_FORMAT, instanceId);
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(heartbeatKey));
    }

    /**
     * 使用 SCAN 收集匹配键，通过 Set 去重并自动关闭游标。
     * 无连接工厂时降级为模板 KEYS 调用；生产查询应走 SCAN 分支。
     * SCAN 不是一致性快照，扫描后键可能过期或发生更新。
     *
     * @param keyPattern Redis 键匹配模式
     * @return 匹配键集合；模板回调或降级调用可能返回 null
     */
    private Set<String> scanKeys(String keyPattern) {
        if (stringRedisTemplate.getConnectionFactory() == null) {
            return stringRedisTemplate.keys(keyPattern);
        }
        return stringRedisTemplate.execute((RedisCallback<Set<String>>) connection -> {
            Set<String> keys = new java.util.HashSet<>();
            ScanOptions options = ScanOptions.scanOptions()
                    .match(keyPattern)
                    .count(SCAN_COUNT)
                    .build();
            try (Cursor<byte[]> cursor = connection.keyCommands().scan(options)) {
                while (cursor.hasNext()) {
                    keys.add(new String(cursor.next(), StandardCharsets.UTF_8));
                }
            }
            return keys;
        });
    }

    /**
     * 组合实例 ID 与 Namespace 编码作为去重键；null 字段按空字符串处理。
     *
     * @param status 待合并记录
     * @return 使用竖线分隔的组合键，不包含运行会话 UUID
     */
    private String buildStatusKey(ServiceInstanceConfigStatusDO status) {
        return safeValue(status.getInstanceId()) + "|" + safeValue(status.getNamespaceCode());
    }

    /**
     * 同一去重键保留上报时间较新的完整记录，不逐字段合并。
     * 左侧缺时间选右侧；仅右侧缺时间或时间相同选左侧；两侧缺时间选右侧。
     *
     * @param left 先收集的记录
     * @param right 后收集的记录
     * @return 按上述规则保留的一条记录
     */
    private ServiceInstanceConfigStatusDO chooseLatestStatus(
            ServiceInstanceConfigStatusDO left,
            ServiceInstanceConfigStatusDO right) {
        LocalDateTime leftReportTime = left.getLastReportTime();
        LocalDateTime rightReportTime = right.getLastReportTime();
        if (leftReportTime == null) {
            return right;
        }
        if (rightReportTime == null) {
            return left;
        }
        return rightReportTime.isAfter(leftReportTime) ? right : left;
    }

    /** 拼接对象中的实例展示地址；无端口时仅返回 IP，不校验网络可达性。 */
    private String buildHost(ServiceInstanceSnapshot instanceInfo) {
        if (!StringUtils.hasText(instanceInfo.getPort())) return instanceInfo.getIp();
        return ServiceInstanceRedisConstants.SERVICE_NAME + "/" + instanceInfo.getIp() + ":" + instanceInfo.getPort();
    }

    /**
     * 将 null 转为空字符串，用于组合去重键，不裁剪非 null 值。
     *
     * @param value 原字段值
     * @return 非 null 的字符串
     */
    private String safeValue(String value) {
        return value == null ? "" : value;
    }
}
