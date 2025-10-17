package top.codestyle.mcp.util;

import top.codestyle.mcp.model.meta.LocalMetaInfo;
import top.codestyle.mcp.model.meta.LocalMetaVariable;
import top.codestyle.mcp.model.sdk.MetaInfo;
import top.codestyle.mcp.model.sdk.MetaVariable;

import java.util.List;
import java.util.stream.Collectors;

public class MetaInfoConvertUtil {

    /**
     * MetaInfo -> LocalMetaInfo
     */
    public static LocalMetaInfo convert(MetaInfo source) {
        if (source == null) {
            return null;
        }
        LocalMetaInfo target = new LocalMetaInfo();

        // 1. 普通字段
        target.setId(source.getId());
        target.setVersion(source.getVersion());
        target.setGroupId(source.getGroupId());
        target.setArtifactId(source.getArtifactId());
        target.setFilePath(source.getFilePath());
        target.setDescription(source.getDescription());
        target.setFilename(source.getFilename());
        target.setSha256(source.getSha256());

        // 2. 模板内容（如果 MetaInfo 没有，这里留空）
        // target.setTemplateContent(null);

        // 3. 列表字段：MetaVariable -> LocalMetaVariable
        List<MetaVariable> vars = source.getMetaVariables();
        if (vars != null && !vars.isEmpty()) {
            target.setMetaVariables(vars);
        }
        return target;
    }

    /**
     * MetaVariable -> LocalMetaVariable
     */
    private static LocalMetaVariable convertVariable(MetaVariable src) {
        if (src == null) {
            return null;
        }
        return new LocalMetaVariable();
    }

    /* 如果列表量大，可用并行流
    public static List<LocalMetaInfo> convertList(List<MetaInfo> sources) {
        return sources.parallelStream()
                      .map(MetaInfoConvertUtil::convert)
                      .collect(Collectors.toList());
    }
    */
}