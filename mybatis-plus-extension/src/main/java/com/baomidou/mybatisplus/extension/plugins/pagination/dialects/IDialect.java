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
package com.baomidou.mybatisplus.extension.plugins.pagination.dialects;

import com.baomidou.mybatisplus.core.toolkit.StringPool;
import com.baomidou.mybatisplus.extension.plugins.pagination.DialectModel;

/**
 * 数据库 分页语句组装接口
 *
 * @author hubin
 * @since 2016-01-23
 */
public interface IDialect {
    // 位于:
    // extension模块下的plugins.pagination.dialects方法包下

    // 作用:
    // 组装分页语句

    // 实现类: 基于各种数据库的分页查询要求,构建不同的XxxDialect分页语句组装对象
    //      DB2Dialect
    //      GBasedbtDialect
    //      MySqlDialect
    //      .. . 等等

    // 这俩没什么特殊意义 只是为了实现类方便使用,以及区分分页 sql 的参数
    String FIRST_MARK = StringPool.QUESTION_MARK;
    String SECOND_MARK = StringPool.QUESTION_MARK;

    /**
     * 组装分页语句
     *
     * @param originalSql 原始语句
     * @param offset      偏移量
     * @param limit       界限
     * @return 分页模型
     */
    DialectModel buildPaginationSql(String originalSql, long offset, long limit);
    // 组装分页语句 -> 因此需要传入原来的originalSql以及offset和limit的值
}
