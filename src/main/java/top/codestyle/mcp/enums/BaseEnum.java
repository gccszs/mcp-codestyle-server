package top.codestyle.mcp.enums;

import java.io.Serializable;

public interface BaseEnum<T extends Serializable> {
    T getValue();

    String getDescription();

    default String getColor() {
        return null;
    }
}
