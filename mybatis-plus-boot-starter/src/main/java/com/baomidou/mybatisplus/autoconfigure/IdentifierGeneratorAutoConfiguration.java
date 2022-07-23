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

import com.baomidou.mybatisplus.core.incrementer.DefaultIdentifierGenerator;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.commons.util.InetUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * @author nieqiurong 2021/1/29
 * @since 3.4.3
 */
@Lazy
@Configuration(proxyBeanMethods = false)
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public class IdentifierGeneratorAutoConfiguration {
    // 特点: 懒加载\不对配置类进行代理


    // 导入的前提: 存在InetUtils的
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(InetUtils.class)
    public static class InetUtilsAutoConfig {

        @Bean
        @ConditionalOnMissingBean
        public IdentifierGenerator identifierGenerator(InetUtils inetUtils) {
            // 只有当容器中不存在IdentifierGenerator时才会提前创建
            // ❗️❗️❗️❗️❗️❗️❗️❗️❗️
            // 有一点:一定要认识到,那就是
            // 项目中的组件类 > 外部配置的spring.factories的配置类 > 项目中的@Bean修饰的方法 > 外部配置的spring.factories的配置类中的@Bean修饰的方法
            // 因此用户只需要向SpringBoot的项目中注入IdentifierGenerator的bean
            // 就会使得这里通过外部配置spring.factories的IdentifierGeneratorAutoConfiguration中的IdentifierGenerator在@ConditionalOnMissingBean的处理下不被导入ioc容器哦

            // 因此Mybatis-plus默认的IdentifierGenerator就是DefaultIdentifierGenerator
            // 然后支持产生UUID,以及雪花ID
            return new DefaultIdentifierGenerator(inetUtils.findFirstNonLoopbackAddress());
        }

    }

}
