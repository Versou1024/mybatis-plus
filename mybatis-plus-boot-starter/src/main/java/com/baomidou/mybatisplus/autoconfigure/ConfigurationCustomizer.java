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

/**
 * Callback interface that can be customized a {@link MybatisConfiguration} object generated on auto-configuration.
 *
 * @author Kazuki Shimizu
 * @since 1.2.1
 */
@FunctionalInterface
public interface ConfigurationCustomizer {
    // 类似: MybatisPlusPropertiesCustomizer

    // 命名:
    // Configuration Customizer = 对MybatisConfiguration的自定义编辑器

    // 作用:
    // 用户通过在项目中实现ConfigurationCustomizer接口完成对MybatisConfiguration的定制化处理 [note: 需要将实现类加入到ioc容器中,否则无效]
    // MybatisPlusAutoConfiguration类将会在初始化方法afterPropertiesSet()中回调用户自定义的ConfigurationCustomizer类 -> 传入 MybatisConfiguration

    // 实际上:
    // 作用: ❗️❗️❗️❗️❗️❗️
    // 接受Spring中以"mybatis-plus"开头的yaml配置,存储在当前类中
    // ❗️❗️❗️❗️❗️❗️❗️❗️❗️ [yaml配置可能会失效]
    // 真的恶心 -> 通过观察源码我们可以发现,当我们将相关配置写在yaml中,会被解析加载到MybatisPlusProperties中
    // -> 而MybatisPlusProperties将会被MybatisPlusAutoConfiguration的构造器依赖注入进去 [只有这个地方使用到]
    // -> 而 @Bean @ConditionalOnMissingBean SqlSessionFactory(..) 创建SqlSessionFactory的前提是,用户没有注入SqlSessionFactory
    // -> 因此用户如果注入 SqlSessionFactory [多数据源的情况,或者用户想要定制的情况下]  -> yaml 配置文件的大部分属性就不会配置到SqlSessionFactory上,因此就会失效
    // -> 同样会导致用户自定义的 ConfigurationCustomizer 失效


    /**
     * Customize the given a {@link MybatisConfiguration} object.
     *
     * @param configuration the configuration object to customize
     */
    void customize(MybatisConfiguration configuration);
}
