/*
 * Copyright (c) 2025-present IPBD Organization. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package top.codestyle.mcp.model.entity;

import cn.hutool.core.util.StrUtil;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import top.codestyle.mcp.constant.CharConstants;
import top.codestyle.mcp.util.StrUtils;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 生成配置实体
 *
 * @author artboy
 * @since 2023/4/12 20:21
 */
@Data
@NoArgsConstructor
public class GenConfigDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 表名称
     */
    private String tableName;

    /**
     * 描述
     */
    private String comment;

    /**
     * 模块名称
     */
    private String moduleName;

    /**
     * 包名称
     */
    private String packageName;

    /**
     * 业务名称
     */
    private String businessName;

    /**
     * 作者
     */
    private String author;

    /**
     * 表前缀
     */
    private String tablePrefix;

    /**
     * 是否覆盖
     */
//    TODO @ToolParam(description = "是否覆盖", example = "false")
    private Boolean isOverride;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 修改时间
     */
    private LocalDateTime updateTime;

    public GenConfigDO(String tableName) {
        this.setTableName(tableName);
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
        // 默认表前缀（sys_user -> sys_）
        int underLineIndex = StrUtil.indexOf(tableName, CharConstants.UNDERLINE);
        if (-1 != underLineIndex) {
            this.tablePrefix = StrUtil.subPre(tableName, underLineIndex + 1);
        }
    }

    /**
     * 类名前缀
     */
    @Schema(description = "类名前缀", example = "User")
    public String getClassNamePrefix() {
        String tableName = this.getTableName();
        String rawClassName = StrUtils.blankToDefault(this.getTablePrefix(), tableName, prefix -> StrUtil
            .removePrefix(tableName, prefix));
        return StrUtil.upperFirst(StrUtil.toCamelCase(rawClassName));
    }
}
