package com.ronan.serialx.admin.dto;

import com.ronan.serialx.common.request.PageQueryRequest;
import lombok.Getter;
import lombok.Setter;

/**
 * 后台用户分页查询请求。
 */
@Getter
@Setter
public class AdminUserPageQueryRequest extends PageQueryRequest {

    private String keyword;

    private Integer status;
}
