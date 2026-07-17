package com.smartdrive.common.annotation;

import java.lang.annotation.*;

/**
 * 操作审计注解 — 标记需要记录审计日志的 Controller 方法
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditLog {

    /** 操作类型 */
    AuditAction value();

    /** 目标类型 */
    TargetType targetType();

    /** 目标ID对应的 @RequestParam 参数名 */
    String targetIdParam() default "";

    /** 目标名称对应的 @RequestParam 参数名（可选） */
    String targetNameParam() default "";
}
