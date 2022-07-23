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
package com.baomidou.mybatisplus.core.injector.methods;

import com.baomidou.mybatisplus.core.enums.SqlMethod;
import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlSource;

/**
 * 查询满足条件一条数据，为了精简注入方法，该方法采用 list.get(0) 处理后续不再使用
 *
 * @author hubin
 * @since 2018-04-06
 */
@Deprecated
public class SelectOne extends AbstractMethod {

    public SelectOne() {
        super(SqlMethod.SELECT_ONE.getMethod());
    }

    /**
     * @param name 方法名
     * @since 3.5.0
     */
    public SelectOne(String name) {
        super(name);
    }

    @Override
    public MappedStatement injectMappedStatement(Class<?> mapperClass, Class<?> modelClass, TableInfo tableInfo) {
        // 1. 确认 SqlMethod ->
        // <script>
        // %s SELECT %s FROM %s %s %s
        // </script>
        SqlMethod sqlMethod = SqlMethod.SELECT_ONE;
        // 分析:
        // 第一个%s: sqlFirst() 返回的一般都是: <if test=" ew != null and ew.sqlFirst != null"> ${ew.sqlFirst} </test>
        // 第二个%s: sqlSelectColumns(tableInfo, true) 返回的一般都是: 需要查询的字段的sql片段
        // 第三个%s: tableInfo.getTableName() 返回一般都是: 表名
        // 第四个%s: sqlWhereEntityWrapper(true, tableInfo)  返回的一般是: 需要筛选的字段的where片段
        // 第五个%s: sqlComment() 返回的一般是: <if test=" ew != null and ew.sqlComment != null"> ${ew.sqlComment} </test>
        SqlSource sqlSource = languageDriver.createSqlSource(configuration, String.format(sqlMethod.getSql(),
            sqlFirst(), sqlSelectColumns(tableInfo, true), tableInfo.getTableName(),
            sqlWhereEntityWrapper(true, tableInfo), sqlComment()), modelClass);
        return this.addSelectMappedStatementForTable(mapperClass, getMethod(sqlMethod), sqlSource, tableInfo);
    }
}
