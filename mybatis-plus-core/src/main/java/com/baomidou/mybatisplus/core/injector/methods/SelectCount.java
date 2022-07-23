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
 * 查询满足条件总记录数
 *
 * @author hubin
 * @since 2018-04-08
 */
public class SelectCount extends AbstractMethod {

    public SelectCount() {
        super(SqlMethod.SELECT_COUNT.getMethod());
    }

    /**
     * @param name 方法名
     * @since 3.5.0
     */
    public SelectCount(String name) {
        super(name);
    }

    @Override
    public MappedStatement injectMappedStatement(Class<?> mapperClass, Class<?> modelClass, TableInfo tableInfo) {
        // 对应: selectCount(@Param(Constants.WRAPPER) Wrapper<T> queryWrapper) -> 形参名为"ew"的queryWrapper包装器哦
        // 对应: sqlMethod = SqlMethod.SELECT_COUNT
        // 1. sql脚本结构
        // <script>
        //      %s SELECT COUNT(%s) FROM %s %s %s
        // </script>
        SqlMethod sqlMethod = SqlMethod.SELECT_COUNT;
        // 第1个%s: sqlFirst() -> 取决于 ew.sqlFirst 的设置
            // <if test=" ew != null and ew.sqlFirst != null">
            //      ${ew.sqlFirst}
            // </test>
        // 第2个%s: sqlCount() -> 确定查询的select的列
            // <choose>
            // <when test=" ew != null and ew.sqlSelect != null"> ${ew.sqlSelect} </when>   ew.sqlSelect决定返回的行数
            // <otherwise> * </otherwise>                                                   * 表示全查
            // </choose>
        // 第3个%s: tableInfo.getTableName() 表名
        // 第4个%s: sqlWhereEntityWrapper(true, tableInfo)
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
            //          <otherwise> AND deleted = 0 </otherwise>
            //      </choose>
            // </where>
        // 第5个%s: sqlComment() -> sql补充
            // <if test=" ew != null and ew.sqlComment != null">
            //      ${ew.sqlComment}
            // </if>
        String sql = String.format(sqlMethod.getSql(), sqlFirst(), sqlCount(), tableInfo.getTableName(),
            sqlWhereEntityWrapper(true, tableInfo), sqlComment());
        SqlSource sqlSource = languageDriver.createSqlSource(configuration, sql, modelClass);
        return this.addSelectMappedStatementForOther(mapperClass, getMethod(sqlMethod), sqlSource, Long.class);
    }
}
