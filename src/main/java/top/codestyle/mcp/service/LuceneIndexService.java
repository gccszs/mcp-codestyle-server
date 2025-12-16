package top.codestyle.mcp.service;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.springframework.stereotype.Service;
import top.codestyle.mcp.config.RepositoryConfig;
import top.codestyle.mcp.model.meta.LocalMetaConfig;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Lucene本地索引服务 - 模板索引和检索
 *
 * @author movclantian
 * @since 2025-12-02
 */
@Service
@RequiredArgsConstructor
public class LuceneIndexService {

    private static final String INDEX_DIR = "lucene-index",
            F_GID = "groupId",
            F_AID = "artifactId",
            F_DESC = "description",
            F_PATH = "metaPath",
            F_PATH_KEYWORDS = "pathKeywords",
            F_CONTENT = "content";

    private final RepositoryConfig repositoryConfig;
    private final ReentrantReadWriteLock indexLock = new ReentrantReadWriteLock();
    private Directory directory;
    private Analyzer analyzer;
    private volatile long lastIndexBuildTime = 0;
    private volatile int lastMetaFileCount = 0;
    private volatile long lastCheckTime = 0;
    private static final long CHECK_INTERVAL_MS = 5000; // 检查间隔5秒

    /**
     * 初始化Lucene索引服务
     * <p>创建索引目录,初始化中文分词器,并重建索引
     *
     * @throws IOException 索引目录创建失败
     */
    @PostConstruct
    public void init() throws IOException {
        var indexPath = Paths.get(repositoryConfig.getRepositoryDir(), INDEX_DIR);
        FileUtil.mkdir(indexPath.toFile());
        directory = FSDirectory.open(indexPath);
        analyzer = new SmartChineseAnalyzer();
        rebuildIndex();
    }

    /**
     * 销毁Lucene索引服务
     * <p>关闭索引目录资源
     *
     * @throws IOException 关闭失败
     */
    @PreDestroy
    public void destroy() throws IOException {
        if (directory != null) {
            directory.close();
        }
    }

    /**
     * 重建索引
     * <p>扫描本地仓库所有meta.json文件并建立索引
     *
     * @throws IOException 索引写入失败
     */
    public void rebuildIndex() throws IOException {
        indexLock.writeLock().lock();
        try {
            var config = new IndexWriterConfig(analyzer).setOpenMode(IndexWriterConfig.OpenMode.CREATE);
            try (var writer = new IndexWriter(directory, config)) {
                scanAndIndexTemplates(writer, repositoryConfig.getRepositoryDir());
            }
            lastIndexBuildTime = System.currentTimeMillis();
            lastMetaFileCount = countMetaFiles(new File(repositoryConfig.getRepositoryDir()));
        } finally {
            indexLock.writeLock().unlock();
        }
    }

    /**
     * 扫描并索引模板
     *
     * @param writer   索引写入器
     * @param basePath 基础路径
     * @throws IOException 扫描失败
     */
    private void scanAndIndexTemplates(IndexWriter writer, String basePath) throws IOException {
        var baseDir = new File(basePath);
        if (!baseDir.isDirectory())
            return;
        var groupDirs = baseDir.listFiles(File::isDirectory);
        if (groupDirs == null)
            return;

        for (var groupDir : groupDirs) {
            if (INDEX_DIR.equals(groupDir.getName()))
                continue;
            var artifactDirs = groupDir.listFiles(File::isDirectory);
            if (artifactDirs == null)
                continue;
            for (var artifactDir : artifactDirs) {
                var metaFile = new File(artifactDir, "meta.json");
                if (metaFile.exists())
                    indexTemplate(writer, metaFile);
            }
        }
    }

    /**
     * 索引单个模板
     *
     * @param writer   索引写入器
     * @param metaFile meta.json文件
     */
    private void indexTemplate(IndexWriter writer, File metaFile) {
        try {
            var meta = JSONUtil.toBean(FileUtil.readUtf8String(metaFile), LocalMetaConfig.class);
            var desc = readDescription(metaFile.getParentFile(), meta);
            var pathKeywords = extractPathKeywords(meta);
            writer.addDocument(createDoc(meta.getGroupId(), meta.getArtifactId(), desc, pathKeywords, metaFile.getAbsolutePath()));
        } catch (Exception ignored) {
            // 单个模板索引失败不影响其他模板
        }
    }

    /**
     * 提取路径关键词
     * 从meta.json中提取所有文件路径的目录名作为关键词
     *
     * @param meta 元配置
     * @return 路径关键词字符串
     */
    private String extractPathKeywords(LocalMetaConfig meta) {
        HashSet<String> keywords = new HashSet<>();
        if (meta.getConfigs() != null) {
            for (var config : meta.getConfigs()) {
                if (config.getFiles() != null) {
                    for (var file : config.getFiles()) {
                        String path = file.getFilePath();
                        if (path != null && !path.isEmpty()) {
                            // 分割路径: /bankend/src/main → [bankend, src, main]
                            String[] segments = path.split("[/\\\\]");
                            for (String seg : segments) {
                                if (!seg.isEmpty() && !seg.equals(".")) {
                                    keywords.add(seg);
                                }
                            }
                        }
                    }
                }
            }
        }
        return String.join(" ", keywords);
    }

    /**
     * 从README.md读取描述信息
     *
     * @param artifactDir 模板目录
     * @param meta        元配置信息
     * @return 描述内容
     */
    private String readDescription(File artifactDir, LocalMetaConfig meta) {
        var configs = meta.getConfigs();
        if (configs == null || configs.isEmpty())
            return meta.getArtifactId();
        var readme = new File(artifactDir, configs.get(configs.size() - 1).getVersion() + File.separator + "README.md");
        return readme.exists() ? FileUtil.readUtf8String(readme) : meta.getArtifactId();
    }

    /**
     * 创建Lucene文档
     *
     * @param groupId      组ID
     * @param artifactId   项目ID
     * @param desc         模板描述
     * @param pathKeywords 路径关键词
     * @param metaPath     meta.json路径
     * @return Lucene文档
     */
    private Document createDoc(String groupId, String artifactId, String desc, String pathKeywords, String metaPath) {
        var doc = new Document();
        doc.add(new StringField(F_GID, groupId, Field.Store.YES));
        doc.add(new StringField(F_AID, artifactId, Field.Store.YES));
        doc.add(new StringField(F_PATH, metaPath, Field.Store.YES));
        doc.add(new TextField(F_DESC, StrUtil.nullToEmpty(desc), Field.Store.YES));
        doc.add(new TextField(F_PATH_KEYWORDS, StrUtil.nullToEmpty(pathKeywords), Field.Store.NO));
        doc.add(new TextField(F_CONTENT, String.join(" ", groupId, artifactId, StrUtil.nullToEmpty(desc), StrUtil.nullToEmpty(pathKeywords)),
                Field.Store.NO));
        return doc;
    }

    /**
     * 更新单个模板的索引
     *
     * @param groupId      组ID
     * @param artifactId   项目ID
     * @param desc         模板描述
     * @param pathKeywords 路径关键词
     * @param metaPath     meta.json路径
     */
    public void updateIndex(String groupId, String artifactId, String desc, String pathKeywords, String metaPath) {
        indexLock.writeLock().lock();
        try {
            var config = new IndexWriterConfig(analyzer).setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
            try (var writer = new IndexWriter(directory, config)) {
                writer.deleteDocuments(new Term(F_PATH, metaPath));
                writer.addDocument(createDoc(groupId, artifactId, desc, pathKeywords, metaPath));
            }
        } catch (IOException ignored) {
            // 索引更新失败不影响主流程
        } finally {
            indexLock.writeLock().unlock();
        }
    }

    /**
     * 本地检索模板 - 全文搜索
     * <p>在所有字段（groupId、artifactId、description、pathKeywords）中搜索，返回评分最高的结果。
     * 支持两种搜索模式：
     * <ul>
     *   <li>关键词搜索：搜索所有字段</li>
     *   <li>精确搜索：使用 groupId/artifactId 格式精确匹配</li>
     * </ul>
     * 自动检测仓库更新并重建索引。
     *
     * @param keyword 搜索关键词或 groupId/artifactId 格式
     * @return 匹配的模板列表（按相关度排序）
     */
    public List<SearchResult> fetchLocalMetaConfig(String keyword) {
        // 自动检测并重建索引（如果仓库有更新）
        autoRebuildIndexIfNeeded();

        indexLock.readLock().lock();
        try {
            if (!DirectoryReader.indexExists(directory)) {
                indexLock.readLock().unlock();
                rebuildIndex();
                indexLock.readLock().lock();
            }
            try (var reader = DirectoryReader.open(directory)) {
                var searcher = new IndexSearcher(reader);

                Query query;
                // 检测 "groupId/artifactId" 格式
                if (keyword.contains("/")) {
                    String[] parts = keyword.split("/", 2);
                    if (parts.length == 2) {
                        // 精确匹配 groupId 和 artifactId
                        var builder = new BooleanQuery.Builder();
                        builder.add(new TermQuery(new Term(F_GID, parts[0])), BooleanClause.Occur.MUST);
                        builder.add(new TermQuery(new Term(F_AID, parts[1])), BooleanClause.Occur.MUST);
                        query = builder.build();
                    } else {
                        // 格式错误，降级为全文搜索
                        var parser = new QueryParser(F_CONTENT, analyzer);
                        parser.setDefaultOperator(QueryParser.Operator.OR);
                        query = parser.parse(QueryParser.escape(keyword));
                    }
                } else {
                    // 全文搜索
                    var parser = new QueryParser(F_CONTENT, analyzer);
                    parser.setDefaultOperator(QueryParser.Operator.OR);
                    String queryStr = keyword.matches(".*[+\\-&|!(){}\\[\\]^\"~*?:\\\\/].*")
                        ? QueryParser.escape(keyword)
                        : keyword;
                    query = parser.parse(queryStr);
                }

                var topDocs = searcher.search(query, Integer.MAX_VALUE);
                var results = new ArrayList<SearchResult>();

                for (var scoreDoc : topDocs.scoreDocs) {
                    var doc = reader.storedFields().document(scoreDoc.doc);
                    results.add(new SearchResult(
                        doc.get(F_GID),
                        doc.get(F_AID),
                        doc.get(F_DESC),
                        doc.get(F_PATH)
                    ));
                }
                return results;
            }
        } catch (Exception ignored) {
            // 检索失败返回空列表
        } finally {
            indexLock.readLock().unlock();
        }
        return Collections.emptyList();
    }

    /**
     * 自动检测仓库更新并重建索引
     * <p>为避免频繁检查造成性能损失，最多每5秒检查一次。
     * 检查文件修改时间和文件数量，满足以下任一条件则重建索引：
     * <ul>
     *   <li>有 meta.json 文件修改时间晚于上次索引构建时间（新增/修改）</li>
     *   <li>meta.json 文件数量发生变化（删除/新增）</li>
     * </ul>
     */
    private void autoRebuildIndexIfNeeded() {
        try {
            long now = System.currentTimeMillis();

            // 距离上次检查不足5秒，跳过检查
            if (now - lastCheckTime < CHECK_INTERVAL_MS) {
                return;
            }

            if (lastIndexBuildTime == 0) {
                return; // 首次构建，跳过检查
            }

            File baseDir = new File(repositoryConfig.getRepositoryDir());
            if (!baseDir.isDirectory()) {
                return;
            }

            // 更新检查时间
            lastCheckTime = now;

            // 检查文件数量变化
            int currentCount = countMetaFiles(baseDir);
            if (currentCount != lastMetaFileCount) {
                rebuildIndex();
                return;
            }

            // 检查文件修改时间
            if (hasNewerMetaFiles(baseDir, lastIndexBuildTime)) {
                rebuildIndex();
            }
        } catch (Exception ignored) {
            // 自动重建失败不影响主流程
        }
    }

    /**
     * 递归检查是否有比指定时间更新的meta.json文件
     *
     * @param dir       目录
     * @param timestamp 时间戳
     * @return true表示有更新的文件
     */
    private boolean hasNewerMetaFiles(File dir, long timestamp) {
        File[] files = dir.listFiles();
        if (files == null) {
            return false;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                // 跳过lucene索引目录
                if (INDEX_DIR.equals(file.getName())) {
                    continue;
                }
                // 递归检查子目录
                if (hasNewerMetaFiles(file, timestamp)) {
                    return true;
                }
            } else if ("meta.json".equals(file.getName())) {
                // 检查meta.json修改时间
                if (file.lastModified() > timestamp) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 递归统计meta.json文件数量
     *
     * @param dir 目录
     * @return meta.json文件数量
     */
    private int countMetaFiles(File dir) {
        File[] files = dir.listFiles();
        if (files == null) {
            return 0;
        }

        int count = 0;
        for (File file : files) {
            if (file.isDirectory()) {
                // 跳过lucene索引目录
                if (INDEX_DIR.equals(file.getName())) {
                    continue;
                }
                // 递归统计子目录
                count += countMetaFiles(file);
            } else if ("meta.json".equals(file.getName())) {
                count++;
            }
        }
        return count;
    }

    /**
     * 检索结果记录
     *
     * @param groupId     组ID
     * @param artifactId  项目ID
     * @param description 模板描述
     * @param metaPath    meta.json路径
     */
    public record SearchResult(String groupId, String artifactId, String description, String metaPath) {
    }
}
