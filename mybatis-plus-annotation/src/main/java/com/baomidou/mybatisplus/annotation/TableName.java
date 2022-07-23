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

import java.lang.annotation.*;

/**
 * 数据库表相关
 *
 * @author hubin, hanchunlin
 * @since 2016-01-23
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
public @interface TableName {
    // mp 全称就是: mybatis-plus

    // 关于`autoResultMap`属性的说明:
    // mp会自动构建一个ResultMap并注入到mybatis里(一般用不上).下面讲两句:
    // 因为mp底层是mybatis,所以一些mybatis的常识你要知道,mp只是帮你注入了常用crud到mybatis里,
    // 注入之前可以说是动态的(根据你entity的字段以及注解变化而变化),
    // 但是注入之后是静态的(等于你写在xml的东西) 而对于直接指定typeHandler,mybatis只支持你写在2个地方:
    //
    // 1. 定义在resultMap里,只作用于select查询的返回结果封装
    // 2. 定义在insert和update的sql的#{property}里的property后面(例:#{property,typeHandler=xxx.xxx.xxx}),只作用于设置值
    // 而除了这两种直接指定typeHandler -- mybatis有一个全局的扫描你自己的typeHandler包的配置,这是根据你的property的类型去找typeHandler并使用.

    /**
     * 实体对应的表名
     */
    String value() default "";

    /**
     * schema
     * <p>
     * 配置此值将覆盖全局配置的 schema
     *
     * @since 3.1.1
     */
    String schema() default "";

    /**
     * 是否保持使用全局的 tablePrefix 的值
     * <p> 只生效于 既设置了全局的 tablePrefix 也设置了上面 {@link #value()} 的值 </p>
     * <li> 如果是 false , 全局的 tablePrefix 不生效 </li>
     *
     * @since 3.1.1
     */
    boolean keepGlobalPrefix() default false;
    // 是否需要使用 GlobalConfig 中的 tablePrefix 表名前缀的值 -> 前提是你需要指定 @TableName的 value 值
    // 否则表名tableName()取决于 TableInfoHelper#initTableNameWithDbConfig(..) 方法
    // 简述:
    // 1. 拿到实体类的类名作为tableName
    // 2. dbConfig.isTableUnderline 中是否开启表名下划线申明 -> 是的话,tableName从驼峰命名改为下划线
    // 3. DbConfig中是否开启表名大写命名判断 -> 是的话,tableName全部大写字母 -> 否则就首个字母小写

    /**
     * 实体映射结果集,
     * 只生效于 mp 自动注入的 method
     */
    String resultMap() default "";
    // xml 中 resultMap 的 id

    /**
     * 是否自动构建 resultMap 并使用,
     * 只生效于 mp 自动注入的 method,
     * 如果设置 resultMap 则不会进行 resultMap 的自动构建并注入,
     * 只适合个别字段 设置了 typeHandler 或 jdbcType 的情况
     *
     * @since 3.1.2
     */
    boolean autoResultMap() default false;

    /**
     * 需要排除的属性名
     *
     * @since 3.3.1
     */
    String[] excludeProperty() default {};
}
