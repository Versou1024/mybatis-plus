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
 * 根据 whereEntity 条件，更新记录
 *
 * @author hubin
 * @since 2018-04-06
 */
public class Update extends AbstractMethod {

    public Update() {
        super(SqlMethod.UPDATE.getMethod());
    }

    /**
     * @since 3.5.0
     * @param name 方法名
     */
    public Update(String name) {
        super(name);
    }

    @Override
    public MappedStatement injectMappedStatement(Class<?> mapperClass, Class<?> modelClass, TableInfo tableInfo) {
        // 对应: BaseMapper#update(@Param(Constants.ENTITY) T entity, @Param(Constants.WRAPPER) Wrapper<T> updateWrapper)
        // 两个形参 -> "et" 与 "ew"
        // sql脚本框架:  SqlMethod.UPDATE
        // <script>
        // UPDATE %s %s %s %s
        // </script>
        SqlMethod sqlMethod = SqlMethod.UPDATE;
        // 第一个%s: tableInfo.getTableName() 表名
        // 第二个%s: sqlSet(true, true, tableInfo, true, ENTITY, ENTITY_DOT) -> set 的 sql 脚本片段
            //  <set>
            //      <if test = 'et != null'>
            //          <if test='et.property1 != null'> column1 = #{et.property1} </if>                             当updateStrategy为FieldStrategy.NOE_NULL时
            //          <if test='et.property2 != null and et.property2 != '' '> column2 = #{et.property2} </if>     当updateStrategy为FieldStrategy.NOE_EMPTY时
            //          <if test='et.property3 != null'> column3 = column3 + 1 </if>                                 当@TableField.udpate() = "%s+1" 时
            //          <if test='et.property4 != null'> column4 = now() </if>                                       当@TableField.udpate() = "now()" 时
            //      </if>
            //      <if test=" ew != null and ew.sqlSet != null">
            //          ew.sqlSet                                                                                    这个sql片段取决于ew的boolean值
            //      </if>
            //  </set>
        // 第三个%s: sqlWhereEntityWrapper(true, tableInfo) -> where 的 sql 脚本片段
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
        // 第四个%s: sqlComment() -> sql  comment 片段
            // <if test=" ew != null and ew.sqlComment != null">
            //      ${ew.sqlComment}
            // </if>
        String sql = String.format(sqlMethod.getSql(), tableInfo.getTableName(),
            sqlSet(true, true, tableInfo, true, ENTITY, ENTITY_DOT),
            sqlWhereEntityWrapper(true, tableInfo), sqlComment());
        SqlSource sqlSource = languageDriver.createSqlSource(configuration, sql, modelClass);
        return this.addUpdateMappedStatement(mapperClass, modelClass, getMethod(sqlMethod), sqlSource);
    }
}
