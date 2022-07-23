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
package com.baomidou.mybatisplus.core.config;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.core.incrementer.IKeyGenerator;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.baomidou.mybatisplus.core.injector.DefaultSqlInjector;
import com.baomidou.mybatisplus.core.injector.ISqlInjector;
import com.baomidou.mybatisplus.core.mapper.Mapper;
import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.ibatis.session.SqlSessionFactory;

import java.io.Serializable;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * Mybatis 全局缓存
 *
 * @author Caratacus
 * @since 2016-12-06
 */
@Data
@Accessors(chain = true)
@SuppressWarnings("serial")
public class GlobalConfig implements Serializable {
    // 命名:
    // GlobalConfig = MP的全局配置

    // 配置:
    // additional-spring-configuration-metadata.json 的 mybatis-plus.global-config 组
    // 也就是说: GlobalConfig 下的属性都可以通过 mybatis-plus.global-config.xx 就可以修改DbConfig的xx属性
    // 当前也有特殊性的属性:比如metaObjectHandler不可通过 mybatis-plus.global-config.meta-object-handler 指定 -> [请使用@Bean的方式注入至Spring容器.]


    // 起作用:
    // 在 @Bean @ConditionalOnMissingBean 标注的  MybatisPlusAutoConfiguration#sqlSessionFactory(..) 方法中向GlobalConfig注入MetaObjectHandler\IKeyGenerator\ISqlInjector\IdentifierGenerator
    //      GlobalConfig globalConfig = this.properties.getGlobalConfig();
    //      this.getBeanThen(MetaObjectHandler.class, globalConfig::setMetaObjectHandler);
    //      this.getBeansThen(IKeyGenerator.class, i -> globalConfig.getDbConfig().setKeyGenerators(i));
    //      this.getBeanThen(ISqlInjector.class, globalConfig::setSqlInjector);
    //      this.getBeanThen(IdentifierGenerator.class, globalConfig::setIdentifierGenerator);
    // ❗️❗️❗️❗️❗️❗️ -> 同样的我们也需要注意,一旦手动注入了 SqlSessionFactory -> 就会导致上述@Bean标注的方法失效,不会去理会yaml配置,也不会去创建GlobalConfig
    // 也就是说: 一旦手动注入了 SqlSessionFactory, mybatis-plus.global-config 会失效

    // 是否开启Logo -> 默认开启
    private boolean banner = true;

    // 是否初始化SqlRunner -> 默认关闭
    private boolean enableSqlRunner = false;

    // 数据库相关配置 -> 在 GlobalConfigUtils.defaults() 方法中默认为: new GlobalConfig.DbConfig()
    private DbConfig dbConfig;

    // SQL注入器 -> 默认为 DefaultSqlInjector
    // 不可通过: mybatis-plus.global-config.i-sql-injector 指定
    // 请见源码: MybatisPlusAutoConfiguration#sqlSessionFactory() 中的 this.getBeanThen(ISqlInjector.class, globalConfig::setSqlInjector);
    private ISqlInjector sqlInjector = new DefaultSqlInjector();

    // Mapper接口创建的代理对象需要实现的父类 -> 默认为标记接口Mapper
    private Class<?> superMapperClass = Mapper.class;

    // 仅用于缓存 SqlSessionFactory(外部勿进行set,set了也没用)
    // 会在: MybatisSqlSessionFactoryBuilder#build(Configuration)中进行设置哦
    private SqlSessionFactory sqlSessionFactory;

    // 缓存已注入MP的CRUD方法的Mapper接口的全限定类名
    // 即: mapper接口实现了BaseMapper,mp已经将BaseMapper中的inset()等基本CRUD方法给解析注册了 -> 那么这个对应的mapper接口就会被加入到mapperRegistryCache -> 表示已经mapper接口的CRUD方法已经被注册啦
    private Set<String> mapperRegistryCache = new ConcurrentSkipListSet<>();

    // 元对象字段填充控制器
    // 不可通过 mybatis-plus.global-config.meta-object-handler 指定 -> mp 3.0开始废除此属性，请使用@Bean的方式注入至Spring容器
    // 请见源码: MybatisPlusAutoConfiguration#sqlSessionFactory() 中的 this.getBeanThen(MetaObjectHandler.class, globalConfig::setMetaObjectHandler);
    private MetaObjectHandler metaObjectHandler;

    // 主键生成器
    // 不可通过 mybatis-plus.global-config.identifier-generator 指定 -> [请使用@Bean的方式注入至Spring容器.]
    // 请见源码: MybatisPlusAutoConfiguration#sqlSessionFactory() 中的    this.getBeanThen(IdentifierGenerator.class, globalConfig::setIdentifierGenerator);
    private IdentifierGenerator identifierGenerator;

    @Data
    public static class DbConfig {
        // 命名:
        // DbConfig = MP的全局数据库配置

        // 配置:
        // additional-spring-configuration-metadata.json 的 mybatis-plus.global-config.db-config 组
        // 也就是说: DbConfig 下的属性都可以通过 mybatis-plus.global-config.db-config.xx 就可以修改DbConfig的xx属性
        // 当前也有特殊性的属性:比如keyGenerators不可通过 mybatis-plus.global-config.db-config.key-generators 指定 -> [请使用@Bean的方式注入至Spring容器.]

        // 主键类型, 默认为:分配ID (主键类型为number或string
        private IdType idType = IdType.ASSIGN_ID;

        // 所有表名的前缀,默认为空的
        private String tablePrefix;

        private String schema;

        // db字段 format,例: `%s`,对主键无效
        // 主要是 查找出来列名后还需要经过这里的列格式转换
        // 1. @TableField.value属性 > 属性名 column = @TableField.value > 属性名
        // 2. tableInfo.isUnderCamel()开启 -> 驼峰转下划线 column = StringUtils.camelToUnderline(column)
        // 3. dbConfig.isCapitalMode()开启 -> 全部大写模式 column = column.toUpperCase()
        // 4. GlobalConfig.getColumnFormat()非空 -> 格式化模式 column = String.format(columnFormat, column)
        private String columnFormat;

        // entity 的字段(property)的 format,只有在 column as property 这种情况下生效
        // 例: `name:%s` 通过String.format(columnFormat, property)格式化的property="user"最终出来的结果就是 name:user
        // 对主键无效
        private String propertyFormat;

        // 实验性功能,占位符替换,等同于 com.baomidou.mybatisplus.extension.plugins.inner.ReplacePlaceholderInnerInterceptor,
        // 只是这个属于启动时替换,用得地方多会启动慢一点点,不适用于其他的 org.apache.ibatis.scripting.LanguageDriver
        private boolean replacePlaceholder;

        // 转义符
        // 配合 replacePlaceholder 使用时有效
        // 例: " 或 ' 或 `
        private String escapeSymbol;

        // 表名是否使用驼峰转下划线命名,只对表名生效 -> 默认为true
        private boolean tableUnderline = true;

        // 大写命名,对表名和字段名均生效 -> 默认为false
        // 建议不要开启 -> [开启后属于是恶心人]
        private boolean capitalMode = false;

        // 表主键生成器 note: 可注入多个IKeyGenerator
        // 不可通过 mybatis-plus.global-config.db-config.key-generators 指定 -> [请使用@Bean的方式注入至Spring容器.]
        // 请见源码:  MybatisPlusAutoConfiguration#sqlSessionFactory() 中的 this.getBeansThen(IKeyGenerator.class, i -> globalConfig.getDbConfig().setKeyGenerators(i));
        private List<IKeyGenerator> keyGenerators;

        // 逻辑删除全局属性名
        // 前提: 没有@TableLogic注解
        private String logicDeleteField;

        // 逻辑删除全局值（默认 1、表示已删除）
        // 同下:
        private String logicDeleteValue = "1";

        // 逻辑未删除全局值（默认 0、表示未删除）
        // 生效的前提:
        // 1. 逻辑删除字段没有使用@TableLogic
        // 2. 逻辑删除字段使用@TableLogic,但没有指定@TableLogic.value()的值
        private String logicNotDeleteValue = "0";

        // 字段验证策略之 inert
        // 默认为NOT_NULL,即字段只要非null值,在MP注入的方法中,属于insert操作而言,就可以作为接下来插入的值
        private FieldStrategy insertStrategy = FieldStrategy.NOT_NULL;

        // 字段验证策略之 update
        // 默认为NOT_NULL,即字段只要非null值,在MP注入的方法中,属于update操作而言,就可以作为接下来更新的值
        private FieldStrategy updateStrategy = FieldStrategy.NOT_NULL;

        // 字段验证策略之 select
        // 默认为NOT_NULL,即字段只要非null值,在MP注入的方法中,属于update操作而言,就可以作为接下来更新的值
        @Deprecated
        private FieldStrategy selectStrategy;

        // 字段验证策略之 where
        // 默认为NOT_NULL,即字段只要非null值,在MP注入的方法中,属于select操作而言,就可以作为接下来where中的条件值
        private FieldStrategy whereStrategy = FieldStrategy.NOT_NULL;

        /**
         * 重写whereStrategy的get方法，适配低版本：
         * - 如果用户自定义了selectStrategy则用用户自定义的，
         * - 后续版本移除selectStrategy后，直接删除该方法即可。
         *
         * @return 字段作为查询条件时的验证策略
         * @since 3.4.4
         */
        public FieldStrategy getWhereStrategy() {
            return selectStrategy == null ? whereStrategy : selectStrategy;
        }
    }
}
