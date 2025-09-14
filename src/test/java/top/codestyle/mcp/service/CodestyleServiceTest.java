package top.codestyle.mcp.service;


import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.spec.McpSchema;

import java.io.IOException;
import java.util.Map;

/**
 * 使用stdio传输，MCP服务器由客户端自动启动
 * 但你需要先构建服务器jar:
 *
 * <pre>
 * ./mvnw clean install -DskipTests
 * </pre>
 */
class CodestyleServiceTest {

    public static void main(String[] args) {

        String Root_Path = "E:/kaiyuan/mcp-codestyle-server";

        var stdioParams = ServerParameters.builder("java")
                .args("-jar",
                        "-Dspring.ai.mcp.server.stdio=true",
                        "-Dspring.main.web-application-type=none",
                        "-Dlogging.pattern.console=",
                        Root_Path + "/target/mcp-codestyle-server-0.0.1.jar")
                .build();

        var transport = new StdioClientTransport(stdioParams);
        var client = McpClient.sync(transport).build();

        client.initialize();

        // 列出并展示可用的工具
        McpSchema.ListToolsResult toolsList = client.listTools();
        System.out.println("可用工具 = " + toolsList);

        // 获取模板
        McpSchema.CallToolResult codestyle = client.callTool(
                new McpSchema.CallToolRequest("get-codestyle",
                Map.of("searchText", "CRUD")));
        System.out.println("代码模板: " + codestyle);

        client.closeGracefully();
    }
}