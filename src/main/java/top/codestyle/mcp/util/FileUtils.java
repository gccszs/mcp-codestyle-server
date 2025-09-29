package top.codestyle.mcp.util;

import top.codestyle.mcp.model.entity.TemplateInfo;

/**
 * 文件操作工具类
 * 提供文件下载、文件处理等相关功能
 */
public class FileUtils {
    /**
     * 根据 groupId/artifactId/filename 到文件服务器下载模板并封装成 TemplateInfo
     * 返回 null 表示下载失败
     */
    public static TemplateInfo downloadFromFileServer(TemplateInfo req) {
        // TODO: 实现真实下载
        return null;
    }

    /**
     * 占位方法：根据 groupId/artifactId/filename 到文件服务器下载模板
     * 返回封装好的 TemplateInfo；若下载失败返回 null
     */
    public static TemplateInfo downloadFromFileServer(TemplateInfo t, String fileName) {
        // TODO: 实现真正的下载逻辑，例如
        // String url = "http://fileserver/template/" + t.getGroupId() + "/" + t.getArtifactId() + "/" + fileName;
        // String content = HttpUtil.get(url);
        // 然后填充 TemplateInfo 并返回
        return null;
    }
}
