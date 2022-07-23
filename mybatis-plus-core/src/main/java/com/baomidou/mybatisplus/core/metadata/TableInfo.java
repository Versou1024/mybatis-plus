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

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.core.toolkit.*;
import com.baomidou.mybatisplus.core.toolkit.sql.SqlScriptUtils;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.ibatis.mapping.ResultFlag;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.reflection.Reflector;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.session.Configuration;

import java.lang.reflect.Constructor;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static java.util.stream.Collectors.joining;

/**
 * 数据库表反射信息
 *
 * @author hubin
 * @since 2016-01-23
 */
@Data
@Setter(AccessLevel.PACKAGE)
@Accessors(chain = true)
public class TableInfo implements Constants {
    // 位于: com.baomidou.mybatisplus.core.metadata

    // 命名:
    // Table Info = 数据表元数据信息 -> 数据库表的基本反射信息 -> 也对应一个实体类的
    // 基于MP的各种注解@TableField\@TableId等等以及全局配置GlobalConfig\DbConfig解析出来的哦

    // 实体类类型
    private Class<?> entityType;

    // 表主键ID 类型
    // 取决于 @TableId.type属性
    // 当 @TableId.type属性为 IdType.NONE 时, IdType 就是从 DbConfig.getIdType() 方法获取 [即注解优先级大于全局的]
    // 然后全局的 DbConfig.getIdType() 默认是 IdType.ASSIGN_ID , 可通过 "mybatis-plus.global-config.db-config.id-type" 来指定的
    private IdType idType = IdType.NONE;

    // 表名称
    // 获取逻辑如下: TableInfoHelper.initTableName(..) 中
    // 1. 实体类上@TableName存在
    //      1.1 @TableName的value值不为空
    //          1.1.1 如果同时@TableName的keepGlobalPrefix为false,就表示不需要表名前缀,将tablePrefixEffect设置为false
    //      1.2 @TableName的value值为空
    //          1.1.2 根据实体类的类名和DbConfig确定tableName
    //              a: 如果DbConfig开启类名驼峰命名转为下划线命名,是的话类名的驼峰命名转换为下划线命名
    //              b: 接着上面的步骤,如果DbConfig开启全部转为大写,就将表名继续处理,全部转换为大写字母,后者仅仅首字母小写
    // 2. 实体类上@TableName不存在
    //          1.1.2 根据实体类的类名和DbConfig确定tableName
    //              a: 如果DbConfig开启类名驼峰命名转为下划线命名,是的话类名的驼峰命名转换为下划线命名
    //              b: 接着上面的步骤,如果DbConfig开启全部转为大写,就将表名继续处理,全部转换为大写字母,后者仅仅首字母小写
    // 3. 通过1和2已经确定了表名
    //      3.1 如果tablePrefixEffect为true,且DbConfig中的tablePrefix有值,就继续在表名上加上全局表名前缀
    //      3.2 如果scheme不为空 [@TableName的scheme 优先级大于 全局DbConfig.schema值],继续拼接为 scheme + "." + tableName
    // 举例:
    // 类名为SysUser,@TableName的value为t_sys_user,其余默认 -> 最终表名: t_sys_user
    // 类名为SysUser,@TableName的value为t_sys_user,@TableName的schema为website, 其余默认 -> 最终表名: website.t_sys_user
    // 类名为SysUser,@TableName的value为t_sys_user,@TableName的keepGlobalPrefix为true,@TableName的schema为website,DbConfig的tablePrefix为"oms_" 其余默认 -> 最终表名: website.oms_t_sys_user
    // 类名为SysUser,@TableName的keepGlobalPrefix为true,DbConfig的schema为website,DbConfig的tablePrefix为"t_", 其余默认 -> 最终表名: website.t_sys_user
    // 类名为SysUser,没有@TableName注解,DbConfig的schema为website,DbConfig的tablePrefix为"t_", 其余默认 -> 最终表名: website.t_sys_user
    // 类名为SysUser,有@TableName注解的,@TableName的scheme为developer,DbConfig的schema为website,DbConfig的tablePrefix为"t_", 其余默认 -> 最终表名: developer.sys_user
    // 类名为SysUser,也没有@TableName注解,其余默认 -> 最终表名: sys_user
    private String tableName;

    // 表映射结果集
    // 第一: 来自@TableName的resultMap属性
    // 第二: 如果@TableName.resultMap为空,且@TableName.autoResultMap为true -> 自动去构建ResultMap -> 对应的id就是: mapper接口的名字 + ".mybatis-plus_" + 实体类的简单类名
    //       比如 com.sdk.mapper.UserMapper.mybatis-plus_User
    private String resultMap;

    // 是否是需要自动生成的 resultMap
    // 第一来源: 来自@TableName.autoResultMap()
    private boolean autoInitResultMap;

    // 主键是否有存在字段名与属性名关联
    // true: 表示要进行 as
    // 简述:
    // 当开启全局驼峰命名转换后,只要属性名等于列名去掉下划线后不相等,就需要转换
    // 当没有开启全局驼峰命名转换时,只要属性名和列名不相等,就需要转换
    private boolean keyRelated;

    // 表主键ID 列名
    // @TableId.value属性 优先级大于 字段类名 [字段类名会经过是否需要驼峰命名转下划线以及是否全局大写的判断]
    private String keyColumn;

    // 表主键ID 属性名
    private String keyProperty;

    // 表主键ID 属性类型
    private Class<?> keyType;

    // 表主键ID Sequence
    // 第一来源: 实体类上的@KeySequence注解
    private KeySequence keySequence;

    // 整个实体类除去@TableName.excludeProperty()字段后
    // 每个字段都需要映射为 TableFieldInfo
    private List<TableFieldInfo> fieldList;

    // 命名空间 (对应的 mapper 接口的全类名)
    // 来自: MapperBuilderAssistant.getCurrentNamespace()
    // 过程简述 -> 主要是在 MybatisMapperAnnotationBuilder.parse() -> parserInjector() 注入MP的CRUD动态SQL -> SQLInjector.inspectInject(assistant, type)
    // 最终还是: mapper接口的name : mapperType.getName()
    private String currentNamespace;
    /**
     * MybatisConfiguration 标记 (Configuration内存地址值)
     */
    @Getter
    private Configuration configuration;

    // 是否开启下划线转驼峰
    // 推算 column 的命名
    // 第一来源于: configuration.isMapUnderscoreToCamelCase()
    // note:
    // 1.对于@TableField.value()也是同样生效的哦,例如@TableField.value()=userName,开启当前配置,那么最终的列名还是被映射为"user_name" [❗️❗️❗️]
    // 2.对于没有@TableField开启当前,如果字段名是"userName",那么列名会映射为"user_name"
    private boolean underCamel;

    // 缓存包含主键及字段的 sql select
    @Setter(AccessLevel.NONE)
    @Getter(AccessLevel.NONE)
    private String allSqlSelect;

    // 缓存主键字段的 sql select
    @Setter(AccessLevel.NONE)
    @Getter(AccessLevel.NONE)
    private String sqlSelect;

    // 表字段是否启用了插入填充
    // 取决: this.fieldFill == FieldFill.INSERT || this.fieldFill == FieldFill.INSERT_UPDATE;
    @Getter
    @Setter(AccessLevel.NONE)
    private boolean withInsertFill;

    // 表字段是否启用了更新填充
    // 取决: this.fieldFill == FieldFill.UPDATE || this.fieldFill == FieldFill.INSERT_UPDATE;
    @Getter
    @Setter(AccessLevel.NONE)
    private boolean withUpdateFill;

    // 表字段是否启用了逻辑删除
    // 即实体类中是否有逻辑删除字段 [@TableLogic标志的 或者 字段名等于GlobalConfig.logicDeleteField]
    @Getter
    @Setter(AccessLevel.NONE)
    private boolean withLogicDelete;
    /**
     * 逻辑删除字段
     *
     * @since 3.4.0
     */
    @Getter
    @Setter(AccessLevel.NONE)
    private TableFieldInfo logicDeleteFieldInfo;

    // 实体类中是否有字段启用了乐观锁
    @Getter
    @Setter(AccessLevel.NONE)
    private boolean withVersion;

    // 使用了@Version标注的实体类的字段 [一个实体类仅只有一个哦]
    @Getter
    @Setter(AccessLevel.NONE)
    private TableFieldInfo versionFieldInfo;

    // 排序列表
    // 标记有@OrderBy的字段->对应形成的TableFieldInfo
    @Setter
    private List<TableFieldInfo> orderByFields;

    // 实体对象PO的Reflector -> 能够帮助获取PO类的各种get方法\set方法...
    @Getter
    private Reflector reflector;

    /**
     * @param entityType 实体类型
     * @deprecated 3.4.4 {@link #TableInfo(Configuration, Class)}
     */
    @Deprecated
    public TableInfo(Class<?> entityType) {
        this.entityType = entityType;
        this.reflector = SystemMetaObject.NULL_META_OBJECT.getReflectorFactory().findForClass(entityType);
    }

    /**
     * @param configuration 配置对象
     * @param entityType    实体类型
     * @since 3.4.4
     */
    public TableInfo(Configuration configuration, Class<?> entityType) {
        this.configuration = configuration;
        this.entityType = entityType;
        this.reflector = configuration.getReflectorFactory().findForClass(entityType);
        this.underCamel = configuration.isMapUnderscoreToCamelCase();
    }

    /**
     * 获得注入的 SQL Statement
     *
     * @param sqlMethod MybatisPlus 支持 SQL 方法
     * @return SQL Statement
     * @deprecated 3.4.0 如果存在的多mapper共用一个实体的情况，这里可能会出现获取命名空间错误的情况
     */
    @Deprecated
    public String getSqlStatement(String sqlMethod) {
        // 获取当前TableInfo自动注入的CRUD的方法的MappedStatement的id值
        // currentNamespace + "." + sqlMethod
        return currentNamespace + DOT + sqlMethod;
    }

    /**
     * @deprecated 3.4.4 {@link #TableInfo(Configuration, Class)}
     * 设置 Configuration
     */
    @Deprecated
    void setConfiguration(Configuration configuration) {
        Assert.notNull(configuration, "Error: You need Initialize MybatisConfiguration !");
        this.configuration = configuration;
        this.underCamel = configuration.isMapUnderscoreToCamelCase();
        this.reflector = configuration.getReflectorFactory().findForClass(this.entityType);
    }

    /**
     * 是否有主键
     *
     * @return 是否有
     */
    public boolean havePK() {
        return StringUtils.isNotBlank(keyColumn);
    }

    /**
     * 获取主键的 select sql 片段
     *
     * @return sql 片段
     */
    public String getKeySqlSelect() {
        // 获取主键的 select sql 片段

        // 1. 缓存
        if (sqlSelect != null) {
            return sqlSelect;
        }

        // 缓存失效

        // 2. 有主键
        if (havePK()) {
            // 2.1 是否需要 AS 关键字转换
            sqlSelect = keyColumn;
            if (resultMap == null && keyRelated) {
                sqlSelect += (AS + keyProperty);
            }
        }
        // 3. 无主键
        else {
            // 3.1 标记为无主键的EMPTY
            sqlSelect = EMPTY;
        }
        return sqlSelect;
    }

    /**
     * 获取包含主键及字段的 select sql 片段
     *
     * @return sql 片段
     */
    public String getAllSqlSelect() {
        // 1. 是否已经解析过主键及字段的 select sql 片段 -> 已经解析过会存放找allSqlSelect缓存起来
        if (allSqlSelect != null) {
            return allSqlSelect;
        }
        // 2. 第一次解析,解析后缓存到allSqlSelect上去
        allSqlSelect = chooseSelect(TableFieldInfo::isSelect);
        return allSqlSelect;
    }

    /**
     * 获取需要进行查询的 select sql 片段
     *
     * @param predicate 过滤条件
     * @return sql 片段
     */
    public String chooseSelect(Predicate<TableFieldInfo> predicate) {
        // 最终结果一般都是
        // idColumn as idProperty,column1 as property1, column2, column3 as property3

        // 1. 获取主键的的sql select片段
        // sqlSelect常见结果: id 或者 idColumn as idProperty
        String sqlSelect = getKeySqlSelect();
        // 2. 遍历出fieldList中没有被@TableField.exist()标记为false的对象
        // 然后通过 TableFieldInfo::getSqlSelect 转换为对应的 sql select 脚本片段, 并使用 "," 联合起来
        // fieldsSqlSelect常见结果: column1 as property1, column2, column3 as property3
        String fieldsSqlSelect = fieldList.stream().filter(predicate).map(TableFieldInfo::getSqlSelect).collect(joining(COMMA));
        // 3. 存在主键的sql select片段且fieldsSqlSelect
        if (StringUtils.isNotBlank(sqlSelect) && StringUtils.isNotBlank(fieldsSqlSelect)) {
            // 3.1 联合后返回
            return sqlSelect + COMMA + fieldsSqlSelect;
        } else if (StringUtils.isNotBlank(fieldsSqlSelect)) {
            // 3.2 只返回: fieldsSqlSelect
            return fieldsSqlSelect;
        }
        return sqlSelect;
    }

    /**
     * 获取 insert 时候主键 sql 脚本片段
     * <p>insert into table (字段) values (值)</p>
     * <p>位于 "值" 部位</p>
     *
     * @return sql 脚本片段
     */
    public String getKeyInsertSqlProperty(final boolean batch, final String prefix, final boolean newLine) {
        // 主键存在,且非批量自增
            // <if test= " prefix.KeyProperty != null"> #{prefix.KeyProperty}, </if>
        // 主键不存在
            // #{prefix.KeyProperty},


        final String newPrefix = prefix == null ? EMPTY : prefix;
        if (havePK()) {
            // 1. 有主键的情况下,需要形成安全入参即 #{prefix+KeyProperty} + ","
            final String prefixKeyProperty = newPrefix + keyProperty;
            String keyColumn = SqlScriptUtils.safeParam(prefixKeyProperty) + COMMA;
            if (idType == IdType.AUTO) {
                if (batch) {
                    // 批量插入必须返回空自增情况下
                    return EMPTY;
                }
                // 1.1 如果是AUTO自增的情况下,需要用<if>标签包装一下
                return SqlScriptUtils.convertIf(keyColumn, String.format("%s != null", prefixKeyProperty), newLine);
            }
            return keyColumn + (newLine ? NEWLINE : EMPTY);
        }
        return EMPTY;
    }

    /**
     * 获取 insert 时候主键 sql 脚本片段
     * <p>insert into table (字段) values (值)</p>
     * <p>位于 "字段" 部位</p>
     *
     * @return sql 脚本片段
     */
    public String getKeyInsertSqlColumn(final boolean batch, final boolean newLine) {
        // 返回结果:
        // 有主键:
        // <if test="keyProperty != null "> keyColumn, </if>
        // 无主键:
        // ""

        if (havePK()) {
            if (idType == IdType.AUTO) {
                if (batch) {
                    // 批量插入必须返回空自增情况下
                    return EMPTY;
                }
                // 创建if标签 -> <if test="ifTest">sqlScript</if>
                // 即只有当keyProperty不为空的时候, keyColumn + , 才会生效哦
                return SqlScriptUtils.convertIf(keyColumn + COMMA, String.format("%s != null", keyProperty), newLine);
            }
            return keyColumn + COMMA + (newLine ? NEWLINE : EMPTY);
        }
        return EMPTY;
    }

    /**
     * 获取所有 insert 时候插入值 sql 脚本片段
     * <p>insert into table (字段) values (值)</p>
     * <p>位于 "值" 部位</p>
     *
     * <li> 自动选部位,根据规则会生成 if 标签 </li>
     *
     * @return sql 脚本片段
     */
    public String getAllInsertSqlPropertyMaybeIf(final String prefix) {
        // 获取所有 insert 时候插入值 sql 脚本片段
        // insert into table (字段) values (值)
        // 位于 "值" 部位
        final String newPrefix = prefix == null ? EMPTY : prefix;
        // 1. getKeyInsertSqlProperty(false, newPrefix, true)
            // <if test= " prefix.KeyProperty != null"> #{prefix.KeyProperty}, </if>                         -> 主键
        // 2. fieldList.stream().map(i -> i.getInsertSqlPropertyMaybeIf(newPrefix))
            // <if test="newPrefix.property != null" > #{newPrefix.el}, </if>                                -> 没有插入填充策略,insertStrategy=FieldStrategy.NOT_NULL
            // <if test="newPrefix.property != null and newPrefix.property != '' " > #{newPrefix.el}, </if>  -> 没有插入填充策略,insertStrategy=FieldStrategy.NOT_EMPTY
            // #{newPrefix.el}, ->  有插入填充策略
        return getKeyInsertSqlProperty(false, newPrefix, true) + fieldList.stream()
            .map(i -> i.getInsertSqlPropertyMaybeIf(newPrefix)).filter(Objects::nonNull).collect(joining(NEWLINE));
    }

    /**
     * 获取 insert 时候字段 sql 脚本片段
     * <p>insert into table (字段) values (值)</p>
     * <p>位于 "字段" 部位</p>
     *
     * <li> 自动选部位,根据规则会生成 if 标签 </li>
     *
     * @return sql 脚本片段
     */
    public String getAllInsertSqlColumnMaybeIf(final String prefix) {
        // 最终结果:
        // <if test="keyProperty != null "> keyColumn, </if> -> 主键对应的insert
        // column1,                                          -> 属性1对应的列1存在自动填充  @TableField.fill()= FieldFill.INSERT
        // <if test="prefix.property2"> column2, </if>       -> 属性2对应的列2没有自动填充

        // 获取 insert 时候字段 sql 脚本片段
        final String newPrefix = prefix == null ? EMPTY : prefix;
        // 1. getKeyInsertSqlColumn(false, true)
        // <if test="keyProperty != null "> keyColumn, </if>
        // 2. fieldList.stream().map(i -> i.getInsertSqlColumnMaybeIf(newPrefix))
        // column1,                                         -> 属性1对应的列1存在自动填充  @TableField.fill()= FieldFill.INSERT
        // <if test="prefix.property2"> column2, </if>      -> 属性2对应的列2没有自动填充
        return getKeyInsertSqlColumn(false, true) + fieldList.stream().map(i -> i.getInsertSqlColumnMaybeIf(newPrefix))
            .filter(Objects::nonNull).collect(joining(NEWLINE));
    }

    /**
     * 获取所有的查询的 sql 片段
     *
     * @param ignoreLogicDelFiled 是否过滤掉逻辑删除字段
     * @param withId              是否包含 id 项
     * @param prefix              前缀
     * @return sql 脚本片段
     */
    public String getAllSqlWhere(boolean ignoreLogicDelFiled, boolean withId, final String prefix) {
        // 获取所有的查询的 sql 片段
        // Params:  ignoreLogicDelFiled – 是否过滤掉逻辑删除字段
        //          withId – 是否包含 id 项 -> 目前都为true
        //          prefix – 前缀 -> 目前都为 ew.entity.

        // 目前前缀prefix都是 "ew.entity." 这个值哦
        // 因此: newPrefix = "ew.entity."

        // 最终结果: keyProperty为主键属性名
        //  <if test=" #{ew.entity. + keyProperty} != null "> keyColumn = #{ew.entity. + keyProperty} </if>
        //  <if test=" #{ew.entity.property1} != null " > AND column = #{ew.entity. + e1l} </if>   // ❗️❗️❗ <if>标签的test是收@TableField.whereStrategy()控制确定成立条件,AND后的比较逻辑是受到@TableField.condition()控制的️
        //  <if test=" #{ew.entity.property2} != null and #{ew.entity.property} != ''" > AND column != #{ew.entity. + el2} </if> //  [比如: condition为SQLCondition.EQUALS,whereStrategy=FieldStrategy.NOT_EMPTY]
        //  <if test=" #{ew.entity.property3} != null " > AND column LIKE concat('%',#{ew.entity. + el3},'%'} </if>   //  [比如: condition为SQLCondition.LIKE,whereStrategy=FieldStrategy.NOT_NULL]


        final String newPrefix = prefix == null ? EMPTY : prefix;
        // 1. 如果ignoreLogicDelFiled为true,就忽略路基删除字段
        // 并通过 i.getSqlWhere(newPrefix) 获取对应字段的 sql where 脚本片段
        String filedSqlScript = fieldList.stream()
            .filter(i -> {
                if (ignoreLogicDelFiled) {
                    return !(isWithLogicDelete() && i.isLogicDelete());
                }
                return true;
            })
            // 1.1 TableFieldInfo.getSqlWhere(..) 获取当前字段作为where查询语句时的脚本片段 -> [该脚本片段被<if>包括,且处理逻辑都是AND]
            // <if test=" #{ew.entity.property} != null " > AND column = #{ew.entity. + el} </if> [@TableField.whereStrategy()控制where的逻辑 和 @TableField.condition()控制if标签]
            .map(i -> i.getSqlWhere(newPrefix)).filter(Objects::nonNull).collect(joining(NEWLINE));
        // 2. 如果withId为false,表示不需要包含id项 || 如果withId为true,但keyProperty为空表示没有id
        // 都不进行下一步处理立即 return filedSqlScript
        if (!withId || StringUtils.isBlank(keyProperty)) {
            return filedSqlScript;
        }
        // 3. 构建id的where筛选条件的sql脚本片段 -> #{ew.entity. + keyProperty主键属性名 }
        String newKeyProperty = newPrefix + keyProperty;
        String keySqlScript = keyColumn + EQUALS + SqlScriptUtils.safeParam(newKeyProperty);
        // 4. 将id的where筛选条件的sql脚本片段使用<if>包装起来
        // 即 <if test=" #{ew.entity. + keyProperty} != null "> keyColumn = #{ew.entity. + keyProperty} </if>
        // +
        // filedSqlScript
        return SqlScriptUtils.convertIf(keySqlScript, String.format("%s != null", newKeyProperty), false)
            + NEWLINE + filedSqlScript;
    }

    /**
     * 获取所有的 sql set 片段
     *
     * @param ignoreLogicDelFiled 是否过滤掉逻辑删除字段
     * @param prefix              前缀
     * @return sql 脚本片段
     */
    public String getAllSqlSet(boolean ignoreLogicDelFiled, final String prefix) {
        final String newPrefix = prefix == null ? EMPTY : prefix;
        // prefix = 'et.'
        // 结果为:
        // 所有字段的set片段合并
        // <if test='et.property1 != null'> column1 = #{et.property1} </if>                             当updateStrategy为FieldStrategy.NOE_NULL时
        // <if test='et.property2 != null and et.property2 != '' '> column2 = #{et.property2} </if>     当updateStrategy为FieldStrategy.NOE_EMPTY时
        // <if test='et.property3 != null'> column3 = column3 + 1 </if>                                 当@TableField.udpate() = "%s+1" 时
        // <if test='et.property4 != null'> column4 = now() </if>                                       当@TableField.udpate() = "now()" 时

        return fieldList.stream()
            .filter(i -> {
                if (ignoreLogicDelFiled) {
                    return !(isWithLogicDelete() && i.isLogicDelete());
                }
                return true;
            }).map(i -> i.getSqlSet(newPrefix)).filter(Objects::nonNull).collect(joining(NEWLINE));
    }

    /**
     * 获取逻辑删除字段的 sql 脚本
     *
     * @param startWithAnd 是否以 and 开头
     * @param isWhere      是否需要的是逻辑删除值
     * @return sql 脚本
     */
    public String getLogicDeleteSql(boolean startWithAnd, boolean isWhere) {
        // 获取逻辑删除字段的 sql 脚本
        //      startWithAnd – 是否以 and 开头
        //      isWhere – 是否需要的是逻辑删除值
        // table.getLogicDeleteSql(false, false): deleted = 1
        // table.getLogicDeleteSql(true, false): and deleted = 1
        // table.getLogicDeleteSql(false, true): deleted = 0
        // table.getLogicDeleteSql(true, true): and deleted = 0


        // 1. 有逻辑删除字段
        if (withLogicDelete) {
            // 1.1 形参逻辑删除的片段sql
            String logicDeleteSql = formatLogicDeleteSql(isWhere);
            if (startWithAnd) {
                // 1.2 是否以AND开始 -> 加上 " AND " 前缀
                logicDeleteSql = " AND " + logicDeleteSql;
            }
            return logicDeleteSql;
        }
        return EMPTY;
    }

    /**
     * format logic delete SQL, can be overrided by subclass
     * github #1386
     *
     * @param isWhere true: logicDeleteValue, false: logicNotDeleteValue
     * @return sql
     */
    protected String formatLogicDeleteSql(boolean isWhere) {
        // 格式逻辑删除 SQL，可以被子类 github  -> 四种情况
        // a: isWhere为true,逻辑未删除字段值为null -> deleted IS NULL
        // b: isWhere为true,逻辑未删除字段值为0 -> deleted = 0
        // c: isWhere为false,逻辑未删除字段值为null -> deleted = nul
        // d: isWhere为false,逻辑未删除字段值为1 -> deleted = 1

        // 0.  逻辑删除字段用在where子句中 -> 就获取逻辑未删除字段/ 或者获取逻辑删除字段
        final String value = isWhere ? logicDeleteFieldInfo.getLogicNotDeleteValue() : logicDeleteFieldInfo.getLogicDeleteValue();
        // 1. 逻辑删除字段是否使用在where子句中 -> 要求过滤出非逻辑删除的数据 -> 故添加逻辑删除在where上
        if (isWhere) {
            if (NULL.equalsIgnoreCase(value)) {
                return logicDeleteFieldInfo.getColumn() + " IS NULL";
            } else {
                // 1.1 大部分情况都是这样: 假设value=0表示没有被删除
                // logic_column = 0
                return logicDeleteFieldInfo.getColumn() + EQUALS + String.format(logicDeleteFieldInfo.isCharSequence() ? "'%s'" : "%s", value);
            }
        }
        // 2. 逻辑删除字段不使用在where子句上 -> 使用的就是逻辑删除字段值

        // 2.1 构建 logic_column = 0
        final String targetStr = logicDeleteFieldInfo.getColumn() + EQUALS;
        if (NULL.equalsIgnoreCase(value)) {
            return targetStr + NULL;
        } else {
            return targetStr + String.format(logicDeleteFieldInfo.isCharSequence() ? "'%s'" : "%s", value);
        }
    }

    /**
     * 自动构建 resultMap 并注入(如果条件符合的话)
     */
    void initResultMapIfNeed() {
        // 1. @TableName.autoResultMap()为true && resultMap并且为空
        if (autoInitResultMap && null == resultMap) {
            // 1.1 resultMap的id: mapper接口的名字 + ".mybatis-plus_" + 实体类的简单类名
            // 比如 com.sdk.mapper.UserMapper.mybatis-plus_User
            String id = currentNamespace + DOT + MYBATIS_PLUS + UNDERSCORE + entityType.getSimpleName();
            List<ResultMapping> resultMappings = new ArrayList<>();
            // 1.2 如果有主键,请先处理主键的ResultMapping
            if (havePK()) {
                ResultMapping idMapping = new ResultMapping.Builder(configuration, keyProperty, StringUtils.getTargetColumn(keyColumn), keyType)
                    .flags(Collections.singletonList(ResultFlag.ID)).build(); // ResultFlag.ID 表示为id
                resultMappings.add(idMapping);
            }
            // 1.3 遍历处理 fieldList
            if (CollectionUtils.isNotEmpty(fieldList)) {
                fieldList.forEach(i -> resultMappings.add(i.getResultMapping(configuration)));
            }
            ResultMap resultMap = new ResultMap.Builder(configuration, id, entityType, resultMappings).build();
            // 1.4 将构建的resultMap添加到Configuration中
            configuration.addResultMap(resultMap);
            this.resultMap = id;
        }
    }

    void setFieldList(List<TableFieldInfo> fieldList) {
        this.fieldList = fieldList;
        AtomicInteger logicDeleted = new AtomicInteger();
        AtomicInteger version = new AtomicInteger();
        fieldList.forEach(i -> {
            if (i.isLogicDelete()) {
                this.withLogicDelete = true;
                this.logicDeleteFieldInfo = i;
                logicDeleted.getAndAdd(1);
            }
            if (i.isWithInsertFill()) {
                this.withInsertFill = true;
            }
            if (i.isWithUpdateFill()) {
                this.withUpdateFill = true;
            }
            if (i.isOrderBy()) {
                this.getOrderByFields().add(i);
            }
            if (i.isVersion()) {
                this.withVersion = true;
                this.versionFieldInfo = i;
                version.getAndAdd(1);
            }
        });
        /* 校验字段合法性 */
        // 2. 逻辑删除字段只能有一个 [即 @TableLogic标注的字段 或者 字段名和GlobalConfig.logicDeleteField字段值相同 ]
        Assert.isTrue(logicDeleted.get() <= 1, "@TableLogic not support more than one in Class: \"%s\"", entityType.getName());
        // 3. 乐观锁字段只能有一个 [即 @Version标注的字段 ]
        Assert.isTrue(version.get() <= 1, "@Version not support more than one in Class: \"%s\"", entityType.getName());
    }

    public List<TableFieldInfo> getFieldList() {
        return Collections.unmodifiableList(fieldList);
    }

    public List<TableFieldInfo> getOrderByFields() {
        if (null == this.orderByFields) {
            this.orderByFields = new LinkedList<>();
        }
        return this.orderByFields;
    }

    @Deprecated
    public boolean isLogicDelete() {
        return withLogicDelete;
    }

    /**
     * 获取对象属性值
     *
     * @param entity   对象
     * @param property 属性名
     * @return 属性值
     * @since 3.4.4
     */
    public Object getPropertyValue(Object entity, String property) {
        try {
            return this.reflector.getGetInvoker(property).invoke(entity, null);
        } catch (ReflectiveOperationException e) {
            throw ExceptionUtils.mpe("Error: Cannot read property in %s.  Cause:", e, entity.getClass().getSimpleName());
        }
    }

    /**
     * 设置对象属性值
     *
     * @param entity   实体对象
     * @param property 属性名
     * @param values   参数
     * @since 3.4.4
     */
    public void setPropertyValue(Object entity, String property, Object... values) {
        try {
            this.reflector.getSetInvoker(property).invoke(entity, values);
        } catch (ReflectiveOperationException e) {
            throw ExceptionUtils.mpe("Error: Cannot write property in %s.  Cause:", e, entity.getClass().getSimpleName());
        }
    }

    /**
     * 创建实例
     *
     * @param <T> 泛型
     * @return 初始化实例
     * @since 3.5.0
     */
    @SuppressWarnings("unchecked")
    public <T> T newInstance() {
        Constructor<?> defaultConstructor = reflector.getDefaultConstructor();
        if (!defaultConstructor.isAccessible()) {
            defaultConstructor.setAccessible(true);
        }
        try {
            return (T) defaultConstructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw ExceptionUtils.mpe(e);
        }
    }

}
