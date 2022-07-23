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

import java.util.List;

import static com.baomidou.mybatisplus.core.enums.SqlKeyword.GROUP_BY;
import static java.util.stream.Collectors.joining;

/**
 * Group By SQL 片段
 *
 * @author miemie
 * @since 2018-06-27
 */
@SuppressWarnings("serial")
public class GroupBySegmentList extends AbstractISegmentList {
    // 位于: com.baomidou.mybatisplus.core.conditions.segments

    // 命名:
    // GroupBy Segment List  = Group By SQL 片段

    @Override
    protected boolean transformList(List<ISqlSegment> list, ISqlSegment firstSegment, ISqlSegment lastSegment) {
        list.remove(0);
        return true;
    }

    @Override
    protected String childrenSqlSegment() {
        if (isEmpty()) {
            return EMPTY;
        }
        // 1. 处理
        // 分隔符为:   ","
        // 前缀为:     "GROUP BY"
        // 后缀为:     ""
        // 每行数据为:  ISqlSegment::getSqlSegment

        // 一般情况都是: GROUP BY column1,column2
        return this.stream().map(ISqlSegment::getSqlSegment).collect(joining(COMMA, SPACE + GROUP_BY.getSqlSegment() + SPACE, EMPTY));
    }
}
