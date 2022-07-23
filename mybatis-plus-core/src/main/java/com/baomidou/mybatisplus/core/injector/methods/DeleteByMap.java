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

import java.util.Map;

/**
 * 根据columnMap 条件删除记录
 *
 * @author hubin
 * @since 2018-04-06
 */
public class DeleteByMap extends AbstractMethod {

    public DeleteByMap() {
        super("deleteByMap");
    }

    /**
     * @param name 方法名
     * @since 3.5.0
     */
    public DeleteByMap(String name) {
        super(name);
    }

    @Override
    public MappedStatement injectMappedStatement(Class<?> mapperClass, Class<?> modelClass, TableInfo tableInfo) {
        // 对应: BaseMapper#deleteByMap(@Param(Constants.COLUMN_MAP) Map<String, Object> columnMap)
        // 对应: SqlMethod.LOGIC_DELETE_BY_MAP / DELETE_BY_MAP -> [取决于逻辑删除还是物理删除]
        String sql;
        SqlMethod sqlMethod = SqlMethod.LOGIC_DELETE_BY_MAP;
        // 1. 逻辑删除的情况:
        // <script>
        // UPDATE %s %s %s
        // </script>
        if (tableInfo.isWithLogicDelete()) {
            // 第一个%s: tableInfo.getTableName() -> 表名
            // 第二个%s: sqlLogicSet(tableInfo) -> 逻辑删除的set的sql脚本
                // set deleted = 0
            // 第三个%s: sqlWhereByMap(tableInfo) -> 根据传入的map决定筛选where的逻辑哦
                // <where>
                //  <if test=" cm != null and !cm.isEmpty">
                //      <foreach collection="cm" index="k" item="v" separator="AND">
                //          <choose>
                //              <when test="v == null"> ${k} IS NULL </when>        -> k和v来自于<foreach>标签的collection为"cm"的map时,index="k",item="v"
                //              <otherwise> ${k} = #{v} </otherwise>
                //          </choose>
                //      <foreach>
                //  </if>
                // and deleted = 0
                // <where>
            sql = String.format(sqlMethod.getSql(), tableInfo.getTableName(), sqlLogicSet(tableInfo), sqlWhereByMap(tableInfo));
            SqlSource sqlSource = languageDriver.createSqlSource(configuration, sql, Map.class);
            return addUpdateMappedStatement(mapperClass, Map.class, getMethod(sqlMethod), sqlSource);
        } else {
            sqlMethod = SqlMethod.DELETE_BY_MAP;
            sql = String.format(sqlMethod.getSql(), tableInfo.getTableName(), this.sqlWhereByMap(tableInfo));
            SqlSource sqlSource = languageDriver.createSqlSource(configuration, sql, Map.class);
            return this.addDeleteMappedStatement(mapperClass, getMethod(sqlMethod), sqlSource);
        }
    }
}
