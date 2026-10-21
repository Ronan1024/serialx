package com.ronan.serialx.admin.dto;

import com.ronan.serialx.common.request.PageQueryRequest;
import lombok.Getter;
import lombok.Setter;

/**
 * 应用分页查询请求。
 */
@Getter
@Setter
public class AppPageQueryRequest extends PageQueryRequest {

    private String keyword;

    private Integer status;
}
