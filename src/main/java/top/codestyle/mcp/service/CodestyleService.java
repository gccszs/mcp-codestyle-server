package top.codestyle.mcp.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import top.codestyle.mcp.model.TemplateInfo;

import java.util.List;

@Slf4j
@Service
public class CodestyleService {

    /**
     * 静态变量
     */
    public final static String PROMPT_TEMPLATE = """
#目录树：
```
{rootTreeInfo}
```
#变量说明：
```
{inputVariables}
```
#详细模板：
{detailTemplates}
""".strip();

    /**
     * 根据任务名称搜索模板库中的代码风格模板
     * @param searchText 任务名称
     * @return 模板库中的代码风格模板
     */
    @Tool(name = "get-codestyle", description = "根据任务名称搜索模板库中的代码风格模板（每次操作代码时需要先检索相关代码风格模板）")
    public String codestyleSearch(@ToolParam(description = "searchText") String searchText) {
        // 1.根据任务名称检索模板库
        List<TemplateInfo> templateInfos = RemoteService.codestyleSearch(searchText);
        log.info("根据任务名称检索模板库，任务名称：{}，模板库中的代码风格模板：{}", searchText, templateInfos);
        // TODO 2.处理templateInfos, 得到配置文件：目录树、变量说明、详细模板
        String rootTreeInfo = "";
        String inputVariables = "";
        StringBuilder detailTemplates = new StringBuilder();
        // 示例代码：content一定要用```包裹```
        for (TemplateInfo templateInfo : templateInfos) {
            detailTemplates.append("```\n").append(templateInfo.getContent()).append("\n```\n");
        }
        // 3.组装，构建提示词
        String prompt = PROMPT_TEMPLATE.formatted(rootTreeInfo, inputVariables, detailTemplates.toString());
        return prompt;
    }

}
