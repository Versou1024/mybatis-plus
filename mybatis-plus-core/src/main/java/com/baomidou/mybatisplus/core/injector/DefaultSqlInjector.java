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
package com.baomidou.mybatisplus.core.injector;

import com.baomidou.mybatisplus.core.injector.methods.*;
import com.baomidou.mybatisplus.core.metadata.TableInfo;

import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;


/**
 * SQL 默认注入器
 *
 * @author hubin
 * @since 2018-04-10
 */
public class DefaultSqlInjector extends AbstractSqlInjector {
    // 位于: core模块下的injector注入器包下

    // 作用:
    // 继承AbstractSqlInjector -> 实现 getMethodList(..) 提供需要注入的 ISqlInjector

    @Override
    public List<AbstractMethod> getMethodList(Class<?> mapperClass, TableInfo tableInfo) {
        // 1. 获取mp内置的所有的AbstractMethod

        // 1.1 通用的AbstractMethod
        Stream.Builder<AbstractMethod> builder = Stream.<AbstractMethod>builder()
            .add(new Insert())
            .add(new Delete())
            .add(new DeleteByMap())
            .add(new Update())
            .add(new SelectByMap())
            .add(new SelectCount())
            .add(new SelectMaps())
            .add(new SelectMapsPage())
            .add(new SelectObjs())
            .add(new SelectList())
            .add(new SelectPage());

        // 1.2 实体类中是否有主键,有主键的情况下,添加适用于主键id上的AbstractMethod
        if (tableInfo.havePK()) {
            builder.add(new DeleteById())
                .add(new DeleteBatchByIds())
                .add(new UpdateById())
                .add(new SelectById())
                .add(new SelectBatchByIds());
        } else {
            logger.warn(String.format("%s ,Not found @TableId annotation, Cannot use Mybatis-Plus 'xxById' Method.",
                tableInfo.getEntityType()));
        }
        return builder.build().collect(toList());
    }
}
