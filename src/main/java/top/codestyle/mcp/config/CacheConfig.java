package top.codestyle.mcp.config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 缓存配置类
 * 管理缓存路径和临时目录配置
 *
 * @author 文艺倾年
 * @since 2025/9/20
 */
@Configuration
public class CacheConfig {

    /**
     * 缓存基础路径，默认C盘
     * 可通过JVM参数 -Dcache.base-path=自定义路径 覆盖
     */
    @Value("${cache.base-path:${java.io.tmpdir}}")
    private String basePath;

    /**
     * 临时目录路径
     * 默认在基础路径下创建codestyle-cache目录
     */
    @Value("${cache.cache-dir:${cache.base-path}/codestyle-cache}")
    private String cacheDir;

    /**
     * 获取缓存基础路径
     */
    public String getBasePath() {
        return basePath;
    }

    /**
     * 获取缓存目录路径
     */
    public String getCacheDir() {
        return cacheDir;
    }

    /**
     * 创建缓存目录并确保其存在
     * 当指定路径无法创建时，自动使用系统临时目录作为备选
     * @return 缓存目录路径
     */
    @Bean
    public Path cacheDirectory() {
        // 记录当前操作系统类型
        String osName = System.getProperty("os.name").toLowerCase();
        System.out.println("当前操作系统: " + osName);
        try {
            // 规范化路径，确保跨平台兼容性
            String normalizedCacheDir = normalizePath(cacheDir);
            Path cachePath = Paths.get(normalizedCacheDir);

            // 如果目录不存在，则创建目录
            if (!Files.exists(cachePath)) {
                Files.createDirectories(cachePath);
            }

            System.out.println("临时缓存目录已创建: " + cachePath.toAbsolutePath());
            return cachePath;
        } catch (Exception e) {
            // 如果创建失败，使用系统临时目录作为备选
            String fallbackTempDir = System.getProperty("java.io.tmpdir") + File.separator + "codestyle-cache";
            Path fallbackPath = Paths.get(fallbackTempDir);
            try {
                if (!Files.exists(fallbackPath)) {
                    Files.createDirectories(fallbackPath);
                }
                System.err.println("使用备选临时缓存目录: " + fallbackPath.toAbsolutePath());
                return fallbackPath;
            } catch (Exception ex) {
                ex.printStackTrace();
                throw new RuntimeException("无法创建临时缓存目录", ex);
            }
        }
    }

    /**
     * 规范化路径字符串，确保在不同操作系统上的兼容性
     * @param path 原始路径字符串
     * @return 规范化后的路径字符串
     */
    private String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return path;
        }

        // 处理不同操作系统的路径分隔符
        String normalizedPath = path.replace('/', File.separatorChar).replace('\\', File.separatorChar);

        // 确保路径不会因为多个连续的分隔符而出问题
        while (normalizedPath.contains(File.separator + File.separator)) {
            normalizedPath = normalizedPath.replace(File.separator + File.separator, File.separator);
        }

        return normalizedPath;
    }
}
