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
package com.baomidou.mybatisplus.core.conditions.segments;

import com.baomidou.mybatisplus.core.conditions.ISqlSegment;
import com.baomidou.mybatisplus.core.toolkit.StringPool;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * SQL 片段集合 处理的抽象类
 *
 * @author miemie
 * @since 2018-06-27
 */
@SuppressWarnings("serial")
public abstract class AbstractISegmentList extends ArrayList<ISqlSegment> implements ISqlSegment, StringPool {
    // 位于: com.baomidou.mybatisplus.core.conditions.segments = core模块下的conditions.segments

    // 继承体系:
    // extends ArrayList<ISqlSegment> -> 组合模式
    // implements ISqlSegment -> 面向接口编程, ISqlSegment#getSqlSegment()

    // 实现类:
    //  NormalSegmentList
    //  GroupBySegmentList
    //  HavingSegmentList
    //  OrderBySegmentList

    /**
     * 最后一个值
     */
    ISqlSegment lastValue = null;
    /**
     * 刷新 lastValue
     */
    boolean flushLastValue = false;

    // 缓存 getSqlSegment(..) 方法的返回值
    // 在没有加入新的SQLSegment到当前集合中,getSqlSegment(..)将返回前一个处理的值 [提供性能]
    private String sqlSegment = EMPTY;

    // 是否缓存过结果集
    // true - 表示没有,不需要处理
    // false - 表示已经有缓存的新的SqlSegment,可以处理
    private boolean cacheSqlSegment = true;

    /**
     * 重写方法,做个性化适配
     *
     * @param c 元素集合
     * @return 是否添加成功
     */
    @Override
    public boolean addAll(Collection<? extends ISqlSegment> c) {
        List<ISqlSegment> list = new ArrayList<>(c);
        // 1. 在其中对值进行判断以及更改 list 的内部元素
        boolean goon = transformList(list, list.get(0), list.get(list.size() - 1));
        if (goon) {
            // 1.1 cacheSqlSegment标记为false - 因为有加入进行的SQLSegment,在下次调用getSqlSegment(..)就需要重新解析哦
            cacheSqlSegment = false;
            if (flushLastValue) {
                this.flushLastValue(list);
            }
            // 1.2 将通过transformList(..)处理过c的结果集合list加入到当前SqlSegment的list中去
            return super.addAll(list);
        }
        // 2. goon返回false,表示不能SQLSegment的集合c加入到当前集合
        return false;
    }

    /**
     * 在其中对值进行判断以及更改 list 的内部元素
     *
     * @param list         传入进来的 ISqlSegment 集合
     * @param firstSegment ISqlSegment 集合里第一个值
     * @param lastSegment  ISqlSegment 集合里最后一个值
     * @return true 是否继续向下执行; false 不再向下执行
     */
    protected abstract boolean transformList(List<ISqlSegment> list, ISqlSegment firstSegment, ISqlSegment lastSegment);

    /**
     * 刷新属性 lastValue
     */
    private void flushLastValue(List<ISqlSegment> list) {
        lastValue = list.get(list.size() - 1);
    }

    /**
     * 删除元素里最后一个值</br>
     * 并刷新属性 lastValue
     */
    void removeAndFlushLast() {
        remove(size() - 1);
        flushLastValue(this);
    }

    @Override
    public String getSqlSegment() {
        // 1. cacheSqlSegment 为true,表示没有新的加入的缓存的sqlSegment,不需要重新解析
        if (cacheSqlSegment) {
            return sqlSegment;
        }
        // 2. 标记 cacheSqlSegment 为true
        cacheSqlSegment = true;
        // 3. 开始处理 -> 缓存到sqlSegment字段上,下一次调用getSqlSegment(..)除非有加入新的sqlSegment元素,否则会直接返回这里的String类型的sqlSegment [❗️❗️❗️ -> 这说明Wrapper是可以重复使用的哦]
        sqlSegment = childrenSqlSegment(); // [抽象方法哦]
        // 4. 返回SQLSegment
        return sqlSegment;
    }

    /**
     * 只有该类进行过 addAll 操作,才会触发这个方法
     * <p>
     * 方法内可以放心进行操作
     *
     * @return sqlSegment
     */
    protected abstract String childrenSqlSegment();

    @Override
    public void clear() {
        super.clear();
        lastValue = null;
        sqlSegment = EMPTY;
        cacheSqlSegment = true;
    }
}
