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

import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.baomidou.mybatisplus.core.toolkit.Assert;
import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.baomidou.mybatisplus.core.toolkit.StringPool;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.parser.JsqlParserSupport;
import net.sf.jsqlparser.expression.BinaryExpression;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.IsNullExpression;
import net.sf.jsqlparser.expression.operators.relational.NotEqualsTo;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.update.Update;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;

import java.sql.Connection;

/**
 * 攻击 SQL 阻断解析器,防止全表更新与删除
 *
 * @author hubin
 * @since 3.4.0
 */
public class BlockAttackInnerInterceptor extends JsqlParserSupport implements InnerInterceptor {
    // 位于: extension模块plugins.inner内部插件包下

    // 目的: 攻击 SQL 阻断解析器,防止全表更新与删除

    @Override
    public void beforePrepare(StatementHandler sh, Connection connection, Integer transactionTimeout) {
        PluginUtils.MPStatementHandler handler = PluginUtils.mpStatementHandler(sh);
        MappedStatement ms = handler.mappedStatement();
        SqlCommandType sct = ms.getSqlCommandType();
        // 1. 主要是防止: UPDATE 和 DELETE 操作
        if (sct == SqlCommandType.UPDATE || sct == SqlCommandType.DELETE) {
            // 1.1 是否@InterceptorIgnore要求忽略BlockAttackInnerInterceptor拦截器,是的话,立即return
            if (InterceptorIgnoreHelper.willIgnoreBlockAttack(ms.getId())) return;
            // 1.2 拿到BoundSql -> 立即开始解析
            BoundSql boundSql = handler.boundSql();
            parserMulti(boundSql.getSql(), null);
        }
    }

    @Override
    protected void processDelete(Delete delete, int index, String sql, Object obj) {
        // 1. 检查Where -> 传入 表名\where条件\异常信息
        this.checkWhere(delete.getTable().getName(), delete.getWhere(), "Prohibition of full table deletion");
    }

    @Override
    protected void processUpdate(Update update, int index, String sql, Object obj) {
        // 1. 检查Where -> 传入 表名\where条件\异常信息
        this.checkWhere(update.getTable().getName(), update.getWhere(), "Prohibition of table update operation");
    }

    protected void checkWhere(String tableName, Expression where, String ex) {
        // this.fullMatch(..) 为 true 抛出异常
        Assert.isFalse(this.fullMatch(where, this.getTableLogicField(tableName)), ex);
    }

    private boolean fullMatch(Expression where, String logicField) {
        // 1. Update或delete操作没有Where关键词 -> 直接返回true -> 抛出异常
        if (where == null) {
            return true;
        }
        // 2. 不存在逻辑删除字段
        if (StringUtils.isNotBlank(logicField)) {
            // 2.1 二元操作符 -> 第一次调用fullMatch(..)时只要where非空 -> 可以 and or in 等等二元操作符号,也可以是直接的 = != 等等请款
            if (where instanceof BinaryExpression) {
                // 例如
                // where logic_column = 1                   binaryExpression.getLeftExpression() 就是 logic_column                满足
                // where 2 = logic_column                   binaryExpression.getLeftExpression() 就是 2                           满足
                // where logic_column = 2 and name = "11"   binaryExpression.getLeftExpression() 就是 "logic_column = 2" 表达式    不满足   BinaryExpression 就是 AndExpression
                BinaryExpression binaryExpression = (BinaryExpression) where;
                // 2.1.1 检查二元操作符的左侧或者右侧是否为逻辑删除字段 -> 是的话,返回true -> 抛出异常
                if (StringUtils.equals(binaryExpression.getLeftExpression().toString(), logicField) || StringUtils.equals(binaryExpression.getRightExpression().toString(), logicField)) {
                    return true;
                }
            }

            // 2.2 IS NULL -> 第一次调用fullMatch(..)时只要where非空 -> 可以 column is null 即可
            if (where instanceof IsNullExpression) {
                IsNullExpression binaryExpression = (IsNullExpression) where;
                // 2.2.1 检查column is null 的左侧column是否为逻辑删除字段 -> 是的话,返回true -> 抛出异常
                if (StringUtils.equals(binaryExpression.getLeftExpression().toString(), logicField)) {
                    return true;
                }
            }
        }
        // 其余情况

        if (where instanceof EqualsTo) {
            // example: 1=1
            EqualsTo equalsTo = (EqualsTo) where;
            return StringUtils.equals(equalsTo.getLeftExpression().toString(), equalsTo.getRightExpression().toString());
        } else if (where instanceof NotEqualsTo) {
            // example: 1 != 2
            NotEqualsTo notEqualsTo = (NotEqualsTo) where;
            return !StringUtils.equals(notEqualsTo.getLeftExpression().toString(), notEqualsTo.getRightExpression().toString());
        } else if (where instanceof OrExpression) {
            // OR 继续遍历
            OrExpression orExpression = (OrExpression) where;
            return fullMatch(orExpression.getLeftExpression(), logicField) || fullMatch(orExpression.getRightExpression(), logicField);
        } else if (where instanceof AndExpression) {
            // AND 继续遍历
            AndExpression andExpression = (AndExpression) where;
            return fullMatch(andExpression.getLeftExpression(), logicField) && fullMatch(andExpression.getRightExpression(), logicField);
        } else if (where instanceof Parenthesis) {
            // example: (1 = 1)
            Parenthesis parenthesis = (Parenthesis) where;
            return fullMatch(parenthesis.getExpression(), logicField);
        }

        return false;
    }

    /**
     * 获取表名中的逻辑删除字段
     *
     * @param tableName 表名
     * @return 逻辑删除字段
     */
    private String getTableLogicField(String tableName) {
        // ❗️❗️❗️

        if (StringUtils.isBlank(tableName)) {
            return StringPool.EMPTY;
        }

        // 1. 根据tableName表名获取TableInfo
        TableInfo tableInfo = TableInfoHelper.getTableInfo(tableName);
        // 2. 检查tableInfo中是否存在逻辑删除字段
        if (tableInfo == null || !tableInfo.isWithLogicDelete() || tableInfo.getLogicDeleteFieldInfo() == null) {
            return StringPool.EMPTY;
        }
        // 3. 返回逻辑删除字段名
        return tableInfo.getLogicDeleteFieldInfo().getColumn();
    }
}
