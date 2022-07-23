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
package com.baomidou.mybatisplus.autoconfigure;


import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.core.incrementer.IKeyGenerator;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.baomidou.mybatisplus.core.injector.ISqlInjector;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.scripting.LanguageDriver;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.type.TypeHandler;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.beans.PropertyDescriptor;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * {@link EnableAutoConfiguration Auto-Configuration} for Mybatis. Contributes a
 * {@link SqlSessionFactory} and a {@link SqlSessionTemplate}.
 * <p>
 * If {@link org.mybatis.spring.annotation.MapperScan} is used, or a
 * configuration file is specified as a property, those will be considered,
 * otherwise this auto-configuration will attempt to register mappers based on
 * the interface definitions in or under the root auto-configuration package.
 * </p>
 *
 * @author Eddú Meléndez
 * @author Josh Long
 * @author Kazuki Shimizu
 * @author Eduardo Macarrón
 */
// 配置类: -- 不使用代理模式,即其中的@Bean方法创建出来的bean不需要被代理
@Configuration(proxyBeanMethods = false)
// 要求: mybatis的SqlSessionFactory存在\mybatis-spring的SqlSessionFactoryBean存在
@ConditionalOnClass({SqlSessionFactory.class, SqlSessionFactoryBean.class})
// 仅在指定类DataSource的bean已包含在BeanFactory中并且可以确定单个候选者时匹配 [一个候选者 或者 多个候选者其中一个指定Primary]
@ConditionalOnSingleCandidate(DataSource.class)
// 开启自动配置的属性 -- MybatisPlusProperties -- 一般情况: @ConfigurationProperties 搭配 @Component 即可完成注册
// 但是如果非项目中的代码,比如作为jar包,使用上面的方法时,默认会以Spring启动类的所在package作为基础包对项目中的@Component的class进行扫描
// 因此使用@Component或@Configuration是无法被扫描到的哦 -- 当然也可以使用@Bean+@COnfigurationProperties的方式
@EnableConfigurationProperties(MybatisPlusProperties.class)
// 在 DataSourceAutoConfiguration 和 MybatisPlusLanguageDriverAutoConfiguration 之后进行自动配置
@AutoConfigureAfter({DataSourceAutoConfiguration.class, MybatisPlusLanguageDriverAutoConfiguration.class})
public class MybatisPlusAutoConfiguration implements InitializingBean {

    private static final Logger logger = LoggerFactory.getLogger(MybatisPlusAutoConfiguration.class);

    // ❗️❗️❗️MybatisPlusProperties 是 MP-boot-starter 项目自动加载到ioc容器的哦 -> 因此可以自动注入
    private final MybatisPlusProperties properties;

    // ❗️❗️❗️用户在Spring容器中添加Interceptor\TypeHandler\LanguageDriver\DatabaseIdProvider
    private final Interceptor[] interceptors;

    private final TypeHandler[] typeHandlers;

    private final LanguageDriver[] languageDrivers;

    private final DatabaseIdProvider databaseIdProvider;

    // ❗️❗️❗️ 用户可以实现 ConfigurationCustomizer/MybatisPlusPropertiesCustomizer 两个定制类 -> 扩展MP的功能
    private final List<ConfigurationCustomizer> configurationCustomizers;

    private final List<MybatisPlusPropertiesCustomizer> mybatisPlusPropertiesCustomizers;

    // Spring自动注入
    private final ResourceLoader resourceLoader;

    // Spring自动注入
    private final ApplicationContext applicationContext;


    // spring的构造器自动注入能力
    public MybatisPlusAutoConfiguration(MybatisPlusProperties properties,
                                        ObjectProvider<Interceptor[]> interceptorsProvider,
                                        ObjectProvider<TypeHandler[]> typeHandlersProvider,
                                        ObjectProvider<LanguageDriver[]> languageDriversProvider,
                                        ResourceLoader resourceLoader,
                                        ObjectProvider<DatabaseIdProvider> databaseIdProvider,
                                        ObjectProvider<List<ConfigurationCustomizer>> configurationCustomizersProvider,
                                        ObjectProvider<List<MybatisPlusPropertiesCustomizer>> mybatisPlusPropertiesCustomizerProvider,
                                        ApplicationContext applicationContext) {
        this.properties = properties;
        this.interceptors = interceptorsProvider.getIfAvailable();
        this.typeHandlers = typeHandlersProvider.getIfAvailable();
        this.languageDrivers = languageDriversProvider.getIfAvailable();
        this.resourceLoader = resourceLoader;
        this.databaseIdProvider = databaseIdProvider.getIfAvailable();
        this.configurationCustomizers = configurationCustomizersProvider.getIfAvailable();
        this.mybatisPlusPropertiesCustomizers = mybatisPlusPropertiesCustomizerProvider.getIfAvailable();
        this.applicationContext = applicationContext;
    }

    @Override
    public void afterPropertiesSet() {
        // ❗️❗️❗️
        // 1. 开始回调用户实现 MybatisPlusPropertiesCustomizer接口 的所有实现类
        if (!CollectionUtils.isEmpty(mybatisPlusPropertiesCustomizers)) {
            mybatisPlusPropertiesCustomizers.forEach(i -> i.customize(properties));
        }
        // 2. 尝试去检查Mybatis.xml文件
        checkConfigFileExists();
    }

    private void checkConfigFileExists() {
        // 1. MybatisPlusProperties如果需要检查Mybatis.xml文件 && 指定了mybatis.xml的文件位置 -- 就需要加载出来
        if (this.properties.isCheckConfigLocation() && StringUtils.hasText(this.properties.getConfigLocation())) {
            // 1.1 99%的情况, MybatisPlusProperties.checkConfigLocation 默认为false -> 现在基本不使用 mybatis.xml 文件
            Resource resource = this.resourceLoader.getResource(this.properties.getConfigLocation());
            Assert.state(resource.exists(), "Cannot find config location: " + resource + " (please add config file or check your Mybatis configuration)");
        }
    }

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Bean
    @ConditionalOnMissingBean // 用户没有注入SqlSessionFactory类型的Bean时 -- 该配置类生效
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        // 根据DataSource创建一个SqlSessionFactory ~~ 如果用户注入了SqlSessionFactory,就可以忽略了啦 [❗️❗️❗️ 小鱼易连 开发者中心\官网等都会自动注入MybatisSqlSessionFactoryBean]
        // 对于多数据源而言 -> 最好还是手动创建SqlSessionFactory吧,不然那注入DataSource可能出现异常哦 -> 因此这里是适合单数据源的哦 ❗️❗️❗️❗️❗️❗️
        // note: 所以并不是一定会通过该方法生成SqlSessionFactory,也就是说 MybatisPlusProperties 的属性并不一定会生效哦

        // 1. 创建 MybatisSqlSessionFactoryBean [❗️❗️❗️❗️❗️❗️❗️❗️❗️]
        // 因此只要使用了 这里的 MybatisSqlSessionFactoryBean -> 毫无疑问最终使用功能的就是 MybatisConfiguration
        // 而 MybatisConfiguration 恰好就是 MybatisXMLLanguageDriver MybatisMapperRegistry 的始作俑者
        // MybatisXMLLanguageDriver -> 又去使用创建的 MybatisParameterHandler
        // MybatisMapperRegistry -> 在addMapper(..)时使用就是 MybatisMapperAnnotationBuilder#parser()来帮助解析
        // 同时这里的注入的 -> MybatisSqlSessionFactoryBean
        // 实际上又和 mybatis-spring 的 ClassPathMapperScanner.scan() 为扫描到的mapper接口注册的BeanDefinition [beanCLass为MapperFactoryBean打下基础]
        // MapperFactoryBean#getObject()时 -> getSqlSession().getMapper() -> SqlSessionTemplate.getMapper() ->
        // SqlSessionTemplate实际上从ioc容器中的SqlSessionFactory 加上 new SqlSessionTemplate(SqlSessionFactory) 完成的 ->
        // ioc容器的SqlSessionFactory就是这里的MybatisSqlSessionFactoryBean
        // -> MybatisSqlSessionFactoryBean#getConfiguration() 最终拿到手的就是 MybatisConfiguration [环环相扣哦]
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setVfs(SpringBootVFS.class);
        if (StringUtils.hasText(this.properties.getConfigLocation())) {
            factory.setConfigLocation(this.resourceLoader.getResource(this.properties.getConfigLocation()));
        }
        // 2.
        // 简述:
        // 1. 如果MybatisPlusProperties.configuration不为空,就可以接受 ConfigurationCustomizer 定制器的处理
        // 2. 如果MybatisPlusProperties.configuration为空,并且由指定mybatis.xml文件位置,就创建一个新的MybatisConfiguration
        // 3. 将上面获取到的Configuration加入到MybatisSqlSessionFactory中
        applyConfiguration(factory);

        // 3. 设置各路属性
        if (this.properties.getConfigurationProperties() != null) {
            factory.setConfigurationProperties(this.properties.getConfigurationProperties());
        }
        if (!ObjectUtils.isEmpty(this.interceptors)) {
            factory.setPlugins(this.interceptors);
        }
        if (this.databaseIdProvider != null) {
            factory.setDatabaseIdProvider(this.databaseIdProvider);
        }
        if (StringUtils.hasLength(this.properties.getTypeAliasesPackage())) {
            factory.setTypeAliasesPackage(this.properties.getTypeAliasesPackage());
        }
        if (this.properties.getTypeAliasesSuperType() != null) {
            factory.setTypeAliasesSuperType(this.properties.getTypeAliasesSuperType());
        }
        if (StringUtils.hasLength(this.properties.getTypeHandlersPackage())) {
            factory.setTypeHandlersPackage(this.properties.getTypeHandlersPackage());
        }
        if (!ObjectUtils.isEmpty(this.typeHandlers)) {
            factory.setTypeHandlers(this.typeHandlers);
        }
        // 4. 源码: 就是通过 new PathMatchingResourcePatternResolver().getResources("ant风格的路径") -> 解析处对应的mapper.xml文件的resource哦
        Resource[] mapperLocations = this.properties.resolveMapperLocations();
        if (!ObjectUtils.isEmpty(mapperLocations)) {
            factory.setMapperLocations(mapperLocations);
        }
        // 5. 从IOC容器中获取TransactionFactory,并注入到MybatisSqlSessionFactoryBean
        this.getBeanThen(TransactionFactory.class, factory::setTransactionFactory);

        // 6. 不要尝试: 自己定义 defaultScriptingLanguageDriver -> 否则将失效MP的几乎所有功能
        Class<? extends LanguageDriver> defaultLanguageDriver = this.properties.getDefaultScriptingLanguageDriver();
        if (!ObjectUtils.isEmpty(this.languageDrivers)) {
            factory.setScriptingLanguageDrivers(this.languageDrivers);
        }
        Optional.ofNullable(defaultLanguageDriver).ifPresent(factory::setDefaultScriptingLanguageDriver);

        // TODO 自定义枚举包
        if (StringUtils.hasLength(this.properties.getTypeEnumsPackage())) {
            factory.setTypeEnumsPackage(this.properties.getTypeEnumsPackage());
        }

        // ❗️❗️❗️ 开始创建:GlobalConfig [前提还是:当前@Bean方法需要生效哦]
        // 1. properties.getGlobalConfig() 默认结果就是: GlobalConfigUtils.defaults()返回值
        // 2. 期间通过 "mybatis-plus.global-config" yaml属性去修改GlobalConfig的默认值
        // 3. 然后这里由通过 this.getBeanThen(..) 去ioc容器中查找指定clazz的bean,并设置到GlobalConfig中
        // [clazz包括填充器MetaObjectHandler\主键生成器IKeyGenerator\sql注入器ISqlInjector\id生成器IdentifierGenerator]

        // TODO 此处必为非 NULL
        GlobalConfig globalConfig = this.properties.getGlobalConfig();
        // TODO 注入填充器
        this.getBeanThen(MetaObjectHandler.class, globalConfig::setMetaObjectHandler);
        // TODO 注入主键生成器
        this.getBeansThen(IKeyGenerator.class, i -> globalConfig.getDbConfig().setKeyGenerators(i));
        // TODO 注入sql注入器
        this.getBeanThen(ISqlInjector.class, globalConfig::setSqlInjector);
        // TODO 注入ID生成器
        this.getBeanThen(IdentifierGenerator.class, globalConfig::setIdentifierGenerator);
        // TODO 设置 GlobalConfig 到 MybatisSqlSessionFactoryBean
        factory.setGlobalConfig(globalConfig);
        return factory.getObject();
    }

    /**
     * 检查spring容器里是否有对应的bean,有则进行消费
     *
     * @param clazz    class
     * @param consumer 消费
     * @param <T>      泛型
     */
    private <T> void getBeanThen(Class<T> clazz, Consumer<T> consumer) {
        if (this.applicationContext.getBeanNamesForType(clazz, false, false).length > 0) {
            consumer.accept(this.applicationContext.getBean(clazz));
        }
    }

    /**
     * 检查spring容器里是否有对应的bean,有则进行消费
     *
     * @param clazz    class
     * @param consumer 消费
     * @param <T>      泛型
     */
    private <T> void getBeansThen(Class<T> clazz, Consumer<List<T>> consumer) {
        if (this.applicationContext.getBeanNamesForType(clazz, false, false).length > 0) {
            final Map<String, T> beansOfType = this.applicationContext.getBeansOfType(clazz);
            List<T> clazzList = new ArrayList<>();
            beansOfType.forEach((k, v) -> clazzList.add(v));
            consumer.accept(clazzList);
        }
    }

    private void applyConfiguration(MybatisSqlSessionFactoryBean factory) {

        // 1. configuration 一般都为null -> 除非指定 mybatis-plus.mybatis-configuration  的属性
        MybatisConfiguration configuration = this.properties.getConfiguration();
        // 2. 如果configuration为null,就可以看看是否有指定Mybatis.xml的文件位置: configLocation
        if (configuration == null && !StringUtils.hasText(this.properties.getConfigLocation())) {
            // 2.1 满足上面的请求: 创建MybatisConfiguration
            configuration = new MybatisConfiguration();
        }
        // 3. 如果configuration不为null && 有用户创建的ConfigurationCustomizer实现类 -> 遍历ConfigurationCustomizer#customize(..)
        if (configuration != null && !CollectionUtils.isEmpty(this.configurationCustomizers)) {
            for (ConfigurationCustomizer customizer : this.configurationCustomizers) {
                customizer.customize(configuration);
            }
        }

        // 4. 最终设置: Configuration -> MybatisConfiguration
        factory.setConfiguration(configuration);
    }

    @Bean
    @ConditionalOnMissingBean
    public SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory sqlSessionFactory) {
        // ❗️❗️❗️
        // 当前类的第二个 @Bean 方法 -> 注册mybatis-spring的SqlSessionTemplate
        // 大前提:  当用户不注入sqlSessionTemplate时当前方法生效

        // 1. 用户可通过 "mybatis-plus.executor-type" 指定执行器类型
        ExecutorType executorType = this.properties.getExecutorType();
        if (executorType != null) {
            return new SqlSessionTemplate(sqlSessionFactory, executorType);
        }
        // 2. 没有指定executorType时
        else {
            return new SqlSessionTemplate(sqlSessionFactory);
        }
    }


    /**
     * This will just scan the same base package as Spring Boot does. If you want more power, you can explicitly use
     * {@link org.mybatis.spring.annotation.MapperScan} but this will get typed mappers working correctly, out-of-the-box,
     * similar to using Spring Data JPA repositories.
     */
    public static class AutoConfiguredMapperScannerRegistrar implements BeanFactoryAware, ImportBeanDefinitionRegistrar {
        // 命名:
        // AutoConfigured MapperScanner Registrar = 自动配置的Mapper扫描器+注册表

        // ❗️❗️❗️ 当前类起作用的前提:
        // 项目中没有使用@MapperScan时 -> 当前类的 AutoConfiguredMapperScannerRegistrar#registerBeanDefinitions(..) 就会生效哦

        // 作用:
        // 重写 - ImportBeanDefinitionRegistrar#registerBeanDefinitions(..)

        private BeanFactory beanFactory;

        @Override
        public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {

            // 1. 无法确定自动配置包，自动映射器扫描已禁用
            // 关于: AutoConfigurationPackages的使用
            // 注解链:
            // @SpringBootApplication -> @EnableAutoConfiguration -> @AutoConfigurationPackage -> @Import(AutoConfigurationPackages.Registrar.class)
            // AutoConfigurationPackages.Registrar -> 向BeanDefinitionRegistry直接注册BeanDefinition ->
            // BeanDefinition的beanName就是AutoConfigurationPackages.class.getName(),beanClass就是BasePackages,并将启动类的package作为BasePackages的构造器参数
            if (!AutoConfigurationPackages.has(this.beanFactory)) {
                logger.debug("Could not determine auto-configuration package, automatic mapper scanning disabled.");
                return;
            }

            logger.debug("Searching for mappers annotated with @Mapper");

            List<String> packages = AutoConfigurationPackages.get(this.beanFactory);
            if (logger.isDebugEnabled()) {
                packages.forEach(pkg -> logger.debug("Using auto-configuration base package '{}'", pkg));
            }

            // 2. 使用 MapperScannerConfigurer -> 其中会用到 ClassPathMapperScanner#scan(..) 去扫描 上面查找出来的 packages 下面的Mapper接口出来哦
            BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(MapperScannerConfigurer.class);
            builder.addPropertyValue("processPropertyPlaceHolders", true); // 处理属性占位符
            // TODO 指定扫描@Mapper注解标注的接口 -> -> ❗️❗️❗️❗️❗️❗️
            // 将MP与mybatis-spring两个项目融合,指定了mybatis-spring总共的ClassPathMapperScanner扫描的基本路径是在SpringBoot启动类下
            // 同时扫描出的必须指定有@Mapper的才会被加入到IOC容器中,也就是说:用户的类必须标注有@Mapper
            // TODO 而如果是mybatis-spring的@MapperScan注解被使用,默认注解的情况下只要是接口就会被认为是Mapper接口哦
            // TODO 除非通过@MapperScan.annotationClass()或者@MapperScan.markerInterface添加额外的要求:比如要有指定的注解,实现指定的超类等等
            builder.addPropertyValue("annotationClass", Mapper.class);
            builder.addPropertyValue("basePackage", StringUtils.collectionToCommaDelimitedString(packages)); // 扫描的包路径basePackage
            BeanWrapper beanWrapper = new BeanWrapperImpl(MapperScannerConfigurer.class);
            Set<String> propertyNames = Stream.of(beanWrapper.getPropertyDescriptors()).map(PropertyDescriptor::getName).collect(Collectors.toSet());
            if (propertyNames.contains("lazyInitialization")) {
                // 2.1 兼容了mybatis.lazy-initialization配置
                // ${mybatis-plus.lazy-initialization:${mybatis.lazy-initialization:false}} 表示读取 mybatis-plus.lazy-initialization 属性
                // 如果没有在yaml中指定"mybatis-plus.lazy-initialization",就去读取"mybatis.lazy-initialization",要是还读取不到,就用默认的false
                builder.addPropertyValue("lazyInitialization", "${mybatis-plus.lazy-initialization:${mybatis.lazy-initialization:false}}");
            }
            if (propertyNames.contains("defaultScope")) {
                // Need to mybatis-spring 2.0.6+
                // 2.2 兼容 defaultScope 属性
                // ${mybatis-plus.mapper-default-scope:} 表示使用yaml配置中的"mybatis-plus.mapper-default-scope"作为MapperScannerConfigurer的defaultScope
                builder.addPropertyValue("defaultScope", "${mybatis-plus.mapper-default-scope:}");
            }
            // 3. 注册进去
            registry.registerBeanDefinition(MapperScannerConfigurer.class.getName(), builder.getBeanDefinition());
        }

        @Override
        public void setBeanFactory(BeanFactory beanFactory) {
            this.beanFactory = beanFactory;
        }
    }

    /**
     * If mapper registering configuration or mapper scanning configuration not present, this configuration allow to scan
     * mappers based on the same component-scanning path as Spring Boot itself.
     */
    @Configuration(proxyBeanMethods = false)
    @Import(AutoConfiguredMapperScannerRegistrar.class)
    // 1. MapperFactoryBean 是 mybatis-spring 项目的类 -> 通过@MapperScan就会生效
    // 2. MapperScannerConfigurer 是 MapperScannerConfigurer -> 通过@MapperScan就会生效
    @ConditionalOnMissingBean({MapperFactoryBean.class, MapperScannerConfigurer.class})
    public static class MapperScannerRegistrarNotFoundConfiguration implements InitializingBean {
        // 注入的一个内部的配置类 ->
        // note: ❗️❗️❗️ -> 当前类是否会被加入到ioc容器,取决于一件事 -> 用户的项目中是否使用@MapperScan注解

        @Override
        public void afterPropertiesSet() {
            logger.debug(
                "Not found configuration for registering mapper bean using @MapperScan, MapperFactoryBean and MapperScannerConfigurer.");
        }
    }
}
