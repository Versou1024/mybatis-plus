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
package com.baomidou.mybatisplus.extension.plugins.inner;

import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.baomidou.mybatisplus.core.toolkit.ExceptionUtils;
import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.baomidou.mybatisplus.core.toolkit.TableNameParser;
import com.baomidou.mybatisplus.extension.plugins.handler.TableNameHandler;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 动态表名
 *
 * @author jobob
 * @since 3.4.0
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuppressWarnings({"rawtypes"})
public class DynamicTableNameInnerInterceptor implements InnerInterceptor {

    // 作用:
    // 描述： Sql执行时，动态的修改表名
    // 简单业务场景： 日志或者其他数据量大的表，通过日期进行了水平分表，需要通过日期参数，动态的查询数据。
    private Runnable hook;

    public void setHook(Runnable hook) {
        this.hook = hook;
    }

    /**
     * 表名处理器，是否处理表名的情况都在该处理器中自行判断
     */
    private TableNameHandler tableNameHandler;
    // 表名处理器，是否处理表名的情况都在该处理器中自行判断

    @Override
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) throws SQLException {
        PluginUtils.MPBoundSql mpBs = PluginUtils.mpBoundSql(boundSql);
        if (!InterceptorIgnoreHelper.willIgnoreDynamicTableName(ms.getId())) {
            // 非忽略执行
            mpBs.sql(this.changeTable(mpBs.sql()));
        }
    }

    @Override
    public void beforePrepare(StatementHandler sh, Connection connection, Integer transactionTimeout) {
        PluginUtils.MPStatementHandler mpSh = PluginUtils.mpStatementHandler(sh);
        MappedStatement ms = mpSh.mappedStatement();
        SqlCommandType sct = ms.getSqlCommandType();
        if (sct == SqlCommandType.INSERT || sct == SqlCommandType.UPDATE || sct == SqlCommandType.DELETE) {
            if (!InterceptorIgnoreHelper.willIgnoreDynamicTableName(ms.getId())) {
                // 非忽略执行
                PluginUtils.MPBoundSql mpBs = mpSh.mPBoundSql();
                mpBs.sql(this.changeTable(mpBs.sql()));
            }
        }
    }

    protected String changeTable(String sql) {
        // ❗️❗️❗️ -> slq是BoundSql.getSql(..)的结果

        ExceptionUtils.throwMpe(null == tableNameHandler, "Please implement TableNameHandler processing logic");
        // 1. 使用 TableNameParser 解析 sql
        TableNameParser parser = new TableNameParser(sql);
        List<TableNameParser.SqlToken> names = new ArrayList<>();
        // 2. 找到当前sql使用的表名加入到names中
        parser.accept(names::add);
        StringBuilder builder = new StringBuilder();
        int last = 0;
        // 3. 遍历sql中所有的表名
        // 举例:
        // 比如 select id,name,age form table1,table2 where age > 13
        // names 就是 table1 和 table2
        // 第一次遍历:
        //      last = 0,start =24
        // builder.append(sql, last, start) -> 就是追加的 select id,name,age form
        // builder.append(tableNameHandler.dynamicTableName(sql, "table1")) -> tableNameHandler只将table1更改为 modify_table1
        // ...
        // 最终 select id,name,age form modify_table1,table2 where age > 13
        for (TableNameParser.SqlToken name : names) {
            // 3.1 拿到表名在sql中的起始位置
            int start = name.getStart();
            if (start != last) {
                // 3.1.1 截取sql从上一次到位置,到本次表名前的位置
                builder.append(sql, last, start);
                // 3.1.2 ❗️❗️❗️ 追加动态的表名
                builder.append(tableNameHandler.dynamicTableName(sql, name.getValue()));
            }
            // 3.2 更改last为上次表名的位置
            last = name.getEnd();
        }


        if (last != sql.length()) {
            builder.append(sql.substring(last));
        }
        if (hook != null) {
            hook.run();
        }
        return builder.toString();
    }
}
