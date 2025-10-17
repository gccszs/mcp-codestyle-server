package top.codestyle.mcp.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Objects;

/**
 * 提示词模板加载器
 * 使用懒加载模式从文件中读取提示词模板
 */
@Service
public class PromptService {



    private static final String PROMPT_TEMPLATE_PATH = "classpath:prompt.txt";

    @Autowired
    private ResourceLoader resourceLoader;

    private volatile String promptTemplate;

    /**
     * 线程安全懒加载模板
     */
    private String getPromptTemplate() {
        if (promptTemplate == null) {
            synchronized (this) {
                if (promptTemplate == null) {
                    promptTemplate = loadTemplate();
                }
            }
        }
        return promptTemplate;
    }

    /**
     * 加载模板；失败即抛异常，避免静默空串
     */
    private String loadTemplate() {
        try {
            Resource resource = resourceLoader.getResource(PROMPT_TEMPLATE_PATH);
            if (!resource.exists()) {
                throw new IllegalStateException("classpath 下找不到 prompt.txt");
            }
            String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return content.strip();
        } catch (IOException e) {
            throw new IllegalStateException("加载 prompt.txt 失败", e);
        }
    }

    /**
     * 按顺序替换模板中的 %{s}
     *
     * @param params 可变参数，依次对应模板中的 %{s}
     */
    public String buildPrompt(String... params) {
        Objects.requireNonNull(params, "params must not be null");
        String template = getPromptTemplate();

        int placeholderCount = countPlaceholder(template);
        if (placeholderCount != params.length) {
            throw new IllegalArgumentException(
                    "模板需要 " + placeholderCount + " 个参数，实际传入 " + params.length);
        }

        String result = template;
        for (String p : params) {
            result = result.replaceFirst("%\\{s}", p == null ? "" : p);
        }
        System.err.println("result的内容"+result);

        return result;
    }

    private static int countPlaceholder(String template) {
        int count = 0, idx = 0;
        while ((idx = template.indexOf("%{s}", idx)) != -1) {
            count++;
            idx += 4;
        }
        return count;
    }


}
