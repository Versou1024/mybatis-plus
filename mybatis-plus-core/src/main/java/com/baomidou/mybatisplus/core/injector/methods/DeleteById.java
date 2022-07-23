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
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.sql.SqlScriptUtils;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlSource;

import java.util.List;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

/**
 * 根据 ID 删除
 *
 * @author hubin
 * @since 2018-04-06
 */
public class DeleteById extends AbstractMethod {

    public DeleteById() {
        super("deleteById");
    }

    /**
     * @param name 方法名
     * @since 3.5.0
     */
    public DeleteById(String name) {
        super(name);
    }

    @Override
    public MappedStatement injectMappedStatement(Class<?> mapperClass, Class<?> modelClass, TableInfo tableInfo) {
        // 对应: BaseMapper#deleteById(Serializable id) || deleteById(T entity) 方法 -> 它传入的参数是唯一的哦
        // 对应: SqlMethod.LOGIC_DELETE_BY_ID
        String sql;

        // 逻辑删除 + 没有update时自动填充的字段
        // <script>
        //      UPDATE table_name
        //      SET
        //          <if test='property1 != null'> column1 = column1 + 1 </if>                         当@TableField.udpate() = "%s+1" ,且@TableField.fill()=FiledFill.NONE
        //          <if test='property2 != null'> column2 = now() </if>                               当@TableField.udpate() = "now()" ,且@TableField.fill()=FiledFill.NONE
        //          logic_deleted_column = 1                                                          逻辑删除字段
        //      WHERE id_column = #{id_property}        对于 BaseMapper#deleteById(Serializable id) 或者 BaseMapper#deleteById(T entity) 都是正确的,因为都只有一个参数
        //      AND logic_deleted_column = 0
        // </script>
        // 逻辑删除 + 有update时自动填充的字段
        // <script>
        //      UPDATE table_name
        //      SET
        //          <if test='property1 != null'> column1 = column1 + 1 </if>                         当@TableField.udpate() = "%s+1" 时,且@TableField.fill()=FiledFill.NONE
        //          column2 = now()                                                                   当@TableField.udpate() = "now()",且@TableField.fill()=FiledFill.UPDATE
        //          logic_deleted_column = 1                                                          逻辑删除
        //      WHERE id_column = #{id_property}
        //      AND logic_deleted_column = 0
        // </script>

        // 1. 逻辑删除的执行脚本框架:
        // <script>
        //      UPDATE %s %s WHERE %s=#{%s} %s
        // </script>
        SqlMethod sqlMethod = SqlMethod.LOGIC_DELETE_BY_ID;
        if (tableInfo.isWithLogicDelete()) {
            // 1.1 拿到实体类中标记需要做: 更新填充的字段 [❗️❗️❗️ 逻辑更新,是一个更新update操作]
            List<TableFieldInfo> fieldInfos = tableInfo.getFieldList().stream()
                .filter(TableFieldInfo::isWithUpdateFill)
                .filter(f -> !f.isLogicDelete())
                .collect(toList());
            // 1.2 需要更新自动填充的字段非空
            if (CollectionUtils.isNotEmpty(fieldInfos)) {
                // 1.2.1 构建set关键字的脚本片段哦
                // 结果为:
                // <if test='property1 != null'> column1 = #{property1} </if>                        当updateStrategy为FieldStrategy.NOE_NULL时
                // <if test='property2 != null and property2 != '' '> column2 = #{property2} </if>   当updateStrategy为FieldStrategy.NOE_EMPTY时
                // <if test='property3 != null'> column3 = column3 + 1 </if>                         当@TableField.udpate() = "%s+1" 时
                // <if test='property4 != null'> column4 = now() </if>                               当@TableField.udpate() = "now()" 时
                // deleted = 1
                String sqlSet = "SET " + SqlScriptUtils.convertIf(fieldInfos.stream()
                    .map(i -> i.getSqlSet(EMPTY)).collect(joining(EMPTY)), "!@org.apache.ibatis.type.SimpleTypeRegistry@isSimpleType(_parameter.getClass())", true)
                    + tableInfo.getLogicDeleteSql(false, false);
                // 第一个%s: tableInfo.getTableName() -> 数据表的表名
                // 第二个%s: sqlSet -> set关键字的脚本 [❗️❗️❗️ 对于需要自动填充的字段也会设置设置update上去]
                // SET <if test='property1 != null'> column1 = #{property1} </if>                        当updateStrategy为FieldStrategy.NOE_NULL时
                // SET <if test='property2 != null and property2 != '' '> column2 = #{property2} </if>   当updateStrategy为FieldStrategy.NOE_EMPTY时
                // SET <if test='property3 != null'> column3 = column3 + 1 </if>                         当@TableField.udpate() = "%s+1" 时
                // SET <if test='property4 != null'> column4 = now() </if>                               当@TableField.udpate() = "now()" 时
                //  deleted = 1
                // 第三个%s: tableInfo.getKeyColumn() -> 主键属性对应的列名
                // 第四个%s: tableInfo.getKeyProperty()) -> 主键属性的属性名
                // 第五个%s: tableInfo.getLogicDeleteSql(true, true) ->
                // and deleted = 0
                sql = String.format(sqlMethod.getSql(), tableInfo.getTableName(), sqlSet, tableInfo.getKeyColumn(),
                    tableInfo.getKeyProperty(), tableInfo.getLogicDeleteSql(true, true));
            }
            // 1.3 没有需要更新自动填充的字段
            else {
                // 第一个%s: tableInfo.getTableName() -> 数据表的表名
                // 第二个%s: sqlLogicSet(tableInfo) -> set关键字的脚本
                // set deleted = 1
                // 第三个%s:tableInfo.getKeyColumn() -> 主键属性对应的列名
                // 第四个%s: tableInfo.getKeyProperty()) -> 主键属性的属性名
                // 第五个%s: tableInfo.getLogicDeleteSql(true, true) ->
                // and deleted = 0
                // 最终结果一般都是
                // <script>
                //      UPDATE table_name
                //      SET logic_deleted_column = 1
                //      WHERE id_column = #{id_property}
                //      AND logic_deleted_column = 0
                // </script>
                sql = String.format(sqlMethod.getSql(), tableInfo.getTableName(), sqlLogicSet(tableInfo),
                    tableInfo.getKeyColumn(), tableInfo.getKeyProperty(),
                    tableInfo.getLogicDeleteSql(true, true));
            }
            SqlSource sqlSource = languageDriver.createSqlSource(configuration, sql, Object.class);
            return addUpdateMappedStatement(mapperClass, modelClass, getMethod(sqlMethod), sqlSource);
        }

        // 2. 物理删除的执行sql脚本框架:
        // <script>
        // DELETE FROM %s WHERE %s=#{%s} // ❗️❗ note: 已经使用#{}将主键属性包装起来啦
        // </script>
        else {
            sqlMethod = SqlMethod.DELETE_BY_ID;
            // 第一个%s: tableInfo.getTableName() -> 数据表的表名
            // 第二个%s: tableInfo.getKeyColumn() -> 主键属性对应的主键列名
            // 第三个%s: tableInfo.getKeyProperty() -> 主键属性
            sql = String.format(sqlMethod.getSql(), tableInfo.getTableName(), tableInfo.getKeyColumn(),
                tableInfo.getKeyProperty());
            SqlSource sqlSource = languageDriver.createSqlSource(configuration, sql, Object.class);
            return this.addDeleteMappedStatement(mapperClass, getMethod(sqlMethod), sqlSource);
        }
    }
}
