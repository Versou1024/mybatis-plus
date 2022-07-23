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
 * 查询满足条件所有数据
 *
 * @author hubin
 * @since 2018-04-06
 */
public class SelectList extends AbstractMethod {

    public SelectList() {
        super(SqlMethod.SELECT_LIST.getMethod());
    }

    /**
     * @param name 方法名
     * @since 3.5.0
     */
    public SelectList(String name) {
        super(name);
    }

    @Override
    public MappedStatement injectMappedStatement(Class<?> mapperClass, Class<?> modelClass, TableInfo tableInfo) {
        // 对应: BaseMapper的方法 -> List<T> selectList(@Param(Constants.WRAPPER) Wrapper<T> queryWrapper)
        // 使用的sql脚本主体: 形参为"ew"的queryWrapper
        // <script>
        // %s SELECT %s FROM %s %s %s %s
        // </script>
        SqlMethod sqlMethod = SqlMethod.SELECT_LIST;
        // 第一个%s: sqlFirst() ->
            // <if test=" ew != null and ew.sqlFirst != null">
            //      ${ew.sqlFirst}
            // </test>
        // 第二个%s: sqlSelectColumns(tableInfo, true) ->
            // <choose>
            // <when test=" ew != null and ew.sqlSelect != null"> ${ew.sqlSelect} </when>  [❗️❗️❗️ 也就是说如果QueryWrapper如果指定返回的列名,那么 ew.sqlSelect 就不为空,以此为对象]
            // <otherwise> idColumn as idProperty,column1 as property1, column2, column3 as property3 </otherwise> [❗️❗️❗️ 如果QueryWrapper如果没有指定返回的列名,那就以PO实体类确定]
            // </choose>
        // 第三个%s: tableInfo.getTableName() -> 表名
        // 第四个%s: sqlWhereEntityWrapper(true, tableInfo) -> 获取<where>标签
            // <where>
            //      <choose>
            //          <when test=" ew != null">
            //             <if test="ew.entity != null">
            //                  <if test=" #{ew.entity. + keyProperty} != null "> keyColumn = #{ew.entity. + keyProperty} </if>
            //                  <if test=" #{ew.entity.property1} != null " > AND column = #{ew.entity. + e1l} </if>   // ❗️❗️❗ <if>标签的test是收@TableField.whereStrategy()控制确定成立条件,AND后的比较逻辑是受到@TableField.condition()控制的️
            //                  <if test=" #{ew.entity.property2} != null and #{ew.entity.property} != ''" > AND column != #{ew.entity. + el2} </if> //  [比如: condition为SQLCondition.EQUALS,whereStrategy=FieldStrategy.NOT_EMPTY]
            //                  <if test=" #{ew.entity.property3} != null " > AND column LIKE concat('%',#{ew.entity. + el3},'%'} </if>   //  [比如: condition为SQLCondition.LIKE,whereStrategy=FieldStrategy.NOT_NULL]
            //             <!if>
            //             AND deleted = 0
            //             <if test= "ew.sqlSegment != null and ew.sqlSegment != '' and "ew.nonEmptyOfNormal"" > AND ${ew.sqlSegment} </if>
            //             <if test= "ew.sqlSegment != null and ew.sqlSegment != '' and "ew.emptyOfNormal"" > ${ew.sqlSegment} </if>
            //          </when>
            //          <otherwise> deleted = 0 </otherwise>
            //      </choose>
        // </where>
        // 第五个%s: sqlOrderBy(tableInfo) -> 确定排序规则
            // <if test=" ew == null or ew.useAnnotationOrderBy">
            //      ORDER BY column1 asc, column2 desc
            // </if>
        // 第六个%s: sqlComment() ->
            // <if test=" ew != null and ew.sqlComment != null">
            //      ${ew.sqlComment}
            // </if>
        String sql = String.format(sqlMethod.getSql(), sqlFirst(), sqlSelectColumns(tableInfo, true), tableInfo.getTableName(),
            sqlWhereEntityWrapper(true, tableInfo), sqlOrderBy(tableInfo), sqlComment());
        SqlSource sqlSource = languageDriver.createSqlSource(configuration, sql, modelClass);
        return this.addSelectMappedStatementForTable(mapperClass, getMethod(sqlMethod), sqlSource, tableInfo);
    }
}
