package top.codestyle.mcp.util;

import cn.hutool.core.text.CharSequenceUtil;

import java.util.function.Function;

public class StrUtils {
    private StrUtils() {
    }

    public static <T> T blankToDefault(CharSequence str, T defaultValue, Function<String, T> mapper) {
        return CharSequenceUtil.isBlank(str) ? defaultValue : mapper.apply(str.toString());
    }
}
