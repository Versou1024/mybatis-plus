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

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.enums.SqlMethod;
import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.sql.SqlScriptUtils;
import org.apache.ibatis.executor.keygen.Jdbc3KeyGenerator;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.executor.keygen.NoKeyGenerator;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlSource;

/**
 * 插入一条数据（选择字段插入）
 *
 * @author hubin
 * @since 2018-04-06
 */
public class Insert extends AbstractMethod {
    // 位于: com.baomidou.mybatisplus.core.injector.methods = core项目下 injector.methods

    // 作用:
    // mybatis解析BaseMapper#insert()方法时,通过Insert.injectMappedStatement()来注入sql

    public Insert() {
        // 默认的methodName就是insert
        super(SqlMethod.INSERT_ONE.getMethod());
    }

    /**
     * @param name 方法名
     * @since 3.5.0
     */
    public Insert(String name) {
        // 也可以定制:methodName
        super(name);
    }

    @Override
    public MappedStatement injectMappedStatement(Class<?> mapperClass, Class<?> modelClass, TableInfo tableInfo) {
        // 对应: BaseMapper#insert(T entity)
        // 对应: SqlMethod.INSERT_ONE

        // 1. keyGenerator为NoKeyGenerator
        KeyGenerator keyGenerator = NoKeyGenerator.INSTANCE;
        // 2. sqlMethod:
        // <script>
        // INSERT INTO %s %s VALUES %s
        // </script>
        // 为什么需要 <script> ❗️❗️❗️ -> 这就是设计mybatis的代码设计啦 -> 在Mybatis框架的 XMLLanguageDriver.createSqlSource(Configuration configuration, String script, Class<?> parameterType)
        // 要求传入的形参script,以<script>开头,以</script>结尾
        SqlMethod sqlMethod = SqlMethod.INSERT_ONE;
        // 3. 生成trim的column sql片段
            // <trim prefix="(" suffix=")" suffixOverrides=",">
            //      <if test="keyProperty != null "> keyColumn, </if> -> 主键对应的insert
            //      column1,                                          -> 属性1对应的列1存在自动填充  @TableField.fill()= FieldFill.INSERT
            //      <if test="prefix.property2"> column2, </if>       -> 属性2对应的列2没有自动填充
            // </trim>
        String columnScript = SqlScriptUtils.convertTrim(tableInfo.getAllInsertSqlColumnMaybeIf(null), LEFT_BRACKET, RIGHT_BRACKET, null, COMMA);
        // 4. 生成trim的value sql 片段
            // <trim prefix="(" suffix=")" suffixOverrides=",">
            // <if test= " prefix.KeyProperty != null"> #{prefix.KeyProperty}, </if>                         -> 主键
            // <if test="newPrefix.property != null" > #{newPrefix.el}, </if>                                -> 没有自动插入填充,insertStrategy=FieldStrategy.NOT_NULL
            // <if test="newPrefix.property != null and newPrefix.property != '' " > #{newPrefix.el}, </if>  -> 没有自动插入填充,insertStrategy=FieldStrategy.NOT_EMPTY
            // #{newPrefix.el},                                                                              -> 有自动插入填充的普通字段
            // </trim>
        String valuesScript = SqlScriptUtils.convertTrim(tableInfo.getAllInsertSqlPropertyMaybeIf(null), LEFT_BRACKET, RIGHT_BRACKET, null, COMMA);
        String keyProperty = null;
        String keyColumn = null;
        // 4. 表包含主键处理逻辑,如果不包含主键当普通字段处理 -> 确定 keyGenerator\keyProperty\keyColumn
        if (StringUtils.isNotBlank(tableInfo.getKeyProperty())) {
            if (tableInfo.getIdType() == IdType.AUTO) {
                // 4.1 自增主键
                keyGenerator = Jdbc3KeyGenerator.INSTANCE;
                keyProperty = tableInfo.getKeyProperty();
                keyColumn = tableInfo.getKeyColumn();
            } else {
                // 4.2 存在@KeySequence主键 -> 涉及到 IKeyGenerator 发挥作用的
                if (null != tableInfo.getKeySequence()) {
                    keyGenerator = TableInfoHelper.genKeyGenerator(this.methodName, tableInfo, builderAssistant);
                    keyProperty = tableInfo.getKeyProperty();
                    keyColumn = tableInfo.getKeyColumn();
                }
            }
        }
        // 4. 生成最终使用的sql吧 -> INSERT INTO %s %s VALUES %s
        // note: 第一个为表名,第二个为columnScript,第三个valuesScript
        String sql = String.format(sqlMethod.getSql(), tableInfo.getTableName(), columnScript, valuesScript);
        // 5. 构建: SqlSource
        SqlSource sqlSource = languageDriver.createSqlSource(configuration, sql, modelClass);
        // 6. 添加tMappedStatement并返回出来
        return this.addInsertMappedStatement(mapperClass, modelClass, getMethod(sqlMethod), sqlSource, keyGenerator, keyProperty, keyColumn);

        // <script>
        // INSERT INTO %s %s VALUES %s
        // </script>
        // 第一个%s: tableInfo.getTableName() -> 表名
        // 第二个%s: columnScript -> 列sql脚本
                // <trim prefix="(" suffix=")" suffixOverrides=",">
                //      <if test="keyProperty != null "> keyColumn, </if> -> 主键对应的insert
                //      column1,                                          -> 属性1对应的列1存在自动填充  @TableField.fill()= FieldFill.INSERT
                //      <if test="prefix.property2"> column2, </if>       -> 属性2对应的列2没有自动填充
                // </trim>
        // 第三个%s: valuesScript -> 值sql脚本 [这里的prefix都是null哦]
                // <trim prefix="(" suffix=")" suffixOverrides=",">
                // <if test="prefix.KeyProperty != null"> #{prefix.KeyProperty}, </if>                         -> 主键
                // <if test="prefix.property != null" > #{prefix.el}, </if>                                -> 没有自动插入填充,insertStrategy=FieldStrategy.NOT_NULL
                // <if test="prefix.property != null and prefix.property != '' " > #{prefix.el}, </if>  -> 没有自动插入填充,insertStrategy=FieldStrategy.NOT_EMPTY
                // #{newPrefix.el},                                                                              -> 有自动插入填充的普通字段
                // </trim>
    }
}
