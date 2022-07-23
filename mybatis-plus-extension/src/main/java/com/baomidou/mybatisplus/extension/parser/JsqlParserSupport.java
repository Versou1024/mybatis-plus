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
package com.baomidou.mybatisplus.extension.parser;

import com.baomidou.mybatisplus.core.toolkit.ExceptionUtils;
import com.baomidou.mybatisplus.core.toolkit.StringPool;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.update.Update;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;

/**
 * https://github.com/JSQLParser/JSqlParser
 *
 * @author miemie
 * @since 2020-06-22
 */
public abstract class JsqlParserSupport {

    /**
     * 日志
     */
    protected final Log logger = LogFactory.getLog(this.getClass());

    public String parserSingle(String sql, Object obj) {
        // ❗️❗️❗️ ->
        // 原始Sql: BoundSql#getSql(..)
        if (logger.isDebugEnabled()) {
            logger.debug("original SQL: " + sql);
        }
        try {
            // 1. 解析为statement
            Statement statement = CCJSqlParserUtil.parse(sql);
            return processParser(statement, 0, sql, obj);
        } catch (JSQLParserException e) {
            throw ExceptionUtils.mpe("Failed to process, Error SQL: %s", e.getCause(), sql);
        }
    }

    public String parserMulti(String sql, Object obj) {
        if (logger.isDebugEnabled()) {
            logger.debug("original SQL: " + sql);
        }
        try {
            // fixed github pull/295
            StringBuilder sb = new StringBuilder();
            // 1. 不同于 parserSingle(..) 这里解析后返回的是Statements
            Statements statements = CCJSqlParserUtil.parseStatements(sql);
            int i = 0;
            // 2. 遍历Statements
            for (Statement statement : statements.getStatements()) {
                if (i > 0) {
                    sb.append(StringPool.SEMICOLON);
                }
                sb.append(processParser(statement, i, sql, obj));
                i++;
            }
            return sb.toString();
        } catch (JSQLParserException e) {
            throw ExceptionUtils.mpe("Failed to process, Error SQL: %s", e.getCause(), sql);
        }
    }

    /**
     * 执行 SQL 解析
     *
     * @param statement JsqlParser Statement
     * @return sql
     */
    protected String processParser(Statement statement, int index, String sql, Object obj) {
        // 执行 SQL 解析

        if (logger.isDebugEnabled()) {
            logger.debug("SQL to parse, SQL: " + sql);
        }
        // 1. 插入
        if (statement instanceof Insert) {
            this.processInsert((Insert) statement, index, sql, obj);
        }
        // 2. 查询
        else if (statement instanceof Select) {
            this.processSelect((Select) statement, index, sql, obj);
        }
        // 3. 更新
        else if (statement instanceof Update) {
            this.processUpdate((Update) statement, index, sql, obj);
        }
        // 4. 删除
        else if (statement instanceof Delete) {
            this.processDelete((Delete) statement, index, sql, obj);
        }
        // 5. statement在上面的 processXxxx(..) 被处理后,重新去获取sql值哦
        sql = statement.toString();
        if (logger.isDebugEnabled()) {
            logger.debug("parse the finished SQL: " + sql);
        }
        // 6. 将处理后得到的新sql返回
        return sql;
    }

    // ❗️❗️❗️
    // 以下:四个方法实现类有需要的时候就需要重写哦

    /**
     * 新增
     */
    protected void processInsert(Insert insert, int index, String sql, Object obj) {
        throw new UnsupportedOperationException();
    }

    /**
     * 删除
     */
    protected void processDelete(Delete delete, int index, String sql, Object obj) {
        throw new UnsupportedOperationException();
    }

    /**
     * 更新
     */
    protected void processUpdate(Update update, int index, String sql, Object obj) {
        throw new UnsupportedOperationException();
    }

    /**
     * 查询
     */
    protected void processSelect(Select select, int index, String sql, Object obj) {
        throw new UnsupportedOperationException();
    }
}
