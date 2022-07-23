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
import com.baomidou.mybatisplus.core.incrementer.DefaultIdentifierGenerator;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.baomidou.mybatisplus.core.injector.SqlRunnerInjector;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.apache.ibatis.exceptions.ExceptionFactory;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.Properties;

/**
 * 重写SqlSessionFactoryBuilder
 *
 * @author nieqiurong 2019/2/23.
 */
public class MybatisSqlSessionFactoryBuilder extends SqlSessionFactoryBuilder {

    // MybatisSqlSessionFactoryBuilder -> 构建SqlSessionFactory -> SqlSessionFactory构建SqlSession实例
    // 传递进来的Reader/InputStream都是针对Mybatis.xml
    // 重写以下两个方法的目的: 替换为使用MybatisXMLConfigBuilder -> 两个方法并没有被 mybatis-plus-boot 项目自己使用到哦

    @SuppressWarnings("Duplicates")
    @Override
    public SqlSessionFactory build(Reader reader, String environment, Properties properties) {
        try {
            //TODO 这里换成 MybatisXMLConfigBuilder 而不是 XMLConfigBuilder
            MybatisXMLConfigBuilder parser = new MybatisXMLConfigBuilder(reader, environment, properties);
            return build(parser.parse());
        } catch (Exception e) {
            throw ExceptionFactory.wrapException("Error building SqlSession.", e);
        } finally {
            ErrorContext.instance().reset();
            try {
                reader.close();
            } catch (IOException e) {
                // Intentionally ignore. Prefer previous error.
            }
        }
    }

    @SuppressWarnings("Duplicates")
    @Override
    public SqlSessionFactory build(InputStream inputStream, String environment, Properties properties) {
        try {
            //TODO 这里换成 MybatisXMLConfigBuilder 而不是 XMLConfigBuilder
            MybatisXMLConfigBuilder parser = new MybatisXMLConfigBuilder(inputStream, environment, properties);
            return build(parser.parse());
        } catch (Exception e) {
            throw ExceptionFactory.wrapException("Error building SqlSession.", e);
        } finally {
            ErrorContext.instance().reset();
            try {
                inputStream.close();
            } catch (IOException e) {
                // Intentionally ignore. Prefer previous error.
            }
        }
    }


    // ❗️❗️❗️
    // 当前方法会被 MybatisSqlSessionFactoryBean#getObject() 方法最终调用到哦
    @Override
    public SqlSessionFactory build(Configuration configuration) {
        // 1. 获取 configuration 对应 GlobalConfig
        GlobalConfig globalConfig = GlobalConfigUtils.getGlobalConfig(configuration);

        // 2.1 当GlobalConfig.getIdentifierGenerator()为空 -> 即用户没有向ioc容器注入IdentifierGenerator时
        // 使用默认的 DefaultIdentifierGenerator [id生成器]
        final IdentifierGenerator identifierGenerator;
        if (null == globalConfig.getIdentifierGenerator()) {
            identifierGenerator = new DefaultIdentifierGenerator();
            globalConfig.setIdentifierGenerator(identifierGenerator);
        } else {
            identifierGenerator = globalConfig.getIdentifierGenerator();
        }

        // 2.2 向IdWorker设置id生成器
        IdWorker.setIdentifierGenerator(identifierGenerator);

        // 2.3 默认关闭的 SqlRunner
        if (globalConfig.isEnableSqlRunner()) {
            new SqlRunnerInjector().inject(configuration);
        }

        // 3. 调用  超类 SqlSessionFactoryBuilder.build(Configuration)
        SqlSessionFactory sqlSessionFactory = super.build(configuration);

        // 4. 缓存 sqlSessionFactory
        globalConfig.setSqlSessionFactory(sqlSessionFactory);

        return sqlSessionFactory;
    }
}
