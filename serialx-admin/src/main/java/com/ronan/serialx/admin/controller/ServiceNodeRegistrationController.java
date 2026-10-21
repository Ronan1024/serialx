package com.ronan.serialx.admin.controller;

import com.ronan.serialx.coordinator.application.SnowflakeNodeManager;
import com.ronan.serialx.common.error.BusinessErrorCode;
import com.ronan.serialx.common.node.*;
import com.ronan.serialx.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.ronan.serialx.common.error.SystemErrorCode;
import org.springframework.dao.DataAccessException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

/**
 * 迁移期间保留的内部节点 HTTP 入口，共享机器令牌由独立安全链校验，不接受后台 JWT。
 * legacy-http-enabled=false 时不装配；启用时过滤器仍逐次检查迁移截止时间。
 * 新机器身份通过 coordinator 的独立 mTLS gRPC 入口认证，本入口不提供等价的实例级身份保证。
 */
@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/internal/service-nodes")
@ConditionalOnProperty(prefix = "serialx.admin.node-allocation", name = "legacy-http-enabled", havingValue = "true", matchIfMissing = true)
public class ServiceNodeRegistrationController {
    /**
     * 控制面节点租约管理器，返回成功前已完成事务提交。
     */
    private final SnowflakeNodeManager manager;

    /**
     * 为本次启动注册节点；相同有效会话重试返回原租约，不隐式续租。
     */
    @PostMapping("/register")
    public NodeLease register(@Valid @RequestBody NodeRegisterRequest request) {
        log.debug("收到节点注册请求，runtimeId={}", request.getRuntimeId());
        NodeLease lease = manager.register(request);
        log.debug("返回节点注册结果，runtimeId={}, datacenterId={}, workerId={}", lease.getRuntimeId(), lease.getDatacenterId(), lease.getWorkerId());
        return lease;
    }

    /**
     * 校验完整节点身份后延长租期和预留窗口，不允许过期会话复活。
     */
    @PostMapping("/renew")
    public NodeLease renew(@Valid @RequestBody NodeIdentityRequest request) {
        log.debug("收到节点续租请求，runtimeId={}, datacenterId={}, workerId={}",
                request.runtimeId(), request.datacenterId(), request.workerId());
        NodeLease lease = manager.renew(request);
        log.debug("返回节点续租结果，runtimeId={}, leaseExpireAtMs={}", lease.getRuntimeId(), lease.getLeaseExpireAtMs());
        return lease;
    }

    /**
     * 释放当前所有权但保留历史水位；数据面应先停止发号。
     */
    @PostMapping("/release")
    public void release(@Valid @RequestBody NodeIdentityRequest request) {
        log.debug("收到节点释放请求，runtimeId={}, datacenterId={}, workerId={}",
                request.runtimeId(), request.datacenterId(), request.workerId());
        manager.release(request);
        log.debug("节点释放请求处理完成，runtimeId={}", request.runtimeId());
    }

    /**
     * 保留节点错误的非成功 HTTP 状态，避免全局业务异常处理返回 HTTP 200。
     */
    @ExceptionHandler(NodeAllocationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAllocationFailure(NodeAllocationException ex) {
        log.warn("节点操作被拒绝，httpStatus={}, errorCode={}", ex.getHttpStatus(), ex.getCode());
        return ResponseEntity.status(ex.getHttpStatus()).body(ApiResponse.fail(ex.getCode(), ex.getMessage()));
    }

    /**
     * 字段约束错误及不可解析 JSON 统一返回 400，不暴露请求解析细节。
     */
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiResponse<Void>> handleInvalidRequest() {
        log.warn("节点请求参数校验失败，httpStatus=400");
        return ResponseEntity.badRequest().body(ApiResponse.fail(BusinessErrorCode.NODE_PARAM_INVALID.getCode(),
                BusinessErrorCode.NODE_PARAM_INVALID.getMessage()));
    }

    /**
     * 主库或事务不可用返回 503；仅在服务端记录异常，不能将失败视为租约成功。
     */
    @ExceptionHandler({DataAccessException.class, org.springframework.transaction.TransactionException.class})
    public ResponseEntity<ApiResponse<Void>> handleStorageUnavailable(Exception ex) {
        log.warn("节点分配存储或事务不可用，httpStatus=503, exceptionType={}", ex.getClass().getSimpleName());
        return ResponseEntity.status(503).body(ApiResponse.fail(BusinessErrorCode.NODE_NOT_READY.getCode(),
                BusinessErrorCode.NODE_NOT_READY.getMessage()));
    }

    /**
     * 未预期异常返回 500；仅记录异常类型，避免底层消息和堆栈携带凭证或请求正文。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedFailure(Exception ex) {
        log.error("节点分配发生未预期异常，httpStatus=500, exceptionType={}", ex.getClass().getSimpleName());
        return ResponseEntity.status(500).body(ApiResponse.fail(SystemErrorCode.INTERNAL_ERROR.getCode(),
                SystemErrorCode.INTERNAL_ERROR.getMessage()));
    }
}
