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
package com.baomidou.mybatisplus.annotation;

import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.UnknownTypeHandler;

import java.lang.annotation.*;

/**
 * 表字段标识
 *
 * @author hubin sjy tantan
 * @since 2016-09-09
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.ANNOTATION_TYPE})
public @interface TableField {
    // 用在PO类上非主键的字段上
    // 可以配置: 对应的列值/

    /**
     * 数据库字段值
     * <p>
     * 不需要配置该值的情况:
     * <li> 当 {@link com.baomidou.mybatisplus.core.MybatisConfiguration#mapUnderscoreToCamelCase} 为 true 时,
     * (mp下默认是true,mybatis默认是false), 数据库字段值.replace("_","").toUpperCase() == 实体属性名.toUpperCase() </li>
     * <li> 当 {@link com.baomidou.mybatisplus.core.MybatisConfiguration#mapUnderscoreToCamelCase} 为 false 时,
     * 数据库字段值.toUpperCase() == 实体属性名.toUpperCase() </li>
     */
    String value() default "";
    //  属性对应的列名
    //  com.baomidou.mybatisplus.core.MybatisConfiguration.mapUnderscoreToCamelCase 为 true 时
    //  数据表的列名.replace("_","").toUpperCase() == 实体类的属性名.toUpperCase() -- 因此忽略大小写以及忽略下划线

    /**
     * 是否为数据库表字段
     * <p>
     * 默认 true 存在，false 不存在
     */
    boolean exist() default true; // ❗️❗️❗️

    /**
     * 字段 where 实体查询比较条件
     * <p>
     * 默认 {@link SqlCondition#EQUAL}
     */
    String condition() default "";
    // 表示用在where上时,
    // 默认为SqlCondition.EQUAL
    // 用户可以自己定义 -- 其中%s就表示这个属性值
    // 比如: 等于就是 %s=#{%s}

    /**
     * 字段 update set 部分注入, 该注解优于 el 注解使用
     * <p>
     * 例1：@TableField(.. , update="%s+1") 其中 %s 会填充为字段
     * 输出 SQL 为：update 表 set 字段=字段+1 where ...
     * <p>
     * 例2：@TableField(.. , update="now()") 使用数据库时间
     * 输出 SQL 为：update 表 set 字段=now() where ...
     */
    String update() default "";
    // 字段 update set 部分注入, 该注解优于 el 注解使用
    // 例1：@TableField(.. , update="%s+1") 其中 %s 会填充为字段 输出 SQL 为：update 表 set column=column+1 where ...
    // 例2：@TableField(.. , update="now()") 使用数据库时间 输出 SQL 为：update 表 set column=now() where ...

    /**
     * 字段验证策略之 insert: 当insert操作时，该字段拼接insert语句时的策略
     * <p>
     * IGNORED: 直接拼接 insert into table_a(column) values (#{columnProperty});
     * NOT_NULL: insert into table_a(<if test="columnProperty != null">column</if>) values (<if test="columnProperty != null">#{columnProperty}</if>)
     * NOT_EMPTY: insert into table_a(<if test="columnProperty != null and columnProperty!=''">column</if>) values (<if test="columnProperty != null and columnProperty!=''">#{columnProperty}</if>)
     * NOT_EMPTY 如果针对的是非 CharSequence 类型的字段则效果等于 NOT_NULL
     *
     * @since 3.1.2
     */
    FieldStrategy insertStrategy() default FieldStrategy.DEFAULT;
    // 字段验证策略之 insert: 当insert操作时，该字段拼接insert语句时的策略
    // <p>
    // IGNORED: 直接拼接 insert into table_a(column) values (#{columnProperty});
    // NOT_NULL: insert into table_a(<if test="columnProperty != null">column</if>) values (<if test="columnProperty != null">#{columnProperty}</if>)
    // NOT_EMPTY: insert into table_a(<if test="columnProperty != null and columnProperty!=''">column</if>) values (<if test="columnProperty != null and columnProperty!=''">#{columnProperty}</if>)
    // NOT_EMPTY 如果针对的是非 CharSequence 类型的字段则效果等于 NOT_NULL

    /**
     * 字段验证策略之 update: 当更新操作时，该字段拼接set语句时的策略
     * <p>
     * IGNORED: 直接拼接 update table_a set column=#{columnProperty}, 属性为null/空string都会被set进去
     * NOT_NULL: update table_a set <if test="columnProperty != null">column=#{columnProperty}</if>
     * NOT_EMPTY: update table_a set <if test="columnProperty != null and columnProperty!=''">column=#{columnProperty}</if>
     * NOT_EMPTY 如果针对的是非 CharSequence 类型的字段则效果等于 NOT_NULL
     *
     * @since 3.1.2
     */
    FieldStrategy updateStrategy() default FieldStrategy.DEFAULT;
    // 字段验证策略之 update: 当更新操作时，该字段拼接set语句时的策略
    // <p>
    // IGNORED: 直接拼接 update table_a set column=#{columnProperty}, 属性为null/空string都会被set进去
    // NOT_NULL: update table_a set <if test="columnProperty != null">column=#{columnProperty}</if>
    // NOT_EMPTY: update table_a set <if test="columnProperty != null and columnProperty!=''">column=#{columnProperty}</if>
    // NOT_EMPTY 如果针对的是非 CharSequence 类型的字段则效果等于 NOT_NULL

    /**
     * 字段验证策略之 where: 表示该字段在拼接where条件时的策略
     * <p>
     * IGNORED: 直接拼接 column=#{columnProperty}
     * NOT_NULL: <if test="columnProperty != null">column=#{columnProperty}</if>
     * NOT_EMPTY: <if test="columnProperty != null and columnProperty!=''">column=#{columnProperty}</if>
     * NOT_EMPTY 如果针对的是非 CharSequence 类型的字段则效果等于 NOT_NULL
     *
     * @since 3.1.2
     */
    FieldStrategy whereStrategy() default FieldStrategy.DEFAULT;
    // 字段验证策略之 where: 表示该字段在拼接where条件时的策略
    // <p>
    // IGNORED: 直接拼接 column=#{columnProperty}
    // NOT_NULL: <if test="columnProperty != null">column=#{columnProperty}</if>
    // NOT_EMPTY: <if test="columnProperty != null and columnProperty!=''">column=#{columnProperty}</if>
    // NOT_EMPTY 如果针对的是非 CharSequence 类型的字段则效果等于 NOT_NULL

    /**
     * 字段自动填充策略
     * <p>
     * 在对应模式下将会忽略 insertStrategy 或 updateStrategy 的配置,等于断言该字段必有值
     */
    FieldFill fill() default FieldFill.DEFAULT;
    // 填充模式 -- 包括 insert/update/insert_update
    // 有了填充模式,必须注册一个MetaObjectHanlder
    // 提供一个新增时填充的方法、一个修改时填充的方法

    /**
     * 是否进行 select 查询
     * <p>
     * 大字段可设置为 false 不加入 select 查询范围
     */
    boolean select() default true;
    // 表示当前字段是否会在查询的时候,查询出来 -> 即 select password 这种

    /**
     * 是否保持使用全局的 columnFormat 的值
     * <p>
     * 只生效于 既设置了全局的 columnFormat 也设置了上面 {@link #value()} 的值
     * 如果是 false , 全局的 columnFormat 不生效
     *
     * @since 3.1.1
     */
    boolean keepGlobalFormat() default false;

    /**
     * {@link ResultMapping#property} and {@link ParameterMapping#property}
     *
     * @since 3.4.4
     */
    String property() default "";

    /**
     * JDBC类型 (该默认值不代表会按照该值生效),
     * 只生效于 mp 自动注入的 method,
     * 建议配合 {@link TableName#autoResultMap()} 一起使用
     * <p>
     * {@link ResultMapping#jdbcType} and {@link ParameterMapping#jdbcType}
     *
     * @since 3.1.2
     */
    JdbcType jdbcType() default JdbcType.UNDEFINED;
    // 指定jdbcType

    /**
     * 类型处理器 (该默认值不代表会按照该值生效),
     * 只生效于 mp 自动注入的 method,
     * 建议配合 {@link TableName#autoResultMap()} 一起使用
     * <p>
     * {@link ResultMapping#typeHandler} and {@link ParameterMapping#typeHandler}
     *
     * @since 3.1.2
     */
    Class<? extends TypeHandler> typeHandler() default UnknownTypeHandler.class;
    // 类型处理器TypeHandler (该默认值不代表会按照该值生效), 只生效于 mp 自动注入的 method
    // 建议配合 TableName.autoResultMap() 一起使用

    /**
     * 只在使用了 {@link #typeHandler()} 时判断是否辅助追加 javaType
     * <p>
     * 一般情况下不推荐使用
     * {@link ParameterMapping#javaType}
     *
     * @since 3.4.0 @2020-07-23
     */
    boolean javaType() default false;
    // 只在使用了 typeHandler() 时判断是否辅助追加 javaTyp
    // 一旦表示需要追加javaType
    // 那么就会向 TypeAliasRegistry 中注册 以属性类型propertyType.getName()作为key, 该字段类型的的别名作为value

    /**
     * 指定小数点后保留的位数,
     * 只生效于 mp 自动注入的 method,
     * 建议配合 {@link TableName#autoResultMap()} 一起使用
     * <p>
     * {@link ParameterMapping#numericScale}
     *
     * @since 3.1.2
     */
    String numericScale() default "";

    // note
    // numericScale只生效于 update 的 sql 语句,
    // jdbcType和typeHandler如果不配合@TableName#autoResultMap = true一起使用,也只生效于 update 的sql.
    // 对于typeHandler如果你的字段类型和set进去的类型为equals关系,则只需要让你的typeHandler让Mybatis加载到即可,不需要使用注解
    // ❗️❗️❗️所以,可以在注册时将TypeHandler的jdbcType和javaType写为特殊的形式,以使得,不会导致一些常规的列或者属性映射上去

    // 比如想要对指定某个数据表的账号进行加密 -- 假设账号为String类型
    // 那么就创建一个EncryptTypeHandler继承BaseTypeHandler实现对应的几个方法
    // 注意这里: 如果使用 @MappedTypes({java.lang.String}) 且 @MappedJdbcTypes({JDBCType.VARCHAR})
    // 就会导致使用自动映射的那些SQL,类型为String的情况下会找到这个TypeHandler进行处理 -- 这就违背了只是为账号加密的需求
    // 应该不使用 @MappedTypes 和  @MappedJdbcTypes 注解 -- 这样就只会被注册到TypeHandlerRegistry.allTypeHandlersMap中 -- 而不是Map<Type, Map<JdbcType, TypeHandler<?>>> typeHandlerMap中

    // 然后再PO类上对应的@TableName上将autoMapping=true开启
    // 然后再到PO类上的账号属性上使用@TableField中将typeHandle=EncryptTypeHandler.class
    // 这样MP主动注入的方法在使用到这个属性 -- 比如使用QueryWrapper做查询\insert\update\delete使用该字段时,都会使用到EncryptTypeHandler加密和解密功能
    // 注意哦: 仅仅对MP主动注入的方法有效 -- 对于Mapper.xml想要生效就必须在两个地方指定
    // select 查询语句的返回值中如果有账号就应该解密出来使用,那么就需要<resultMap>中的<result>[当然并不一定是这一个标签,其余标签都是可以的]指定typeHandler属性
    // DML 增删改查语句中需要传入的账号如果是明文的话,就无法后对应的数据表中的值做比较,需要加密后比较,因此可以这样使用 {username,typeHandler=EncryptTypeHandler.class}
    // note: resultMap 仅仅是针对select的返回结果集ResultSet的封装,不会对insert/update/delete/select中的#{username}起任何作用
    // #{username}不指定typeHandler时,同时没有使用ParamType或者ParamMap时,就会根据这个属性username为String属性时,去TypeHandlerRegister中根据javaType为String找
}
