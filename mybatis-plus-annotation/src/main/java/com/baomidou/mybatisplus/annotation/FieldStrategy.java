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

/**
 * 字段策略枚举类
 * <p>
 * 如果字段是基本数据类型则最终效果等同于 {@link #IGNORED}
 *
 * @author hubin
 * @since 2016-09-09
 */
public enum FieldStrategy {

    // 命名:
    // Filed Strategy: bean中的字段在insert\update\where时的如何拼接的策略


    // 使用:
    // 搭配@TableFiled的whereStrategy属性
    // 字段验证策略之 where: 表示该字段在拼接where条件时的策略
    // <p>
    // IGNORED: 直接拼接 column=#{columnProperty}
    // NOT_NULL: <if test="columnProperty != null">column=#{columnProperty}</if>
    // NOT_EMPTY: <if test="columnProperty != null and columnProperty!=''">column=#{columnProperty}</if>
    // NOT_EMPTY 如果针对的是非 CharSequence 类型的字段则效果等于 NOT_NULL


    // 搭配@TableFiled的updateStrategy属性
    // 字段验证策略之 update: 当更新操作时，该字段拼接set语句时的策略
    // <p>
    // IGNORED: 直接拼接 update table_a set column=#{columnProperty}, 属性为null/空string都会被set进去
    // NOT_NULL: update table_a set <if test="columnProperty != null">column=#{columnProperty}</if>
    // NOT_EMPTY: update table_a set <if test="columnProperty != null and columnProperty!=''">column=#{columnProperty}</if>
    // NOT_EMPTY 如果针对的是非 CharSequence 类型的字段则效果等于 NOT_NULL

    // 搭配@TableFiled的insertStrategy属性
    // 字段验证策略之 insert: 当insert操作时，该字段拼接insert语句时的策略
    // <p>
    // IGNORED: 直接拼接 insert into table_a(column) values (#{columnProperty});
    // NOT_NULL: insert into table_a(<if test="columnProperty != null">column</if>) values (<if test="columnProperty != null">#{columnProperty}</if>)
    // NOT_EMPTY: insert into table_a(<if test="columnProperty != null and columnProperty!=''">column</if>) values (<if test="columnProperty != null and columnProperty!=''">#{columnProperty}</if>)
    // NOT_EMPTY 如果针对的是非 CharSequence 类型的字段则效果等于 NOT_NULL

    /**
     * 忽略判断
     */
    IGNORED,
    /**
     * 非NULL判断
     */
    NOT_NULL,
    /**
     * 非空判断(只对字符串类型字段,其他类型字段依然为非NULL判断)
     */
    NOT_EMPTY,
    /**
     * 默认的,一般只用于注解里
     * <p>1. 在全局里代表 NOT_NULL</p>
     * <p>2. 在注解里代表 跟随全局</p>
     */
    DEFAULT,
    /**
     * 不加入 SQL
     */
    NEVER
}
