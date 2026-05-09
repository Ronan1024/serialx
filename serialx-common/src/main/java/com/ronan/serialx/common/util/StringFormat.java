package com.ronan.serialx.common.util;

/**
 * 字符串格式化工具类，使用 {} 作为顺序占位符。
 */
public final class StringFormat {

    private static final String PLACEHOLDER = "{}";

    private StringFormat() {
    }

    /**
     * 按顺序使用参数替换模板中的 {} 占位符。
     */
    public static String format(String template, Object... args) {
        if (template == null) {
            return null;
        }
        if (args == null || args.length == 0) {
            return template;
        }
        StringBuilder builder = new StringBuilder(template.length() + args.length * 16);
        int templateIndex = 0;
        int argIndex = 0;
        while (templateIndex < template.length()) {
            int placeholderIndex = template.indexOf(PLACEHOLDER, templateIndex);
            if (placeholderIndex < 0 || argIndex >= args.length) {
                builder.append(template, templateIndex, template.length());
                break;
            }
            builder.append(template, templateIndex, placeholderIndex);
            builder.append(args[argIndex++]);
            templateIndex = placeholderIndex + PLACEHOLDER.length();
        }
        return builder.toString();
    }
}
