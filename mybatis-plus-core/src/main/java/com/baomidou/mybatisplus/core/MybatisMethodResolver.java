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

import org.apache.ibatis.builder.annotation.MethodResolver;

import java.lang.reflect.Method;

/**
 * 继承 {@link MethodResolver}
 *
 * @author miemie
 * @since 2019-01-05
 */
public class MybatisMethodResolver extends MethodResolver {
    // 位于: core模块

    // 作用:
    // 在MapperAnnotationBuilder#parser(..)解析过程中抛出IncompleteElementException异常时
    // 包装解析的方法method,用来做解析的annotationBuilder
    // MybatisMethodResolver 主要就是将 MapperAnnotationBuilder 替换为 MybatisMapperAnnotationBuilder

    // TODO 将原先的 MapperAnnotationBuilder 替换为 MybatisMapperAnnotationBuilder
    private final MybatisMapperAnnotationBuilder annotationBuilder;
    private final Method method;

    public MybatisMethodResolver(MybatisMapperAnnotationBuilder annotationBuilder, Method method) {
        super(null, null);
        this.annotationBuilder = annotationBuilder;
        this.method = method;
    }

    @Override
    public void resolve() {
        // 继续重新尝试解析
        annotationBuilder.parseStatement(method);
    }
}
