package top.codestyle.mcp.enums;

import java.io.Serializable;

public interface ISqlFunction {
    String findInSet(Serializable value, String set);
}