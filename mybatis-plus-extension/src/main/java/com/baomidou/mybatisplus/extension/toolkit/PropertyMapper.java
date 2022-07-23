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
package com.baomidou.mybatisplus.extension.toolkit;

import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.StringPool;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author miemie
 * @since 2020-06-15
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class PropertyMapper {
    private final Properties delegate;

    public static PropertyMapper newInstance(Properties properties) {
        return new PropertyMapper(properties);
    }

    public Set<String> keys() {
        return delegate.stringPropertyNames();
    }

    public PropertyMapper whenNotBlank(String key, Consumer<String> consumer) {
        String value = delegate.getProperty(key);
        if (StringUtils.isNotBlank(value)) {
            consumer.accept(value);
        }
        return this;
    }

    public <T> PropertyMapper whenNotBlank(String key, Function<String, T> function, Consumer<T> consumer) {
        String value = delegate.getProperty(key);
        if (StringUtils.isNotBlank(value)) {
            consumer.accept(function.apply(value));
        }
        return this;
    }

    /**
     * mp 内部规则分组
     *
     * @return 分组
     */
    public Map<String, Properties> group(String group) {
        // 目前唯一调用处: MybatisPlusInterceptor#setProperties(..) 中 -> PropertyMapper#grpup("@")

        // 1. 拿到委托的Properties类型的delegate中的key集合
        final Set<String> keys = keys();
        // 2. 过滤出key的是以group开头的key集合 -> 目前group就是"@"
        Set<String> inner = keys.stream().filter(i -> i.startsWith(group)).collect(Collectors.toSet());
        // 3. inner为空提前返回empty map
        if (CollectionUtils.isEmpty(inner)) {
            return Collections.emptyMap();
        }
        // 4. 处理 -> 别名
        // 比如 key有一个是 "@page" -> 那么inner就是@page,之后将page作为别名开始查询
        Map<String, Properties> map = CollectionUtils.newHashMap();
        inner.forEach(i -> {
            // 4.1 为 inner= "@page" 新建一个 Properties()
            Properties p = new Properties();
            // 4.2 inner 去掉"@"加上":",结果就是"page:"
            String key = i.substring(group.length()) + StringPool.COLON;
            int keyIndex = key.length();
            // 4.3 委托的Properties类型的delegate中的key集合中以 :page:" 开头的属性集合哦,剔除"page:"后的value集合
            keys.stream().filter(j -> j.startsWith(key)).forEach(j -> p.setProperty(j.substring(keyIndex), delegate.getProperty(j)));
            map.put(delegate.getProperty(i), p);
        });
        return map;

        // 举例:
        // delegate的键值对情况如下:
        // @page = com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor
        // page:limit = 100
        // page:size = 10
        // page:current = 1
        // ....
        // 那么返回的结果 Map<String, Properties>
        // 其中有一个Entry项的
        //      key就是 "com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor"
        //      value就是 limit = 100 size = 10 current = 1
    }
}
