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

import com.baomidou.mybatisplus.core.toolkit.AES;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.env.OriginTrackedMapPropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.SimpleCommandLinePropertySource;

import java.util.HashMap;

/**
 * 安全加密处理器
 *
 * @author hubin
 * @since 2020-05-23
 */
public class SafetyEncryptProcessor implements EnvironmentPostProcessor {
    // 命名
    // Safety Encrypt Processor = 安全加密处理器

    // 起作用的原因:
    // Spring.factories 中有一行:
    // org.springframework.boot.env.EnvironmentPostProcessor = com.baomidou.mybatisplus.autoconfigure.SafetyEncryptProcessor
    // 其实主要还是:
    // SpringBoot下的
    // class ConfigFileApplicationListener implements EnvironmentPostProcessor, SmartApplicationListener, Ordered
    // 	    @Override
    //	    public void onApplicationEvent(ApplicationEvent event) {
    //	    	if (event instanceof ApplicationEnvironmentPreparedEvent) {
    //              // 在 refresh ApplicationContext之前发布ApplicationEnvironmentPreparedEvent事件,允许用户自定义应用程序的Environment
    //	    		onApplicationEnvironmentPreparedEvent((ApplicationEnvironmentPreparedEvent) event);
    //	    	}
    //	    	if (event instanceof ApplicationPreparedEvent) {
    //	    		onApplicationPreparedEvent(event);
    //	    	}
    //	    }
    //
    //	    private void onApplicationEnvironmentPreparedEvent(ApplicationEnvironmentPreparedEvent event) {
    //	    	List<EnvironmentPostProcessor> postProcessors = loadPostProcessors(); // 加载EnvironmentPostProcessor的bean出来
    //	    	postProcessors.add(this);
    //	    	AnnotationAwareOrderComparator.sort(postProcessors); // 对EnvironmentPostProcessor的bean排序
    //	    	for (EnvironmentPostProcessor postProcessor : postProcessors) {
    //              // 触发 EnvironmentPostProcessor#postProcessEnvironment(..)
    //	    		postProcessor.postProcessEnvironment(event.getEnvironment(), event.getSpringApplication());
    //	    	}
    //	    }
    //
    //      List<EnvironmentPostProcessor> loadPostProcessors() {
    //          // 为什么 spring.factories 的 EnvironmentPostProcessor.class 作为 key 生效的原因哦
    //		    return SpringFactoriesLoader.loadFactories(EnvironmentPostProcessor.class, getClass().getClassLoader());
    //	    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {

        // 1. 命令行中指定了mpw.key,就获取获取密钥
        String mpwKey = null;
        for (PropertySource<?> ps : environment.getPropertySources()) {
            if (ps instanceof SimpleCommandLinePropertySource) {
                SimpleCommandLinePropertySource source = (SimpleCommandLinePropertySource) ps;
                mpwKey = source.getProperty("mpw.key");
                break;
            }
        }

        // 2. 使用mpwKey处理加密内容 -> 要求:加密内容以 mpw: 开头才可以哦
        if (StringUtils.isNotBlank(mpwKey)) {
            HashMap<String, Object> map = new HashMap<>();
            for (PropertySource<?> ps : environment.getPropertySources()) {
                if (ps instanceof OriginTrackedMapPropertySource) {
                    OriginTrackedMapPropertySource source = (OriginTrackedMapPropertySource) ps;
                    for (String name : source.getPropertyNames()) {
                        Object value = source.getProperty(name);
                        if (value instanceof String) {
                            String str = (String) value;
                            if (str.startsWith("mpw:")) {
                                map.put(name, AES.decrypt(str.substring(4), mpwKey));
                            }
                        }
                    }
                }
            }
            // 3. 将解密的数据放入环境变量，并处于第一优先级上
            if (CollectionUtils.isNotEmpty(map)) {
                environment.getPropertySources().addFirst(new MapPropertySource("custom-encrypt", map));
            }
        }
    }
}
