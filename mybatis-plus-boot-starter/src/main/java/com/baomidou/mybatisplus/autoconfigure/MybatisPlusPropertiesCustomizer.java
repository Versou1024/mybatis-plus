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

/**
 * Callback interface that can be customized a {@link MybatisPlusProperties} object generated on auto-configuration.
 *
 * <p> 慎用 </p>
 *
 * @author miemie
 * @since 3.1.2
 */
@FunctionalInterface
public interface MybatisPlusPropertiesCustomizer {
    // 命名:
    // MybatisPlusProperties Customizer = 对MybatisPlusProperties的自定义编辑器

    // 作用:
    // 用户通过在项目中实现MybatisPlusPropertiesCustomizer完成对MybatisPlusProperties的定制化 [note: 需要将实现类加入到ioc容器中,否则无效]

    // 结论:
    // ❗️❗️❗️因此建议用户通过 MybatisPlusPropertiesCustomizer -- 修改 MybatisPlusProperties

    /**
     * Customize the given a {@link MybatisPlusProperties} object.
     *
     * @param properties the MybatisPlusProperties object to customize
     */
    void customize(MybatisPlusProperties properties);
}
