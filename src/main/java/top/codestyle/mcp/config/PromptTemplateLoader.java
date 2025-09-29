package top.codestyle.mcp.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * 提示词模板加载器
 * 使用懒加载模式从文件中读取提示词模板
 */
@Component
public class PromptTemplateLoader {

    private static final String PROMPT_TEMPLATE_PATH = "classpath:prompt.txt";

    @Autowired
    private ResourceLoader resourceLoader;

    // volatile 确保多线程环境下的可见性
    private volatile String promptTemplate;

    /**
     * 获取提示词模板
     * 使用双重检查锁定模式确保线程安全和高性能
     */
    public String getPromptTemplate() {
        // 第一次检查，避免不必要的同步
        if (promptTemplate == null) {
            synchronized (this) {
                // 第二次检查，确保只加载一次
                if (promptTemplate == null) {
                    promptTemplate = loadTemplateFromFile();
                }
            }
        }
        return promptTemplate;
    }

    /**
     * 从文件中加载提示词模板
     */
    private String loadTemplateFromFile() {
        try {
            Resource resource = resourceLoader.getResource(PROMPT_TEMPLATE_PATH);
            return new String(Files.readAllBytes(Paths.get(resource.getURI())), StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            // 如果文件加载失败，使用默认模板作为备用
            return "#目录树：\n```\n%s\n```\n#变量说明：\n```\n%s\n```\n#详细模板：\n%s";
        }
    }
}
