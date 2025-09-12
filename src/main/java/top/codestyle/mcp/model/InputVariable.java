package top.codestyle.mcp.model;

import lombok.Data;

@Data
public class InputVariable {
    private String variableName; // 变量名：packageName
    private String variableType; // 变量类型：List、String...
    private String variableComment; // 变量备注；
}
