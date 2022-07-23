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
package com.baomidou.mybatisplus.core;

import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.exceptions.MybatisPlusException;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.core.toolkit.sql.SqlUtils;
import org.apache.ibatis.builder.IncompleteElementException;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;

import java.util.List;

/**
 * 继承 {@link XMLLanguageDriver} 重装构造函数, 使用自己的 MybatisParameterHandler
 *
 * @author hubin
 * @since 2016-03-11
 */
public class MybatisXMLLanguageDriver extends XMLLanguageDriver {
    // 位于: 直接位于core模块下

    // 作用:
    // 继承XMLLanguageDriver,LanguageDriver能够用来产生 ParameterHandler \ SqlSource \ ResourceSetHandler

    // TODO 下面改动啦 -> 将原本使用的 DefaultParameterHandler 修改为 MybatisParameterHandler [ 额外扩展MetaObjectHandler的自动填充\IdentifyGenerator主键自动注入的功能 ]
    @Override
    public ParameterHandler createParameterHandler(MappedStatement mappedStatement,
                                                   Object parameterObject, BoundSql boundSql) {
        // 使用 MybatisParameterHandler 而不是 ParameterHandler
        return new MybatisParameterHandler(mappedStatement, parameterObject, boundSql);
    }
    // TODO 上面改动啦


    // TODO 下面改动啦
    @Override
    public SqlSource createSqlSource(Configuration configuration, String script, Class<?> parameterType) {
        // 1. 拿到Configuration对应的DbConfig
        GlobalConfig.DbConfig config = GlobalConfigUtils.getDbConfig(configuration);
        // 2. 是否需要解析占位符 [❗️❗️❗️]
        if (config.isReplacePlaceholder()) {
            // 2.1 查找script中的需要填充的数据
            List<String> find = SqlUtils.findPlaceholder(script);
            // 2.2 开始处理占位符
            if (CollectionUtils.isNotEmpty(find)) {
                try {
                    script = SqlUtils.replaceSqlPlaceholder(script, find, config.getEscapeSymbol());
                } catch (MybatisPlusException e) {
                    throw new IncompleteElementException();
                }
            }
        }
        return super.createSqlSource(configuration, script, parameterType);
    }
    // TODO 上面改动啦

}
