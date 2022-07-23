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
package com.baomidou.mybatisplus.extension.conditions.query;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.Query;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.toolkit.ExceptionUtils;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.conditions.AbstractChainWrapper;

import java.util.function.Predicate;

/**
 * @author miemie
 * @since 2018-12-19
 */
@SuppressWarnings({"serial"})
public class LambdaQueryChainWrapper<T> extends AbstractChainWrapper<T, SFunction<T, ?>, LambdaQueryChainWrapper<T>, LambdaQueryWrapper<T>> implements ChainQuery<T>, Query<LambdaQueryChainWrapper<T>, T, SFunction<T, ?>> {

    // 继承体系:
    // 1. 继承了 ChainQuery 接口
    // 2. 继承了 Query 接口

    // 比较:
    // QueryWrapper
        // @Test
        //    void TestQueryWrapperSelect() {
        //        //1、条件用法
        //        List<User> userList = userMapper.selectList(new QueryWrapper<User>()
        //                .like("email", "24252")
        //                .between("age", 20, 22)
        //                .or()
        //                .eq("name", "zcx")
        //        );
        //        System.out.println("userList:" + userList);
        //
        //        //2、排序用法
        //        List<User> users = userMapper.selectList(new QueryWrapper<User>()
        //                .eq("nick_name", "xx")
        //                .orderByAsc("age")  //升序
        ////                .orderByDesc("age") //降序
        //                .last("limit 0,3") //last用法：在sql末尾添加sql语句，有sql注入风险
        //        );
        //        System.out.println("users:"+users);
        //
        //    }

    // LambdaQueryWrapper
        //@Test
        //    void TestLambdaQueryWrapper() {
        //        //1、查询单条
        //        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        //        queryWrapper.eq(User::getName,"liangd1");
        //        User selectOne = userMapper.selectOne(queryWrapper);
        //        System.out.println(selectOne);
        //
        //        //2、查询list以及统计条数
        //        queryWrapper.eq(User::getName, "zcx");
        //        List<User> userList = userMapper.selectList(queryWrapper);
        //        System.out.println("userList:" + userList);
        //        Integer result = userMapper.selectCount(queryWrapper);
        //        System.out.println("result:" + result);
        //    }

    // QueryChainWrapper
        // @Test
        //    void TestLambdaQueryChainWrapper() {
        //        //1、eq查询单条
        //        User one = new QueryChainWrapper<>(userMapper)
        //                .eq("name", "liangd1")
        //                .one();
        //        System.out.println("UserOne:" + one);
        //
        //        //2、查询list
        //        List<User> users = new LambdaQueryChainWrapper<>(userMapper)
        //                .eq("email", "zcx@qq.com")
        //                .list();
        //        System.out.println("UserList:" + users);
        //
        //        //3、模糊查询
        //        List<User> LikeList = new LambdaQueryChainWrapper<>(userMapper)
        //                .like(User::getEmail, "test")
        //                .list();
        //        System.out.println("LikeUser:" + LikeList);
        //    }

    // LambdaQueryChainWrapper
        // @Test
        //    void TestLambdaQueryChainWrapper() {
        //        //1、eq查询单条
        //        User one = new LambdaQueryChainWrapper<>(userMapper)
        //                .eq(User::getName, "liangd1")
        //                .one();
        //        System.out.println("UserOne:" + one);
        //
        //        //2、查询list
        //        List<User> users = new LambdaQueryChainWrapper<>(userMapper)
        //                .eq(User::getName, "zcx")
        //                .list();
        //        System.out.println("UserList:" + users);
        //
        //        //3、模糊查询
        //        List<User> LikeList = new LambdaQueryChainWrapper<>(userMapper)
        //                .like(User::getEmail, "test")
        //                .list();
        //        System.out.println("LikeUser:" + LikeList);
        //    }

    // 原理非常的简单:
    // LambdaQueryChainWrapper 要求必须注入 BaseMapper -> 这样在结尾操作比如 list() one() count() 的时候可以调用BaseMapper的相关方法
    // 然后重写 eq(..) alleq(..) or(..) 等方法 -> 直接重定向到new LambdaQueryWrapper<>()新建的LambdaQueryWrapper上去执行即可
    // 这样 ChainWrapper#getBaseMapper() ChainWrapper#getWrapper()都解决啦

    private final BaseMapper<T> baseMapper;

    // note: ❗️❗️❗️ 不同于 LambdaQueryWrapper 和 QueryWrapper
    // LambdaQueryChainWrapper 的 唯一构造函数 -> 必须是去指定baseMapper的哦
    public LambdaQueryChainWrapper(BaseMapper<T> baseMapper) {
        super();
        this.baseMapper = baseMapper;
        // 同时: 新建一个 LambdaQueryWrapper
        super.wrapperChildren = new LambdaQueryWrapper<>();
    }

    @SafeVarargs
    @Override
    public final LambdaQueryChainWrapper<T> select(SFunction<T, ?>... columns) {
        wrapperChildren.select(columns);
        return typedThis;
    }

    @Override
    public LambdaQueryChainWrapper<T> select(Class<T> entityClass, Predicate<TableFieldInfo> predicate) {
        wrapperChildren.select(entityClass, predicate);
        return typedThis;
    }

    @Override
    public String getSqlSelect() {
        throw ExceptionUtils.mpe("can not use this method for \"%s\"", "getSqlSelect");
    }

    @Override
    public BaseMapper<T> getBaseMapper() {
        return baseMapper;
    }
}
