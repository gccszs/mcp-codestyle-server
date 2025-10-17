package top.codestyle.mcp.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.codestyle.mcp.config.RepositoryConfig;
import top.codestyle.mcp.model.meta.LocalMetaInfo;
import top.codestyle.mcp.model.sdk.MetaInfo;
import top.codestyle.mcp.model.sdk.MetaVariable;
import top.codestyle.mcp.util.MetaInfoConvertUtil;
import top.codestyle.mcp.util.SDKUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class TemplateService {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RepositoryConfig repositoryConfig;

    /**
     * 加载远程配置
     */
    public List<MetaInfo> search(String searchText) {
        // 远程拉取文件
        List<MetaInfo> metaInfos = SDKUtils.search(searchText);
        // 本地缓存
        return metaInfos;
    }

    /**
     * 加载模板文件
     */
    public List<LocalMetaInfo> loadTemplateFile(List<MetaInfo> metaInfos) {
//        // 本地拉取
//        List<LocalMetaInfo> localMetaInfos = new ArrayList<>();
//        // 比对本地元信息，取出不在本地的文件配置，然后远程拉取文件
//        try {
//            List<MetaInfo> Infos = loadFromLocalRepo(metaInfos);
//            for (MetaInfo Info: Infos) {
//                LocalMetaInfo convert = MetaInfoConvertUtil.convert(Info);
//                localMetaInfos.add(convert);
//            }
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//
//        // 填充LocalMetaInfo中的 templateContent 字段
//
//
//        // 本地异步同步
//        return localMetaInfos;
        // 1. 本地拉取
        List<LocalMetaInfo> localMetaInfos = new ArrayList<>();
        try {
            List<MetaInfo> infos = loadFromLocalRepo(metaInfos);   // 名字已修正
            for (MetaInfo info : infos) {
                LocalMetaInfo convert = MetaInfoConvertUtil.convert(info);
                // 2. 读取模板文件内容并填充
                convert.setTemplateContent(readTemplateContent(info));
                localMetaInfos.add(convert);
            }
        } catch (IOException e) {
            throw new RuntimeException("加载模板文件失败", e);
        }
        return localMetaInfos;
    }

    /**
     * 根据 MetaInfo 里记录的文件名（含路径）读取模板内容
     */
    private String readTemplateContent(MetaInfo info) throws IOException {
        // 拼装绝对路径：本地仓库根目录 + 相对路径 + 文件名
        Path templatePath = Paths.get(repositoryConfig.getLocalPath(),info.getGroupId(),info.getArtifactId(),info.getFilename())
                .toAbsolutePath()
                .normalize();


        if (!Files.exists(templatePath)) {
            throw new IOException("模板文件不存在: " + templatePath);
        }
        // 一次性读入，文件通常几十 KB 以内，性能足够
        return Files.readString(templatePath, StandardCharsets.UTF_8);
    }
    /* -------------------------------------------------
     *  若需要异步填充（可选）
     * ------------------------------------------------- */
    private final ExecutorService pool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r);
        t.setName("template-loader-" + t.getId());
        return t;
    });

    public CompletableFuture<List<LocalMetaInfo>> loadTemplateFileAsync(List<MetaInfo> metaInfos) {
        return CompletableFuture.supplyAsync(() -> loadTemplateFile(metaInfos), pool);
    }




    public List<MetaInfo> loadFromLocalRepo(List<MetaInfo> input) throws IOException {

        String base = repositoryConfig.getLocalPath();
        List<MetaInfo> result = new ArrayList<>();

        for (MetaInfo req : input) {          // 每个 req 只代表一个文件
            Path repo = Paths.get(base, req.getGroupId(), req.getArtifactId());
            Path metaFile = repo.resolve("meta.json");

            LocalMetaInfo meta = null;                 // 用来承载命中 meta 的那一行
            if (Files.exists(metaFile)) {
                List<LocalMetaInfo> items = objectMapper.readValue(metaFile.toFile(),
                        new TypeReference<List<LocalMetaInfo>>() {
                        });
                // 按 filename 快速查找
                meta = items.stream()
                        .filter(it -> it.getFilename().equalsIgnoreCase(req.getFilename()))
                        .findFirst()
                        .orElse(null);
            }

            MetaInfo out;
            if (meta != null && Files.exists(repo.resolve(meta.getFilename()))) {
                /* ===== 本地命中 ===== */
                out = new MetaInfo();
                out.setGroupId(req.getGroupId());
                out.setArtifactId(req.getArtifactId());
                out.setFilename(meta.getFilename());
                out.setFilePath(meta.getFilePath());
                out.setPath(meta.getFilePath() + "/" + meta.getFilename());
                out.setVersion(meta.getVersion());
                out.setDescription(meta.getDescription());
                out.setSha256(meta.getSha256());

                // 变量转换
                List<MetaVariable> vars = new ArrayList<>();
                for (MetaVariable mv : meta.getMetaVariables()) {
                    MetaVariable v = new MetaVariable();
                    v.variableName = mv.getVariableName().replace("变量名：", "").trim();
                    v.variableType = mv.getVariableType().replace("变量类型：", "").trim();
                    v.variableComment = mv.getVariableComment();
                    vars.add(v);
                }
                out.setMetaVariables(vars);
                // 读内容
//                out.setContent(Files.readString(repo.resolve(meta.getFilename()), StandardCharsets.UTF_8));
            } else {
                /* ===== 本地未命中，去文件服务器拉取 ===== */
                out = SDKUtils.downloadFile(req, repositoryConfig.getRemotePath());
                System.err.println("当前未命中情况下out内容"+out);
                if (out == null) continue;   // 拉取失败就跳过
            }
            result.add(out);
        }
        return result;
    }
}
