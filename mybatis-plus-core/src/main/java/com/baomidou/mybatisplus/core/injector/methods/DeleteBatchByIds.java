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
 * 根据 ID 集合删除
 *
 * @author hubin
 * @since 2018-04-06
 */
public class DeleteBatchByIds extends AbstractMethod {

    public DeleteBatchByIds() {
        super("deleteBatchIds");
    }

    /**
     * @param name 方法名
     * @since 3.5.0
     */
    public DeleteBatchByIds(String name) {
        super(name);
    }

    @Override
    public MappedStatement injectMappedStatement(Class<?> mapperClass, Class<?> modelClass, TableInfo tableInfo) {
        // 对应: BaseMapper#deleteBatchIds(@Param(Constants.COLLECTION) Collection<?> idList) -> 形参只有一个: Constants.COLLECTION = "coll"
        // 对应: SqlMethod.LOGIC_DELETE_BATCH_BY_IDS / DELETE_BATCH_BY_IDS -> 取决于是否为逻辑删除

        String sql;
        // 1. 逻辑删除情况下的批量删除操作
        // <script>
        // UPDATE %s %s WHERE %s IN (%s) %s
        // </script>
        SqlMethod sqlMethod = SqlMethod.LOGIC_DELETE_BATCH_BY_IDS;
        if (tableInfo.isWithLogicDelete()) {
            sql = logicDeleteScript(tableInfo, sqlMethod);
            SqlSource sqlSource = languageDriver.createSqlSource(configuration, sql, Object.class);
            return addUpdateMappedStatement(mapperClass, modelClass, getMethod(sqlMethod), sqlSource);
        }
        // 2. 物理删除的下的根据id的批量参数操作
        //  <script>
        //  DELETE FROM %s WHERE %s IN (%s)
        //  </script>
        else {
            sqlMethod = SqlMethod.DELETE_BATCH_BY_IDS;
            // 第一个%s: tableInfo.getTableName() -> 表名
            // 第二个%s: tableInfo.getKeyColumn() -> 主键的列名
            // 第三个%s: SqlScriptUtils.convertForeach() 构建 <foreach> 标签
                // <foreach collection="coll" item="item" separator=",">      -> 注意这里的coll就是deleteBatchId(..)传递进来的id集合哦
                //      <choose>
                //          <when test="@org.apache.ibatis.type.SimpleTypeRegistry@isSimpleType(item.getClass())"> #{item} </when> -> ❗️简单类型,也就是集合中的元素就是待删除的id
                //          <otherwise> #{item.keyProperty} </otherwise>        -> ❗️非简单类型,认为集合中的元素是实体类,因此通过item.keyProperty获取实体列中的学会了哦
                //      </choose>
                // <foreach>
            sql = String.format(sqlMethod.getSql(), tableInfo.getTableName(), tableInfo.getKeyColumn(),
                SqlScriptUtils.convertForeach(
                    SqlScriptUtils.convertChoose("@org.apache.ibatis.type.SimpleTypeRegistry@isSimpleType(item.getClass())",
                        "#{item}", "#{item." + tableInfo.getKeyProperty() + "}"),
                    COLLECTION, null, "item", COMMA));
            SqlSource sqlSource = languageDriver.createSqlSource(configuration, sql, Object.class);
            return this.addDeleteMappedStatement(mapperClass, getMethod(sqlMethod), sqlSource);
        }
    }

    /**
     * @param tableInfo 表信息
     * @return 逻辑删除脚本
     * @since 3.5.0
     */
    public String logicDeleteScript(TableInfo tableInfo, SqlMethod sqlMethod) {
        // 获取逻辑删除的批量删除脚本:
        // <script>
        // UPDATE %s %s WHERE %s IN (%s) %s
        // </script>
        // 第一个%s: tableInfo.getTableName() -> 表名
        // 第二个%s: sqlLogicSet(tableInfo)   -> 逻辑删除的set字段
            // set deleted = 1
        // 第三个%s: tableInfo.getKeyColumn() -> 主键列名
        // 第四个%s: SqlScriptUtils.convertForeach(..) -> 构建<foreach>标签,处理批量删除逻辑
            // <foreach collection="coll" item="item" separator=",">      -> 注意这里的coll就是deleteBatchId(..)传递进来的id集合哦
            //      <choose>
            //          <when test="@org.apache.ibatis.type.SimpleTypeRegistry@isSimpleType(item.getClass())"> #{item} </when> -> ❗️简单类型,也就是集合中的元素就是待删除的id
            //          <otherwise> #{item.keyProperty} </otherwise>        -> ❗️非简单类型,认为集合中的元素是实体类,因此通过item.keyProperty获取实体列中的学会了哦
            //      </choose>
            // <foreach>
        // 第五个%s:
            // and deleted = 0

        return String.format(sqlMethod.getSql(), tableInfo.getTableName(),
            sqlLogicSet(tableInfo), tableInfo.getKeyColumn(), SqlScriptUtils.convertForeach(
                SqlScriptUtils.convertChoose("@org.apache.ibatis.type.SimpleTypeRegistry@isSimpleType(item.getClass())",
                    "#{item}", "#{item." + tableInfo.getKeyProperty() + "}"),
                COLLECTION, null, "item", COMMA),
            tableInfo.getLogicDeleteSql(true, true));
    }
}
