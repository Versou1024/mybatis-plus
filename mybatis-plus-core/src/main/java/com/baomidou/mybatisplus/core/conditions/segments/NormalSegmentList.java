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
import com.baomidou.mybatisplus.core.enums.SqlKeyword;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 普通片段
 *
 * @author miemie
 * @since 2018-06-27
 */
@SuppressWarnings("serial")
public class NormalSegmentList extends AbstractISegmentList {
    // 命名
    // Normal Segment List = 普通SQL片段列表

    /**
     * 是否处理了的上个 not
     */
    private boolean executeNot = true;

    NormalSegmentList() {
        this.flushLastValue = true;
    }

    @Override
    protected boolean transformList(List<ISqlSegment> list, ISqlSegment firstSegment, ISqlSegment lastSegment) {
        // 1. 待加入的元素只有一个:
        if (list.size() == 1) {
            // 1.1 实际上 只有 QueryWrapper#and() 以及 or() 以及 not() 三个方法会进入 -> 尝试添加 SqlKeyword.AND SqlKeyword.OR SqlKeyword.NOT

            // 1.2 不是SqlKeyword.NOT -> 表示SqlKeyword.AND或者SqlKeyword.OR
            if (!MatchSegment.NOT.match(firstSegment)) {
                // 1.2.1 不是SqlKeyword.NOT,且当前的NormalSegmentList为空
                if (isEmpty()) {
                    // 1.2.1.1 sqlSegment是 SqlKeyword.AND 或者 SqlKeyword.OR ,想要在第一个位置拼接是没有意义的,直接不执行,返回false [因为 where and id =1 的and没有必要的哦]
                    return false;
                }
                // 1.2.2 不是SqlKeyword.NOT,但是当前的NormalSegmentList不为空
                boolean matchLastAnd = MatchSegment.AND.match(lastValue);
                boolean matchLastOr = MatchSegment.OR.match(lastValue);
                // 1.2.3 只要是SqlKeyword.AND 或者 SqlKeyword.OR
                if (matchLastAnd || matchLastOr) {
                    //1.2.3.1 但是上次最后一个值是 and 或者 or -> 也不需要添加这个segment -> [❗️❗️❗️ 因为在sql中不需要连续的 and and id=1 或者 or or id=1]
                    if (matchLastAnd && MatchSegment.AND.match(firstSegment)) {
                        return false;
                    } else if (matchLastOr && MatchSegment.OR.match(firstSegment)) {
                        return false;
                    } else {
                        // 和上次的不一样
                        removeAndFlushLast();
                    }
                }
            }
            // 1.3 加入的是SqlKeyword.NOT -> NOT不能放在第一个位置,将executeNot标记为false,后续再去处理
            // [比如 new QueryWrapper.eq(..).eq(..).or().eq().eq()]
            // 最终结果是: xx = xx and xx = xx or xx = xx and xx = xx
            // [又比如 new QueryWrapper.eq(..).eq(..).or().eq().or().eq()]
            // 最终结果是: xx = xx and xx = xx or xx = xx or xx = xx
            else {
                executeNot = false;
                return false;
            }
        }
        // 2. 待加入的元素不止有一个哦
        else {
            // 2.1 SqlKeyword.APPLY 是标志性SqlKeyword -> note: 注意需要去除哦
            if (MatchSegment.APPLY.match(firstSegment)) {
                list.remove(0);
            }
            // 2.2 lastValue 不是AND或者OR,并且当前NormalSegmentList非空 -> 追加一个AND操作进去 [❗️❗️❗️ 所以默认 QueryWrapper.eq(..).eq(..) 默认都是认为AND的级联操作]
            if (!MatchSegment.AND_OR.match(lastValue) && !isEmpty()) {
                add(SqlKeyword.AND);
            }
            // 2.3 executeNot为false,表示上一个NOT没有进行处理
            if (!executeNot) {
                // 2.3.1 先处理添加一个NOT
                list.add(0, SqlKeyword.NOT);
                executeNot = true;
            }
        }
        return true;
    }

    @Override
    protected String childrenSqlSegment() {
        // 1. 当进行生产sql片段时,最后一个Segment是AND或者OR时,需要剔除出去
        if (MatchSegment.AND_OR.match(lastValue)) {
            removeAndFlushLast();
        }
        // 2. 构建sql
        // a: 内容 -> ISqlSegment::getSqlSegment
        // b: 分隔符 -> 空格" "
        final String str = this.stream().map(ISqlSegment::getSqlSegment).collect(Collectors.joining(SPACE));
        // 3. 使用 () 包裹起来
        return (LEFT_BRACKET + str + RIGHT_BRACKET);
    }

    @Override
    public void clear() {
        super.clear();
        flushLastValue = true;
        executeNot = true;
    }
}
