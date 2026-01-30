package com.chengliuxiang.xiaochengshu.data.align.constant;

public class TableConstants {

    /**
     * 表名中的分隔符
     */
    private static final String TABLE_NAME_SEPARATE = "_";

    /**
     * 拼接表名后缀
     * @param date
     * @param hashKey
     * @return
     */
    public static String buildTableNameSuffix(String date, int hashKey) {
        return date + TABLE_NAME_SEPARATE + hashKey;
    }
}
