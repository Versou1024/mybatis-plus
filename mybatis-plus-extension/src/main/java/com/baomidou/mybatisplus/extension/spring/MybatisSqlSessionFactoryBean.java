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
package com.baomidou.mybatisplus.extension.spring;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisPlusVersion;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.MybatisXMLConfigBuilder;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.exceptions.MybatisPlusException;
import com.baomidou.mybatisplus.core.handlers.MybatisEnumTypeHandler;
import com.baomidou.mybatisplus.core.toolkit.Assert;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.core.toolkit.StringPool;
import com.baomidou.mybatisplus.extension.toolkit.SqlHelper;
import com.baomidou.mybatisplus.extension.toolkit.SqlRunner;
import lombok.Setter;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.io.VFS;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;
import org.apache.ibatis.scripting.LanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.mybatis.logging.Logger;
import org.mybatis.logging.LoggerFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.framework.AbstractSingletonProxyFactoryBean;
import org.springframework.aop.framework.autoproxy.AbstractAutoProxyCreator;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor;
import org.springframework.beans.factory.annotation.InitDestroyAnnotationBeanPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;
import org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.NestedIOException;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.type.ClassMetadata;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.jca.context.BootstrapContextAware;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.util.ClassUtils;

import javax.annotation.PreDestroy;
import javax.sql.DataSource;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;

import static org.springframework.util.Assert.notNull;
import static org.springframework.util.Assert.state;
import static org.springframework.util.ObjectUtils.isEmpty;
import static org.springframework.util.StringUtils.hasLength;
import static org.springframework.util.StringUtils.tokenizeToStringArray;

/**
 * 拷贝类 {@link SqlSessionFactoryBean} 修改方法 buildSqlSessionFactory() 加载自定义
 * <p> MybatisXmlConfigBuilder </p>
 * <p> 移除 sqlSessionFactoryBuilder 属性,强制使用 `new MybatisSqlSessionFactoryBuilder()` </p>
 * <p> 移除 environment 属性,强制使用 `MybatisSqlSessionFactoryBean.class.getSimpleName()` </p>
 *
 * @author hubin
 * @since 2017-01-04
 */
public class MybatisSqlSessionFactoryBean implements FactoryBean<SqlSessionFactory>, InitializingBean, ApplicationListener<ApplicationEvent> {
    // 位于: com.baomidou.mybatisplus.extension.spring = extension项目的spring包

    // 命名:
    // MybatisSqlSession FactoryBean = 用来输出MybatisSqlSession的FactoryBean

    // 核心: ❗️❗️❗️

    // 作用:
    // 重点->实现了FactoryBean<SqlSessionFactory>, InitializingBean, ApplicationListener<ApplicationEvent>三个接口

    private static final Logger LOGGER = LoggerFactory.getLogger(MybatisSqlSessionFactoryBean.class);

    private static final ResourcePatternResolver RESOURCE_PATTERN_RESOLVER = new PathMatchingResourcePatternResolver();
    private static final MetadataReaderFactory METADATA_READER_FACTORY = new CachingMetadataReaderFactory();

    // Mybatis.xml文件的资源
    // 从 MybatisPlusProperties.configLocation获取 [99%的情况用户不会设置这个值,因此为null]
    private Resource configLocation;

    // TODO: 使用 MybatisConfiguration
    private MybatisConfiguration configuration;

    // 设置MyBatis映射器文件即mapper.xml文件的位置，这些文件将在运行时合并到SqlSessionFactory配置中。
    // ❗️❗️❗️ 这是在MyBatis.xml配置文件中指定“<sqlMapper>”标签的替代方法
    // 对于手动注入 MybatisSqlSessionFactoryBean -> 可以通过 MybatisSqlSessionFactoryBean.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/*.xml"))也是可以的哦
    private Resource[] mapperLocations;

    // 设置DataSource
    private DataSource dataSource;

    // Mybatis的TransactionFactory接口
    private TransactionFactory transactionFactory;

    // 全局的配置属性 -> 最终会写入到 MybatisConfiguration.variables变量中去
    private Properties configurationProperties;

    private SqlSessionFactory sqlSessionFactory;

    // 如果为 true，则对 Configuration 进行最后检查，以确保所有映射语句都已完全加载，并且没有任何待解决的语句。默认为假
    private boolean failFast;

    // mybatis的插件
    // -> mybatis-plus 对于 Interceptor 只有一个实现, 那就是 MybatisPlusInterceptor
    private Interceptor[] plugins;

    // 设置类型处理程序TypeHandler
    // ❗️❗️❗️它们必须使用@MappedTypes和可选的@MappedJdbcTypes进行标注
    private TypeHandler<?>[] typeHandlers;

    // 用于搜索类型处理程序TypeHandler的包。
    // 从 2.0.1 开始，允许指定通配符，例如com.example.*.typeHandler 。
    private String typeHandlersPackage;

    private Class<?>[] typeAliases;

    // 用于搜索类型别名的包。
    private String typeAliasesPackage;

    // 域对象必须扩展的超类才能创建类型别名。如果没有配置要扫描的包，则无效。
    private Class<?> typeAliasesSuperType;

    // 脚本的LanguageDriver
    private LanguageDriver[] scriptingLanguageDrivers;

    private Class<? extends LanguageDriver> defaultScriptingLanguageDriver;

    private DatabaseIdProvider databaseIdProvider;

    // VFS: 一个方便的查找服务器资源的类
    private Class<? extends VFS> vfs;

    private Cache cache;

    private ObjectFactory objectFactory;

    private ObjectWrapperFactory objectWrapperFactory;

    // 自定义枚举包
    // 即扫描@EnumValue或检查IEnum接口的枚举
    @Setter
    private String typeEnumsPackage;

    // TODO 自定义全局配置
    @Setter
    private GlobalConfig globalConfig;


    // 无构造器

    /**
     * Sets the ObjectFactory.
     *
     * @param objectFactory a custom ObjectFactory
     * @since 1.1.2
     */
    public void setObjectFactory(ObjectFactory objectFactory) {
        this.objectFactory = objectFactory;
    }

    /**
     * Sets the ObjectWrapperFactory.
     *
     * @param objectWrapperFactory a specified ObjectWrapperFactory
     * @since 1.1.2
     */
    public void setObjectWrapperFactory(ObjectWrapperFactory objectWrapperFactory) {
        this.objectWrapperFactory = objectWrapperFactory;
    }

    /**
     * Gets the DatabaseIdProvider
     *
     * @return a specified DatabaseIdProvider
     * @since 1.1.0
     */
    public DatabaseIdProvider getDatabaseIdProvider() {
        return databaseIdProvider;
    }

    /**
     * Sets the DatabaseIdProvider. As of version 1.2.2 this variable is not initialized by default.
     *
     * @param databaseIdProvider a DatabaseIdProvider
     * @since 1.1.0
     */
    public void setDatabaseIdProvider(DatabaseIdProvider databaseIdProvider) {
        this.databaseIdProvider = databaseIdProvider;
    }

    /**
     * Gets the VFS.
     *
     * @return a specified VFS
     */
    public Class<? extends VFS> getVfs() {
        return this.vfs;
    }

    /**
     * Sets the VFS.
     *
     * @param vfs a VFS
     */
    public void setVfs(Class<? extends VFS> vfs) {
        this.vfs = vfs;
    }

    /**
     * Gets the Cache.
     *
     * @return a specified Cache
     */
    public Cache getCache() {
        return this.cache;
    }

    /**
     * Sets the Cache.
     *
     * @param cache a Cache
     */
    public void setCache(Cache cache) {
        this.cache = cache;
    }

    /**
     * Mybatis plugin list.
     *
     * @param plugins list of plugins
     * @since 1.0.1
     */
    public void setPlugins(Interceptor... plugins) {
        this.plugins = plugins;
    }

    /**
     * Packages to search for type aliases.
     *
     * <p>
     * Since 2.0.1, allow to specify a wildcard such as {@code com.example.*.model}.
     *
     * @param typeAliasesPackage package to scan for domain objects
     * @since 1.0.1
     */
    public void setTypeAliasesPackage(String typeAliasesPackage) {
        this.typeAliasesPackage = typeAliasesPackage;
    }

    /**
     * Super class which domain objects have to extend to have a type alias created. No effect if there is no package to
     * scan configured.
     *
     * @param typeAliasesSuperType super class for domain objects
     * @since 1.1.2
     */
    public void setTypeAliasesSuperType(Class<?> typeAliasesSuperType) {
        this.typeAliasesSuperType = typeAliasesSuperType;
    }

    /**
     * Packages to search for type handlers.
     *
     * <p>
     * Since 2.0.1, allow to specify a wildcard such as {@code com.example.*.typehandler}.
     *
     * @param typeHandlersPackage package to scan for type handlers
     * @since 1.0.1
     */
    public void setTypeHandlersPackage(String typeHandlersPackage) {
        this.typeHandlersPackage = typeHandlersPackage;
    }

    /**
     * Set type handlers. They must be annotated with {@code MappedTypes} and optionally with {@code MappedJdbcTypes}
     *
     * @param typeHandlers Type handler list
     * @since 1.0.1
     */
    public void setTypeHandlers(TypeHandler<?>... typeHandlers) {
        this.typeHandlers = typeHandlers;
    }

    /**
     * List of type aliases to register. They can be annotated with {@code Alias}
     *
     * @param typeAliases Type aliases list
     * @since 1.0.1
     */
    public void setTypeAliases(Class<?>... typeAliases) {
        this.typeAliases = typeAliases;
    }

    /**
     * If true, a final check is done on Configuration to assure that all mapped statements are fully loaded and there is
     * no one still pending to resolve includes. Defaults to false.
     *
     * @param failFast enable failFast
     * @since 1.0.1
     */
    public void setFailFast(boolean failFast) {
        this.failFast = failFast;
    }

    /**
     * Set the location of the MyBatis {@code SqlSessionFactory} config file. A typical value is
     * "WEB-INF/mybatis-configuration.xml".
     *
     * @param configLocation a location the MyBatis config file
     */
    public void setConfigLocation(Resource configLocation) {
        this.configLocation = configLocation;
    }

    /**
     * Set a customized MyBatis configuration.
     * TODO 这里的入参使用 MybatisConfiguration 而不是 Configuration
     *
     * @param configuration MyBatis configuration
     * @since 1.3.0
     */
    public void setConfiguration(MybatisConfiguration configuration) {
        this.configuration = configuration;
    }

    public MybatisConfiguration getConfiguration() {
        return this.configuration;
    }

    /**
     * Set locations of MyBatis mapper files that are going to be merged into the {@code SqlSessionFactory} configuration
     * at runtime.
     * <p>
     * This is an alternative to specifying "&lt;sqlmapper&gt;" entries in an MyBatis config file. This property being
     * based on Spring's resource abstraction also allows for specifying resource patterns here: e.g.
     * "classpath*:sqlmap/*-mapper.xml".
     *
     * @param mapperLocations location of MyBatis mapper files
     */
    public void setMapperLocations(Resource... mapperLocations) {
        this.mapperLocations = mapperLocations;
    }

    /**
     * Set optional properties to be passed into the SqlSession configuration, as alternative to a
     * {@code &lt;properties&gt;} tag in the configuration xml file. This will be used to resolve placeholders in the
     * config file.
     *
     * @param sqlSessionFactoryProperties optional properties for the SqlSessionFactory
     */
    public void setConfigurationProperties(Properties sqlSessionFactoryProperties) {
        this.configurationProperties = sqlSessionFactoryProperties;
    }

    /**
     * Set the JDBC {@code DataSource} that this instance should manage transactions for. The {@code DataSource} should
     * match the one used by the {@code SqlSessionFactory}: for example, you could specify the same JNDI DataSource for
     * both.
     * <p>
     * A transactional JDBC {@code Connection} for this {@code DataSource} will be provided to application code accessing
     * this {@code DataSource} directly via {@code DataSourceUtils} or {@code DataSourceTransactionManager}.
     * <p>
     * The {@code DataSource} specified here should be the target {@code DataSource} to manage transactions for, not a
     * {@code TransactionAwareDataSourceProxy}. Only data access code may work with
     * {@code TransactionAwareDataSourceProxy}, while the transaction manager needs to work on the underlying target
     * {@code DataSource}. If there's nevertheless a {@code TransactionAwareDataSourceProxy} passed in, it will be
     * unwrapped to extract its target {@code DataSource}.
     *
     * @param dataSource a JDBC {@code DataSource}
     */
    public void setDataSource(DataSource dataSource) {
        if (dataSource instanceof TransactionAwareDataSourceProxy) {
            // If we got a TransactionAwareDataSourceProxy, we need to perform
            // transactions for its underlying target DataSource, else data
            // access code won't see properly exposed transactions (i.e.
            // transactions for the target DataSource).
            this.dataSource = ((TransactionAwareDataSourceProxy) dataSource).getTargetDataSource();
        } else {
            this.dataSource = dataSource;
        }
    }

    /**
     * Set the MyBatis TransactionFactory to use. Default is {@code SpringManagedTransactionFactory}
     * <p>
     * The default {@code SpringManagedTransactionFactory} should be appropriate for all cases:
     * be it Spring transaction management, EJB CMT or plain JTA. If there is no active transaction,
     * SqlSession operations will execute SQL statements non-transactionally.
     *
     * <b>It is strongly recommended to use the default {@code TransactionFactory}.</b> If not used, any
     * attempt at getting an SqlSession through Spring's MyBatis framework will throw an exception if
     * a transaction is active.
     *
     * @param transactionFactory the MyBatis TransactionFactory
     * @see SpringManagedTransactionFactory
     */
    public void setTransactionFactory(TransactionFactory transactionFactory) {
        this.transactionFactory = transactionFactory;
    }

    /**
     * Set scripting language drivers.
     *
     * @param scriptingLanguageDrivers scripting language drivers
     * @since 2.0.2
     */
    public void setScriptingLanguageDrivers(LanguageDriver... scriptingLanguageDrivers) {
        this.scriptingLanguageDrivers = scriptingLanguageDrivers;
    }

    /**
     * Set a default scripting language driver class.
     *
     * @param defaultScriptingLanguageDriver A default scripting language driver class
     * @since 2.0.2
     */
    public void setDefaultScriptingLanguageDriver(Class<? extends LanguageDriver> defaultScriptingLanguageDriver) {
        this.defaultScriptingLanguageDriver = defaultScriptingLanguageDriver;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        // 1. 检查configuration与configLocation是否都为null,或者都不为null
        notNull(dataSource, "Property 'dataSource' is required");
        state((configuration == null && configLocation == null) || !(configuration != null && configLocation != null),
            "Property 'configuration' and 'configLocation' can not specified with together");
        // 2. 清理掉资源  建议不要保留这个玩意了
        SqlRunner.DEFAULT.close();
        // 3. 构建 sqlSessionFactory ❗️❗️❗️
        this.sqlSessionFactory = buildSqlSessionFactory();
    }

    /**
     * Build a {@code SqlSessionFactory} instance.
     * <p>
     * The default implementation uses the standard MyBatis {@code XMLConfigBuilder} API to build a
     * {@code SqlSessionFactory} instance based on an Reader. Since 1.3.0, it can be specified a
     * {@link Configuration} instance directly(without config file).
     * </p>
     *
     * @return SqlSessionFactory
     * @throws IOException if loading the config file failed
     */
    protected SqlSessionFactory buildSqlSessionFactory() throws Exception {

        final Configuration targetConfiguration;

        // 1. 使用 MybatisXmlConfigBuilder 而不是 XMLConfigBuilder
        // 这里的: MybatisXMLConfigBuilder没有继承XMLConfigBuilder,而是从 XMLConfigBuilder copy 过来, 只是为了使用 MybatisConfiguration 而不是 Configuration 而已而已 ❗️❗️❗️
        MybatisXMLConfigBuilder xmlConfigBuilder = null;

        // 2. 确定 configuration
        // 描述一下:
        //  a.  当用户没有注入SqlSessionFactory时,将由MybatisPlusAutoConfiguration的@Bean方法创建MybatisSessionFactoryBean
        //      Configuration与configLocation取决于 MybatisPlusAutoConfiguration#applyConfiguration(..)
        //          a.1 当有指定 "mybatis-plus.mybatis-configuration" 属性,MybatisPlusProperties中就有对应的MybatisConfiguration设置进去
        //          a.2 没有指定 "mybatis-plus.mybatis-configuration" 属性,但有指定 "mybatis-plus.config-location" 就会设置当前类的configLocation
        //          a.3 当啊a.1和a.2都不成立时,直接new MybatisConfiguration()设置到当前类的configuration上
        //  b.  用户自己注入SqlSessionFactory时,并且使用MybatisSqlSessionFactory.getObject()时,就完全取决于用户的自定义行为而已

        // 2.1 configuration 不为空
        if (this.configuration != null) {
            targetConfiguration = this.configuration;
            if (targetConfiguration.getVariables() == null) {
                targetConfiguration.setVariables(this.configurationProperties);
            } else if (this.configurationProperties != null) {
                targetConfiguration.getVariables().putAll(this.configurationProperties);
            }
        }
        // 2.2 使用configLocation指定的spring-mybatis.xml位置
        else if (this.configLocation != null) {
            // 2.2.1 传入spring-mybatis.xml,以及配置属性configurationProperties,构建MybatisXMLConfigBuilder,获取其中的 new MybatisConfiguration() 作为目标Configuration类
            xmlConfigBuilder = new MybatisXMLConfigBuilder(this.configLocation.getInputStream(), null, this.configurationProperties);
            targetConfiguration = xmlConfigBuilder.getConfiguration();
        }
        // 2.3 configuration与configurationLocation都为null
        // 目标Configuration设置为MybatisConfiguration即可 -> 并加入全局配置属性:configurationProperties
        else {
            LOGGER.debug(() -> "Property 'configuration' or 'configLocation' not specified, using default MyBatis Configuration");
            targetConfiguration = new MybatisConfiguration();
            Optional.ofNullable(this.configurationProperties).ifPresent(targetConfiguration::setVariables);
        }

        // 3. 设置GlobalConfig -> ❗️❗️❗️
        // 简述:
        //  a.  当用户没有向ioc容器注入SqlSessionFactory时,将由 @Bean 标注的 MybatisPlusAutoConfiguration#sqlSessionFactory() 决定哦
        //      当前类的globalConfig字段取决于  MybatisPlusAutoConfiguration#sqlSessionFactory() 的源码处理过程,如下:
        //        1. 拿到 properties.getGlobalConfig() 值, 结果就是: GlobalConfigUtils.defaults()默认返回值
        //        2. 期间通过 "mybatis-plus.global-config" yaml属性去修改GlobalConfig的默认值
        //        3. 然后这里由通过 this.getBeanThen(..) 去ioc容器中查找指定clazz的bean,并设置到GlobalConfig中
        //           [clazz包括填充器MetaObjectHandler\主键生成器IKeyGenerator\sql注入器ISqlInjector\id生成器IdentifierGenerator]
        //  b.  用户自己向ioc容器注入SqlSessionFactory时,并且使用MybatisSqlSessionFactory.getObject()时,就完全取决于用户的自定义行为而已

        this.globalConfig = Optional.ofNullable(this.globalConfig).orElseGet(GlobalConfigUtils::defaults);
        this.globalConfig.setDbConfig(Optional.ofNullable(this.globalConfig.getDbConfig()).orElseGet(GlobalConfig.DbConfig::new));

        // 4.  设置 Configuration 与 GlobalConfig 的对照关系
        GlobalConfigUtils.setGlobalConfig(targetConfiguration, this.globalConfig);

        // 5. 自定义枚举类扫描处理
        // 简述:
        // a. 当用户没有向ioc容器注入SqlSessionFactory时,将由 @Bean 标注的 MybatisPlusAutoConfiguration#sqlSessionFactory() 决定哦
        //    当前类的typeEnumsPackage取决于: "mybatis-plus.type-enums-package"的yaml属性
        // b. 用户自己向ioc容器注入SqlSessionFactory时,并且使用MybatisSqlSessionFactory.getObject()时,就完全取决于用户的自定义行为而已

        // 5.1 有设置 typeEnumsPackage
        if (hasLength(this.typeEnumsPackage)) {
            Set<Class<?>> classes;
            // 5.1.1 typeEnumsPackage仅仅包含有一个分组
            if (typeEnumsPackage.contains(StringPool.STAR) && !typeEnumsPackage.contains(StringPool.COMMA) && !typeEnumsPackage.contains(StringPool.SEMICOLON)) {
                // 5.1.1.1 遍历出typeEnumsPackage下Class集合
                classes = scanClasses(typeEnumsPackage, null);
                if (classes.isEmpty()) {
                    LOGGER.warn(() -> "Can't find class in '[" + typeEnumsPackage + "]' package. Please check your configuration.");
                }
            }
            // 5.1.2 typeEnumsPackage包含至少2个分组
            else {
                classes = new HashSet<>();
                String[] typeEnumsPackageArray = tokenizeToStringArray(this.typeEnumsPackage, ConfigurableApplicationContext.CONFIG_LOCATION_DELIMITERS);
                Assert.notNull(typeEnumsPackageArray, "not find typeEnumsPackage:" + typeEnumsPackage);
                Stream.of(typeEnumsPackageArray).forEach(typePackage -> {
                    try {
                        Set<Class<?>> scanTypePackage = scanClasses(typePackage, null);
                        if (scanTypePackage.isEmpty()) {
                            LOGGER.warn(() -> "Can't find class in '[" + typePackage + "]' package. Please check your configuration.");
                        } else {
                            classes.addAll(scanTypePackage);
                        }
                    } catch (IOException e) {
                        throw new MybatisPlusException("Cannot scan class in '[" + typePackage + "]' package", e);
                    }
                });
            }
            // 5.1.3 取得类型转换注册器

            // 5.1.3.1 获取: TypeHandlerRegistry注册表 [❗️❗️❗️]
            TypeHandlerRegistry typeHandlerRegistry = targetConfiguration.getTypeHandlerRegistry();
            // 5.1.3.2 当class是枚举,并且有实现IEnum或者有@EnumValue标注的字段时
            // 注册到 TypeHandlerRegistry 中
            classes.stream()
                .filter(Class::isEnum)
                .filter(MybatisEnumTypeHandler::isMpEnums)
                // ❗️❗️❗️
                // 5.1.3.3 简述过程:
                // 1. 使用MybatisEnumTypeHandler带有Class形参的构造器,以当前枚举作为值作为构造器参数传递进去,生成一个MybatisEnumTypeHandler对象 [如果不存在Class形参的构造器,就以空参构造器来创建,很明显MybatisEnumTypeHandler是有的哦]
                // 2. 最终注册为: jdbcType为这里的java枚举类作为key, 以[jdbcType为null,TypeHandler对象为MybatisEnumTypeHandler]的value [❗️❗️❗️ 了解:MybatisEnumTypeHandler]
                .forEach(cls -> typeHandlerRegistry.register(cls, MybatisEnumTypeHandler.class));
        }

        // 6.  向MybatisConfiguration设置: objectFactory\ObjectWrapperFactory\VfsImpl
        // 简述:
        // a. 当用户没有向ioc容器注入SqlSessionFactory时,将由 @Bean 标注的 MybatisPlusAutoConfiguration#sqlSessionFactory() 决定哦
        //    当前类的objectFactory就是null -> 因此在MybatisConfiguration中还是默认值即DefaultObjectFactory
        //    当前类的objectWrapperFactory就是null ->  因此在MybatisConfiguration中还是默认值即DefaultObjectWrapperFactory
        //    当前类的vfs直接就是SpringBootVFS
        // b. 用户自己向ioc容器注入SqlSessionFactory时,并且使用MybatisSqlSessionFactory.getObject()时,就完全取决于用户的自定义行为而已
        Optional.ofNullable(this.objectFactory).ifPresent(targetConfiguration::setObjectFactory);
        Optional.ofNullable(this.objectWrapperFactory).ifPresent(targetConfiguration::setObjectWrapperFactory);
        Optional.ofNullable(this.vfs).ifPresent(targetConfiguration::setVfsImpl);

        // 7. 需要扫描的typeAliasPackage [简述内容同上,MybatisPlusAutoConfiguration#sqlSessionFactory()生效时,typeAliasesPackage就是 "mybatis-plus.type-aliases-package"]
        if (hasLength(this.typeAliasesPackage)) {
            // 7.1 扫描typeAliasesPackage下,实现了typeAliasesSuperType的classes
            // 7.2 并过滤出非匿名类\非接口\非内部类的classes
            // 7.3 然后注册到 TypeAliasRegistry
            //      7.3.1 类上有@Alias的value值时,就以其作为别名
            //      7.3.2 类上没有@Alias的value值时,就以class.getSimpleName()作为别名
            scanClasses(this.typeAliasesPackage, this.typeAliasesSuperType).stream()
                .filter(clazz -> !clazz.isAnonymousClass()).filter(clazz -> !clazz.isInterface())
                .filter(clazz -> !clazz.isMemberClass()).forEach(targetConfiguration.getTypeAliasRegistry()::registerAlias);
        }

        // 8. typeAliases 指定注册的别名 [typeAliases是无法通过"mybatis-plus"设置到的.只能用户手动去设置吧,默认情况都是empty的]
        if (!isEmpty(this.typeAliases)) {
            Stream.of(this.typeAliases).forEach(typeAlias -> {
                targetConfiguration.getTypeAliasRegistry().registerAlias(typeAlias);
                LOGGER.debug(() -> "Registered type alias: '" + typeAlias + "'");
            });
        }

        // 9.  注册plugins ❗️❗️❗️
        // 简述:
        // a. 当用户没有向ioc容器注入SqlSessionFactory时,将由 @Bean 标注的 MybatisPlusAutoConfiguration#sqlSessionFactory() 决定哦
        //    当前类的plugins就是从ioc容器中找出所有的Interceptor类型的Bean
        // b. 用户自己向ioc容器注入SqlSessionFactory时,并且使用MybatisSqlSessionFactory.getObject()时,就完全取决于用户的自定义行为而已
        if (!isEmpty(this.plugins)) {
            Stream.of(this.plugins).forEach(plugin -> {
                targetConfiguration.addInterceptor(plugin);
                LOGGER.debug(() -> "Registered plugin: '" + plugin + "'");
            });
        }

        // 10. 通过扫描指定package注册typeHandler
        // 简述:
        // a. 当用户没有向ioc容器注入SqlSessionFactory时,将由 @Bean 标注的 MybatisPlusAutoConfiguration#sqlSessionFactory() 决定哦
        //    当前类的typeHandlersPackage就是 "mybatis-plus.type-handlers-package
        // b. 用户自己向ioc容器注入SqlSessionFactory时,并且使用MybatisSqlSessionFactory.getObject()时,就完全取决于用户的自定义行为而已
        if (hasLength(this.typeHandlersPackage)) {
            // 10. 要求在typeHandlersPackage下的类是是实现了TypeHandler接口,并且非匿名类,非接口,非抽象类,然后注册到TypeHandlerRegistry
            scanClasses(this.typeHandlersPackage, TypeHandler.class).stream().filter(clazz -> !clazz.isAnonymousClass())
                .filter(clazz -> !clazz.isInterface()).filter(clazz -> !Modifier.isAbstract(clazz.getModifiers()))
                .forEach(targetConfiguration.getTypeHandlerRegistry()::register);
        }

        // 11. 直接注册: typeHandler
        if (!isEmpty(this.typeHandlers)) {
            Stream.of(this.typeHandlers).forEach(typeHandler -> {
                targetConfiguration.getTypeHandlerRegistry().register(typeHandler);
                LOGGER.debug(() -> "Registered type handler: '" + typeHandler + "'");
            });
        }

        // 12. 注册: scriptingLanguageDrivers [忽略~]
        // 一般都是使用 XMLLanguageDriver, 在MP中可以使用 MybatisXMLLanguageDriver
        // a. 当用户没有向ioc容器注入SqlSessionFactory时,将由 @Bean 标注的 MybatisPlusAutoConfiguration#sqlSessionFactory() 决定哦
        //    当前类的scriptingLanguageDrivers就是IOC容器中LanguageDriver类型的bean的集合 [❗️❗️❗️ 说实话,一般没人回去弄LanguageDriver]
        // b. 用户自己向ioc容器注入SqlSessionFactory时,并且使用MybatisSqlSessionFactory.getObject()时,就完全取决于用户的自定义行为而已
        if (!isEmpty(this.scriptingLanguageDrivers)) {
            Stream.of(this.scriptingLanguageDrivers).forEach(languageDriver -> {
                targetConfiguration.getLanguageRegistry().register(languageDriver);
                LOGGER.debug(() -> "Registered scripting language driver: '" + languageDriver + "'");
            });
        }

        // 13. 注册默认的 defaultScriptingLanguageDriver
        // 简述:
        // a. 当用户没有向ioc容器注入SqlSessionFactory时,将由 @Bean 标注的 MybatisPlusAutoConfiguration#sqlSessionFactory() 决定哦
        //    当前类的defaultScriptingLanguage就是 "mybatis-plus.default-scripting-language-driver"
        // b. 用户自己向ioc容器注入SqlSessionFactory时,并且使用MybatisSqlSessionFactory.getObject()时,就完全取决于用户的自定义行为而已
        Optional.ofNullable(this.defaultScriptingLanguageDriver).ifPresent(targetConfiguration::setDefaultScriptingLanguage);

        // 14. 注册 databaseIdProvider -> [忽略~~~]
        if (this.databaseIdProvider != null) {
            try {
                targetConfiguration.setDatabaseId(this.databaseIdProvider.getDatabaseId(this.dataSource));
            } catch (SQLException e) {
                throw new NestedIOException("Failed getting a databaseId", e);
            }
        }

        // 15. 设置缓存
        // a. 当用户没有向ioc容器注入SqlSessionFactory时,将由 @Bean 标注的 MybatisPlusAutoConfiguration#sqlSessionFactory() 决定哦
        //    当前类的cache就是空的null值
        // b. 用户自己向ioc容器注入SqlSessionFactory时,并且使用MybatisSqlSessionFactory.getObject()时,就完全取决于用户的自定义行为而已
        Optional.ofNullable(this.cache).ifPresent(targetConfiguration::addCache);

        // 16. 开始使用 xmlConfigBuilder 解析 [❗️❗️❗️]
        // [忽略~~ -> 因为不会去使用 spring-mybatis.xml ]
        if (xmlConfigBuilder != null) {
            try {
                xmlConfigBuilder.parse();
                LOGGER.debug(() -> "Parsed configuration file: '" + this.configLocation + "'");
            } catch (Exception ex) {
                throw new NestedIOException("Failed to parse config resource: " + this.configLocation, ex);
            } finally {
                ErrorContext.instance().reset();
            }
        }

        // 17. 设置Environment
        // 新建一个Environment [id/TransactionFactory/dataSource]
        // TransactionFactory -> ioc容器查找出来的,如果为空的时候,使用SpringManagedTransactionFactory [前提不注入SqlSessionFactory,否则取决于用户注入的SqlSessionFactory如何设置]
        // DataSource -> ioc容器中查找出来的  [前提不注入SqlSessionFactory,否则取决于用户注入的SqlSessionFactory如何设置]
        targetConfiguration.setEnvironment(new Environment(MybatisSqlSessionFactoryBean.class.getSimpleName(),
            this.transactionFactory == null ? new SpringManagedTransactionFactory() : this.transactionFactory,
            this.dataSource));

        // 18. ❗️❗️❗️ -> 指定mapper.xml文件扫描地址 [很重要的地方哦]
        if (this.mapperLocations != null) {
            if (this.mapperLocations.length == 0) {
                LOGGER.warn(() -> "Property 'mapperLocations' was specified but matching resources are not found.");
            } else {
                for (Resource mapperLocation : this.mapperLocations) {
                    if (mapperLocation == null) {
                        continue;
                    }
                    try {
                        // 18.1 使用XMLMapperBuilder进行扫描mapper.xml文件哦
                        XMLMapperBuilder xmlMapperBuilder = new XMLMapperBuilder(mapperLocation.getInputStream(),
                            targetConfiguration, mapperLocation.toString(), targetConfiguration.getSqlFragments());
                        xmlMapperBuilder.parse();
                    } catch (Exception e) {
                        throw new NestedIOException("Failed to parse mapping resource: '" + mapperLocation + "'", e);
                    } finally {
                        ErrorContext.instance().reset();
                    }
                    LOGGER.debug(() -> "Parsed mapper file: '" + mapperLocation + "'");
                }
            }
        } else {
            LOGGER.debug(() -> "Property 'mapperLocations' was not specified.");
        }

        // 19. 构建最终的: SqlSessionFactory 吧
        final SqlSessionFactory sqlSessionFactory = new MybatisSqlSessionFactoryBuilder().build(targetConfiguration);
        System.out.println(sqlSessionFactory);
        // 20. 设置到
        SqlHelper.FACTORY = sqlSessionFactory;

        // TODO 打印骚东西 Banner
        if (globalConfig.isBanner()) {
            System.out.println(" _ _   |_  _ _|_. ___ _ |    _ ");
            System.out.println("| | |\\/|_)(_| | |_\\  |_)||_|_\\ ");
            System.out.println("     /               |         ");
            System.out.println("                        " + MybatisPlusVersion.getVersion() + " ");
        }

        return sqlSessionFactory;
    }

    // ---------------------
    // 实现 FactoryBean 的接口
    // ---------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public SqlSessionFactory getObject() throws Exception {
        // ❗️❗️❗️ 生成:SqlSessionFactory
        // 稍微简述一下: FactoryBean#getObject() 的触发时机 ->

//1. getBean() 方法触发 -> 调用doGetBean()
//    1.1 转换用户传递name，如果是”&BeanFactoryImplName“，那么beanName=BeanFactoryImplName； 如果是别名的话，会返回最终的实际beanName
//    1.2 尝试从一级二级三级缓存中获取beanName对应的bean，找到直接返回
//    1.3 检查是否为原型对象，如果是原型对象且还在创建中，直接报错 -> 原型对象不允许有循环依赖
//    1.4 一步也是必须要做的，若存在父容器，得看看父容器是否实例化过它了。避免被重复实例化（若父容器被实例化，就以父容器的为准） 【SpringBoot默认单容器】
//    1.5 根据beanName名字获取合并过的RootBeanDefinition [❗️❗️❗️-> BeanFactory的beanName就是BeanFactory本身]
//    1.6 完成@DependsOn
//        1.6.1 检查依赖关系，比如是否a dependsOn b，b depends a，这样的话，两个bean a和b 都无法创建。
//        1.6.2 注入新的依赖关系比如a @DependsOn b，注入依赖关系，a依赖b后，还需要去getBean(b)
//    1.7 是否为单例
//        1.7.1 从一级缓存获取，获取失败，就加入到正在创建的单例BeanName集合中 [❗️❗️❗️ 依赖注入：就需要关注这个正在创建的单例Bean的beanName集合哦]
//        1.7.2 开始正式创建对象 [❗️❗️❗️ 创建Bean，很核心哦]
//            1.7.2.1  检查是否有@Lookup注解，@Lookup使用场景：比如单例的A，希望每次使用成员B（@Autowrite注入的B）的时候都是一个新的对象，就可以见@Lookup放在成员B上
//                     因此需要：为即将生成的Bean，如果这个bean是有lookup和replace方法的话，就需要动态为当前bean生产代理并使用对应的拦截器为bean做增强处理
//            1.7.2.2  触发： InstantiationAwareBeanPostProcessor#applyBeanPostProcessorsBeforeInstantiation()，如果中间任意一个方法返回不为null,直接结束调用。
//                     上面只要触发成功就接着触发： BeanPostProcessor#postProcessAfterInitialization方法（同样如果任意一次返回不为null,即终止调用。) -> 这个bean就会被直接返回啦
//                     【❗️❗️❗️ AbstractAutoProxyCreator 自动代理创建器，是同时InstantiationAwareBeanPostProcessor#applyBeanPostProcessorsBeforeInstantiation()和BeanPostProcessor#postProcessAfterInitialization方法和SmartInstantiationAwareBeanPostProcessor#getEarlyBeanReference()，但实际上applyBeanPostProcessorsBeforeInstantiation()基本无效，因为没有定制的TargetSourceCreator，实际上是在：SmartInstantiationAwareBeanPostProcessor#getEarlyBeanReference()这一步去生成代理对象的哦】
//                     【❗️❗️❗️ AbstractSingletonProxyFactoryBean 是通过FactoryBean的方式来创建代理对象的】
//            1.7.2.3 只要前两个步骤创建出来了代理bean，就直接返回这个bena【代理bean很少是在这里处理吧❗️❗️❗️】
//            1.7.2.4 既然applyBeanPostProcessorsBeforeInstantiation()没有创建Bean，那就自己来doCreateBean()
//                a. 实例化当前bean
//                    如果设置Supplier回调，则调用obtainFromSupplier()方法进行初始化                              ->  人为提供的实例化策略instanceSupplier
//		            如果存在工厂方法，则使用工厂方法进行初始化                                                    -> @Bean标注的方法
//		            判断缓存中是否存在构造函数，如果存在，则根据是否使用自动注入，否则去找一个可用的构造器
//                        SmartInstantiationAwareBeanPostProcessor#determineCandidateConstructors(..)获取可用的构造器，谁第一个发现一个可用的构造器，就return，否则返回null
//                        常用的就是：AutowiredAnnotationBeanPostProcessor#determineCandidateConstructor      -> 构造器依赖注入在这里完成的[对于循环依赖只能报错哦❗️❗️❗️]
//		            如果上述条件还是不满足，则使用无参构造方法来创建实例                                           -> 空参构造器构造 【Gclib或Jdk】
//                b. 在实例化之后、属性填充之前，对BeanDefinition进行修改操作 ->  MergedBeanDefinitionPostProcessor#postProcessMergedBeanDefinition(..)
//                      note:常见的比如:   CommonAnnotationBeanPostProcessor -> 处理 @Resource\@PostConstruct
//                c. 当前bean为单例Bean，且允许循环依赖
//                    c.1 那就存入三级缓存中，并且 value 为 () -> getEarlyBeanReference(beanName, mbd, bean)
//                    c.2 getEarlyBeanReference(beanName, mbd, bean) -> 一直遍历 SmartInstantiationAwareBeanPostProcessor#getEarlyBeanReference() 将前一个结果作为后一个结果放进去【❗️❗️❗️ 允许去创建一个代理对象，比如 AbstractAutoProxyCreator 就是这个方法创建的哦】
//                d. bean实例的属性填充
//                    d.1 调用 InstantiationAwareBeanPostProcessor#postProcessAfterInstantiation() -> 实例化之后，属性填充之前的一个时机 -> 只要其中一个返回false，就会导致后续的步骤不在执行哦
//                    d.2 调用 InstantiationAwareBeanPostProcessor#postProcessProperties() -> 实例化之后，属性填充之前，修改PropertieValues
//                    d.3 如果 InstantiationAwareBeanPostProcessor#postProcessProperties() 返回null -> 就去处理 InstantiationAwareBeanPostProcessor#postProcessPropertyValues() [❗️❗️❗️ 为什么，这里主要是一个兼容，后来的版本加入了postProcessPropertyValues(..)提供了一个PropertyDescriptor属性描述符，想要使用，就必须老版的postProcessProperties(..)返回null哦]
//                    d.4 接受经过增强的 PropertyValues 设置到bean的各个属性上去
//                e. 初始化bean -> [❗️❗️❗️ 允许返回一个新的代理bean]
//                    e.1 实现 BeanNameAware / BeanClassLoaderAware / BeanFactoryAwareAware 接口，注入相关对象
//                    e.2 调用 BeanPostProcessor#postProcessBeforeInitialization()  -> 初始化前置处理
//                        基本上也是执行Aware接口的注入，比如：
//                        InitDestroyAnnotationBeanPostProcessor
//                        ServletContextAwareProcessor 实现感知接口的注入 -> ServletContextAware、ServletConfigAware
//                        ApplicationContextAwareProcessor/ServletContextAwareProcessor 实现感知接口的注入 -> 比如EnvironmentAware/EmbeddedValueResolverAware/ResourceLoaderAware/ApplicationEventPublisherAware/MessageSourceAware/ApplicationContextAware
//                        BootstrapContextAwareProcessor 实现感知接口的注入 -> BootstrapContextAware
//                    e.3 执行初始化方法
//                        e.3 实现了initialzingBean，调用实现的 afterPropertiesSet()
//                        e.4 配置了init-mothod，调用相应的init()方法
//                    e.5 调用 BeanPostProcessor#postProcessAfterInitialization     -> 初始化后置处理【直到一个返回null值结束】
//                f. 上面将单例Bean已经存入到三级缓存中 -> 需要验证一下循环依赖问题【❗️❗️❗️】
//                    f.1 尝试二级和一级缓存拿到上面的 c.1 中 getEarlyBeanReference(beanName, mbd, bean) 输出的结果 ->  原因： 如果拿到非null结果，说明对象从三级缓存升级到二级缓存，此刻存在循环依赖，因为加入的 () -> getEarlyBeanReference(beanName, mbd, bean) 是加入到三级缓存的，除非a->b,b->a才会导致a升级到二级缓存
//                    f.2 然后如何初始化过程中e.5初始化步骤将bean已经做过代理，那么就会导致和 getEarlyBeanReference(beanName, mbd, bean) 中的bean不是同一个，也就是说 bean b 依赖的 bean a 使无效的，需要报错
//                g. 注册@PreDestroy、DisposableBean等注销方法
//        1.7.3 没有任何异常，结束单例Bean的创建检查，从上述正在创建的单例BeanName集合中移除其beanName
//        1.7.4 创建单例Bean成功，提升到一级缓存，清空二级三级缓存
//    1.8 调用 getObjectForBeanInstance(sharedInstance, name, beanName, mbd) -> sharedInstance 是成功创建的Bean，就下来就需要考虑这个Bean是否为需要的Bean，还是需要FactoryBean里面的getObject的Bean
//        1.8.1 用户传递进来的name是”&BeanFactoryName“的形式，表明其需要的就是BeanFactory对象，直接返回出去
//        1.8.2 bean本身也不是FactoryBean类型的，直接return -> 因为非FactoryBean无法知道用getObject()
//        1.8.2 使用FactoryBean#isSingleton()判断是否单例,并且是否在单例bean容器中缓存起来,没有缓存起来就调用FactoryBean#getObject()并缓存后返回出去 -> 【❗️❗️❗️】

        if (this.sqlSessionFactory == null) {
            afterPropertiesSet();
        }

        return this.sqlSessionFactory;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Class<? extends SqlSessionFactory> getObjectType() {
        return this.sqlSessionFactory == null ? SqlSessionFactory.class : this.sqlSessionFactory.getClass();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isSingleton() {
        return true;
    }

    // ---------------------
    // 实现 ApplicationListener<ApplicationEvent> 接口
    // ---------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        // 1. 监听 ApplicationEvent 中的 ContextRefreshedEvent 事件
        // 简述: failFast = 如果为 true，则对 Configuration 进行最后检查，以确保所有映射语句都已完全加载，并且没有任何待解决的语句。默认为假 [所以当前方法可忽略~~]
        if (failFast && event instanceof ContextRefreshedEvent) {
            // fail-fast -> check all statements are completed
            this.sqlSessionFactory.getConfiguration().getMappedStatementNames();
        }
    }

    private Set<Class<?>> scanClasses(String packagePatterns, Class<?> assignableType) throws IOException {
        // 遍历packagePatterns中的packagePattern的类路径: 找出其中所有符合规定的Class哦
        // 整个处理流程: 借用Spring的工具进行处理

        Set<Class<?>> classes = new HashSet<>();
        String[] packagePatternArray = tokenizeToStringArray(packagePatterns, ConfigurableApplicationContext.CONFIG_LOCATION_DELIMITERS);
        for (String packagePattern : packagePatternArray) {
            Resource[] resources = RESOURCE_PATTERN_RESOLVER.getResources(ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX + ClassUtils.convertClassNameToResourcePath(packagePattern) + "/**/*.class");
            for (Resource resource : resources) {
                try {
                    ClassMetadata classMetadata = METADATA_READER_FACTORY.getMetadataReader(resource).getClassMetadata();
                    Class<?> clazz = Resources.classForName(classMetadata.getClassName());
                    if (assignableType == null || assignableType.isAssignableFrom(clazz)) {
                        classes.add(clazz);
                    }
                } catch (Throwable e) {
                    LOGGER.warn(() -> "Cannot load the '" + resource + "'. Cause by " + e.toString());
                }
            }
        }
        return classes;
    }
}
