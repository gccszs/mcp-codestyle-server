package top.codestyle.mcp.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Matcher;

/**
 * 提示词模板加载服务
 * 使用懒加载模式从classpath读取提示词模板
 *
 * @author 小航love666, Kanttha, movclantian
 * @since 2025-09-29
 */
@Service
public class PromptService {

    private static final String CONTENT_RESULT_TEMPLATE_PATH = "classpath:prompt/content-result.txt";
    private static final String SEARCH_RESULT_TEMPLATE_PATH = "classpath:prompt/search-result.txt";
    private static final String REMOTE_UNAVAILABLE_TEMPLATE_PATH = "classpath:prompt/remote-unavailable.txt";
    private static final String LOCAL_NOT_FOUND_TEMPLATE_PATH = "classpath:prompt/local-not-found.txt";
    private static final String MULTI_RESULT_TEMPLATE_PATH = "classpath:prompt/multi-result.txt";
    private static final String GROUP_AGGREGATED_TEMPLATE_PATH = "classpath:prompt/group-aggregated.txt";

    @Autowired
    private ResourceLoader resourceLoader;

    private volatile String contentResultTemplate;
    private volatile String searchResultTemplate;
    private volatile String remoteUnavailableTemplate;
    private volatile String localNotFoundTemplate;
    private volatile String multiResultTemplate;
    private volatile String groupAggregatedTemplate;

    /**
     * 线程安全懒加载模板内容模板
     *
     * @return 模板内容字符串
     */
    private String getContentResultTemplate() {
        if (contentResultTemplate == null) {
            synchronized (this) {
                if (contentResultTemplate == null) {
                    contentResultTemplate = loadTemplate(CONTENT_RESULT_TEMPLATE_PATH);
                }
            }
        }
        return contentResultTemplate;
    }

    /**
     * 线程安全懒加载搜索结果模板
     *
     * @return 搜索结果模板字符串
     */
    private String getSearchResultTemplate() {
        if (searchResultTemplate == null) {
            synchronized (this) {
                if (searchResultTemplate == null) {
                    searchResultTemplate = loadTemplate(SEARCH_RESULT_TEMPLATE_PATH);
                }
            }
        }
        return searchResultTemplate;
    }

    /**
     * 线程安全懒加载远程不可用模板
     *
     * @return 远程不可用模板字符串
     */
    private String getRemoteUnavailableTemplate() {
        if (remoteUnavailableTemplate == null) {
            synchronized (this) {
                if (remoteUnavailableTemplate == null) {
                    remoteUnavailableTemplate = loadTemplate(REMOTE_UNAVAILABLE_TEMPLATE_PATH);
                }
            }
        }
        return remoteUnavailableTemplate;
    }

    /**
     * 线程安全懒加载本地未找到模板
     *
     * @return 本地未找到模板字符串
     */
    private String getLocalNotFoundTemplate() {
        if (localNotFoundTemplate == null) {
            synchronized (this) {
                if (localNotFoundTemplate == null) {
                    localNotFoundTemplate = loadTemplate(LOCAL_NOT_FOUND_TEMPLATE_PATH);
                }
            }
        }
        return localNotFoundTemplate;
    }

    /**
     * 线程安全懒加载多结果模板
     *
     * @return 多结果模板字符串
     */
    private String getMultiResultTemplate() {
        if (multiResultTemplate == null) {
            synchronized (this) {
                if (multiResultTemplate == null) {
                    multiResultTemplate = loadTemplate(MULTI_RESULT_TEMPLATE_PATH);
                }
            }
        }
        return multiResultTemplate;
    }

    /**
     * 线程安全懒加载分组聚合模板
     *
     * @return 分组聚合模板字符串
     */
    private String getGroupAggregatedTemplate() {
        if (groupAggregatedTemplate == null) {
            synchronized (this) {
                if (groupAggregatedTemplate == null) {
                    groupAggregatedTemplate = loadTemplate(GROUP_AGGREGATED_TEMPLATE_PATH);
                }
            }
        }
        return groupAggregatedTemplate;
    }

    /**
     * 从classpath加载模板文件
     *
     * @param templatePath 模板文件路径
     * @return 模板内容字符串
     * @throws IllegalStateException 文件不存在或加载失败
     */
    private String loadTemplate(String templatePath) {
        try {
            Resource resource = resourceLoader.getResource(templatePath);
            if (!resource.exists()) {
                throw new IllegalStateException("classpath 下找不到 " + templatePath);
            }
            String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return content.strip();
        } catch (IOException e) {
            throw new IllegalStateException("加载 " + templatePath + " 失败", e);
        }
    }

    /**
     * 构建内容提示词(按顺序替换模板中的%{s}占位符)
     *
     * @param params 可变参数,依次对应模板中的%{s}
     * @return 替换后的提示词字符串
     * @throws IllegalArgumentException 参数数量与占位符数量不匹配
     */
    public String buildPrompt(String... params) {
        return buildFromTemplate(getContentResultTemplate(), params);
    }

    /**
     * 构建搜索结果(按顺序替换模板中的%{s}占位符)
     *
     * @param params 可变参数,依次对应模板中的%{s}
     * @return 替换后的搜索结果字符串
     * @throws IllegalArgumentException 参数数量与占位符数量不匹配
     */
    public String buildSearchResult(String... params) {
        return buildFromTemplate(getSearchResultTemplate(), params);
    }

    /**
     * 构建远程不可用消息
     *
     * @param templateKeyword 模板关键词
     * @return 格式化后的消息
     */
    public String buildRemoteUnavailable(String templateKeyword) {
        return buildFromTemplate(getRemoteUnavailableTemplate(), templateKeyword);
    }

    /**
     * 构建本地未找到消息
     *
     * @param repositoryPath 仓库路径
     * @param templateKeyword 模板关键词
     * @return 格式化后的消息
     */
    public String buildLocalNotFound(String repositoryPath, String templateKeyword) {
        return buildFromTemplate(getLocalNotFoundTemplate(), repositoryPath, templateKeyword);
    }

    /**
     * 构建多结果响应
     *
     * @param count           结果数量
     * @param keyword         搜索关键词
     * @param resultList      结果列表字符串
     * @param exampleArtifact 示例artifactId
     * @return 格式化后的消息
     */
    public String buildMultiResult(String count, String keyword, String resultList, String exampleArtifact) {
        return buildFromTemplate(getMultiResultTemplate(), count, keyword, resultList, exampleArtifact);
    }

    /**
     * 构建分组聚合结果
     *
     * @param groupId       组ID
     * @param count         模板组数量
     * @param artifactList  模板组列表
     * @return 格式化后的描述
     */
    public String buildGroupAggregated(String groupId, String count, String artifactList) {
        return buildFromTemplate(getGroupAggregatedTemplate(), groupId, count, artifactList);
    }

    /**
     * 从模板构建内容
     *
     * @param template 模板内容
     * @param params   可变参数,依次对应模板中的%{s}
     * @return 替换后的字符串
     * @throws IllegalArgumentException 参数数量与占位符数量不匹配
     */
    private String buildFromTemplate(String template, String... params) {
        Objects.requireNonNull(params, "params must not be null");

        int placeholderCount = countPlaceholder(template);
        if (placeholderCount != params.length) {
            throw new IllegalArgumentException(
                    "模板需要 " + placeholderCount + " 个参数，实际传入 " + params.length);
        }

        String result = template;
        for (String p : params) {
            // 使用 Matcher.quoteReplacement 来转义特殊字符，避免 $ 和 \ 被当作正则表达式的反向引用
            String replacement = Matcher.quoteReplacement(p == null ? "" : p);
            result = result.replaceFirst("%\\{s}", replacement);
        }
        return result;
    }

    /**
     * 统计模板中%{s}占位符的数量
     *
     * @param template 模板字符串
     * @return 占位符数量
     */
    private static int countPlaceholder(String template) {
        int count = 0, idx = 0;
        while ((idx = template.indexOf("%{s}", idx)) != -1) {
            count++;
            idx += 4;
        }
        return count;
    }

}
