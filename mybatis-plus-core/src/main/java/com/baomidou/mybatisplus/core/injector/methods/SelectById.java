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
import org.apache.ibatis.scripting.defaults.RawSqlSource;

/**
 * 根据ID 查询一条数据
 *
 * @author hubin
 * @since 2018-04-06
 */
public class SelectById extends AbstractMethod {

    public SelectById() {
        super(SqlMethod.SELECT_BY_ID.getMethod());
    }

    /**
     * @param name 方法名
     * @since 3.5.0
     */
    public SelectById(String name) {
        super(name);
    }

    @Override
    public MappedStatement injectMappedStatement(Class<?> mapperClass, Class<?> modelClass, TableInfo tableInfo) {
        // 对应: BaseMapper#selectById(Serializable id)
        // 对应: SqlMethod.SELECT_BY_ID
        // 脚本框架:
        // SELECT %s FROM %s WHERE %s=#{%s} %s
        SqlMethod sqlMethod = SqlMethod.SELECT_BY_ID;
        // 第1个%s: sqlSelectColumns(tableInfo, false) ->
            // idColumn as idProperty,column1 as property1, column2, column3 as property3
        // 第2个%s: tableInfo.getTableName() -> 表名
        // 第3个%s: tableInfo.getKeyColumn() -> 主键id的列名
        // 第4个%s: tableInfo.getKeyProperty() -> 主键id的属性名
        // 第5个%s: tableInfo.getLogicDeleteSql(true, true) -> 逻辑未删除
            //  and deleted = 0
        SqlSource sqlSource = new RawSqlSource(configuration, String.format(sqlMethod.getSql(),
                sqlSelectColumns(tableInfo, false),
                tableInfo.getTableName(), tableInfo.getKeyColumn(), tableInfo.getKeyProperty(),
                tableInfo.getLogicDeleteSql(true, true)), Object.class);
        return this.addSelectMappedStatementForTable(mapperClass, getMethod(sqlMethod), sqlSource, tableInfo);
    }
}
