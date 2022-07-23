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
package com.baomidou.mybatisplus.annotation;

import java.lang.annotation.*;

/**
 * 表字段逻辑处理注解（逻辑删除）
 *
 * @author hubin
 * @since 2017-09-09
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.ANNOTATION_TYPE})
public @interface TableLogic {
    // 用在属性上表示逻辑删除
    // 可以全局配置 -- 逻辑删除和没有被逻辑删除的字段值

    /**
     * 默认逻辑未删除值（该值可无、会自动获取全局配置）
     */
    String value() default "";

    /**
     * 默认逻辑删除值（该值可无、会自动获取全局配置）
     */
    String delval() default "";

    // 说明:
    //
    // 只对自动注入的sql起效:
    //
    // 插入: 不作限制
    // 查找: 追加where条件过滤掉已删除数据,且使用 wrapper.entity 生成的where条件会忽略该字段
    // 更新: 追加where条件防止更新到已删除数据,且使用 wrapper.entity 生成的where条件会忽略该字段
    // 删除: 转变为 更新
    // 例如:
    //
    // 删除: update user set deleted=1 where id = 1 and deleted=0
    // 查找: select id,name,deleted from user where deleted=0
    // 字段类型支持说明:
    //
    // 支持所有数据类型(推荐使用 Integer,Boolean,LocalDateTime)
    // 如果数据库字段使用datetime,逻辑未删除值和已删除值支持配置为字符串null,另一个值支持配置为函数来获取值如now()
    // 附录:
    //
    // 逻辑删除是为了方便数据恢复和保护数据本身价值等等的一种方案，但实际就是删除。
    // 如果你需要频繁查出来看就不应使用逻辑删除，而是以一个状态去表示。
}
