package com.ronan.serialx.common.node;

/**
 * HTTP 和 standalone 进程内调用共用的节点分配契约。
 * 成功响应必须在持久化事务提交后返回；调用失败不能视为获得或延长租约。
 */
public interface NodeAllocationGateway {
    /**
     * 为运行会话分配节点；有效会话重复注册返回原租约，不隐式续租。
     *
     * @param request 本次启动的会话和实例标识
     * @return 已提交的节点租约
     * @throws NodeAllocationException 参数非法、会话失效或没有安全可分配的节点
     */
    NodeLease register(NodeRegisterRequest request);

    /**
     * 延长当前所有者的租约和预留窗口，不允许复活过期或已释放会话。
     *
     * @param request 注册时获得的完整节点身份
     * @return 已提交的新租约，时间水位不会倒退
     * @throws NodeAllocationException 参数非法、所有权丢失或时间窗口越界
     */
    NodeLease renew(NodeIdentityRequest request);

    /**
     * 释放所有权但保留历史时间水位；同一已释放会话重复调用幂等。
     * 调用方应先停止发号，释放不意味着节点可以立即复用。
     *
     * @param request 注册时获得的完整节点身份
     * @throws NodeAllocationException 参数非法或所有权不匹配
     */
    void release(NodeIdentityRequest request);
}
