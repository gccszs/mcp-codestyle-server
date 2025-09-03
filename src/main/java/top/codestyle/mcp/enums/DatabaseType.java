package top.codestyle.mcp.enums;

import java.io.Serializable;

/**
 * 数据库类型枚举，实现ISqlFunction接口，提供不同数据库的SQL函数实现
 */
public enum DatabaseType implements ISqlFunction {
    /**
     * MySQL数据库类型
     */
    MYSQL("MySQL") {
        /**
         * MySQL的FIND_IN_SET函数实现，判断值是否在集合中
         * @param value 要查找的值
         * @param set 目标集合（通常是数据库字段名）
         * @return 生成的SQL条件片段
         */
        public String findInSet(Serializable value, String set) {
            return "find_in_set('%s', %s) <> 0".formatted(value, set);
        }
    },

    /**
     * PostgreSQL数据库类型
     */
    POSTGRE_SQL("PostgreSQL") {
        /**
         * PostgreSQL的FIND_IN_SET等效实现，通过position函数判断值是否在集合中
         * @param value 要查找的值
         * @param set 目标集合（通常是数据库字段名）
         * @return 生成的SQL条件片段
         */
        public String findInSet(Serializable value, String set) {
            return "(select position(',%s,' in ','||%s||',')) <> 0".formatted(value, set);
        }
    };

    /**
     * 数据库类型名称
     */
    private final String database;

    private DatabaseType(String database) {
        this.database = database;
    }

    /**
     * 根据数据库名称获取对应的DatabaseType枚举实例，忽略大小写
     * @param database 数据库名称
     * @return 匹配的DatabaseType枚举实例，无匹配时返回null
     */
    public static DatabaseType get(String database) {
        DatabaseType[] var1 = values();
        int var2 = var1.length;

        for(int var3 = 0; var3 < var2; ++var3) {
            DatabaseType databaseType = var1[var3];
            if (databaseType.database.equalsIgnoreCase(database)) {
                return databaseType;
            }
        }

        return null;
    }

    public String getDatabase() {
        return this.database;
    }
}
