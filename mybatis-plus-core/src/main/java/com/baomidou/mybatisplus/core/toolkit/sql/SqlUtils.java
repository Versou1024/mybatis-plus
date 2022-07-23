/*
 * Copyright (c) 2011-2022, baomidou (jobob@qq.com).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.baomidou.mybatisplus.core.toolkit.sql;

import com.baomidou.mybatisplus.core.enums.SqlLike;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.Assert;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SqlUtils工具类
 * !!! 本工具不适用于本框架外的类使用 !!!
 *
 * @author Caratacus
 * @since 2016-11-13
 */
@SuppressWarnings("serial")
public abstract class SqlUtils implements Constants {

    // 以下实例都是满足pattern匹配的:
    // {@12}
    // {@user}
    // {@user:name}
    // {@user:city:name}
    private static final Pattern pattern = Pattern.compile("\\{@((\\w+?)|(\\w+?:\\w+?)|(\\w+?:\\w+?:\\w+?))}");

    /**
     * 用%连接like
     *
     * @param str 原字符串
     * @return like 的值
     */
    public static String concatLike(Object str, SqlLike type) {
        switch (type) {
            case LEFT:
                // 1. %str
                return PERCENT + str;
            case RIGHT:
                // 2. str%
                return str + PERCENT;
            default:
                // 3. %str%
                return PERCENT + str + PERCENT;
        }
    }

    public static List<String> findPlaceholder(String sql) {
        Matcher matcher = pattern.matcher(sql);
        List<String> list = new ArrayList<>();
        while (matcher.find()) {
            list.add(matcher.group());
        }
        return list;
    }

    public static String replaceSqlPlaceholder(String sql, List<String> placeHolder, String escapeSymbol) {
        for (String s : placeHolder) {
            // 1. 去掉开头的 '{@' 和结尾的 '}'
            String s1 = s.substring(2, s.length() - 1);
            // 2. 确定第一个 ":" 的位置
            int i1 = s1.indexOf(COLON);
            String tableName;
            String alisa = null;
            String asAlisa = null;
            // 3.1 占位符中没有":"
            if (i1 < 0) {
                // 3.1.1 tableName 就是整个占位符
                tableName = s1;
            }
            // 3.2 占位符中有":"
            else {
                // 以: {@user:city:name} 为例
                // 3.2.1 user 就是 tableName
                tableName = s1.substring(0, i1);
                s1 = s1.substring(i1 + 1);
                i1 = s1.indexOf(COLON);
                if (i1 < 0) {
                    // 如果以 {@user:city} 为例
                    // alisa 即使city
                    alisa = s1;
                } else {
                    // 3.2.2 alisa 就是 city
                    alisa = s1.substring(0, i1);
                    // 3.2.3 asAlisa 就是 name
                    asAlisa = s1.substring(i1 + 1);
                }
            }
            sql = sql.replace(s, getSelectBody(tableName, alisa, asAlisa, escapeSymbol));
        }
        return sql;
    }

    public static String getSelectBody(String tableName, String alisa, String asAlisa, String escapeSymbol) {
        // 1. 拿到实体类对应的TableInfo
        TableInfo tableInfo = TableInfoHelper.getTableInfo(tableName);
        Assert.notNull(tableInfo, "can not find TableInfo Cache by \"%s\"", tableName);
        // 2.  s 一般就是 idColumn as idProperty,column1 as property1, column2, column3 as property3
        String s = tableInfo.chooseSelect(TableFieldInfo::isSelect);
        if (alisa == null) {
            return s;
        }
        // 3. 继续递归哦
        return getNewSelectBody(s, alisa, asAlisa, escapeSymbol);
    }

    public static String getNewSelectBody(String selectBody, String alisa, String asAlisa, String escapeSymbol) {
        String[] split = selectBody.split(COMMA);
        StringBuilder sb = new StringBuilder();
        boolean asA = asAlisa != null;
        for (String body : split) {
            final String sa = alisa.concat(DOT);
            if (asA) {
                int as = body.indexOf(AS);
                if (as < 0) {
                    sb.append(sa).append(body).append(AS).append(escapeColumn(asAlisa.concat(DOT).concat(body), escapeSymbol));
                } else {
                    String column = body.substring(0, as);
                    String property = body.substring(as + 4);
                    property = StringUtils.getTargetColumn(property);
                    sb.append(sa).append(column).append(AS).append(escapeColumn(asAlisa.concat(DOT).concat(property), escapeSymbol));
                }
            } else {
                sb.append(sa).append(body);
            }
            sb.append(COMMA);
        }
        return sb.deleteCharAt(sb.length() - 1).toString();
    }

    private static String escapeColumn(String column, String escapeSymbol) {
        return escapeSymbol.concat(column).concat(escapeSymbol);
    }
}
