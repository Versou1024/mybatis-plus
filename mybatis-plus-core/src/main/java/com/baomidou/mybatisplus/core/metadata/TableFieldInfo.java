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
package com.baomidou.mybatisplus.core.metadata;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.sql.SqlScriptUtils;
import lombok.*;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.reflection.Reflector;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.*;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * 数据库表字段反射信息
 *
 * @author hubin sjy willenfoo tantan
 * @since 2016-09-09
 */
@Getter
@ToString
@EqualsAndHashCode
@SuppressWarnings("serial")
public class TableFieldInfo implements Constants {
    // 位于: core模块的metadata元数据包下

    // 作用:
    // 封装实体类或者数据表中某个字段的信息

    // 字段
    private final Field field;

    // 字段对应的列名
    // 简述:
    // 1. @TableField.value属性 > 属性名 column = @TableField.value > 属性名
    // 2. tableInfo.isUnderCamel()开启 -> 驼峰转下划线 column = StringUtils.camelToUnderline(column)
    // 3. dbConfig.isCapitalMode()开启 -> 全部大写模式 column = column.toUpperCase()
    // 4. GlobalConfig.getColumnFormat()非空 -> 格式化模式 column = String.format(columnFormat, column)
    private final String column;

    // 属性名
    private final String property;

    // 属性表达式#{property}, 可以指定jdbcType, typeHandler等
    // 简述:
    // el 就是MP自动注入的方法中需要当前字段使用时填充的sql脚本片段, 例如有一个场景,使用userName字段做一个eq筛选
    // el就可以是mapper.xml需要使用的:比如 where user_name = #{userName,javaType=String,jdbcType=VARCHAR,typeHandler=StringTypeHandler}
    // --> 仅限于在MP自动注入的MP方法中使用哦 [BaseMapper中的方法]
    private final String el;

    // jdbcType, typeHandler等部分
    // 即 el 表达式中第一个逗号后的内容 -> 以上面的el表达式"userName,javaType=String,jdbcType=VARCHAR,typeHandler=StringTypeHandler"为例
    // 相应的 mapping 就是 "javaType=String,jdbcType=VARCHAR,typeHandler=StringTypeHandler" 部分哦
    private final String mapping;

    // 属性类型 -> 主要是getXxx()返回值类型决定
    private final Class<?> propertyType;

    // 是否是基本数据类型
    private final boolean isPrimitive;

    // 属性是否是 CharSequence 类型
    private final boolean isCharSequence;

    // 字段验证策略之 insert Refer to TableField.insertStrategy()
    // 第一来源: @TableField.insertStrategy(..) 为默认时,根据第二来源
    // 第二来源: DbConfig#getInsertStrategy(..)
    private final FieldStrategy insertStrategy;

    // 字段验证策略之 update Refer to TableField.updateStrategy()
    // 第一来源: @TableField.updateStrategy(..) 为默认时,根据第二来源
    // 第二来源: DbConfig#getUpdateStrategy(..)
    private final FieldStrategy updateStrategy;

    // 字段验证策略之 where Refer to TableField.whereStrategy()
    // 第一来源: @TableField.whereStrategy(..) 为默认时,根据第二来源
    // 第二来源: DbConfig#getWhereStrategy(..)
    private final FieldStrategy whereStrategy;

    // 是否是乐观锁字段 - 即字段上标注了@Version注解
    private final boolean version;

    // 该字段是否参与 select 查询
    // 与 @TableField.select()有关
    private boolean select = true;

    // 是否是逻辑删除字段
    @Getter
    private boolean logicDelete = false;

    // 逻辑删除值
    // @TableLogic.delval() 非空 -> 直接指定逻辑删除的值
    // @TableLogic.delval() 为空 -> dbConfig.getLogicDeleteValue() 全局配置的逻辑删除值 - 默认为0
    //  字段名等于全局的逻辑删除字段名:dbConfig.getLogicDeleteField() -> 直接获取全局配置的逻辑删除值: dbConfig.getLogicDeleteValue()
    private String logicDeleteValue;

    // 逻辑未删除值
    // @TableLogic.value() 非空 -> 直接指定逻辑未删除的值
    // @TableLogic.value() 为空 -> dbConfig.getLogicNotDeleteValue() 全局配置的逻辑未删除值 - 默认为0
    // 字段名等于全局的逻辑删除字段名:dbConfig.getLogicDeleteField() -> 直接获取全局配置的逻辑未删除值: dbConfig.getLogicNotDeleteValue()
    private String logicNotDeleteValue;

    // 字段 update set 部分注入
    // 取决于: @TableFiled.update() -> [作用请见@TableField]
    private String update;

    // where 字段比较条件
    // 取决于: @TableField.condition() 值
    private String condition = SqlCondition.EQUAL;

    // 字段填充策略
    // 取决于: @TableFiled.fill() [默认是: FieldFill.DEFAULT -> 表示跟随全局]
    private FieldFill fieldFill = FieldFill.DEFAULT;

    // 表字段是否启用了插入填充
    // 取决: this.fieldFill == FieldFill.INSERT || this.fieldFill == FieldFill.INSERT_UPDATE;
    private boolean withInsertFill;

    // 表字段是否启用了更新填充
    // this.fieldFill == FieldFill.UPDATE || this.fieldFill == FieldFill.INSERT_UPDATE;
    private boolean withUpdateFill;

    // 缓存 sql select
    // 当 P注入的方法上没有指定使用ResultMap [@TableName.resultMap为空],并且没有有要求自动初始化ResultMap [@TableName.autoResultMap()为false]
    // 并且property和column需要使用AS关键字映射 [即property和column a:在驼峰命名转下划线下不相等  b:没有开启驼峰命名转换下不相等]
    // 此刻: sqlSelect = column +  "as" + 需要转换过来的属性名
    @Setter(AccessLevel.NONE)
    private String sqlSelect;

    // JDBC类型
    // 取决: @TableField.jdbcType() [前提不是 JdbcType.UNDEFINED()]
    private JdbcType jdbcType;

    // 类型处理器
    // 取决于: @TableField.typeHandler() -> 要想生效的前提就是: @TableName.autoResultMap() 返回ture 且 @TableName.resultMap() 为空字符串 -> ❗️❗️❗️
    // 查证代码: TableInfo.initResultMapIfNeed() -> TableFieldInfo.getResultMapping() [方法生效的前提就是上面说的这个]
    // note: ❗️❗️❗️ 注意一点: @TableField的typeHandler并不会注册到TypeHandlerRegistry注册表中哦
    private Class<? extends TypeHandler<?>> typeHandler;

    // 是否存在OrderBy注解
    private boolean isOrderBy;

    // 排序类型
    // asc || desc
    private String orderByType;

    // 排序顺序
    // 取决于: @OrderBy.sort()
    private short orderBySort;

    /**
     * 全新的 存在 TableField 注解时使用的构造函数
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public TableFieldInfo(GlobalConfig.DbConfig dbConfig, TableInfo tableInfo, Field field, TableField tableField,
                          Reflector reflector, boolean existTableLogic, boolean isOrderBy) {
        // 全新的 存在 @TableField 注解时使用的构造函数

        // 1. 普通构建: 存在 TableField 注解时使用的构造函数
        this(dbConfig, tableInfo, field, tableField, reflector, existTableLogic);
        this.isOrderBy = isOrderBy;
        if (isOrderBy) {
            initOrderBy(field);
        }
    }

    /**
     * 全新的 存在 TableField 注解时使用的构造函数
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public TableFieldInfo(GlobalConfig.DbConfig dbConfig, TableInfo tableInfo, Field field, TableField tableField,
                          Reflector reflector, boolean existTableLogic) {
        field.setAccessible(true);
        this.field = field;
        this.version = field.getAnnotation(Version.class) != null;
        this.property = field.getName();
        this.propertyType = reflector.getGetterType(this.property);
        this.isPrimitive = this.propertyType.isPrimitive();
        this.isCharSequence = StringUtils.isCharSequence(this.propertyType);
        this.fieldFill = tableField.fill();
        // 1. 相比于: 不存在 TableField 注解时, 使用的构造函数之间的去呗
        this.withInsertFill = this.fieldFill == FieldFill.INSERT || this.fieldFill == FieldFill.INSERT_UPDATE;
        this.withUpdateFill = this.fieldFill == FieldFill.UPDATE || this.fieldFill == FieldFill.INSERT_UPDATE;
        this.update = tableField.update();
        JdbcType jdbcType = tableField.jdbcType();
        final Class<? extends TypeHandler> typeHandler = tableField.typeHandler();
        final String numericScale = tableField.numericScale();
        boolean needAs = false;
        // 2. 处理el表达式\mapping表达式
        String el = this.property;
        if (StringUtils.isNotBlank(tableField.property())) {
            el = tableField.property();
            needAs = true;
        }
        if (JdbcType.UNDEFINED != jdbcType) {
            this.jdbcType = jdbcType;
            el += (COMMA + SqlScriptUtils.mappingJdbcType(jdbcType)); // ❗️❗️❗️ 拼接后 el = property + ",jdbcType=" + jdbcType
        }
        if (UnknownTypeHandler.class != typeHandler) {
            this.typeHandler = (Class<? extends TypeHandler<?>>) typeHandler;
            if (tableField.javaType()) {
                // 注册 javaType
                String javaType = null;
                TypeAliasRegistry registry = tableInfo.getConfiguration().getTypeAliasRegistry();
                Map<String, Class<?>> typeAliases = registry.getTypeAliases();
                for (Map.Entry<String, Class<?>> entry : typeAliases.entrySet()) {
                    if (entry.getValue().equals(propertyType)) {
                        javaType = entry.getKey();
                        break;
                    }
                }
                if (javaType == null) {
                    javaType = propertyType.getName();
                    registry.registerAlias(javaType, propertyType);
                }
                el += (COMMA + "javaType=" + javaType); // ❗️❗️❗️ 拼接后 el +=  ",javaType=" + javaType实际类型
            }
            el += (COMMA + SqlScriptUtils.mappingTypeHandler(this.typeHandler)); // ❗️❗️❗️ 拼接后 el +=  ",typeHandler=" + typeHandler的实际烈性
        }
        if (StringUtils.isNotBlank(numericScale)) {
            el += (COMMA + SqlScriptUtils.mappingNumericScale(Integer.valueOf(numericScale))); // ❗️❗️❗️ 拼接后 el +=  ",numericScale=" + @TableField.numericScale()的值
        }
        this.el = el;
        int index = el.indexOf(COMMA);
        this.mapping = index > 0 ? el.substring(++index) : null;
        // 3. 初始化逻辑删除字段
        this.initLogicDelete(dbConfig, field, existTableLogic);

        // 4. 确定属性对应的列名
        String column = tableField.value();
        if (StringUtils.isBlank(column)) {
            column = this.property;
            if (tableInfo.isUnderCamel()) {
                /* 开启字段下划线申明 */
                // note: 驼峰命名转下划线 -> 对于@TableField.value()也是同样生效的哦
                column = StringUtils.camelToUnderline(column);
            }
            if (dbConfig.isCapitalMode()) {
                /* 开启字段全大写申明 */
                column = column.toUpperCase();
            }
        }
        String columnFormat = dbConfig.getColumnFormat();
        if (StringUtils.isNotBlank(columnFormat) && tableField.keepGlobalFormat()) {
            column = String.format(columnFormat, column);
        }

        this.column = column;
        this.sqlSelect = column;
        // 5. 确定 sqlSelect

        if (needAs) {
            // 存在指定转换属性
            String propertyFormat = dbConfig.getPropertyFormat();
            if (StringUtils.isBlank(propertyFormat)) {
                propertyFormat = "%s";
            }
            this.sqlSelect += (AS + String.format(propertyFormat, tableField.property()));
        } else if (tableInfo.getResultMap() == null && !tableInfo.isAutoInitResultMap() &&
            TableInfoHelper.checkRelated(tableInfo.isUnderCamel(), this.property, this.column)) {
            /* 未设置 resultMap 也未开启自动构建 resultMap, 字段规则又不符合 mybatis 的自动封装规则 */
            String propertyFormat = dbConfig.getPropertyFormat();
            String asProperty = this.property;
            if (StringUtils.isNotBlank(propertyFormat)) {
                asProperty = String.format(propertyFormat, this.property);
            }
            this.sqlSelect += (AS + asProperty);
        }

        // 6. 确定insertStrategy/updateStrategy/whereStrategy
        this.insertStrategy = this.chooseFieldStrategy(tableField.insertStrategy(), dbConfig.getInsertStrategy());
        this.updateStrategy = this.chooseFieldStrategy(tableField.updateStrategy(), dbConfig.getUpdateStrategy());
        this.whereStrategy = this.chooseFieldStrategy(tableField.whereStrategy(), dbConfig.getWhereStrategy());

        if (StringUtils.isNotBlank(tableField.condition())) {
            // 细粒度条件控制
            this.condition = tableField.condition();
        }

        // 字段是否注入查询
        this.select = tableField.select();
    }

    /**
     * 优先使用单个字段注解，否则使用全局配置
     */
    private FieldStrategy chooseFieldStrategy(FieldStrategy fromAnnotation, FieldStrategy fromDbConfig) {
        return fromAnnotation == FieldStrategy.DEFAULT ? fromDbConfig : fromAnnotation;
    }

    /**
     * 不存在 TableField 注解时, 使用的构造函数
     */
    public TableFieldInfo(GlobalConfig.DbConfig dbConfig, TableInfo tableInfo, Field field, Reflector reflector,
                          boolean existTableLogic, boolean isOrderBy) {
        // 1. 不存在 TableField 注解时, 使用的构造函数
        this(dbConfig, tableInfo, field, reflector, existTableLogic);
        // 2. 是否为order by使用的字段
        this.isOrderBy = isOrderBy;
        if (isOrderBy) {
            // 2.1 初始化 order by 的字段
            initOrderBy(field);
        }
    }

    /**
     * 不存在 TableField 注解时, 使用的构造函数
     */
    public TableFieldInfo(GlobalConfig.DbConfig dbConfig, TableInfo tableInfo, Field field, Reflector reflector,
                          boolean existTableLogic) {
        // 1. 基本字段填充
        field.setAccessible(true);
        this.field = field;
        this.version = field.getAnnotation(Version.class) != null;
        this.property = field.getName();
        this.propertyType = reflector.getGetterType(this.property);
        this.isPrimitive = this.propertyType.isPrimitive();
        this.isCharSequence = StringUtils.isCharSequence(this.propertyType);
        this.el = this.property;
        this.mapping = null;
        this.insertStrategy = dbConfig.getInsertStrategy();
        this.updateStrategy = dbConfig.getUpdateStrategy();
        this.whereStrategy = dbConfig.getWhereStrategy();
        // 2. 初始化逻辑删除
        this.initLogicDelete(dbConfig, field, existTableLogic);

        // 3. 列名 -> tableInfo.isUnderCamel() + dbConfig.isCapitalMode() + dbConfig.getColumnFormat()
        String column = this.property;
        if (tableInfo.isUnderCamel()) {
            /* 开启字段下划线申明 */
            column = StringUtils.camelToUnderline(column);
        }
        if (dbConfig.isCapitalMode()) {
            /* 开启字段全大写申明 */
            column = column.toUpperCase();
        }

        String columnFormat = dbConfig.getColumnFormat();
        if (StringUtils.isNotBlank(columnFormat)) {
            column = String.format(columnFormat, column);
        }

        this.column = column;
        this.sqlSelect = column;

        // 4. 关键: 别名转换
        // 4.1 MP注入的方法上没有指定使用ResultMap,并且没有有要求自动初始化ResultMap,并且property和column需要使用AS关键字映射
        // tableInfo.getResultMap() 取决于 @TableName 的 resultMap 属性
        // tableInfo.isAutoInitResultMap() 取决于 @TableName 的 autoResultMap
        /* 未设置 resultMap 也未开启自动构建 resultMap, 字段规则又不符合 mybatis 的自动封装规则 */
        if (tableInfo.getResultMap() == null && !tableInfo.isAutoInitResultMap() &&
            TableInfoHelper.checkRelated(tableInfo.isUnderCamel(), this.property, this.column)) {
            String propertyFormat = dbConfig.getPropertyFormat();
            String asProperty = this.property;
            if (StringUtils.isNotBlank(propertyFormat)) {
                asProperty = String.format(propertyFormat, this.property);
            }
            this.sqlSelect += (AS + asProperty);
        }
    }

    /**
     * 排序初始化
     *
     * @param field 字段
     */
    private void initOrderBy(Field field) {
        OrderBy orderBy = field.getAnnotation(OrderBy.class);
        // 1.1 @OrderBy注解不为空
        if (null != orderBy) {
            this.isOrderBy = true;
            this.orderBySort = orderBy.sort();
            String _orderBy = Constants.DESC;
            if (orderBy.asc() || !orderBy.isDesc()) {
                _orderBy = Constants.ASC;
            }
            this.orderByType = _orderBy;
        }
        // 1.2 @OrderBy注解不为空
        else {
            this.isOrderBy = false;
        }
    }

    /**
     * 逻辑删除初始化
     *
     * @param dbConfig 数据库全局配置
     * @param field    字段属性对象
     */
    private void initLogicDelete(GlobalConfig.DbConfig dbConfig, Field field, boolean existTableLogic) {
        /* 获取注解属性，逻辑处理字段 */

        TableLogic tableLogic = field.getAnnotation(TableLogic.class);
        // 1.1 字段上有@TableLogic
        if (null != tableLogic) {
            // 1.1.1
            // @TableLogic.value() 非空 -> 直接指定逻辑未删除的值
            // @TableLogic.value() 为空 -> dbConfig.getLogicNotDeleteValue() 全局配置的逻辑未删除值 - 默认为0
            if (StringUtils.isNotBlank(tableLogic.value())) {
                this.logicNotDeleteValue = tableLogic.value();
            } else {
                this.logicNotDeleteValue = dbConfig.getLogicNotDeleteValue();
            }
            // 1.1.2
            // @TableLogic.delval() 非空 -> 直接指定逻辑删除的值
            // @TableLogic.delval() 为空 -> dbConfig.getLogicDeleteValue() 全局配置的逻辑删除值 - 默认为0
            if (StringUtils.isNotBlank(tableLogic.delval())) {
                this.logicDeleteValue = tableLogic.delval();
            } else {
                this.logicDeleteValue = dbConfig.getLogicDeleteValue();
            }
            this.logicDelete = true;
        }
        // 1.2 字段上没有@TableLogic
        else if (!existTableLogic) {
            String deleteField = dbConfig.getLogicDeleteField();
            // 1.2.1 字段名等于全局的逻辑删除字段名:dbConfig.getLogicDeleteField()
            // 直接获取: dbConfig.getLogicNotDeleteValue()/dbConfig.getLogicDeleteValue()
            if (StringUtils.isNotBlank(deleteField) && this.property.equals(deleteField)) {
                this.logicNotDeleteValue = dbConfig.getLogicNotDeleteValue();
                this.logicDeleteValue = dbConfig.getLogicDeleteValue();
                this.logicDelete = true;
            }
        }
    }

    /**
     * 获取 insert 时候插入值 sql 脚本片段
     * <p>insert into table (字段) values (值)</p>
     * <p>位于 "值" 部位</p>
     *
     * <li> 不生成 if 标签 </li>
     *
     * @return sql 脚本片段
     */
    public String getInsertSqlProperty(final String prefix) {
        // 获取 insert 时候插入值 sql 脚本片段
        // insert into table (字段) values (值)
        // 位于 "值" 部位
        // 主要就是 #{newPrefix + el} + ","
        final String newPrefix = prefix == null ? EMPTY : prefix;
        return SqlScriptUtils.safeParam(newPrefix + el) + COMMA;
    }

    /**
     * 获取 insert 时候插入值 sql 脚本片段
     * <p>insert into table (字段) values (值)</p>
     * <p>位于 "值" 部位</p>
     *
     * <li> 根据规则会生成 if 标签 </li>
     *
     * @return sql 脚本片段
     */
    public String getInsertSqlPropertyMaybeIf(final String prefix) {
        // 没有插入填充策略
            // <if test="newPrefix.property != null" > #{newPrefix.el}, </if>                                -> insertStrategy=FieldStrategy.NOT_NULL
            // <if test="newPrefix.property != null and newPrefix.property != '' " > #{newPrefix.el}, </if>  -> insertStrategy=FieldStrategy.NOT_EMPTY
        // 有插入填充策略
            // #{newPrefix.el},
        final String newPrefix = prefix == null ? EMPTY : prefix;
        String sqlScript = getInsertSqlProperty(newPrefix);
        if (withInsertFill) {
            // 如果有插入填充策略,就不需要使用<if>标签包装起来咯
            return sqlScript;
        }
        return convertIf(sqlScript, newPrefix + property, insertStrategy);
    }

    /**
     * 获取 insert 时候字段 sql 脚本片段
     * <p>insert into table (字段) values (值)</p>
     * <p>位于 "字段" 部位</p>
     *
     * <li> 不生成 if 标签 </li>
     *
     * @return sql 脚本片段
     */
    public String getInsertSqlColumn() {
        return column + COMMA;
    }

    /**
     * 获取 insert 时候字段 sql 脚本片段
     * <p>insert into table (字段) values (值)</p>
     * <p>位于 "字段" 部位</p>
     *
     * <li> 根据规则会生成 if 标签 </li>
     *
     * @return sql 脚本片段
     */
    public String getInsertSqlColumnMaybeIf(final String prefix) {
        // 1. 有自动填充的插入规则时
            // column,
        // 2. 没有自动填充的插入规则时
            // <if test="prefix.property">
            //      column,
            // </if>
        final String newPrefix = prefix == null ? EMPTY : prefix;
        final String sqlScript = getInsertSqlColumn();
        if (withInsertFill) {
            return sqlScript;
        }
        return convertIf(sqlScript, newPrefix + property, insertStrategy);
    }

    /**
     * 获取 set sql 片段
     *
     * @param prefix 前缀
     * @return sql 脚本片段
     */
    public String getSqlSet(final String prefix) {
        // 当前方法执行结果如下:
        // prefix 为 "":
            // <if test='property1 != null'> column1 = #{property1} </if>                        当updateStrategy为FieldStrategy.NOE_NULL时
            // <if test='property2 != null and property2 != '' '> column2 = #{property2} </if>   当updateStrategy为FieldStrategy.NOE_EMPTY时
            // <if test='property3 != null'> column3 = column3 + 1 </if>                         当@TableField.udpate() = "%s+1" 时
            // <if test='property4 != null'> column4 = now() </if>                               当@TableField.udpate() = "now()" 时

        return getSqlSet(false, prefix);
    }

    /**
     * 获取 set sql 片段
     *
     * @param ignoreIf 忽略 IF 包裹
     * @param prefix   前缀
     * @return sql 脚本片段
     */
    public String getSqlSet(final boolean ignoreIf, final String prefix) {
        // 参数:
        //      ignoreIf – 忽略 IF 包裹
        //      prefix – 前缀
        // 当前方法执行结果:
        // 如果ignoreIf为true,prefix为空
                // column = #{el}           当@TableField.update()为空时
                // column = column + 1      当@TableField.update()="%s+1"
                // column = now()           当@TableField.update()="now()"
        // 如果ignoreIf为true,prefix为'ew.entity.'
                // column = #{'ew.entity.' + el}           当@TableField.update()为空时
        // 如果ignoreIf为false,prefix为空
                // <if test='property1 != null'> column1 = #{property1} </if>                        当updateStrategy为FieldStrategy.NOE_NULL时
                // <if test='property2 != null and property2 != '' '> column2 = #{property2} </if>   当updateStrategy为FieldStrategy.NOE_EMPTY时
                // <if test='property3 != null'> column3 = column3 + 1 </if>                         当@TableField.udpate() = "%s+1" 时
                // <if test='property4 != null'> column4 = now() </if>                               当@TableField.udpate() = "now()" 时
        // 如果ignoreIf为false,prefix为'ew.entity.'
                // <if test='ew.entityproperty1 != null'> column1 = #{'ew.entity.' + property1} </if>                        当updateStrategy为FieldStrategy.NOE_NULL时
                // <if test='ew.entityproperty2 != null and ew.entity.property2 != '' '> column2 = #{'ew.entity.' + property2} </if>   当updateStrategy为FieldStrategy.NOE_EMPTY时
                // <if test='ew.entityproperty3 != null'> column3 = column3 + 1 </if>                         当@TableField.udpate() = "%s+1" 时
                // <if test='ew.entityproperty4 != null'> column4 = now() </if>                               当@TableField.udpate() = "now()" 时



        final String newPrefix = prefix == null ? EMPTY : prefix;
        // 默认: column=
        String sqlSet = column + EQUALS;
        // 1. 关于 update
        // 例1：@TableField(.. , update="%s+1") 其中 %s 会填充为字段 输出 SQL 为：update 表 set 字段=字段+1 where ...
        // 例2：@TableField(.. , update="now()") 使用数据库时间 输出 SQL 为：update 表 set 字段=now() where ...
                // column = #{prefix+el}    当@TableField.update()为空时
                // column = column + 1      当@TableField.update()="%s+1"
                // column = now()           当@TableField.update()="now()"
        if (StringUtils.isNotBlank(update)) {
            sqlSet += String.format(update, column);
        } else {
            sqlSet += SqlScriptUtils.safeParam(newPrefix + el);
        }
        sqlSet += COMMA;
        // 2. 是否忽略<if>标签
        if (ignoreIf) {
            return sqlSet;
        }
        // 3. 是否加入自动更新的填充 -> 是的话,就不进行<if>包裹
        if (withUpdateFill) {
            // 不进行 if 包裹
            return sqlSet;
        }
        // 4. 要求不忽略<if>标签,且当前字段本身要求做更新时的自动填充操作 [<if>标签和updateStrategy的策略行为有很大的关系哦]
        // FieldStrategy.NEVER -> 返回null
        // FieldStrategy.NOT_NULL ->
            // <if test="xx != null">
            //      sqlSet
            // </if>
        // FieldStrategy.NOT_EMPTY ->
            // <if test=" xx != null and xx != ''">
            //      sqlSet
            // </if>
        return convertIf(sqlSet, convertIfProperty(newPrefix, property), updateStrategy);
    }

    private String convertIfProperty(String prefix, String property) {
        return StringUtils.isNotBlank(prefix) ? prefix.substring(0, prefix.length() - 1) + "['" + property + "']" : property;
    }

    /**
     * 获取 查询的 sql 片段
     *
     * @param prefix 前缀
     * @return sql 脚本片段
     */
    public String getSqlWhere(final String prefix) {
        // 获取当前字段对应在where关键字中的sql片段
        // 可以有:
        // a: condition为SQLCondition.EQUAL prefix="ew.entity." whereStrategy=FieldStrategy.NOT_NULL
        //    <if test=" ew.entity.property != null "> AND column = #{ew.entity. +el} </if>
        // b: condition为SQLCondition.EQUAL prefix="ew.entity." whereStrategy=FieldStrategy.NOT_EMPTY
        //    <if test=" ew.entity.property != null and ew.entity.property != '' "> AND column = #{'ew.entity.'+el} </if>
        // c: condition为SQLCondition.LIKE prefix="ew.entity." whereStrategy=FieldStrategy.NOT_NULL
        //    <if test=" ew.entity.property != null "> AND column LIKE CONCAT('%%',#{'ew.entity.'+el},'%%')} </if>

        // 1. 前缀
        final String newPrefix = prefix == null ? EMPTY : prefix;
        // 2. 默认:  AND 与操作符号 ❗️❗️❗️
        // 根据 SqlCondition 的不同 -> 可以映射为不同条件比如
        // AND column = #{newPrefix + el} -> 对应 SQLCondition.EQUAL
        // AND column LIKE CONCAT('%%',#{newPrefix+el},'%%') -> 对应SQLCondition.LIKE
        String sqlScript = " AND " + String.format(condition, column, newPrefix + el);
        // 3, 查询的时候根据whereStrategy做出不同的<if>标签准备 -> 将sqlScript包装在<if>标签红
        // 比如
        // <if test=" %s != null "> sqlScript </if>                 -> 当whereStrategy等于FieldStrategy.NOT_NULL
        // <if test=" %s != null and %s != '' "> sqlScript </if>    -> 当whereStrategy等于FieldStrategy.NOT_EMPTY
        // ...
        return convertIf(sqlScript, convertIfProperty(newPrefix, property), whereStrategy);
    }

    /**
     * 获取 ResultMapping
     *
     * @param configuration MybatisConfiguration
     * @return ResultMapping
     */
    ResultMapping getResultMapping(final Configuration configuration) {
        // ❗️❗️❗️
        // 触发条件: @TableName.autoResultMap()为true && @TableName.resultMap()为空 -> 调用当前方法根据当前TableFieldInfo解析处ResultMapping出来

        // 1. 构建ResultMapping.Builder
        ResultMapping.Builder builder = new ResultMapping.Builder(configuration, property, StringUtils.getTargetColumn(column), propertyType);
        TypeHandlerRegistry registry = configuration.getTypeHandlerRegistry();
        // 2. jdbcType非空且非UNDEFINED -> 构建ResultMapping.jdbcType
        if (jdbcType != null && jdbcType != JdbcType.UNDEFINED) {
            builder.jdbcType(jdbcType);
        }
        // 3. typeHandler非空且非UnKnownTypeHandler
        if (typeHandler != null && typeHandler != UnknownTypeHandler.class) {
            TypeHandler<?> typeHandler = registry.getMappingTypeHandler(this.typeHandler);
            if (typeHandler == null) {
                typeHandler = registry.getInstance(propertyType, this.typeHandler);
                // todo 这会有影响 registry.register(typeHandler);
            }
            // 3.1 直接将TypeHandler设置到ResultMapping中去
            builder.typeHandler(typeHandler);
        }
        // 4. 构建完毕
        return builder.build();
    }

    public String getVersionOli(final String alias, final String prefix) {
        // 获取乐观锁的sql脚本片段
        // 假设:
        // alias = "et" prefix = "et."
            // <if test=" et != null and et[property]  != null and et[property] != ''">     property为标记有@Versiond的字段名
            //      AND column_name = #{MP_OPTLOCK_VERSION_ORIGINAL}                        MP_OPTLOCK_VERSION_ORIGINAL为乐观锁字段哦
            // </if>

        // 1. AND column_name = #{MP_OPTLOCK_VERSION_ORIGINAL}
        final String oli = " AND " + column + EQUALS + SqlScriptUtils.safeParam(MP_OPTLOCK_VERSION_ORIGINAL);
        // 2. et[property]
        final String ognlStr = convertIfProperty(prefix, property);

        // 2. 当前属性为String类型
        if (isCharSequence) {
            // <if test=" et != null and et[property]  != null and et[property] != ''">
            //      oli
            // </if>
            return SqlScriptUtils.convertIf(oli, String.format("%s != null and %s != null and %s != ''", alias, ognlStr, ognlStr), false);
        }
        // 3. 当前属性不是String类型
        else {
            // <if test=" et != null and et[property]  != null">
            //      oli
            // </if>
            return SqlScriptUtils.convertIf(oli, String.format("%s != null and %s != null", alias, ognlStr), false);
        }
    }

    /**
     * 转换成 if 标签的脚本片段
     *
     * @param sqlScript     sql 脚本片段
     * @param property      字段名
     * @param fieldStrategy 验证策略
     * @return if 脚本片段
     */
    private String convertIf(final String sqlScript, final String property, final FieldStrategy fieldStrategy) {
        // 1. 不加入SQL -> FieldStrategy.NEVER
        if (fieldStrategy == FieldStrategy.NEVER) {
            return null;
        }
        // 2. 忽略判断,直接返回sqlScript -> FieldStrategy.IGNORED
        if (isPrimitive || fieldStrategy == FieldStrategy.IGNORED) {
            return sqlScript;
        }
        // 3. 需要判断为 非空empty -> FieldStrategy.NOT_EMPTY
        if (fieldStrategy == FieldStrategy.NOT_EMPTY && isCharSequence) {
            // <if test="property != null and property != ''"> sqlScript </test>
            return SqlScriptUtils.convertIf(sqlScript, String.format("%s != null and %s != ''", property, property),
                false);
        }
        // 4. 需要判断为 非空null -> FieldStrategy.NOT_NULL
        return SqlScriptUtils.convertIf(sqlScript, String.format("%s != null", property), false);
    }
}
