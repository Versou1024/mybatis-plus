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
import lombok.AccessLevel;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;

/**
 * 合并 SQL 片段
 *
 * @author miemie
 * @since 2018-06-27
 */
@Getter
@SuppressWarnings("serial")
public class MergeSegments implements ISqlSegment {
    // 位于: com.baomidou.mybatisplus.core.conditions.segments = core模块的conditions.segments包下

    // 作用: 合并SQL片段

    // 持有:
    // normal\groupBy\having\orderBy的SegmentList哦
    private final NormalSegmentList normal = new NormalSegmentList();
    private final GroupBySegmentList groupBy = new GroupBySegmentList();
    private final HavingSegmentList having = new HavingSegmentList();
    private final OrderBySegmentList orderBy = new OrderBySegmentList();

    @Getter(AccessLevel.NONE)
    private String sqlSegment = StringPool.EMPTY;

    // 缓存 Sql 段 ->
    // 为ture: 表示缓存的所有的sql片段为空,或者已经被处理过即 MergeSegments.getSqlSegment(..)
    @Getter(AccessLevel.NONE)
    private boolean cacheSqlSegment = true;

    public void add(ISqlSegment... iSqlSegments) {
        List<ISqlSegment> list = Arrays.asList(iSqlSegments);
        ISqlSegment firstSqlSegment = list.get(0);
        // 1. firstSqlSegment是否为SqlKeyword.ORDER_BY
        if (MatchSegment.ORDER_BY.match(firstSqlSegment)) {
            orderBy.addAll(list);
        }
        // 2. firstSqlSegment是否为SqlKeyword.GROUP_BY
        else if (MatchSegment.GROUP_BY.match(firstSqlSegment)) {
            groupBy.addAll(list);
        }
        // 3. firstSqlSegment是否为SqlKeyword.HAVING
        else if (MatchSegment.HAVING.match(firstSqlSegment)) {
            having.addAll(list);
        }
        // 4. 其余情况都加入到normal中去
        else {
            normal.addAll(list);
        }
        // 5. 标记cacheSqlSegment为false -> 有缓存SQLSegment
        cacheSqlSegment = false;
    }

    @Override
    public String getSqlSegment() {
        // 1. cacheSqlSegment为ture,表示没有缓存的SQLSegment,或者已经被getSqlSegment(..)处理过 -> 不需要重复处理
        if (cacheSqlSegment) {
            return sqlSegment;
        }
        // 2. cacheSqlSegment 标记为ture
        cacheSqlSegment = true;
        // 3.1 NormalSegmentList 为空 -> 表示为Where关键词之后的 GroupBy Having OrderBy 的Segment
        if (normal.isEmpty()) {
            // 3.1.1 GroupBySegmentList或者OrderBySegment至少有一个非空
            if (!groupBy.isEmpty() || !orderBy.isEmpty()) {
                // 调用各自的getSqlSegment(..)然后拼接起来
                // 按照 groupBy + having + orderBy 的顺序拼接哦
                sqlSegment = groupBy.getSqlSegment() + having.getSqlSegment() + orderBy.getSqlSegment();
            }
        }
        // 3.2 NormalSegmentList 非空 -> 表示为Where筛选的Segment + GroupBy Having OrderBy 的Segment
        else {
            sqlSegment = normal.getSqlSegment() + groupBy.getSqlSegment() + having.getSqlSegment() + orderBy.getSqlSegment();
        }
        return sqlSegment;
    }

    /**
     * 清理
     *
     * @since 3.3.1
     */
    public void clear() {
        sqlSegment = StringPool.EMPTY;
        cacheSqlSegment = true;
        normal.clear();
        groupBy.clear();
        having.clear();
        orderBy.clear();
    }
}
