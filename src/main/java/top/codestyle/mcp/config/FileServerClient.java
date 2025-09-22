package top.codestyle.mcp.config;


import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

@Slf4j
@Component
public class FileServerClient {

    private final String baseUrl;
    private final int maxRetries;
    private final int bufferSize;
    private final HttpClient http;

    public FileServerClient(@Value("${file-server.base-url}") String baseUrl,
                            @Value("${file-server.max-retries:2}") int maxRetries,
                            @Value("${file-server.buffer-size:32768}") int bufferSize) {
        this.baseUrl = baseUrl;
        this.maxRetries = maxRetries;
        this.bufferSize = bufferSize;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /**
     * 把文件服务器上的 sha256 文件下载到本地
     *
     * @param sha256     文件唯一标识
     * @param targetPath 本地目标路径（含文件名）
     * @return true=下载成功；false=服务器不存在或网络异常
     */
    public boolean download(String sha256, Path targetPath) {
        String url = baseUrl + "/" + sha256;
        URI uri = URI.create(url);

        /* 1. 本地 file 协议 */
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            try {
                Path src = Paths.get(uri);   // file:///C:/xxx/123456
                if (Files.exists(src)) {
                    Files.copy(src, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    log.info("本地 file 协议复制成功 {} -> {}", src, targetPath);
                    return true;
                }
                log.warn("本地 file 源文件不存在 {}", src);
                return false;
            } catch (IOException e) {
                log.error("file 协议复制失败", e);
                return false;
            }
        }

        /* 2. 远程 http/https 协议 */
        if (!"http".equalsIgnoreCase(uri.getScheme()) &&
                !"https".equalsIgnoreCase(uri.getScheme())) {
            log.error("不支持的协议 {}", uri.getScheme());
            return false;
        }

        // 下面是你原来的 HttpClient 逻辑
        for (int i = 0; i <= maxRetries; i++) {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(uri)
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();
                HttpResponse<Path> resp =
                        http.send(req, HttpResponse.BodyHandlers.ofFile(targetPath));
                if (resp.statusCode() == 200) {
                    log.info("http 下载成功 {}", uri);
                    return true;
                }
                if (resp.statusCode() == 404) return false;
            } catch (Exception e) {
                log.warn("第 {} 次下载失败 {}", i + 1, e.getMessage());
            }
        }
        return false;
    }
}
