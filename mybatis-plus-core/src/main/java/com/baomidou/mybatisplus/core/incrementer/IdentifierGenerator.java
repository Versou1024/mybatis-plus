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


import com.baomidou.mybatisplus.core.toolkit.IdWorker;


/**
 * Id生成器接口
 *
 * @author  nieqiuqiu
 * @since 2019-10-15
 * @since 3.3.0
 */
public interface IdentifierGenerator {
    // 生成ID的接口
    // 只带一个默认方法--生成UUID

    // note: 生效前提
    // 1. 要求ParameterHandler使用MybatisParameterHandler实现类
    // 2. 要求MP方法如下:
    //      2.1 形参是实体类的话,且标注有@Param(Constants.ENTITY),就会生效 -> 对应的就是 -> MP自动注入的方法即BaseMapper下的方法,如果形参是实体类的话,就习惯设置@Param(Constants.ENTITY)
    //      2.2 mapper方法只有一个形参,且是实体类的,且没有标注@Param注解
    //      note: ❗️❗️❗️ 只有以上两种Mapper方法会生效,其余情况MetaObjectHandler都无法生效哦
    // 3. 满足MP方法后,要求实体类上有主键才可以
    // 4. 满足有主键后,要求对应的IdType不能是 IdType.AUTO自增 | IdType.NONE没有 | IdType.INPUT手动输入
    // 5. 满足上述之后,并且实体类对象的id值为null才可使用 IdentifierGenerator#nextId(..) 或者 IdentifierGenerator#nextUUID(..)

    /**
     * 生成Id
     *
     * @param entity 实体
     * @return id
     */
    Number nextId(Object entity);

    /**
     * 生成uuid
     *
     * @param entity 实体
     * @return uuid
     */
    default String nextUUID(Object entity) {
        // 生成UUID的方式是默认方法 -- 不需要重写
        return IdWorker.get32UUID();
    }
}
