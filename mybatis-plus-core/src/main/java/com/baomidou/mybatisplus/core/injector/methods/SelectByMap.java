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
 * 根据columnMap 查询一条数据
 *
 * @author hubin
 * @since 2018-04-06
 */
public class SelectByMap extends AbstractMethod {

    public SelectByMap() {
        super(SqlMethod.SELECT_BY_MAP.getMethod());
    }

    /**
     * @param name 方法名
     * @since 3.5.0
     */
    public SelectByMap(String name) {
        super(name);
    }

    @Override
    public MappedStatement injectMappedStatement(Class<?> mapperClass, Class<?> modelClass, TableInfo tableInfo) {
        // 对应: BaseMapper#selectByMap(@Param(Constants.COLUMN_MAP) Map<String, Object> columnMap) -> 形参名为"cm"
        // 对应: SqlMethod.SELECT_BY_MAP;
        // 1. sql脚本框架:
        //<script>
        //  SELECT %s FROM %s %s
        //</script>
        SqlMethod sqlMethod = SqlMethod.SELECT_BY_MAP;
        // 2.
        // 第1个%s: sqlSelectColumns(tableInfo, false)
            // idColumn as idProperty,column1 as property1, column2, column3 as property3
        // 第2个%s: tableInfo.getTableName() -> 表名
        // 第3个%s: sqlWhereByMap(tableInfo) -> 以逻辑删除为例,并通过传递名为"cm"的map参数做where筛选条件
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
        String sql = String.format(sqlMethod.getSql(), sqlSelectColumns(tableInfo, false),
            tableInfo.getTableName(), sqlWhereByMap(tableInfo));
        SqlSource sqlSource = languageDriver.createSqlSource(configuration, sql, Map.class);
        return this.addSelectMappedStatementForTable(mapperClass, getMethod(sqlMethod), sqlSource, tableInfo);
    }
}
