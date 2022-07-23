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
package com.baomidou.mybatisplus.core.incrementer;

import com.baomidou.mybatisplus.core.toolkit.Sequence;

import java.net.InetAddress;

/**
 * 默认生成器
 *
 * @author  nieqiuqiu
 * @since 2019-10-15
 * @since 3.3.0
 */
public class DefaultIdentifierGenerator implements IdentifierGenerator {
    // 位于: com.baomidou.mybatisplus.core.incrementer = core项目的incrementer包

    // 命名:
    // Default IdentifierGenerator = 默认的id生成器

    // 使用算法:
    // DefaultIdentifierGenerator 生成器的默认实现 -- 分布式雪花id生成器

    // 起作用的条件:
    // a. 用户没有注入SqlSessionFactory时,用户也没有向ioc容器注入IdentifierGenerator
    //      GlobalConfig.IdentifierGenerator() 就会是默认的 DefaultIdentifierGenerator
    // b. 用户注入的SqlSessionFactory,使用的是 MybatisSqlSessionFactoryBean#getObject() 时没有注入 GlobalConfig 或者注入的 GlobalConfig 没有设置 IdentifierGenerator
    //     虽然在GlobalConfig.IdentifierGenerator()默认是null,但是在后续的 MybatisSqlSessionFactoryBuild.build(Configuration) -> globalConfig.setIdentifierGenerator(identifierGenerator) 就会是设置为默认的 DefaultIdentifierGenerator

    // 代码:
    // MybatisSqlSessionFactoryBean#getObject() -> MybatisSqlSessionFactoryBean#afterPropertiesSet(..) ->
    // MybatisSqlSessionFactoryBuilder#build(Configuration...) -> 就会设置默认的id生成器: DefaultIdentifierGenerator

    private final Sequence sequence;

    public DefaultIdentifierGenerator() {
        // 无ip - Sequence 中自动查找ip
        this.sequence = new Sequence(null);
    }

    public DefaultIdentifierGenerator(InetAddress inetAddress) {
        // 指定ip -> 需要通过ip确定雪花id中的workerId/dataCenterId
        this.sequence = new Sequence(inetAddress);
    }

    public DefaultIdentifierGenerator(long workerId, long dataCenterId) {
        // 直接指定 workerId/dataCenterId [就不需要ip啦]
        this.sequence = new Sequence(workerId, dataCenterId);
    }

    public DefaultIdentifierGenerator(Sequence sequence) {
        this.sequence = sequence;
    }

    @Override
    public Long nextId(Object entity) {
        return sequence.nextId();
    }
}
