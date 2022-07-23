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
package com.baomidou.mybatisplus.core.override;

import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import org.apache.ibatis.binding.MapperProxy;
import org.apache.ibatis.reflection.ExceptionUtil;
import org.apache.ibatis.session.SqlSession;

import java.io.Serializable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * <p> 从 {@link MapperProxy}  copy 过来 </p>
 * <li> 使用 MybatisMapperMethod </li>
 *
 * @author miemie
 * @since 2018-06-09
 */
public class MybatisMapperProxy<T> implements InvocationHandler, Serializable {
    // 位于: core模块的override重写包下

    // 作用:
    // MybatisMapperProxy 为 Mapper 接口创建Proxy代理对象

    // 和Mybatis的MapperProxy的区别:
    // 几乎没有区别
    //  a: 只是对于Mapper接口下的默认方法的执行提出了各自的使用方法 [MybatisMapperProxy对于默认的方法的使用更加细致化,区分了JDK8.0的环境来使用哦]
    //  b: 对于mapper接口下的抽象公共的mapper方法 -> Mybatis使用的是MapperMethod, MP使用的是MybatisMapperMethod


    private static final long serialVersionUID = -5154982058833204559L;
    private static final int ALLOWED_MODES = MethodHandles.Lookup.PRIVATE | MethodHandles.Lookup.PROTECTED | MethodHandles.Lookup.PACKAGE | MethodHandles.Lookup.PUBLIC;

    // JDK8.0指的就是: MethodHandles.Lookup.class.getDeclaredConstructor(Class.class, int.class) 对应的构造器
    // 99%都是JDK8.0,因此其余情况不做思考
    private static final Constructor<MethodHandles.Lookup> lookupConstructor;

    // privateLookupInMethod 在 JDK8.0 一般都是 null
    private static final Method privateLookupInMethod;

    // 当前mapper方法执行时的sqlSession
    private final SqlSession sqlSession;

    // mapper接口
    private final Class<T> mapperInterface;

    // 缓存:  以Mapper接口的方法为key,以对应的MapperMethodInvoker为vlaue
    private final Map<Method, MapperMethodInvoker> methodCache;

    public MybatisMapperProxy(SqlSession sqlSession, Class<T> mapperInterface, Map<Method, MapperMethodInvoker> methodCache) {
        this.sqlSession = sqlSession;
        this.mapperInterface = mapperInterface;
        this.methodCache = methodCache;
    }

    static {
        // 静态代码块 -> 确定 MethodHandles.Lookup

        // 1. JDK8.0在MethodHandles中是否无法查找到有方法名为"privateLookupIn",形参值为Class与MethodHandles.Lookup的
        Method privateLookupIn;
        try {
            privateLookupIn = MethodHandles.class.getMethod("privateLookupIn", Class.class, MethodHandles.Lookup.class);
        } catch (NoSuchMethodException e) {
            privateLookupIn = null;
        }
        privateLookupInMethod = privateLookupIn;

        // 2. 确定 MethodHandles.Lookup的lookupConstructor
        Constructor<MethodHandles.Lookup> lookup = null;
        if (privateLookupInMethod == null) {
            // JDK 1.8
            try {
                lookup = MethodHandles.Lookup.class.getDeclaredConstructor(Class.class, int.class);
                lookup.setAccessible(true);
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException(
                    "There is neither 'privateLookupIn(Class, Lookup)' nor 'Lookup(Class, int)' method in java.lang.invoke.MethodHandles.",
                    e);
            } catch (Throwable t) {
                lookup = null;
            }
        }
        lookupConstructor = lookup;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        try {
            // 1. 执行Object下的方法的就是执行当前类的方法
            if (Object.class.equals(method.getDeclaringClass())) {
                return method.invoke(this, args);
            }
            // 2. 非Object下的方法,通过 MapperMethodInvoker.invoke(..) 来执行哦
            else {
                return cachedInvoker(method).invoke(proxy, method, args, sqlSession);
            }
        } catch (Throwable t) {
            throw ExceptionUtil.unwrapThrowable(t);
        }
    }

    private MapperMethodInvoker cachedInvoker(Method method) throws Throwable {
        try {
            return CollectionUtils.computeIfAbsent(methodCache, method, m -> {
                // 1.1 如果方法是Mapper接口中的默认方法
                if (m.isDefault()) {
                    try {
                        // 1.1.1 privateLookupInMethod 在JDK8.0中一般都是null
                        // 构建一个: new DefaultMethodInvoker(getMethodHandleJava8(method))
                        if (privateLookupInMethod == null) {
                            return new DefaultMethodInvoker(getMethodHandleJava8(method));
                        } else {
                            return new DefaultMethodInvoker(getMethodHandleJava9(method));
                        }
                    } catch (IllegalAccessException | InstantiationException | InvocationTargetException
                        | NoSuchMethodException e) {
                        throw new RuntimeException(e);
                    }
                }
                // 1.2 Mapper接口中的方法method并不是默认方法 -> 生成PlainMethodInvoker
                // ❗️ 使用的是: PlainMethodInvoker#invoke(..) 实际委托给 -> MybatisMapperMethod#execute(..) 执行去啦
                else {
                    return new PlainMethodInvoker(new MybatisMapperMethod(mapperInterface, method, sqlSession.getConfiguration()));
                }
            });
        } catch (RuntimeException re) {
            Throwable cause = re.getCause();
            throw cause == null ? re : cause;
        }
    }

    private MethodHandle getMethodHandleJava9(Method method)
        throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        final Class<?> declaringClass = method.getDeclaringClass();
        return ((MethodHandles.Lookup) privateLookupInMethod.invoke(null, declaringClass, MethodHandles.lookup())).findSpecial(
            declaringClass, method.getName(), MethodType.methodType(method.getReturnType(), method.getParameterTypes()),
            declaringClass);
    }

    private MethodHandle getMethodHandleJava8(Method method) throws IllegalAccessException, InstantiationException, InvocationTargetException {
        // 1. 特点:
        // lookupConstructor.newInstance(declaringClass, ALLOWED_MODES).unreflectSpecial(method, declaringClass)
        // 使用 MethodHandles.Lookup带有Class和int的构造器 去创建 MethodHandles.Lookup -> 其中int参数来源于ALLOWED_MODES,Class就是declaringClass
        // MethodHandles.Lookup#unreflectSpecial(..) -> 为反射方法生成方法句柄
        final Class<?> declaringClass = method.getDeclaringClass();
        return lookupConstructor.newInstance(declaringClass, ALLOWED_MODES).unreflectSpecial(method, declaringClass);
    }

    interface MapperMethodInvoker {
        // MapperMethod引用者的接口
        // 定义一个: invoke(..) 方法
        Object invoke(Object proxy, Method method, Object[] args, SqlSession sqlSession) throws Throwable;
    }

    private static class PlainMethodInvoker implements MapperMethodInvoker {
        private final MybatisMapperMethod mapperMethod;

        public PlainMethodInvoker(MybatisMapperMethod mapperMethod) {
            super();
            this.mapperMethod = mapperMethod;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args, SqlSession sqlSession) throws Throwable {
            // ❗️ 直接执行 MybatisMapperMethod#execute(..) -> 委托过去
            return mapperMethod.execute(sqlSession, args);
        }
    }

    private static class DefaultMethodInvoker implements MapperMethodInvoker {
        // 作用: 为Mapper接口中的default修饰的默认方法封装对应的DefaultMethodInvoker -> 方便统一调用

        private final MethodHandle methodHandle;

        public DefaultMethodInvoker(MethodHandle methodHandle) {
            super();
            this.methodHandle = methodHandle;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args, SqlSession sqlSession) throws Throwable {
            // ❗️ 当Mapper接口中的默认方法绑定到代理对象上,并使用给定的参数args进行执行哦
            return methodHandle.bindTo(proxy).invokeWithArguments(args);
        }
    }
}
