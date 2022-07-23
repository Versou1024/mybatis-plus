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

import com.baomidou.mybatisplus.core.mapper.Mapper;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.core.toolkit.ReflectionKit;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;

import java.util.List;
import java.util.Set;

/**
 * SQL 自动注入器
 *
 * @author hubin
 * @since 2018-04-07
 */
public abstract class AbstractSqlInjector implements ISqlInjector {
    // 位于: com.baomidou.mybatisplus.core.injector = core模块下的injector注入器package中

    // 命名:
    // Abstract Sql Injector = 抽象的sql注入器

    // 作用:
    // ❗️❗️❗️ SQL抽象自动注入器,帮助指定的Mapper接口注入抽象的sql方法 -> MP的核心:自动注入了单表的CRUD操作

    // 对于用户而言:
    // AbstractSqlInjector 有一个实现类就是 DefaultSqlInjector ,用户可以通过实现 DefaultSqlInjector 然后在 super.getMethodList(..)
    // 上去扩展添加自己的 AbstractMethod --> 当然用户还需要注意将自定义的sql注入器存入ioc容器中

    protected final Log logger = LogFactory.getLog(this.getClass());

    @Override
    public void inspectInject(MapperBuilderAssistant builderAssistant, Class<?> mapperClass) {
        // 1.  获取mapperClass继承关系上的Mapper<?>的泛型信息 -> 该泛型信息就是对应的PO类
        Class<?> modelClass = ReflectionKit.getSuperClassGenericType(mapperClass, Mapper.class, 0);
        if (modelClass != null) {
            // 1.1 mapperClass的全限定类名
            String className = mapperClass.toString();
            // 1.2 获取GlobalConfig中已注入CRUD的Mapper缓存信息
            Set<String> mapperRegistryCache = GlobalConfigUtils.getMapperRegistryCache(builderAssistant.getConfiguration());
            // 1.3 没有解析过,就需要第一次去解析去CRUD操作
            if (!mapperRegistryCache.contains(className)) {
                // 1.3.1 拿到TableInfo -> [❗️❗️❗️ @TableName @TableId @TableLogic @Version 等信息都在这里解析出来] -> 也就是一个实体类modelClass对应一个tableInfo
                TableInfo tableInfo = TableInfoHelper.initTableInfo(builderAssistant, modelClass);
                // 1.3.2  ❗️❗️❗️ 抽象方法 -> 获取当前MapperClass需要注入的方法
                List<AbstractMethod> methodList = this.getMethodList(mapperClass, tableInfo);
                // 1.3.3 调用MP需要注入的方法对应的 AbstractMethod.inject()
                if (CollectionUtils.isNotEmpty(methodList)) {
                    // 1.3.3.1 循环注入自定义方法
                    // 传入的是: builderAssistant mapperClass modelClass tableInfo
                    methodList.forEach(m -> m.inject(builderAssistant, mapperClass, modelClass, tableInfo));
                } else {
                    logger.debug(mapperClass.toString() + ", No effective injection method was found.");
                }
                // 1.3.4 标记: 已经注入CRUD操作的mapper接口
                mapperRegistryCache.add(className);
            }
        }
    }

    /**
     * <p>
     * 获取 注入的方法
     * </p>
     *
     * @param mapperClass 当前mapper
     * @return 注入的方法集合
     * @since 3.1.2 add  mapperClass
     */
    public abstract List<AbstractMethod> getMethodList(Class<?> mapperClass,TableInfo tableInfo);

}
