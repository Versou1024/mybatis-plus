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
package com.baomidou.mybatisplus.core.plugins;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.toolkit.*;
import lombok.Builder;
import lombok.Data;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * @author miemie
 * @since 2020-07-31
 */
public abstract class InterceptorIgnoreHelper {
    // 位于: com.baomidou.mybatisplus.core.plugins = core模块的plugins下

    // 作用:
    // 与Mapper接口的上的@InterceptorIgnore注解有关

    // SQL 解析缓存
    // value 是 @InterceptorIgnore 对应的 InterceptorIgnoreCache
    // 当@InterceptorCache在mapper接口上时,key是class的name -> 当@InterceptorCache在mapper方法上时,是mappedStatement的ID,
    private static final Map<String, InterceptorIgnoreCache> INTERCEPTOR_IGNORE_CACHE = new ConcurrentHashMap<>();

    /**
     * 初始化缓存
     * <p>
     * Mapper 上 InterceptorIgnore 注解信息
     *
     * @param mapperClass Mapper Class
     */
    public synchronized static InterceptorIgnoreCache initSqlParserInfoCache(Class<?> mapperClass) {
        // 1. 获取mapper接口上的@InterceptorIgnore注解
        InterceptorIgnore ignore = mapperClass.getAnnotation(InterceptorIgnore.class);
        if (ignore != null) {
            // 2. 非空的的情况下,以mapperClass.getName()为key,以对应interceptorIgnore注解构建一个InterceptorIgnoreCache,然后存入缓存
            String key = mapperClass.getName();
            InterceptorIgnoreCache cache = buildInterceptorIgnoreCache(key, ignore);
            INTERCEPTOR_IGNORE_CACHE.put(key, cache);
            return cache;
        }
        return null;
    }

    /**
     * 初始化缓存
     * <p>
     * Mapper#method 上 InterceptorIgnore 注解信息
     *
     * @param mapperAnnotation Mapper Class Name
     * @param method           Method
     */
    public static void initSqlParserInfoCache(InterceptorIgnoreCache mapperAnnotation, String mapperClassName, Method method) {
        // 1. 拿到mapepr方法上的@InterceptorIgnore注解
        // note: 注意和传递进来的形参 mapperAnnotation 区分开来 -> 形参mapperAnnotation还是从mapper接口的类上查找的@InterceptorIgnore注解转换的InterceptorIgnoreCache
        InterceptorIgnore ignore = method.getAnnotation(InterceptorIgnore.class);
        // 2.
        // key  = mapperClassName + "." + 方法名 -> 方法上和注解上都存在@InterceptorIgnore
        // name = mapperClassName + "#" + 方法名 -> 只有方法上存在@InterceptorIgnore
        String key = mapperClassName.concat(StringPool.DOT).concat(method.getName());
        String name = mapperClassName.concat(StringPool.HASH).concat(method.getName());
        // 3. mapepr方法上存在@InterceptorIgnore注解 -> [就忽略类上的@InterceptorIgnore注解对应的mapperAnnotation]
        if (ignore != null) {
            // 3.1 根据方法上@InterceptorIgnore注解的构建InterceptorIgnoreCache [此刻没有进行缓存操作哦]
            InterceptorIgnoreCache methodCache = buildInterceptorIgnoreCache(name, ignore);
            // 3.2 当类上的@InterceptorIgnore不存在时, 缓存, 不过此时的缓存的key是 mapperClassName + "." + 方法名 ❗️❗️❗️
            if (mapperAnnotation == null) {
                INTERCEPTOR_IGNORE_CACHE.put(key, methodCache);
                return;
            }
            // 3.3 当类上的@InterceptorIgnore存在时存入缓存, 不过此时的缓存的key是 mapperClassName + "#" + 方法名 ❗️❗️❗️
            // 且由于类上的@InterceptorIgnore也存在,需要通过chooseCache(..)进行 -> 类上和方法上的@InterceptorIgnore互补
            INTERCEPTOR_IGNORE_CACHE.put(key, chooseCache(mapperAnnotation, methodCache));
        }
    }

    public static boolean willIgnoreTenantLine(String id) {
        return willIgnore(id, InterceptorIgnoreCache::getTenantLine);
    }

    public static boolean willIgnoreDynamicTableName(String id) {
        return willIgnore(id, InterceptorIgnoreCache::getDynamicTableName);
    }

    public static boolean willIgnoreBlockAttack(String id) {
        return willIgnore(id, InterceptorIgnoreCache::getBlockAttack);
    }

    public static boolean willIgnoreIllegalSql(String id) {
        return willIgnore(id, InterceptorIgnoreCache::getIllegalSql);
    }

    public static boolean willIgnoreDataPermission(String id) {
        return willIgnore(id, InterceptorIgnoreCache::getDataPermission);
    }

    public static boolean willIgnoreSharding(String id) {
        return willIgnore(id, InterceptorIgnoreCache::getSharding);
    }

    public static boolean willIgnoreOthersByKey(String id, String key) {
        return willIgnore(id, i -> CollectionUtils.isNotEmpty(i.getOthers()) && i.getOthers().getOrDefault(key, false));
    }

    public static boolean willIgnore(String id, Function<InterceptorIgnoreCache, Boolean> function) {
        InterceptorIgnoreCache cache = INTERCEPTOR_IGNORE_CACHE.get(id);
        if (cache == null) {
            cache = INTERCEPTOR_IGNORE_CACHE.get(id.substring(0, id.lastIndexOf(StringPool.DOT)));
        }
        if (cache != null) {
            Boolean apply = function.apply(cache);
            return apply != null && apply;
        }
        return false;
    }

    private static InterceptorIgnoreCache chooseCache(InterceptorIgnoreCache mapper, InterceptorIgnoreCache method) {
        // 类上和方法上的@InterceptorIgnore都存在时,进行一个互补操作
        // 互补规则: 以@InterceptorIgnore.tenantLine()属性为例,
        //  a:如果方法和类上都存在对@InterceptorIgnore.tenantLine()属性的设置,那么就以方法上的为准
        //  b:如果方法上没有对@InterceptorIgnore.tenantLine()属性设置,为默认的""即对应Boolean就是null,但类上的注解对该属性有设置为true或者false,那就以类上的为准
        return InterceptorIgnoreCache.builder()
            .tenantLine(chooseBoolean(mapper.getTenantLine(), method.getTenantLine()))
            .dynamicTableName(chooseBoolean(mapper.getDynamicTableName(), method.getDynamicTableName()))
            .blockAttack(chooseBoolean(mapper.getBlockAttack(), method.getBlockAttack()))
            .illegalSql(chooseBoolean(mapper.getIllegalSql(), method.getIllegalSql()))
            .dataPermission(chooseBoolean(mapper.getDataPermission(), method.getDataPermission()))
            .sharding(chooseBoolean(mapper.getSharding(), method.getSharding()))
            .others(chooseOthers(mapper.getOthers(), method.getOthers()))
            .build();
    }

    private static InterceptorIgnoreCache buildInterceptorIgnoreCache(String name, InterceptorIgnore ignore) {
        // 构建: InterceptorIgnoreCache -> 主要将@InterceptorIgnore的String格式转为Boolean格式,存入到InterceptorIgnoreCache

        return InterceptorIgnoreCache.builder()
            .tenantLine(getBoolean("tenantLine", name, ignore.tenantLine()))
            .dynamicTableName(getBoolean("dynamicTableName", name, ignore.dynamicTableName()))
            .blockAttack(getBoolean("blockAttack", name, ignore.blockAttack()))
            .illegalSql(getBoolean("illegalSql", name, ignore.illegalSql()))
            .dataPermission(getBoolean("dataPermission", name, ignore.dataPermission()))
            .sharding(getBoolean("sharding", name, ignore.sharding()))
            .others(getOthers(name, ignore.others()))
            .build();
    }

    private static Boolean getBoolean(String node, String name, String value) {
        //  @InterceptorIgnore 中属性都是String格式的,主要是用来支持true 和 false , 1 和 0 , on 和 off 的String格式作为开关

        if (StringUtils.isBlank(value)) {
            return null;
        }
        if (StringPool.ONE.equals(value) || StringPool.TRUE.equals(value) || StringPool.ON.equals(value)) {
            return true;
        }
        if (StringPool.ZERO.equals(value) || StringPool.FALSE.equals(value) || StringPool.OFF.equals(value)) {
            return false;
        }
        throw ExceptionUtils.mpe("unsupported value \"%s\" by `@InterceptorIgnore#%s` on top of \"%s\"", value, node, name);
    }

    private static Map<String, Boolean> getOthers(String name, String[] values) {
        // 注意: 其他非内置的插件是否需要被忽略 -> 就需要通过 @InterceptorCache.others 属性指定
        // 格式:  "key"+"@"+可选项[false,true,1,0,on,off] 例如: "xxx@1" 或 "xxx@true" 或 "xxx@on"
        // 比如: 有一个plugins为TestInterceptor -> 那么通过 Test@true 或者 Test@1 等等表示需要忽略这个 插件plugin
        if (ArrayUtils.isEmpty(values)) {
            return null;
        }
        Map<String, Boolean> map = CollectionUtils.newHashMapWithExpectedSize(values.length);
        for (String s : values) {
            int index = s.indexOf(StringPool.AT);
            Assert.isTrue(index > 0, "unsupported value \"%s\" by `@InterceptorIgnore#others` on top of \"%s\"", s, name);
            String key = s.substring(0, index);
            Boolean value = getBoolean("others", name, s.substring(index + 1));
            map.put(key, value);
        }
        return map;
    }

    /**
     * mapper#method 上的注解 优先级大于 mapper 上的注解
     */
    private static Boolean chooseBoolean(Boolean mapper, Boolean method) {
        // mapper#method 上的@InterceptorIgnoe注解 优先级大于 mapper 上的@InterceptorIgnoe注解

        if (mapper == null && method == null) {
            return null;
        }
        if (method != null) {
            return method;
        }
        return mapper;
    }

    private static Map<String, Boolean> chooseOthers(Map<String, Boolean> mapper, Map<String, Boolean> method) {
        // 类上和方法上的@InterceptorIgnore都存在时,对@InterceptorIgnore.other()进行一个互补操作
        // 规则:
        // 1. 类上和方法上的@InterceptorIgnore.other()都是空的,返回null
        // 2. 类上和方法上的@InterceptorIgnore.other()有一个是空的,另一个非空,就以非空的为主
        // 3.  类上和方法上的@InterceptorIgnore.other()都不是空的
        //      3.1 遍历类上和方法的@InterceptorIgnore.other()合并 -> 对于key冲突的,首先以方法的上为准
        boolean emptyMapper = CollectionUtils.isEmpty(mapper);
        boolean emptyMethod = CollectionUtils.isEmpty(method);
        if (emptyMapper && emptyMethod) {
            return null;
        }
        if (emptyMapper) {
            return method;
        }
        if (emptyMethod) {
            return mapper;
        }
        Set<String> mapperKeys = mapper.keySet();
        Set<String> methodKeys = method.keySet();
        Set<String> keys = new HashSet<>(mapperKeys.size() + methodKeys.size());
        keys.addAll(methodKeys);
        keys.addAll(mapperKeys);
        Map<String, Boolean> map = CollectionUtils.newHashMapWithExpectedSize(keys.size());
        methodKeys.forEach(k -> map.put(k, chooseBoolean(mapper.get(k), method.get(k))));
        return map;
    }

    @Data
    @Builder
    public static class InterceptorIgnoreCache {
        // 简述:
        // 内部类 作为@InterceptorIgnore接口的属性封装类

        // 作用:
        // @InterceptorIgnore中属性都是String格式的,主要是用来支持true 和 false , 1 和 0 , on 和 off 的String格式作为开关
        // InterceptorIgnoreCache类的作用就是封装为Boolean格式,以帮助直接使用
        private Boolean tenantLine;
        private Boolean dynamicTableName;
        private Boolean blockAttack;
        private Boolean illegalSql;
        private Boolean dataPermission;
        private Boolean sharding;
        private Map<String, Boolean> others;
    }
}
