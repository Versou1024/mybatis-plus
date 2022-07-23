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
 * 根据 entity 条件删除记录
 *
 * @author hubin
 * @since 2018-04-06
 */
public class Delete extends AbstractMethod {

    public Delete() {
        super("delete");
    }

    /**
     * @param name 方法名
     * @since 3.5.0
     */
    public Delete(String name) {
        super(name);
    }

    @Override
    public MappedStatement injectMappedStatement(Class<?> mapperClass, Class<?> modelClass, TableInfo tableInfo) {
        String sql;

        SqlMethod sqlMethod = SqlMethod.LOGIC_DELETE;
        // 1. 逻辑删除
        // 1.1 逻辑删除的语句
        // <script>
        // UPDATE %s %s %s %s
        // </script>
        if (tableInfo.isWithLogicDelete()) {
            // 第一个%s: tableInfo.getTableName() : 表名
            // 第二个%s: sqlLogicSet(tableInfo) : 设置逻辑字段为逻辑删除值, 例如 set deleted = 1
            // 第三个%s: sqlWhereEntityWrapper(true, tableInfo): <where>语句
            // 第四个%s: sqlComment(): <if test=" ew != null and ew.sqlComment != null"> ${ew.sqlComment} </test>
            sql = String.format(sqlMethod.getSql(), tableInfo.getTableName(), sqlLogicSet(tableInfo),
                sqlWhereEntityWrapper(true, tableInfo),
                sqlComment());
            SqlSource sqlSource = languageDriver.createSqlSource(configuration, sql, modelClass);
            return addUpdateMappedStatement(mapperClass, modelClass, getMethod(sqlMethod), sqlSource);
        }
        // 2. 物理删除
        // 2.1 物理删除的语句
        // <script>
        // DELETE FROM %s %s %s
        // </script>
        else {
            sqlMethod = SqlMethod.DELETE;
            // 第一个%s: tableInfo.getTableName() : 表名
            // 第二个%s: sqlWhereEntityWrapper(true, tableInfo): <where>语句
            // 第三个%s: sqlComment(): <if test=" ew != null and ew.sqlComment != null"> ${ew.sqlComment} </test>
            sql = String.format(sqlMethod.getSql(), tableInfo.getTableName(),
                sqlWhereEntityWrapper(true, tableInfo),
                sqlComment());
            SqlSource sqlSource = languageDriver.createSqlSource(configuration, sql, modelClass);
            return this.addDeleteMappedStatement(mapperClass, getMethod(sqlMethod), sqlSource);
        }
    }
}
