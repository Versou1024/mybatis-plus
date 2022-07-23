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
 * MYSQL 数据库分页语句组装实现
 *
 * @author hubin
 * @since 2016-01-23
 */
public class MySqlDialect implements IDialect {
    // 对于IDialect的多个实现类,我们关注我们经常使用的: MySqlDialect 即可哦

    @Override
    public DialectModel buildPaginationSql(String originalSql, long offset, long limit) {
        // 1. 构建 sql = originalSql + "LIMIT ?"
        StringBuilder sql = new StringBuilder(originalSql).append(" LIMIT ").append(FIRST_MARK);
        // 2.1 offset非0,即需要便宜量
        if (offset != 0L) {
            // 2.1.1 sql = originalSql + "LIMIT ?,?"
            sql.append(StringPool.COMMA).append(SECOND_MARK);
            // 2.1.2 构建DialectModel
            // DialectModel#setConsumerChain(..) 0> 设置List和Map中传入的offset和limit的消费方式哦
            // ❗️❗️❗️ 因此: MySqlDialect 创建出来的 DialectModel.dialectSql = originalSql + "LIMIT ?,?"
            return new DialectModel(sql.toString(), offset, limit).setConsumerChain();
        }
        // 2.2 不需要偏移量时,直接指定limit即可哦
        else {
            return new DialectModel(sql.toString(), limit).setConsumer(true);
        }
    }
}
