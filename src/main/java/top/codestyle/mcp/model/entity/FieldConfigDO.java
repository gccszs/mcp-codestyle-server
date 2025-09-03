package top.codestyle.mcp.model.entity;

import cn.hutool.core.util.StrUtil;
import cn.hutool.db.meta.Column;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import top.codestyle.mcp.constant.StringConstants;
import top.codestyle.mcp.enums.FormTypeEnum;
import top.codestyle.mcp.enums.QueryTypeEnum;

import java.io.Serial;
import java.io.Serializable;

/**
 * 字段配置实体
 *
 * @author 文艺倾年
 */
@Data
@NoArgsConstructor
public class FieldConfigDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * ID
     */
    private Long id;

    /**
     * 表名称
     */
    private String tableName;

    /**
     * 列名称
     */
    private String columnName;

    /**
     * 列类型
     */
    private String columnType;

    /**
     * 列大小
     */
    private Long columnSize;

    /**
     * 字段名称
     */
    private String fieldName;

    /**
     * 字段类型
     */
    private String fieldType;

    /**
     * 字段排序
     */
    private Integer fieldSort;

    /**
     * 注释
     */
    private String comment;

    /**
     * 是否必填
     */
    private Boolean isRequired;

    /**
     * 是否在列表中显示
     */
    private Boolean showInList;

    /**
     * 是否在表单中显示
     */
    private Boolean showInForm;

    /**
     * 是否在查询中显示
     */
    private Boolean showInQuery;

    /**
     * 表单类型
     */
    @Schema(description = "表单类型", example = "1")
    private FormTypeEnum formType;

    /**
     * 查询方式
     */
    @Schema(description = "查询方式", example = "1")
    private QueryTypeEnum queryType;

    /**
     * 字典编码
     */
    @Schema(description = "字典编码", example = "notice_type")
    private String dictCode;

    public FieldConfigDO(@NonNull Column column) {
        this.setTableName(column.getTableName());
        this.setColumnName(column.getName());
        this.setColumnType(column.getTypeName());
        this.setColumnSize(column.getSize());
        this.setComment(column.getComment());
        this.setIsRequired(!column.isPk() && !column.isNullable());
        this.setShowInList(true);
        this.setShowInForm(this.getIsRequired());
        this.setShowInQuery(this.getIsRequired());
    }

    public void setColumnName(String columnName) {
        this.columnName = columnName;
        this.fieldName = StrUtil.toCamelCase(this.columnName);
    }

    public void setColumnType(String columnType) {
        String[] arr = StrUtil.splitToArray(columnType, StringConstants.SPACE);
        this.columnType = arr.length > 1 ? arr[0].toLowerCase() : columnType.toLowerCase();
    }

    public void setComment(String comment) {
        this.comment = StrUtil.nullToDefault(comment, StringConstants.EMPTY);
    }
}
