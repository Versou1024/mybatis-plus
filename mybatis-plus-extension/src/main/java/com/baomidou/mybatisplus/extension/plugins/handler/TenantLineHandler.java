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
package com.baomidou.mybatisplus.extension.plugins.handler;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Column;

import java.util.List;

/**
 * 租户处理器（ TenantId 行级 ）
 *
 * @author hubin
 * @since 3.4.0
 */
public interface TenantLineHandler {

    // 命名
    // 多租户:处理器

    // 作用:
    // 多租户是一种软件架构技术，在多用户的环境下，共有同一套系统，并且要注意数据之间的隔离性。
    // 举个实际例子：小编曾经开发过一套支付宝程序，这套程序应用在不同的小程序上，当使用者访问不同，并且进入相对应的小程序页面，小程序则会把用户相关数据传输到小编这里。在传输的时候需要带上小程序标识（租户ID），以便小编将数据进行隔离。
    // 当不同的租户使用同一套程序，这里就需要考虑一个数据隔离的情况。
    // 数据隔离有三种方案：
    //      1、独立数据库：简单来说就是一个租户使用一个数据库，这种数据隔离级别最高，安全性最好，但是提高成本。
    //      2、共享数据库、隔离数据架构：多租户使用同一个数据裤，但是每个租户对应一个Schema(数据库user)。-
    //      3、共享数据库、共享数据架构：使用同一个数据库，同一个Schema，但是在表中增加了租户ID的字段，这种共享数据程度最高，隔离级别最低 -> 就是Mybatis-Plus提供的解决方案

    // 比如:
        //@Slf4j
        //@Component
        //public class PreTenantHandler implements TenantHandler {
        //
        //    @Autowired
        //    private PreTenantConfigProperties configProperties;
        //
        //    /**
        //     * 租户Id
        //     *
        //     * @return
        //     */
        //    @Override
        //    public Expression getTenantId(boolean where) {
        //        //可以通过过滤器从请求中获取对应租户id
        //        Long tenantId = PreTenantContextHolder.getCurrentTenantId();
        //        log.debug("当前租户为{}", tenantId);
        //        if (tenantId == null) {
        //            return new NullValue();
        //        }
        //        return new LongValue(tenantId);
        //    }
        //    /**
        //     * 租户字段名
        //     *
        //     * @return
        //     */
        //    @Override
        //    public String getTenantIdColumn() {
        //        return configProperties.getTenantIdColumn();
        //    }
        //
        //    /**
        //     * 根据表名判断是否进行过滤
        //     * 忽略掉一些表：如租户表（sys_tenant）本身不需要执行这样的处理
        //     *
        //     * @param tableName
        //     * @return
        //     */
        //    @Override
        //    public boolean doTableFilter(String tableName) {
        //        return configProperties.getIgnoreTenantTables().stream().anyMatch((e) -> e.equalsIgnoreCase(tableName));
        //    }
        //}

    /**
     * 获取租户 ID 值表达式，只支持单个 ID 值
     * <p>
     *
     * @return 租户 ID 值表达式
     */
    Expression getTenantId();

    /**
     * 获取租户字段名
     * <p>
     * 默认字段名叫: tenant_id
     *
     * @return 租户字段名
     */
    default String getTenantIdColumn() {
        return "tenant_id";
    }

    /**
     * 根据表名判断是否忽略拼接多租户条件
     * <p>
     * 默认都要进行解析并拼接多租户条件
     *
     * @param tableName 表名
     * @return 是否忽略, true:表示忽略，false:需要解析并拼接多租户条件
     */
    default boolean ignoreTable(String tableName) {
        return false;
    }

    /**
     * 忽略插入租户字段逻辑
     *
     * @param columns        插入字段
     * @param tenantIdColumn 租户 ID 字段
     * @return
     */
    default boolean ignoreInsert(List<Column> columns, String tenantIdColumn) {
        return columns.stream().map(Column::getColumnName).anyMatch(i -> i.equalsIgnoreCase(tenantIdColumn));
    }
}
