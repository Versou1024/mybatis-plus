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

import com.baomidou.mybatisplus.core.toolkit.Assert;
import lombok.Getter;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.session.Configuration;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 分页参数动态化所需 model
 *
 * @author miemie
 * @since 2018-10-31
 */
public class DialectModel {
    // 位于:
    // extension模块下的plugins.pagination中

    // 作用:
    // 分页参数动态化所需 model

    private static final String FIRST_PARAM_NAME = "mybatis_plus_first";
    private static final String SECOND_PARAM_NAME = "mybatis_plus_second";

    // 带有方言特性的分页 sql
    @Getter
    private final String dialectSql;

    // 提供 Configuration
    private Configuration configuration;
    /**
     * 用 List<ParameterMapping> 消费第一个值
     */
    private Consumer<List<ParameterMapping>> firstParamConsumer = i -> {
    };
    /**
     * 用 Map<String, Object> 消费第一个值
     */
    private Consumer<Map<String, Object>> firstParamMapConsumer = i -> {
    };
    /**
     * 用 List<ParameterMapping> 消费第二个值
     */
    private Consumer<List<ParameterMapping>> secondParamConsumer = i -> {
    };
    /**
     * 用 Map<String, Object> 消费第二个值
     */
    private Consumer<Map<String, Object>> secondParamMapConsumer = i -> {
    };

    // 提供 第一个值
    // 在MySQL就是offset
    private final long firstParam;

    // 提供 第二个值
    // 在MySQL就是limit
    private final long secondParam;

    public DialectModel(String dialectSql) {
        this(dialectSql, 0, 0);
    }

    public DialectModel(String dialectSql, long firstParam) {
        this(dialectSql, firstParam, 0);
    }

    public DialectModel(String dialectSql, long firstParam, long secondParam) {
        this.dialectSql = dialectSql;
        this.firstParam = firstParam;
        this.secondParam = secondParam;
    }

    /**
     * 设置消费 List<ParameterMapping> 的方式
     * <p>带下标的</p>
     * <p>mark: 标记一下,暂时没看到哪个数据库的分页方言会存在使用该方法</p>
     *
     * @return this
     */
    @SuppressWarnings("unused")
    public DialectModel setConsumer(boolean isFirstParam, Function<List<ParameterMapping>, Integer> function) {
        if (isFirstParam) {
            this.firstParamConsumer = i -> i.add(function.apply(i), new ParameterMapping
                .Builder(configuration, FIRST_PARAM_NAME, long.class).build());
        } else {
            this.secondParamConsumer = i -> i.add(function.apply(i), new ParameterMapping
                .Builder(configuration, SECOND_PARAM_NAME, long.class).build());
        }
        this.setParamMapConsumer(isFirstParam);
        return this;
    }

    /**
     * 设置消费 List<ParameterMapping> 的方式
     * <p>不带下标的</p>
     *
     * @return this
     */
    public DialectModel setConsumer(boolean isFirstParam) {
        // 设置消费 List  的方式

        // 1.1 isFirstParam 为ture
        if (isFirstParam) {
            // 1.1.1 ❗️❗️❗️
            // 添加一个形参名为"mybatis_plus_first",java类型为Long的形参进去
            this.firstParamConsumer = i -> i.add(new ParameterMapping.Builder(configuration, FIRST_PARAM_NAME, long.class).build());
        }
        // 1.2 isFirstParam 为false
        else {
            // 1.2.1 形参名为"mybatis_plus_second"
            // 添加一个形参名为"mybatis_plus_second",java类型为Long的形参进去
            this.secondParamConsumer = i -> i.add(new ParameterMapping.Builder(configuration, SECOND_PARAM_NAME, long.class).build());
        }
        this.setParamMapConsumer(isFirstParam);
        return this;
    }

    /**
     * 设置消费 List<ParameterMapping> 的方式
     * <p>不带下标的,两个值都有</p>
     *
     * @return this
     */
    public DialectModel setConsumerChain() {
        // 设置消费 List  的方式
        // 不带下标的,两个值都有
        return setConsumer(true).setConsumer(false);
    }

    /**
     * 把内部所有需要消费的都消费掉
     *
     * @param parameterMappings    ParameterMapping 集合
     * @param configuration        Configuration
     * @param additionalParameters additionalParameters map
     */
    public void consumers(List<ParameterMapping> parameterMappings, Configuration configuration,
                          Map<String, Object> additionalParameters) {
        Assert.notNull(configuration, "configuration must notNull !");
        Assert.notNull(parameterMappings, "parameterMappings must notNull !");
        Assert.notNull(additionalParameters, "additionalParameters must notNull !");
        this.configuration = configuration;
        // ❗️❗️❗️
        // 通过联动 -> DialectModel#setConsumerChain() 分别除非的 DialectModel#setParamMapConsumer(true) 与 DialectModel#setConsumer(true)
        // 最终 -> 会向 parameterMappings 以及 additionalParameters 中额外添加以下参数
        // parameterMappings 中 add(new ParameterMapping.Builder(configuration, FIRST_PARAM_NAME, long.class).build()
        // parameterMappings 中 add(new ParameterMapping.Builder(configuration, SECOND_PARAM_NAME, long.class).build()
        // additionalParameters 中 put(FIRST_PARAM_NAME, firstParam)
        // additionalParameters 中 put(SECOND_PARAM_NAME, secondParam)
        this.firstParamConsumer.accept(parameterMappings);
        this.secondParamConsumer.accept(parameterMappings);
        this.firstParamMapConsumer.accept(additionalParameters);
        this.secondParamMapConsumer.accept(additionalParameters);
    }

    /**
     * 设置消费 Map<String, Object> 的方式
     */
    private void setParamMapConsumer(boolean isFirstParam) {
        // 设置消费 Map  的方式

        // 1.1 isFirstParam 为true
        if (isFirstParam) {
            // 1.1.1 以"mybatis_plus_first"为key,以firstParam值为value -> 到Map中 [❗️❗️❗️]
            this.firstParamMapConsumer = i -> i.put(FIRST_PARAM_NAME, firstParam);
        }
        // 1.2 isFirstParam 为false
        else {
            // 1.2.1 以"mybatis_plus_second"为key,以firstParam值为value -> 到Map中 [❗️❗️❗️]
            this.secondParamMapConsumer = i -> i.put(SECOND_PARAM_NAME, secondParam);
        }
    }
}
