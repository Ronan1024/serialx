package com.ronan.serialx.admin.convert;

import com.ronan.serialx.admin.dto.AdminUserResponse;
import com.ronan.serialx.infra.entity.AdminUserDO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

/**
 * 后台用户对象转换 Mapper。
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AdminUserConvert {

    AdminUserConvert INSTANCE = Mappers.getMapper(AdminUserConvert.class);

    /**
     * 将后台用户持久化对象转换为响应对象。
     */
    AdminUserResponse toResponse(AdminUserDO user);
}
