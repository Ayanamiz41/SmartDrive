package com.smartdrive.common.enums;

/**
 * 摘要来源（MANUAL=人工；AI_GENERATED=AI自动——后续扩展用，当前仅实现 MANUAL）
 */
public enum SummarySourceEnum {
    MANUAL(1, "人工"),
    AI_GENERATED(2, "AI自动");

    private final int flag;
    private final String desc;

    SummarySourceEnum(int flag, String desc) {
        this.flag = flag;
        this.desc = desc;
    }

    public int getFlag() { return flag; }
    public String getDesc() { return desc; }
}
