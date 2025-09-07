package top.codestyle.mcp.service;

import cn.hutool.db.meta.Column;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.codestyle.mcp.config.GeneratorProperties;
import top.codestyle.mcp.enums.DatabaseType;
import top.codestyle.mcp.model.entity.FieldConfigDO;
import top.codestyle.mcp.model.entity.TableEntity;
import top.codestyle.mcp.model.req.ToolReq;
import top.codestyle.mcp.model.resp.ToolResp;

import javax.sql.DataSource;
import java.util.*;
import java.util.regex.Pattern;

/**
 * @author 文艺倾年
 * @Description
 */
@Slf4j
@Service
public class GeneratorService {

    public enum DbType {
        MYSQL, ORACLE, SQLSERVER, POSTGRESQL
    }
    /** 表名校验：字母开头，字母/数字/下划线 */
    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*$");
    @Autowired
    private DataSource dataSource;

    /**
     * 仅返回 SQL，不执行
     * @param tableName 表名
     * @param dbType    数据库类型
     * @return 查询表字段配置的 SQL（含注释）
     * @throws IllegalArgumentException 表名非法
     */
    @Tool(name = "get-tablemeta-sql-by-tablename", description = "根据表名获取表元数据SQL")
    public String getFieldConfigSQL(@ToolParam(description = "tableName") String tableName, @ToolParam(description = "DbType")DbType dbType) {
        //非法表名检验
        if (tableName == null || !TABLE_NAME_PATTERN.matcher(tableName).matches()) {
            throw new IllegalArgumentException("非法表名，只允许字母、数字、下划线，且以字母开头");
        }
        return switch (dbType) {
            case MYSQL -> "SELECT COLUMN_NAME AS col_name, DATA_TYPE AS data_type, " +
                    "COLUMN_TYPE AS full_type, IS_NULLABLE AS is_nullable, " +
                    "COLUMN_DEFAULT AS col_default, COLUMN_COMMENT AS col_comment " +
                    "FROM information_schema.columns " +
                    "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '" + tableName + "' " +
                    "ORDER BY ORDINAL_POSITION";
            case ORACLE -> "SELECT COLUMN_NAME AS col_name, DATA_TYPE AS data_type, " +
                    "DATA_TYPE || '(' || DATA_LENGTH || ')' AS full_type, " +
                    "NULLABLE AS is_nullable, DATA_DEFAULT AS col_default, " +
                    "COMMENTS AS col_comment " +
                    "FROM ALL_TAB_COLUMNS c " +
                    "LEFT JOIN ALL_COL_COMMENTS m ON c.TABLE_NAME = m.TABLE_NAME AND c.COLUMN_NAME = m.COLUMN_NAME " +
                    "WHERE c.TABLE_NAME = '" + tableName.toUpperCase() + "' " +
                    "ORDER BY c.COLUMN_ID";
            case SQLSERVER -> "SELECT c.name AS col_name, t.name AS data_type, " +
                    "t.name + '(' + CAST(c.max_length AS VARCHAR) + ')' AS full_type, " +
                    "CASE WHEN c.is_nullable = 1 THEN 'YES' ELSE 'NO' END AS is_nullable, " +
                    "dc.definition AS col_default, ep.value AS col_comment " +
                    "FROM sys.columns c " +
                    "JOIN sys.types t ON c.user_type_id = t.user_type_id " +
                    "LEFT JOIN sys.default_constraints dc ON c.default_object_id = dc.object_id " +
                    "LEFT JOIN sys.extended_properties ep ON ep.major_id = c.object_id AND ep.minor_id = c.column_id AND ep.name = 'MS_Description' " +
                    "WHERE OBJECT_NAME(c.object_id) = '" + tableName + "' " +
                    "ORDER BY c.column_id";
            case POSTGRESQL ->
                    "SELECT a.attname AS col_name, pg_catalog.format_type(a.atttypid, a.atttypmod) AS full_type, " +
                            "pg_catalog.format_type(a.atttypid, NULL) AS data_type, " +
                            "CASE WHEN a.attnotnull THEN 'NO' ELSE 'YES' END AS is_nullable, " +
                            "pg_get_expr(d.adbin, d.adrelid) AS col_default, " +
                            "col_description(a.attrelid, a.attnum) AS col_comment " +
                            "FROM pg_attribute a " +
                            "LEFT JOIN pg_attrdef d ON a.attrelid = d.adrelid AND a.attnum = d.adnum " +
                            "WHERE a.attrelid = '" + tableName + "'::regclass AND a.attnum > 0 AND NOT a.attisdropped " +
                            "ORDER BY a.attnum";
            default -> throw new IllegalArgumentException("暂不支持的数据库类型");
        };
    }

    /**
     * TODO 根据表信息获取字段配置
     *
     * @return
     */
    @Tool(name = "get-field-config-by-table-info", description = "根据表信息获取字段配置")
    public String getFieldConfigByTableInfo(@ToolParam(description = "表配置") TableEntity table, @ToolParam(description = "databaseProductName ") String databaseProductName) {
        String result = null;
        List<FieldConfigDO> fieldConfigList = new ArrayList<>();

        // 1.获取数据表列信息
        Collection<Column> columnList = table.getColumns();
        // 2.获取数据库对应的类型映射配置
        // 2.1根据产品名称获取对应的数据库类型枚举
        DatabaseType databaseType = DatabaseType.get(databaseProductName);
        // 2.2根据数据库类型获取类型映射配置
        GeneratorProperties generatorProperties = null;
        Map<String, List<String>> typeMappingMap = generatorProperties.getTypeMappings().get(databaseType);
        Set<Map.Entry<String, List<String>>> typeMappingEntrySet = typeMappingMap.entrySet();
        int i = 1; // 字段排序计数器

        // 遍历数据库表列信息，创建或更新字段配置
        for (Column column : columnList) {
            // 创建新配置
            FieldConfigDO fieldConfig = new FieldConfigDO(column);

            // 根据数据库类型映射确定字段类型
            String fieldType = typeMappingEntrySet.stream()
                    .filter(entry -> entry.getValue().contains(fieldConfig.getColumnType()))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(null);

            fieldConfig.setFieldType(fieldType);
            fieldConfig.setFieldSort(i++); // 设置字段排序
            fieldConfigList.add(fieldConfig);
        }
        System.out.println(fieldConfigList);

        return result;
    }

    /**
     * TODO 获取Code模板
     */
    @Tool(name = "get-code-template", description = "获取Code模板")
    public String getCodeTemplate() {
        String codeTemplate = """
                package ${packageName}.${subPackageName};

                import top.continew.starter.extension.crud.enums.Api;

                import io.swagger.v3.oas.annotations.tags.Tag;

                import org.springframework.web.bind.annotation.*;

                import top.continew.starter.extension.crud.annotation.CrudRequestMapping;
                import top.air.backend.common.base.BaseController;
                import ${packageName}.model.query.${classNamePrefix}Query;
                import ${packageName}.model.req.${classNamePrefix}Req;
                import ${packageName}.model.resp.${classNamePrefix}DetailResp;
                import ${packageName}.model.resp.${classNamePrefix}Resp;
                import ${packageName}.service.${classNamePrefix}Service;

                /**
                 * ${businessName}管理 API
                 *
                 * @author ${author}
                 * @since ${datetime}
                 */
                @Tag(name = "${businessName}管理 API")
                @RestController
                @CrudRequestMapping(value = "/${apiModuleName}/${apiName}", api = {Api.PAGE, Api.DETAIL, Api.ADD, Api.UPDATE, Api.DELETE, Api.EXPORT})
                public class ${className} extends BaseController<${classNamePrefix}Service, ${classNamePrefix}Resp, ${classNamePrefix}DetailResp, ${classNamePrefix}Query, ${classNamePrefix}Req> {}
                        """.strip();
        return codeTemplate;
    }

    /**
     * TODO 获取SQL模板
     */
    @Tool(name = "get-sql-template", description = "获取SQL模板")
    public String getSqlTemplate() {
        String SQLTemplate = """
                -- ${businessName}管理菜单
                INSERT INTO `sys_menu`
                (`title`, `parent_id`, `type`, `path`, `name`, `component`, `redirect`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`, `update_user`, `update_time`)
                VALUES
                ('${businessName}管理', 1000, 2, '/${apiModuleName}/${apiName}', '${classNamePrefix}', '${apiModuleName}/${apiName}/index', NULL, NULL, b'0', b'0', b'0', NULL, 1, 1, 1, NOW(), NULL, NULL);

                SET @parentId = LAST_INSERT_ID();

                -- ${businessName}管理按钮
                INSERT INTO `sys_menu`
                (`title`, `parent_id`, `type`, `path`, `name`, `component`, `redirect`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`, `update_user`, `update_time`)
                VALUES
                ('列表', @parentId, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '${apiModuleName}:${apiName}:list', 1, 1, 1, NOW(), NULL, NULL),
                ('详情', @parentId, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '${apiModuleName}:${apiName}:detail', 2, 1, 1, NOW(), NULL, NULL),
                ('新增', @parentId, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '${apiModuleName}:${apiName}:add', 3, 1, 1, NOW(), NULL, NULL),
                ('修改', @parentId, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '${apiModuleName}:${apiName}:update', 4, 1, 1, NOW(), NULL, NULL),
                ('删除', @parentId, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '${apiModuleName}:${apiName}:delete', 5, 1, 1, NOW(), NULL, NULL),
                ('导出', @parentId, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '${apiModuleName}:${apiName}:export', 6, 1, 1, NOW(), NULL, NULL);      
                        """.strip();
        return SQLTemplate;
    }

    /**
     * TODO 获取模板对外部依赖版本进行的限制列表
     */
    @Tool(name = "get-template-dependency-versions", description = "获取模板对外部依赖版本进行的限制列表")
    public String getTemplateDependencyVersions() {
        return null;
    }

    /**
     * 供测试用
     */
    @Tool(name = "get-weather", description = "Get weather information by city name.")
    public String getWeather(ToolReq toolReq) {
        ToolResp result = null;
        try {
            result = new ToolResp();
            result.setToolParamInfo(toolReq.getToolParamInfo());
            result.setToolParamInfo("晴天！");
            log.info("mcp server run getWeather, result = {}", result);
        } catch (Exception e) {
            log.error("call mcp server failed, e:\n", e);
            return String.format("调用服务失败，异常[%s]", e.getMessage());
        }
        return result.toString();
    }
}
