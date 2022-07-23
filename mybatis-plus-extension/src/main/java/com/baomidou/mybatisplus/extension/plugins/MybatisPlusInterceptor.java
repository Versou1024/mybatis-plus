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
package com.baomidou.mybatisplus.extension.plugins;

import com.baomidou.mybatisplus.core.toolkit.ClassUtils;
import com.baomidou.mybatisplus.core.toolkit.StringPool;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.toolkit.PropertyMapper;
import lombok.Setter;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.sql.Connection;
import java.util.*;

/**
 * @author miemie
 * @since 3.4.0
 */
@SuppressWarnings({"rawtypes"})
@Intercepts(
    {
        @Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class}),
        @Signature(type = StatementHandler.class, method = "getBoundSql", args = {}),
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class}),
    }
)
public class MybatisPlusInterceptor implements Interceptor {
    // MybatisPlus是Mybatis的拦截器

    // 主要其内部管理了系列的Mybatis-Plus提供的InnerInterceptor
    // InnerInterceptor 是mybatis-plus提供的

    // ❗️❗️❗️
    // interceptors 的定义顺序是有讲究的
    //  a: 一旦前一个InnerInterceptor#willDoQuery()返回false,表示不需要执行doQuery,那么后续所有的 InnerInterceptor#beforeQuery(..) 都将不在执行
    //  b: 一旦前一个InnerInterceptor#willDoUpdate()返回false,表示不需要执行doQuery,那么后续所有的 InnerInterceptor#beforeUpdate(..) 都将不在执行
    @Setter
    private List<InnerInterceptor> interceptors = new ArrayList<>();


    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object target = invocation.getTarget();
        Object[] args = invocation.getArgs();
        // 1. Executor
        if (target instanceof Executor) {
            final Executor executor = (Executor) target;
            Object parameter = args[1];
            // 1.1 executor中两个参数的被拦截的方法一定是Update()方法
            boolean isUpdate = args.length == 2;
            // 1.2 MybatisPlusInterceptor拦截的Executor的三个方法的第一个参数都是MappedStatement
            MappedStatement ms = (MappedStatement) args[0];
            // 1.3 非update操作 -> 就只能是Query操作
            if (!isUpdate && ms.getSqlCommandType() == SqlCommandType.SELECT) {
                // 1.3.1 拿到rowBounds\resultHandler\
                RowBounds rowBounds = (RowBounds) args[2];
                ResultHandler resultHandler = (ResultHandler) args[3];
                BoundSql boundSql;
                if (args.length == 4) {
                    boundSql = ms.getBoundSql(parameter);
                } else {
                    // 几乎不可能走进这里面,除非使用Executor的代理对象调用query[args[6]]
                    boundSql = (BoundSql) args[5];
                }
                // 1.3.2 内部拦截器执行
                for (InnerInterceptor query : interceptors) {
                    // 1.3.3 是否需要执行query
                    // ❗️❗️❗️❗️❗️❗️
                    // interceptors 的定义顺序是有讲究的哦,一旦前一个InnerInterceptor返回false,表示不需要执行doQuery,那么后续所有的 InnerInterceptor#beforeQuery(..) 都将不在执行
                    if (!query.willDoQuery(executor, ms, parameter, rowBounds, resultHandler, boundSql)) {
                        return Collections.emptyList(); // ❗️执行结果就是 空集合
                    }
                    // 1.3.4 前置执行
                    query.beforeQuery(executor, ms, parameter, rowBounds, resultHandler, boundSql);
                }
                CacheKey cacheKey = executor.createCacheKey(ms, parameter, rowBounds, boundSql);
                return executor.query(ms, parameter, rowBounds, resultHandler, cacheKey, boundSql);
            }
            else if (isUpdate) {
                // 1.5 更新语句.包括增删改
                for (InnerInterceptor update : interceptors) {
                    if (!update.willDoUpdate(executor, ms, parameter)) {
                        return -1; // ❗️执行结果就是 -1
                    }
                    update.beforeUpdate(executor, ms, parameter);
                }
            }
        }
        // 2. StatementHandler
        else {
            // StatementHandler
            final StatementHandler sh = (StatementHandler) target;
            // 2.1 目前只有StatementHandler.getBoundSql方法args才为null
            if (null == args) {
                for (InnerInterceptor innerInterceptor : interceptors) {
                    innerInterceptor.beforeGetBoundSql(sh);
                }
            }
            // 2.2 prepare()方法有两个参数 -- connection\TransactionalTimeout
            else {
                Connection connections = (Connection) args[0];
                Integer transactionTimeout = (Integer) args[1];
                for (InnerInterceptor innerInterceptor : interceptors) {
                    innerInterceptor.beforePrepare(sh, connections, transactionTimeout);
                }
            }
        }
        return invocation.proceed();
    }

    @Override
    public Object plugin(Object target) {
        // 仅对Executor\StatementHandler创建代理器
        if (target instanceof Executor || target instanceof StatementHandler) {
            return Plugin.wrap(target, this);
        }
        return target;
    }

    public void addInnerInterceptor(InnerInterceptor innerInterceptor) {
        this.interceptors.add(innerInterceptor);
    }

    public List<InnerInterceptor> getInterceptors() {
        return Collections.unmodifiableList(interceptors);
    }

    // 使用内部规则,拿分页插件举个栗子:
    // <p>
    // - key: "@page" ,value: "com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor"
    // - key: "page:limit" ,value: "100"
    // <p>
    // 解读1: key 以 "@" 开头定义了这是一个需要组装的 `InnerInterceptor`, 以 "page" 结尾表示别名, value 是 `InnerInterceptor` 的具体的 class 全名
    // 解读2: key 以上面定义的 "别名 + ':'" 开头指这个 `value` 是定义的该 `InnerInterceptor` 属性需要设置的值
    // <p>
    // 如果这个 `InnerInterceptor` 不需要配置属性也要加别名
    @Override
    public void setProperties(Properties properties) {
        // 1. 通过 pm.group("@") 确定各个拦截器,以及给各个拦截器注入的属性
        // 举例: ❗️❗️❗️ 形参properties有如下k-v
        // @page = com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor
        // page:limit = 100
        // page:size = 10
        // page:current = 1
        // ....
        // 那么返回的结果 Map<String, Properties> 类型的group值
        // 其中有一个Entry项的
        //      key就是 "com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor"
        //      value就是 limit = 100 size = 10 current = 1
        PropertyMapper pm = PropertyMapper.newInstance(properties);
        Map<String, Properties> group = pm.group(StringPool.AT);
        // 2. 实例化出来对应的InnerInterceptor,并且找到的属性设置到实例化后的InnerInterceptor中去
        group.forEach((k, v) -> {
            InnerInterceptor innerInterceptor = ClassUtils.newInstance(k);
            innerInterceptor.setProperties(v);
            addInnerInterceptor(innerInterceptor);
        });
    }
}
