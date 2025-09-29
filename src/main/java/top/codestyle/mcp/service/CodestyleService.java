package top.codestyle.mcp.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.codestyle.mcp.config.PromptTemplateLoader;
import top.codestyle.mcp.config.RepositoryConfig;
import top.codestyle.mcp.model.entity.*;
import top.codestyle.mcp.util.FileUtils;
import top.codestyle.mcp.util.TemplateUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Slf4j
@Service
public class CodestyleService {

    @Autowired
    private PromptTemplateLoader promptTemplateLoader;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RepositoryConfig repositoryConfig;

    // 通过加载器获取提示词模板
    private String buildPrompt(String treeStr, String varStr, String content) {
        // 使用加载器获取模板并格式化
        return String.format(promptTemplateLoader.getPromptTemplate(), treeStr, varStr, content);
    }

    /**
     * 根据任务名称搜索模板库中的代码风格模板
     */
    @Tool(name = "get-codestyle-template", description = "根据任务名称搜索模板库中的代码风格模板（每次操作代码时需要先检索相关代码风格模板）")
    public String codestyleSearch(@ToolParam(description = "searchText") String searchText) {
        // 1.根据任务名称检索模板库
        List<TemplateInfo> templateInfos = RemoteService.codestyleSearch(searchText);
        // 2.处理templateInfos, 得到配置文件：目录树、变量说明、详细模板
        TreeNode treeNode = TemplateUtils.buildTree(templateInfos);
        Map<String, String> vars = new LinkedHashMap<>();
        
        for (TemplateInfo n : templateInfos) {
            if (n.getInputVarivales() == null) continue;
            for (InputVariable v : n.getInputVarivales()) {
                String desc = String.format("%s[%s]", v.getVariableComment(), v.getVariableType());
                vars.putIfAbsent(v.getVariableName(), desc);
            }
        }
        
        // 使用TemplateUtils工具类
        String rootTreeInfo = TemplateUtils.buildTreeString(treeNode, "").trim();
        String inputVariables = TemplateUtils.buildVarString(vars).trim();
        StringBuilder detailTemplates = new StringBuilder();
        
        // 构建详细模板内容
        for (TemplateInfo templateInfo : templateInfos) {
            detailTemplates.append("```\n").append(templateInfo.getContent() != null ? templateInfo.getContent() : "").append("\n```\n");
        }
        
        // 3.组装，构建提示词
        return buildPrompt(rootTreeInfo, inputVariables, detailTemplates.toString());
    }

    
    public List<TemplateInfo> loadFromLocalRepo(List<TemplateInfo> input) throws IOException {

        String base = repositoryConfig.getLocalPath();
        List<TemplateInfo> result = new ArrayList<>();

        for (TemplateInfo req : input) {          // 每个 req 只代表一个文件
            Path repo = Paths.get(base, req.getGroupId(), req.getArtifactId());
            Path metaFile = repo.resolve("meta.json");

            MetaItem meta = null;                 // 用来承载命中 meta 的那一行
            if (Files.exists(metaFile)) {
                List<MetaItem> items = objectMapper.readValue(metaFile.toFile(),
                        new TypeReference<List<MetaItem>>() {});
                // 按 filename 快速查找
                meta = items.stream()
                        .filter(it -> it.getFilename().equalsIgnoreCase(req.getFilename()))
                        .findFirst()
                        .orElse(null);
            }

            TemplateInfo out;
            if (meta != null && Files.exists(repo.resolve(meta.getFilename()))) {
                /* ===== 本地命中 ===== */
                out = new TemplateInfo();
                out.setGroupId(req.getGroupId());
                out.setArtifactId(req.getArtifactId());
                out.setFilename(meta.getFilename());
                out.setFile_path(meta.getFilePath());
                out.setPath(meta.getFilePath() + "/" + meta.getFilename());
                out.setVersion(meta.getVersion());
                out.setDescription(meta.getDescription());
                out.setSha256(meta.getSha256());

                // 变量转换
                List<InputVariable> vars = new ArrayList<>();
                for (MetaVariable mv : meta.getInputVarivales()) {
                    InputVariable v = new InputVariable();
                    v.variableName = mv.getVariableName().replace("变量名：", "").trim();
                    v.variableType = mv.getVariableType().replace("变量类型：", "").trim();
                    v.variableComment = mv.getVariableComment();
                    vars.add(v);
                }
                out.setInputVarivales(vars);

                // 读内容
                out.setContent(Files.readString(repo.resolve(meta.getFilename()), StandardCharsets.UTF_8));
            } else {
                /* ===== 本地未命中，去文件服务器拉取 ===== */
                out = FileUtils.downloadFromFileServer(req);
                if (out == null) continue;   // 拉取失败就跳过
            }
            result.add(out);
        }
        return result;
    }
}