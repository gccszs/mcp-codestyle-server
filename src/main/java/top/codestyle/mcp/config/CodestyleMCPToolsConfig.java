package top.codestyle.mcp.config;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import top.codestyle.mcp.service.CodestyleService;

/**
 * 管理MCP工具的注册 配置类
 *
 * @author ChonghaoGao
 * @date 2025/12/13 22:44)
 */
@Configuration
public class CodestyleMCPToolsConfig {

    @Bean
    public ToolCallbackProvider codestyleTools(CodestyleService codestyleService){
        return MethodToolCallbackProvider.builder().toolObjects(codestyleService).build();
    }

    //可以拓展新的工具
}
