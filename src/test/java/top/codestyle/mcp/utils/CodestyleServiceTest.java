package top.codestyle.mcp.utils;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import top.codestyle.mcp.model.entity.TemplateInfo;
import top.codestyle.mcp.service.CodestyleService;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static top.codestyle.mcp.service.RemoteService.createExampleTemplateInfos;

@SpringBootTest
class CodestyleServiceTest {

    @Autowired
    private CodestyleService codestyleService;

    @Test
    void testCheck() {
        List<TemplateInfo> nodes = createExampleTemplateInfos();
        Map<String, Boolean> r = codestyleService.check(nodes);
        assertFalse(r.isEmpty());
    }
}