package top.codestyle.mcp.service;

import lombok.RequiredArgsConstructor;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import top.codestyle.mcp.config.RepositoryConfig;
import top.codestyle.mcp.model.meta.LocalMetaInfo;
import top.codestyle.mcp.model.sdk.MetaInfo;
import top.codestyle.mcp.model.sdk.RemoteMetaConfig;
import top.codestyle.mcp.model.tree.TreeNode;
import top.codestyle.mcp.util.PromptUtils;

import java.io.IOException;
import java.util.*;

/**
 * 代码模板搜索和内容获取服务
 *
 * @author 小航love666, Kanttha, movclantian
 * @since 2025-12-03
 */
@Service
@RequiredArgsConstructor
public class CodestyleService {

    private final TemplateService templateService;
    private final PromptService promptService;
    private final LuceneIndexService luceneIndexService;
    private final RepositoryConfig repositoryConfig;

    /**
     * 搜索代码模板
     * <p>根据模板提示词搜索模板信息，返回目录树和模板组介绍。
     * 支持本地Lucene检索和远程检索两种模式。
     *
     * @param templateKeyword 模板提示词，支持关键词或 groupId/artifactId 格式，如: CRUD, backend, frontend, continew/DatabaseConfig
     * @return 模板目录树和描述信息字符串
     */
    @McpTool(name = "codestyleSearch", description = """
            根据模板提示词搜索代码模板库，返回匹配的模板目录树和模板组介绍。
            支持以下搜索格式：
            1. 关键词搜索：CRUD, frontend, backend 等
            2. 精确搜索：groupId/artifactId 格式
            """)
    public String codestyleSearch(
            @McpToolParam(description = "模板提示词或 groupId/artifactId，如: CRUD, bankend, frontend, continew/DatabaseConfig") String templateKeyword) {
        try {
            // 远程检索模式
            if (templateService.isRemoteSearchEnabled()) {
                RemoteMetaConfig remoteConfig = templateService.fetchRemoteMetaConfig(templateKeyword);

                if (remoteConfig == null) {
                    return promptService.buildRemoteUnavailable(templateKeyword);
                }

                templateService.smartDownloadTemplate(remoteConfig);

                String groupId = remoteConfig.getGroupId();
                String artifactId = remoteConfig.getArtifactId();
                String description = remoteConfig.getDescription();

                List<MetaInfo> metaInfos = templateService.searchLocalRepository(groupId, artifactId);
                if (metaInfos.isEmpty()) {
                    return "本地仓库模板文件不完整,请检查模板目录";
                }

                TreeNode treeNode = PromptUtils.buildTree(metaInfos);
                String treeStr = PromptUtils.buildTreeStr(treeNode, "").trim();
                return promptService.buildSearchResult(artifactId, treeStr, description);
            }

            // 本地Lucene全文检索模式
            List<LuceneIndexService.SearchResult> searchResults = luceneIndexService.fetchLocalMetaConfig(templateKeyword);

            if (searchResults.isEmpty()) {
                return promptService.buildLocalNotFound(repositoryConfig.getRepositoryDir(), templateKeyword);
            }

            // 检查是否为同一groupId的多个模板（命名空间搜索）
            if (templateService.isGroupIdSearch(searchResults)) {
                return templateService.buildGroupAggregatedResult(templateKeyword, searchResults);
            }

            // 处理多个不同模板的情况（让AI选择）
            if (searchResults.size() > 1) {
                return templateService.buildMultiResultResponse(templateKeyword, searchResults);
            }

            // 单模板结果
            LuceneIndexService.SearchResult searchResult = searchResults.get(0);
            List<MetaInfo> metaInfos = templateService.searchLocalRepository(
                    searchResult.groupId(), searchResult.artifactId());

            if (metaInfos.isEmpty()) {
                return "本地仓库模板文件不完整,请检查模板目录";
            }

            TreeNode treeNode = PromptUtils.buildTree(metaInfos);
            String treeStr = PromptUtils.buildTreeStr(treeNode, "").trim();
            return promptService.buildSearchResult(searchResult.artifactId(), treeStr, searchResult.description());
        } catch (Exception e) {
            return "模板搜索失败: " + e.getMessage();
        }
    }

    /**
     * 获取模板文件内容
     * <p>根据完整的模板文件路径获取详细内容，包括变量说明和模板代码
     *
     * @param templatePath 完整模板文件路径（包含版本号和.ftl扩展名），如: backend/CRUD/1.0.0/src/main/java/com/air/controller/Controller.ftl
     * @return 模板文件的详细信息字符串（包含变量说明和模板内容）
     * @throws IOException 文件读取异常
     */
    @McpTool(name = "getTemplateByPath", description = """
            传入完整的模板文件路径（必须包含版本号和.ftl扩展名），获取模板文件的详细内容(包括变量说明和模板代码)。
            注意：不是 groupId/artifactId 格式，而是完整的文件路径。
            """)
    public String getTemplateByPath(
            @McpToolParam(description = "完整模板文件路径（必须包含版本号和.ftl），如: backend/CRUD/1.0.0/src/main/java/com/air/controller/Controller.ftl") String templatePath)
            throws IOException {

        // 使用精确路径搜索模板
        LocalMetaInfo matchedTemplate = templateService.searchByPath(templatePath);

        // 校验搜索结果
        if (matchedTemplate == null) {
            return String.format("未找到路径为 '%s' 的模板文件,请检查路径是否正确。", templatePath);
        }

        // 构建变量信息
        Map<String, String> vars = new LinkedHashMap<>();
        if (matchedTemplate.getInputVariables() != null && !matchedTemplate.getInputVariables().isEmpty()) {
            for (var variable : matchedTemplate.getInputVariables()) {
                String desc = String.format("%s（示例：%s）[%s]",
                        variable.getVariableComment(),
                        variable.getExample(),
                        variable.getVariableType());
                vars.put(variable.getVariableName(), desc);
            }
        }

        // 使用PromptUtils格式化变量信息
        String varInfo = vars.isEmpty() ? "无变量" : PromptUtils.buildVarString(vars).trim();

        // 使用PromptService模板构建最终输出
        return promptService.buildPrompt(
                templatePath,
                varInfo,
                matchedTemplate.getTemplateContent() != null ? matchedTemplate.getTemplateContent() : "");
    }
}