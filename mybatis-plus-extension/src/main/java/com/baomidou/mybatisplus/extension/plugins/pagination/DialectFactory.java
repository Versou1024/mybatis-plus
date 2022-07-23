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
package com.baomidou.mybatisplus.extension.plugins.pagination;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.toolkit.ExceptionUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.dialects.*;

import java.util.EnumMap;
import java.util.Map;

/**
 * 分页方言工厂类
 *
 * @author hubin
 * @since 2016-01-23
 */
public class DialectFactory {
    // 位于: extension.plugins.pagination

    // 作用:
    // 根据DbType生成对应的Dialect方言出来

    // 缓存 以DbType为key,以IDialect方言为value
    private static final Map<DbType, IDialect> DIALECT_ENUM_MAP = new EnumMap<>(DbType.class);

    public static IDialect getDialect(DbType dbType) {
        // note: ❗️❗️❗️
        // 默认情况下: 大部分的方言都是内置好了的

        // 1. 尝试缓存命中
        IDialect dialect = DIALECT_ENUM_MAP.get(dbType);
        if (null == dialect) {
            // 2.1 缓存命中失败,检查DbType是否为DbType.OTHER,是的话,抛出异常
            if (dbType == DbType.OTHER) {
                ExceptionUtils.mpe("%s database not supported.", dbType.getDb());
            }
            // 3.1mybatis-plus 内置的DbTyoe值 -> MySqlDialect mysql的方言
            else if (dbType == DbType.MYSQL
                || dbType == DbType.MARIADB
                || dbType == DbType.GBASE
                || dbType == DbType.OSCAR
                || dbType == DbType.XU_GU
                || dbType == DbType.CLICK_HOUSE
                || dbType == DbType.OCEAN_BASE
                || dbType == DbType.CUBRID
                || dbType == DbType.GOLDILOCKS
                || dbType == DbType.CSIIDB) {
                dialect = new MySqlDialect();
            }
            // 3.2  mybatis-plus 内置的DbTyoe值 -> OracleDialect oracle的方言
            else if (dbType == DbType.ORACLE
                || dbType == DbType.DM
                || dbType == DbType.GAUSS) {
                dialect = new OracleDialect();
            }
            // .......... 忽略~~
            // postgresql same type
            else if (dbType == DbType.POSTGRE_SQL
                || dbType == DbType.H2
                || dbType == DbType.SQLITE
                || dbType == DbType.HSQL
                || dbType == DbType.KINGBASE_ES
                || dbType == DbType.PHOENIX
                || dbType == DbType.SAP_HANA
                || dbType == DbType.IMPALA) {
                dialect = new PostgreDialect();
            } else if (dbType == DbType.HIGH_GO) {
                dialect = new HighGoDialect();
            } else if (dbType == DbType.ORACLE_12C) {
                dialect = new Oracle12cDialect();
            } else if (dbType == DbType.DB2) {
                dialect = new DB2Dialect();
            } else if (dbType == DbType.SQL_SERVER2005) {
                dialect = new SQLServer2005Dialect();
            } else if (dbType == DbType.SQL_SERVER) {
                dialect = new SQLServerDialect();
            } else if (dbType == DbType.SYBASE) {
                dialect = new SybaseDialect();
            } else if (dbType == DbType.GBASEDBT){
                dialect = new GBasedbtDialect();
            }
            DIALECT_ENUM_MAP.put(dbType, dialect);
        }
        return dialect;
    }
}
