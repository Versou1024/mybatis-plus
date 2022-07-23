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
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.ibatis.scripting.LanguageDriver;
import org.apache.ibatis.session.ExecutorType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.IOException;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * Configuration properties for MyBatis.
 *
 * @author Eddú Meléndez
 * @author Kazuki Shimizu
 */
@Data
@Accessors(chain = true)
@ConfigurationProperties(prefix = Constants.MYBATIS_PLUS) // 关键是 -- 前缀为 mybatis-plus
public class MybatisPlusProperties {
    // 位于: com.baomidou.mybatisplus.autoconfigure

    // 配置:
    // additional-spring-configuration-metadata.json的mybatis-plus组

    // 作用: ❗️❗️❗️❗️❗️❗️
    // 接受Spring中以"mybatis-plus"开头的yaml配置,存储在当前类中
    // ❗️❗️❗️❗️❗️❗️❗️❗️❗️ [yaml配置可能会失效]
    // 真的恶心 -> 通过观察源码我们可以发现,当我们将相关配置写在yaml中,会被解析加载到MybatisPlusProperties中
    // -> 而MybatisPlusProperties将会被MybatisPlusAutoConfiguration的构造器依赖注入进去 [只有这个地方使用到]
    // -> 而 @Bean @ConditionalOnMissingBean SqlSessionFactory(..) 创建SqlSessionFactory的前提是,用户没有注入SqlSessionFactory
    // -> 因此用户如果注入 SqlSessionFactory [多数据源的情况,或者用户想要定制的情况下]  -> yaml 配置文件的大部分属性就不会配置到SqlSessionFactory上,因此就会失效
    // -> 同样会导致用户自定义的 ConfigurationCustomizer 失效
    // but

    // 作用:
    // 接受Spring中以"mybatis-plus"开头的yaml配置,存储在当前类中 -> 然后将这些信息配置到MybatisConfiguration中哦 ❗️❗️❗️

    private static final ResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();

    // 如果需要加载mybatis.xml文件即checkConfigLocation为true
    // 用户就需要指定给了: MyBatis xml 配置文件的位置。
    private String configLocation;

    // Mapper.xml的文件位置
    // 自3.1.2之后,设置了默认扫描的路径: classpath*:/mapper/**/*.xml
    private String[] mapperLocations = new String[]{"classpath*:/mapper/**/*.xml"};

    // 指定package包来搜索类型别名。 （包分隔符为“,;\t\n”）
    // 将注册到Configuration中的TypeAliasRegistry中
    private String typeAliasesPackage;

    // 搭配: typeAliasesPackage
    // 在扫描typeAliasesPackage下的类时,要求类实现typeAliasesSuperType,然后才可以注册到TypeAliasRegistry中
    private Class<?> typeAliasesSuperType;

    // 指定用于搜索类型处理程序TypeHandler的包。 （包分隔符为“,;\t\n”）
    // 当前还有一个默认条件: 就是实现了TypeHandler/BaseTypeHandler
    private String typeHandlersPackage;

    // 指示是否执行 MyBatis xml 配置文件的存在检查
    // 可以通过: "mybatis-plus.check-config-location" 配置这个值 -> 一般不需要配置,因为下载不需要mybatis.xml文件
    private boolean checkConfigLocation = false;

    // 指定执行器类型
    private ExecutorType executorType;

    // The default scripting language driver class. (Available when use together with mybatis-spring 2.0.2+)
    // 如果设置了这个,你会至少失去几乎所有 mp 提供的功能
    // 默认就是 -> MybatisXMLLanguageDriver
    private Class<? extends LanguageDriver> defaultScriptingLanguageDriver;

    // MyBatis 配置的外部化属性。
    // 将会被设置到 Configuration 中的 variables 变量中
    private Properties configurationProperties;

    // 用于自定义默认设置的配置对象。如果指定了configLocation ，则不使用此属性。
    @NestedConfigurationProperty
    private MybatisConfiguration configuration;

    // 枚举包扫描 -> 搜索的默认条件: 首先是枚举类,然后有实现IEnum或者有@Enumvalue标注的在字段上
    private String typeEnumsPackage;

    // 全局配置
    @NestedConfigurationProperty
    private GlobalConfig globalConfig = GlobalConfigUtils.defaults();


    public Resource[] resolveMapperLocations() {
        return Stream.of(Optional.ofNullable(this.mapperLocations).orElse(new String[0]))
            .flatMap(location -> Stream.of(getResources(location))).toArray(Resource[]::new);
    }

    private Resource[] getResources(String location) {
        try {
            return resourceResolver.getResources(location);
        } catch (IOException e) {
            return new Resource[0];
        }
    }
}
