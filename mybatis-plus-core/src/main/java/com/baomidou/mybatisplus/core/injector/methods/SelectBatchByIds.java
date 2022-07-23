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
import com.baomidou.mybatisplus.core.toolkit.sql.SqlScriptUtils;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlSource;

/**
 * 根据ID集合，批量查询数据
 *
 * @author hubin
 * @since 2018-04-06
 */
public class SelectBatchByIds extends AbstractMethod {

    public SelectBatchByIds() {
        super(SqlMethod.SELECT_BATCH_BY_IDS.getMethod());
    }

    /**
     * @param name 方法名
     * @since 3.5.0
     */
    public SelectBatchByIds(String name) {
        super(name);
    }

    @Override
    public MappedStatement injectMappedStatement(Class<?> mapperClass, Class<?> modelClass, TableInfo tableInfo) {
        // 对应: selectBatchIds(@Param(Constants.COLLECTION) Collection<? extends Serializable> idList)
        // 对应: SqlMethod.SELECT_BATCH_BY_IDS
        // sql脚本框架:
        // <script>
        // SELECT %s FROM %s WHERE %s IN (%s) %s
        // </script>
        SqlMethod sqlMethod = SqlMethod.SELECT_BATCH_BY_IDS;
        // 第1个%s: sqlSelectColumns(tableInfo, false) -> 查询出来的select
            // idColumn as idProperty,column1 as property1, column2, column3 as property3
        // 第2个%s: tableInfo.getTableName() -> 表名
        // 第3个%s: tableInfo.getKeyColumn() -> 主键列名
        // 第4个%s: SqlScriptUtils.convertForeach("#{item}", COLLECTION, null, "item", COMMA) -> 构建foreach标签
            // <foreach collection="coll" item="item" separator=",">
            //      #{item}
            // <foreach>
        // 第5个%s: 逻辑删除补充上的where条件
            // AND deleted = 0
        SqlSource sqlSource = languageDriver.createSqlSource(configuration, String.format(sqlMethod.getSql(),
                sqlSelectColumns(tableInfo, false), tableInfo.getTableName(), tableInfo.getKeyColumn(),
                SqlScriptUtils.convertForeach("#{item}", COLLECTION, null, "item", COMMA),
                tableInfo.getLogicDeleteSql(true, true)), Object.class);
        return addSelectMappedStatementForTable(mapperClass, getMethod(sqlMethod), sqlSource, tableInfo);
    }
}
