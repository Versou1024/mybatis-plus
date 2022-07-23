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
package com.baomidou.mybatisplus.core.conditions;

import com.baomidou.mybatisplus.annotation.OrderBy;
import com.baomidou.mybatisplus.core.conditions.interfaces.Compare;
import com.baomidou.mybatisplus.core.conditions.interfaces.Func;
import com.baomidou.mybatisplus.core.conditions.interfaces.Join;
import com.baomidou.mybatisplus.core.conditions.interfaces.Nested;
import com.baomidou.mybatisplus.core.conditions.segments.MergeSegments;
import com.baomidou.mybatisplus.core.enums.SqlKeyword;
import com.baomidou.mybatisplus.core.enums.SqlLike;
import com.baomidou.mybatisplus.core.toolkit.*;
import com.baomidou.mybatisplus.core.toolkit.sql.SqlScriptUtils;
import com.baomidou.mybatisplus.core.toolkit.sql.SqlUtils;
import com.baomidou.mybatisplus.core.toolkit.sql.StringEscape;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

import static com.baomidou.mybatisplus.core.enums.SqlKeyword.*;
import static com.baomidou.mybatisplus.core.enums.WrapperKeyword.APPLY;
import static java.util.stream.Collectors.joining;

/**
 * 查询条件封装
 *
 * @author hubin miemie HCL
 * @since 2017-05-26
 */
@SuppressWarnings({"unchecked"})
public abstract class AbstractWrapper<T, R, Children extends AbstractWrapper<T, R, Children>> extends Wrapper<T> implements Compare<Children, R>, Nested<Children, Children>, Join<Children>, Func<Children, R> {
    // 位于: com.baomidou.mybatisplus.core.conditions = core模块的conditions包中

    // 泛型:
    // T  表示实体类
    // R  表示列名的格式 -> 一般都为String
    // Children 表示继承AbstractWrapper的Wrapper类 -> 比如QueryWrapper或者UpdateWrapper

    // 占位符 -> 表示当前对象
    protected final Children typedThis = (Children) this;

    // 参数名称序列
    protected AtomicInteger paramNameSeq;

    // 参数名称值对
    protected Map<String, Object> paramNameValuePairs;

    //其他
    protected SharedString paramAlias;

    // sql 最后一条
    // 在构造器中会初始化为: SharedString.emptyString()
    // 最终通过: getSqlSegment() ->  expression.getSqlSegment() + lastSql.getStringValue() 中去
    protected SharedString lastSql;

    // SQL注释
    // 在构造器中会初始化为: SharedString.emptyString()
    // 最终通过: getSqlComment() ->
    protected SharedString sqlComment;

    // SQL起始语句
    // 在构造器中会初始化为: SharedString.emptyString()
    // 最终通过: getSqlFirst() 中 ->
    protected SharedString sqlFirst;

    // 数据库表映射实体类
    private T entity;

    // 各种合并的sql片段
    // 在构造器中初始化为: new MergeSegments()
    // 最终通过: getSqlSegment() ->  expression.getSqlSegment() + lastSql.getStringValue() 中去
    protected MergeSegments expression;

    // 实体类型(主要用于确定泛型以及取TableInfo缓存)
    private Class<T> entityClass;

    @Override
    public T getEntity() {
        // 以BaseMapper注入的CRUD的方法为例 -> BaseMapper#selectList(@Param(Constants.WRAPPER) Wrapper<T> queryWrapper) -> 传递进去的是就是当前Wrapper
        // 然后再 SelectList#injectMappedStatement(...) 中
        // %s SELECT %s FROM %s %s %s %s
        // 第四个%s: sqlWhereEntityWrapper(true, tableInfo) -> 获取<where>标签
        // <where>
        //      <choose>
        //          <when test=" ew != null">
        //             <if test="ew.entity != null">
        //                  <if test=" #{ew.entity. + keyProperty} != null "> keyColumn = #{ew.entity. + keyProperty} </if>
        //                  <if test=" #{ew.entity.property1} != null " > AND column = #{ew.entity. + e1l} </if>   // ❗️❗️❗ <if>标签的test是收@TableField.whereStrategy()控制确定成立条件,AND后的比较逻辑是受到@TableField.condition()控制的️
        //                  <if test=" #{ew.entity.property2} != null and #{ew.entity.property} != ''" > AND column != #{ew.entity. + el2} </if> //  [比如: condition为SQLCondition.EQUALS,whereStrategy=FieldStrategy.NOT_EMPTY]
        //                  <if test=" #{ew.entity.property3} != null " > AND column LIKE concat('%',#{ew.entity. + el3},'%'} </if>   //  [比如: condition为SQLCondition.LIKE,whereStrategy=FieldStrategy.NOT_NULL]
        //             <!if>
        //             AND deleted = 0
        //             <if test= "ew.sqlSegment != null and ew.sqlSegment != '' and "ew.nonEmptyOfNormal"" > AND ${ew.sqlSegment} </if>
        //             <if test= "ew.sqlSegment != null and ew.sqlSegment != '' and "ew.emptyOfNormal"" > ${ew.sqlSegment} </if>
        //          </when>
        //          <otherwise> AND deleted = 0 </otherwise>
        //      </choose>
        // </where>

        return entity;
    }

    public Children setEntity(T entity) {
        this.entity = entity;
        return typedThis;
    }

    public Class<T> getEntityClass() {
        if (entityClass == null && entity != null) {
            entityClass = (Class<T>) entity.getClass();
        }
        return entityClass;
    }

    public Children setEntityClass(Class<T> entityClass) {
        if (entityClass != null) {
            this.entityClass = entityClass;
        }
        return typedThis;
    }

    // ---------------------
    // 实现Compare接口
    // ---------------------


    // 核心: eq(..) 与 isNull(..)
    @Override
    public <V> Children allEq(boolean condition, Map<R, V> params, boolean null2IsNull) {
        if (condition && CollectionUtils.isNotEmpty(params)) {
            params.forEach((k, v) -> {
                if (StringUtils.checkValNotNull(v)) {
                    eq(k, v);
                } else {
                    if (null2IsNull) {
                        isNull(k);
                    }
                }
            });
        }
        return typedThis;
    }

    @Override
    public <V> Children allEq(boolean condition, BiPredicate<R, V> filter, Map<R, V> params, boolean null2IsNull) {
        if (condition && CollectionUtils.isNotEmpty(params)) {
            params.forEach((k, v) -> {
                if (filter.test(k, v)) {
                    if (StringUtils.checkValNotNull(v)) {
                        eq(k, v);
                    } else {
                        if (null2IsNull) {
                            isNull(k);
                        }
                    }
                }
            });
        }
        return typedThis;
    }

    @Override
    public Children eq(boolean condition, R column, Object val) {
        // 比如: eq(true,"name",1) ->
        return addCondition(condition, column, EQ, val);
    }

    @Override
    public Children ne(boolean condition, R column, Object val) {
        return addCondition(condition, column, NE, val);
    }

    @Override
    public Children gt(boolean condition, R column, Object val) {
        return addCondition(condition, column, GT, val);
    }

    @Override
    public Children ge(boolean condition, R column, Object val) {
        return addCondition(condition, column, GE, val);
    }

    @Override
    public Children lt(boolean condition, R column, Object val) {
        return addCondition(condition, column, LT, val);
    }

    @Override
    public Children le(boolean condition, R column, Object val) {
        return addCondition(condition, column, LE, val);
    }

    @Override
    public Children like(boolean condition, R column, Object val) {
        return likeValue(condition, LIKE, column, val, SqlLike.DEFAULT);
    }

    @Override
    public Children notLike(boolean condition, R column, Object val) {
        return likeValue(condition, NOT_LIKE, column, val, SqlLike.DEFAULT);
    }

    @Override
    public Children likeLeft(boolean condition, R column, Object val) {
        return likeValue(condition, LIKE, column, val, SqlLike.LEFT);
    }

    @Override
    public Children likeRight(boolean condition, R column, Object val) {
        return likeValue(condition, LIKE, column, val, SqlLike.RIGHT);
    }

    @Override
    public Children between(boolean condition, R column, Object val1, Object val2) {
        // 第一个SQLSegment: columnToSqlSegment(column)
        // 其 ISqlSegment.getSqlSegment() -> 返回列名
        // 第二个SqlSegment: SqlKeyword.BETWEEN
        // 其 ISqlSegment.getSqlSegment() -> 返回 BETWEEN
        // 第三个SqlSegment: () -> formatParam(null, SqlUtils.concatLike(val, sqlLike))
        // ISqlSegment.getSqlSegment() -> 返回 #{ew.paramNameValuePairs.MPGENVAL1}
        // ew.paramNameValuePairs.MPGENVAL1 对应的AbstractWrapper.paramNameValueParis字段中map的key为MPGENVAL1的value值
        // 其中value值等于 val1
        // 第四个SqlSegment: SqlKeyword.AND
        // 其 ISqlSegment.getSqlSegment() -> 返回 AND
        // 第五个SqlSegment: () -> formatParam(null, val2)
        // ISqlSegment.getSqlSegment() -> 返回 #{ew.paramNameValuePairs.MPGENVAL2}
        // ew.paramNameValuePairs.MPGENVAL2 对应的AbstractWrapper.paramNameValueParis字段中map的key为MPGENVAL2的value值
        // 其中value值等于 val2
        return maybeDo(condition, () -> appendSqlSegments(columnToSqlSegment(column), BETWEEN,
            () -> formatParam(null, val1), AND, () -> formatParam(null, val2)));
    }

    @Override
    public Children notBetween(boolean condition, R column, Object val1, Object val2) {
        return maybeDo(condition, () -> appendSqlSegments(columnToSqlSegment(column), NOT_BETWEEN,
            () -> formatParam(null, val1), AND, () -> formatParam(null, val2)));
    }

    @Override
    public Children and(boolean condition, Consumer<Children> consumer) {
        // 比如 new QueryWrapper().eq("age",12).and(i->i.eq("name","pengdong").le("count",20)
        // 结果就是:
        // 调用 expression.normalSegmentList#geTSqlSegment() ->
        // age = 12 and ( name = "pengdong" and count <= 20)
        return and(condition).addNestedCondition(condition, consumer);
    }

    @Override
    public Children or(boolean condition, Consumer<Children> consumer) {
        // 比如 new QueryWrapper().eq("age",12).or(i->i.eq("name","pengdong").le("count",20))
        // 结果就是:
        // 调用 expression.normalSegmentList#geTSqlSegment() ->
        // age = 12 or ( name = "pengdong" and count <= 20)

        // 而
        // 比如 new QueryWrapper().eq("age",12).or().eq("name","pengdong").le("count",20)
        // 结果就是:
        // 调用 expression.normalSegmentList#geTSqlSegment() ->
        // age = 12 or name = "pengdong" and count <= 20
        return or(condition).addNestedCondition(condition, consumer);
    }

    @Override
    public Children nested(boolean condition, Consumer<Children> consumer) {
        // 上面的 new QueryWrapper().eq("age",12).or(i->i.eq("name","pengdong").le("count",20))
        // 结果就是:
        // 调用 expression.normalSegmentList#geTSqlSegment() ->
        // age = 12 or ( name = "pengdong" and count <= 20)

        // 等价于:
        // new QueryWrapper().eq("age",12).or().nested(i->i.eq("name","pengdong").le("count",20))

        return addNestedCondition(condition, consumer);
    }

    @Override
    public Children not(boolean condition, Consumer<Children> consumer) {
        // 上面的 new QueryWrapper().eq("age",12).not(i->i.in("name","p1","p2","p3").or().eq("age",45))
        // 结果就是:
        // 调用 expression.normalSegmentList#geTSqlSegment() ->
        // age = 12 and not ( name in ("p1","p2","p3") or age = 45)
        return not(condition).addNestedCondition(condition, consumer);
    }

    @Override
    public Children or(boolean condition) {
        // 拼接 OR
        // 注意事项:
        // 主动调用or表示紧接着下一个方法不是用and连接!(不调用or则默认为使用and连接)
        // 例: eq("id",1).or().eq("name","老王")---> id = 1 or name = '老王'
        // 例: eq("id",1).or().eq("name","老王").eq("age",23)---> id = 1 or name = '老王' and age = 23

        return maybeDo(condition, () -> appendSqlSegments(OR));
    }

    @Override
    public Children apply(boolean condition, String applySql, Object... values) {
        // 拼接 sql
        // 注意事项:
        // 该方法可用于数据库函数 动态入参的params对应前面applySql内部的{index}部分.这样是不会有sql注入风险的,反之会有!
        // 例: apply("id = 1")--->id = 1
        // 例: apply("date_format(dateColumn,'%Y-%m-%d') = '2008-08-08'") --->date_format(dateColumn,'%Y-%m-%d') = '2008-08-08'")
        // 例: apply("date_format(dateColumn,'%Y-%m-%d') = {0}", "2008-08-08") --->date_format(dateColumn,'%Y-%m-%d') = '2008-08-08'")
        // 例: apply("id in ({0},{1},{2})", 10, 11, 12) ---> id in (10,11,12)
        return maybeDo(condition, () -> appendSqlSegments(APPLY,
            () -> formatSqlMaybeWithParam(applySql, null, values)));
    }

    @Override
    public Children last(boolean condition, String lastSql) {
        // 无视优化规则直接拼接到 sqlSegment 中去哦 -> 当前类#getSqlSegment() -> return expression.getSqlSegment() + lastSql.getStringValue();
        // 注意事项:
        // 只能调用一次,多次调用以最后一次为准 有sql注入的风险,请谨慎使用
        // 例: last("limit 1")

        if (condition) {
            this.lastSql.setStringValue(StringPool.SPACE + lastSql);
        }
        return typedThis;
    }

    @Override
    public Children comment(boolean condition, String comment) {
        // 设置: 注释
        if (condition) {
            this.sqlComment.setStringValue(comment);
        }
        return typedThis;
    }

    @Override
    public Children first(boolean condition, String firstSql) {
        // 设置sql最前面的语句:
        if (condition) {
            this.sqlFirst.setStringValue(firstSql);
        }
        return typedThis;
    }

    @Override
    public Children exists(boolean condition, String existsSql, Object... values) {
        // 拼接 EXISTS ( sql语句 )
        // 例: exists("select id from table where age = {0}", 12) ---> exists (select id from table where age = 12)
        return maybeDo(condition, () -> appendSqlSegments(EXISTS,
            () -> String.format("(%s)", formatSqlMaybeWithParam(existsSql, null, values))));
    }

    @Override
    public Children notExists(boolean condition, String existsSql, Object... values) {
        // 通过 not(..) + exists(..) 即可达到 notExists(..) 的效果
        return not(condition).exists(condition, existsSql, values);
    }

    @Override
    public Children isNull(boolean condition, R column) {
        // 不过多阐述
        return maybeDo(condition, () -> appendSqlSegments(columnToSqlSegment(column), IS_NULL));
    }

    @Override
    public Children isNotNull(boolean condition, R column) {
        // 不过多阐述
        return maybeDo(condition, () -> appendSqlSegments(columnToSqlSegment(column), IS_NOT_NULL));
    }

    @Override
    public Children in(boolean condition, R column, Collection<?> coll) {
        return maybeDo(condition, () -> appendSqlSegments(columnToSqlSegment(column), IN, inExpression(coll)));
    }

    @Override
    public Children in(boolean condition, R column, Object... values) {
        // 例如: in(true,"id",10,11,12) -> id in (#{ew.paramNameValuePairs.MPGENVAL1},#{ew.paramNameValuePairs.MPGENVAL2},#{ew.paramNameValuePairs.MPGENVAL3})
        // 在AbstractWrapper.paramNameValueParis中mMPGENVAL1对应的值就会10,MPGENVAL2对应的值就会11,MPGENVAL3对应的值就会12,
        return maybeDo(condition, () -> appendSqlSegments(columnToSqlSegment(column), IN, inExpression(values)));
    }

    @Override
    public Children notIn(boolean condition, R column, Collection<?> coll) {
        // 不过多阐述
        return maybeDo(condition, () -> appendSqlSegments(columnToSqlSegment(column), NOT_IN, inExpression(coll)));
    }

    @Override
    public Children notIn(boolean condition, R column, Object... values) {
        // 不过多阐述
        return maybeDo(condition, () -> appendSqlSegments(columnToSqlSegment(column), NOT_IN, inExpression(values)));
    }

    @Override
    public Children inSql(boolean condition, R column, String inValue) {
        // 例如 inSql(true, "id", "select id from t_user")
        // id in ( select id from t_user )
        return maybeDo(condition, () -> appendSqlSegments(columnToSqlSegment(column), IN,
            () -> String.format("(%s)", inValue)));
    }

    @Override
    public Children gtSql(boolean condition, R column, String inValue) {
        // 不过多阐述
        return maybeDo(condition, () -> appendSqlSegments(columnToSqlSegment(column), GT,
            () -> String.format("(%s)", inValue)));
    }

    @Override
    public Children geSql(boolean condition, R column, String inValue) {
        // 不过多阐述
        return maybeDo(condition, () -> appendSqlSegments(columnToSqlSegment(column), GE,
            () -> String.format("(%s)", inValue)));
    }

    @Override
    public Children ltSql(boolean condition, R column, String inValue) {
        return maybeDo(condition, () -> appendSqlSegments(columnToSqlSegment(column), LT,
            () -> String.format("(%s)", inValue)));
    }

    @Override
    public Children leSql(boolean condition, R column, String inValue) {
        return maybeDo(condition, () -> appendSqlSegments(columnToSqlSegment(column), LE,
            () -> String.format("(%s)", inValue)));
    }

    @Override
    public Children notInSql(boolean condition, R column, String inValue) {
        return maybeDo(condition, () -> appendSqlSegments(columnToSqlSegment(column), NOT_IN,
            () -> String.format("(%s)", inValue)));
    }

    @Override
    public Children groupBy(boolean condition, R column, R... columns) {
        // 构建 group by 的sql脚本片段

        return maybeDo(condition, () -> {
            String one = columnToString(column);
            if (ArrayUtils.isNotEmpty(columns)) {
                one += (StringPool.COMMA + columnsToString(columns));
            }
            final String finalOne = one;
            // 第一个SqlSegment为: SqlKeyword.GROUP_BY
                // 返回: GROUP BY
            // 第二个SqlSegment为: () -> finalOne
                // 返回: column,column1,column2...
            appendSqlSegments(GROUP_BY, () -> finalOne);
        });
    }

    @Override
    @SafeVarargs
    public final Children orderBy(boolean condition, boolean isAsc, R column, R... columns) {
        return maybeDo(condition, () -> {
            final SqlKeyword mode = isAsc ? ASC : DESC;
            // 第一个SqlSegment为: SqlKeyword.ORDER_BY
                // 返回: ORDER BY
            // 第二个SqlSegment为: columnToSqlSegment(columnSqlInjectFilter(column)) [note:columnSqlInjectFilter(column)主要是检查是否有sql注入问题,无问题后去除空白符号]
                // 返回: column,column1,column2...
            // 第三个SqlSegment: mode
                // 返回: ASC 或者 DESC
            appendSqlSegments(ORDER_BY, columnToSqlSegment(columnSqlInjectFilter(column)), mode);
            if (ArrayUtils.isNotEmpty(columns)) {
                Arrays.stream(columns).forEach(c -> appendSqlSegments(ORDER_BY,
                    columnToSqlSegment(columnSqlInjectFilter(c)), mode));
            }
        });
    }

    @Override
    public Children groupBy(boolean condition, R column) {
        return maybeDo(condition, () -> appendSqlSegments(GROUP_BY, () -> columnToString(column)));
    }

    @Override
    public Children groupBy(boolean condition, List<R> columns) {
        return maybeDo(condition, () -> appendSqlSegments(GROUP_BY, () -> columnsToString(columns)));
    }

    @Override
    public Children orderBy(boolean condition, boolean isAsc, R column) {
        return maybeDo(condition, () -> appendSqlSegments(ORDER_BY, columnToSqlSegment(columnSqlInjectFilter(column)),
            isAsc ? ASC : DESC));
    }

    @Override
    public Children orderBy(boolean condition, boolean isAsc, List<R> columns) {
        return maybeDo(condition, () -> columns.forEach(c -> appendSqlSegments(ORDER_BY,
            columnToSqlSegment(columnSqlInjectFilter(c)), isAsc ? ASC : DESC)));
    }

    /**
     * 字段 SQL 注入过滤处理，子类重写实现过滤逻辑
     *
     * @param column 字段内容
     * @return
     */
    protected R columnSqlInjectFilter(R column) {
        return column;
    }

    @Override
    public Children having(boolean condition, String sqlHaving, Object... params) {
        // HAVING ( sql语句 )
        // 例: having("sum(age) > 10")--->having sum(age) > 10
        // 例: having("sum(age) > {0}", 11)--->having sum(age) > 11
        return maybeDo(condition, () -> appendSqlSegments(HAVING,
            () -> formatSqlMaybeWithParam(sqlHaving, null, params)));
    }

    @Override
    public Children func(boolean condition, Consumer<Children> consumer) {
        // func 方法(主要方便在出现if...else下调用不同方法能不断链)
        // 例: func(i -> if(true) {i.eq("id", 1)} else {i.ne("id", 1)})
        return maybeDo(condition, () -> consumer.accept(typedThis));
    }

    /**
     * 内部自用
     * <p>NOT 关键词</p>
     */
    protected Children not(boolean condition) {
        // NOT 嵌套
        // 主要作用向expression.NormalSegmentList中追尾添加一个 SqlKeyword.AND 进去 [❗️❗️❗️]
        return maybeDo(condition, () -> appendSqlSegments(NOT));
    }

    /**
     * 内部自用
     * <p>拼接 AND</p>
     */
    protected Children and(boolean condition) {
        // AND 嵌套
        // 主要作用向expression.NormalSegmentList中追尾添加一个 SqlKeyword.AND 进去 [❗️❗️❗️]
        return maybeDo(condition, () -> appendSqlSegments(AND));
    }

    /**
     * 内部自用
     * <p>拼接 LIKE 以及 值</p>
     */
    protected Children likeValue(boolean condition, SqlKeyword keyword, R column, Object val, SqlLike sqlLike) {
        // 第一个SQLSegment: columnToSqlSegment(column)
        // 其 ISqlSegment.getSqlSegment() -> 返回列名
        // 第二个SqlSegment: SqlKeyword
        // 比如 SqlKeyword.LIKE 对应的 ISqlSegment.getSqlSegment() -> 返回 LIKE
        // 第三个SqlSegment: () -> formatParam(null, SqlUtils.concatLike(val, sqlLike))
        // ISqlSegment.getSqlSegment() -> 返回 #{ew.paramNameValuePairs.MPGENVAL1}
        // ew.paramNameValuePairs.MPGENVAL1 对应的AbstractWrapper.paramNameValueParis字段中map的key为MPGENVAL1的value值
        // 其中value值取决于:  SqlUtils.concatLike(val, sqlLike) -> 返回 %val 或者 val% 或者 %val%
        return maybeDo(condition, () -> appendSqlSegments(columnToSqlSegment(column), keyword,
            () -> formatParam(null, SqlUtils.concatLike(val, sqlLike))));
    }

    /**
     * 普通查询条件
     *
     * @param condition  是否执行
     * @param column     属性
     * @param sqlKeyword SQL 关键词
     * @param val        条件值
     */
    protected Children addCondition(boolean condition, R column, SqlKeyword sqlKeyword, Object val) {
        // 普通查询条件
        // 第1个SQLSegment:  columnToSqlSegment(column) -> 将column转换为ISqlSegment即
        // () -> columnToString(column) 用来返回列名
        // 第2个SQLSegment: sqlKeyword
        // 可以是 SqlKeyword.EQUAL SqlKeyword.NOT_NULL SqlKeyword.LIKE 等等
        // 第2个SQLSegment: () -> formatParam(null, val) -> 对应ISqlSegment的lambda表达式 ->
        //  用来将生成安全参数: #{ew.paramNaeValueParis.MPGENVAL1}  [❗️❗️❗️表示最终的形参的值,实际上是从 -> AbstractWrapper的paramNaeValueParis参数中以"MPGENVAL1"为key获取值]

        // 以:
        // addCondition(true,"name",SqlKeyword.EQUAL,"彭东") 为例
        // 最终向 NormalSegmentList 加入上面对应的三个SegmentList
        // 且调用 AbstractWrapper.expression.getSqlSegmentList() 返回的就是: name = "彭东"

        return maybeDo(condition, () -> appendSqlSegments(columnToSqlSegment(column), sqlKeyword,
            () -> formatParam(null, val)));
    }

    /**
     * 多重嵌套查询条件
     *
     * @param condition 查询条件值
     */
    protected Children addNestedCondition(boolean condition, Consumer<Children> consumer) {
        // ❗️❗️❗️
        return maybeDo(condition, () -> {
            // 1. 生成了一个新的Wrapper对象,以QueryWrapper为例
            // 其expression是新的MergeSqlSegments对象,sqlFirst\sqlComment\lastSql都是新的SharedString.empty()
            // instance和this持有共同的paramNameSeq/paramNameValuePairs/paramAlias/entity/entityClass
            final Children instance = instance();
            // 2. 处理嵌套的sql查询条件哦
            consumer.accept(instance);
            // 3. 接入到this的and嵌套语句中 -> and ( instance.getSqlSegment()的值 )
            appendSqlSegments(APPLY, instance);
        });
    }

    /**
     * 子类返回一个自己的新对象
     */
    protected abstract Children instance();

    /**
     * 格式化 sql
     * <p>
     * 支持 "{0}" 这种,或者 "sql {0} sql" 这种
     *
     * @param sqlStr  可能是sql片段
     * @param mapping 例如: "javaType=int,jdbcType=NUMERIC,typeHandler=xxx.xxx.MyTypeHandler" 这种
     * @param params  参数
     * @return sql片段
     */
    @SuppressWarnings("SameParameterValue")
    protected final String formatSqlMaybeWithParam(String sqlStr, String mapping, Object... params) {
        // 格式化 sql
        // 支持 "{0}" 这种,或者 "sql {0} sql" 这种
        if (StringUtils.isBlank(sqlStr)) {
            // todo 何时会这样?
            return null;
        }
        if (ArrayUtils.isNotEmpty(params)) {
            for (int i = 0; i < params.length; ++i) {
                final String target = Constants.LEFT_BRACE + i + Constants.RIGHT_BRACE;
                sqlStr = sqlStr.replace(target, formatParam(mapping, params[i]));
            }
        }
        return sqlStr;
    }

    /**
     * 处理入参
     *
     * @param mapping 例如: "javaType=int,jdbcType=NUMERIC,typeHandler=xxx.xxx.MyTypeHandler" 这种
     * @param param   参数
     * @return value
     */
    protected final String formatParam(String mapping, Object param) {
        // ❗️❗️❗️ 重要: 决定了形参值param将被存入到字段paramNameValuePairs中
        // 举例:
        // mapping为null,parma="pengdong"
        // 存入字段paramNameValuePairs中,以"MPGENVAL1"为key,以"pengdong"为value
        // 返回 #{ew.paramNameValuePairs.MPGENVAL1}
        // 后续mybatis.xml解析中,就会去AbstractWrapper的paramNameValuePairs字段中以"MPGENVAL1"为key获取对应的value即"pengdong"

        // 1.自动生成的形参名: MPGENVAL1...MPGENVAL2
        final String genParamName = Constants.WRAPPER_PARAM + paramNameSeq.incrementAndGet();
        // 2. 参数字符串
        // 在QueryWrapper中为: "ew" + ".paramNameValuePairs." + genParamName
        // 举例: ew.paramNaeValueParis.MPGENVAL1
        final String paramStr = getParamAlias() + Constants.WRAPPER_PARAM_MIDDLE + genParamName;
        // 3. 存入"paramNameValuePairs"中
        paramNameValuePairs.put(genParamName, param);
        // 4. 形成安全参数
        // mapping为空 -> #{paramStr}
        // mapping非空 -> #{paramStr + "," + mapping}
        return SqlScriptUtils.safeParam(paramStr, mapping);
    }

    /**
     * 函数化的做事
     *
     * @param condition 做不做
     * @param something 做什么
     * @return Children
     */
    protected final Children maybeDo(boolean condition, DoSomething something) {
        if (condition) {
            something.doIt();
        }
        return typedThis;
    }

    /**
     * 获取in表达式 包含括号
     *
     * @param value 集合
     */
    protected ISqlSegment inExpression(Collection<?> value) {
        if (CollectionUtils.isEmpty(value)) {
            return () -> "()";
        }
        return () -> value.stream().map(i -> formatParam(null, i))
            .collect(joining(StringPool.COMMA, StringPool.LEFT_BRACKET, StringPool.RIGHT_BRACKET));
    }

    /**
     * 获取in表达式 包含括号
     *
     * @param values 数组
     */
    protected ISqlSegment inExpression(Object[] values) {
        if (ArrayUtils.isEmpty(values)) {
            return () -> "()";
        }
        return () -> Arrays.stream(values).map(i -> formatParam(null, i))
            .collect(joining(StringPool.COMMA, StringPool.LEFT_BRACKET, StringPool.RIGHT_BRACKET));
    }

    /**
     * 必要的初始化
     */
    protected void initNeed() {
        paramNameSeq = new AtomicInteger(0);
        paramNameValuePairs = new HashMap<>(16);
        expression = new MergeSegments();
        lastSql = SharedString.emptyString();
        sqlComment = SharedString.emptyString();
        sqlFirst = SharedString.emptyString();
    }

    @Override
    public void clear() {
        entity = null;
        paramNameSeq.set(0);
        paramNameValuePairs.clear();
        expression.clear();
        lastSql.toEmpty();
        sqlComment.toEmpty();
        sqlFirst.toEmpty();
    }

    /**
     * 添加 where 片段
     *
     * @param sqlSegments ISqlSegment 数组
     */
    protected void appendSqlSegments(ISqlSegment... sqlSegments) {
        // 将sqlSegments追加到expression中去
        expression.add(sqlSegments);
    }

    /**
     * 是否使用默认注解 {@link OrderBy} 排序
     *
     * @return true 使用 false 不使用
     */
    public boolean isUseAnnotationOrderBy() {
        // 以BaseMapper注入的CRUD的方法为例 -> BaseMapper#selectList(@Param(Constants.WRAPPER) Wrapper<T> queryWrapper) -> 传递进去的是就是当前Wrapper
        // 然后再 SelectList#injectMappedStatement(...) 中
        // %s SELECT %s FROM %s %s %s %s
            // 第五个%s: sqlOrderBy(tableInfo) -> 确定排序规则
            // <if test=" ew == null or ew.useAnnotationOrderBy">
            //      ORDER BY column1 asc, column2 desc               实体类的标注有@OrderBy的字段
            // </if>

        // 1. 检查是否有调用AbstractWrapper.orderBy(..)等等同名方法 -> 一旦调用,那么@OrderBy标注的实体类的字段排序将失效
        final String _sqlSegment = this.getSqlSegment();
        if (StringUtils.isBlank(_sqlSegment)) {
            return true;
        }
        // 2. 检查_sqlSegment中是否存在 ORDER BY 关键词 或者 LIMIT
        final String _sqlSegmentToUpperCase = _sqlSegment.toUpperCase();
        return !(_sqlSegmentToUpperCase.contains(Constants.ORDER_BY)
            || _sqlSegmentToUpperCase.contains(Constants.LIMIT));
    }

    // ---------------------
    // 重写 Wrapper 接口的部分方法哦
    // ---------------------

    @Override
    public String getSqlSegment() {
        // 以BaseMapper注入的CRUD的方法为例 -> BaseMapper#selectList(@Param(Constants.WRAPPER) Wrapper<T> queryWrapper) -> 传递进去的是就是当前Wrapper
        // 然后再 SelectList#injectMappedStatement(...) 中
        // %s SELECT %s FROM %s %s %s %s
        // 第四个%s: sqlWhereEntityWrapper(true, tableInfo) -> 获取<where>标签
        // <where>
        //      <choose>
        //          <when test=" ew != null">
        //             <if test="ew.entity != null">
        //                  <if test=" #{ew.entity. + keyProperty} != null "> keyColumn = #{ew.entity. + keyProperty} </if>
        //                  <if test=" #{ew.entity.property1} != null " > AND column = #{ew.entity. + e1l} </if>   // ❗️❗️❗ <if>标签的test是收@TableField.whereStrategy()控制确定成立条件,AND后的比较逻辑是受到@TableField.condition()控制的️
        //                  <if test=" #{ew.entity.property2} != null and #{ew.entity.property} != ''" > AND column != #{ew.entity. + el2} </if> //  [比如: condition为SQLCondition.EQUALS,whereStrategy=FieldStrategy.NOT_EMPTY]
        //                  <if test=" #{ew.entity.property3} != null " > AND column LIKE concat('%',#{ew.entity. + el3},'%'} </if>   //  [比如: condition为SQLCondition.LIKE,whereStrategy=FieldStrategy.NOT_NULL]
        //             <!if>
        //             AND deleted = 0
        //             <if test= "ew.sqlSegment != null and ew.sqlSegment != '' and "ew.nonEmptyOfNormal"" > AND ${ew.sqlSegment} </if>
        //             <if test= "ew.sqlSegment != null and ew.sqlSegment != '' and "ew.emptyOfNormal"" > ${ew.sqlSegment} </if>
        //          </when>
        //          <otherwise> deleted = 0 </otherwise>
        //      </choose>
        // </where>

        // ❗️❗️❗️
        // 触发: expression.getSqlSegment + lastSql.getStringValue()
        // 在上面我们可以看见:
        //             <if test= "ew.sqlSegment != null and ew.sqlSegment != '' and "ew.nonEmptyOfNormal"" > AND ${ew.sqlSegment} </if>
        //             <if test= "ew.sqlSegment != null and ew.sqlSegment != '' and "ew.emptyOfNormal"" > ${ew.sqlSegment} </if>
        // 也就说: 即使AbstractWrapper#getSqlSegment()非空
        //      a:ew.emptyOfNormal为true,即expression.NormalSegmentList为空,表示不存在存在sql关键词where后的sql脚本片段,因此直接使用 ${ew.sqlSegment} = groupBy.getSqlSegment() + having.getSqlSegment() + orderBy.getSqlSegment()
        //      b:ew.nonEmptyOfNormal为true,即expression.NormalSegmentList不为空,表示存在sql关键词where后的sql脚本片段,因此需要有 AND ${ew.sqlSegment} = normal.getSqlSegment() + groupBy.getSqlSegment() + having.getSqlSegment() + orderBy.getSqlSegment()
        // ❗️❗️❗️
        // 注意: sql标签<where>并不是表示里面就不可以使用Order by\Group by\Having等等,实际上是可以的
        // 以为<where>标签就是<trim>标签.会默认添加where关键词,并且后续的第一个如果是AND\WHERE\NOT,就将去去掉哦 [❗️❗️❗️]
        return expression.getSqlSegment() + lastSql.getStringValue();
    }

    @Override
    public String getSqlComment() {
        // 以BaseMapper注入的CRUD的方法为例 -> BaseMapper#selectList(@Param(Constants.WRAPPER) Wrapper<T> queryWrapper) -> 传递进去的是就是当前Wrapper
        // 然后再 SelectList#injectMappedStatement(...) 中
        // %s SELECT %s FROM %s %s %s %s
        // 第六个%s: sqlComment() ->
        // <if test=" ew != null and ew.sqlComment != null">
        //      ${ew.sqlComment}
        // </if>
        if (StringUtils.isNotBlank(sqlComment.getStringValue())) {
            return "/*" + StringEscape.escapeRawString(sqlComment.getStringValue()) + "*/"; // ❗️❗️❗️ -> 真的是注释哦 [被 "/*" 和 "*/" 给括起来啦]
        }
        return null;
    }

    @Override
    public String getSqlFirst() {
        // 以BaseMapper注入的CRUD的方法为例 -> BaseMapper#selectList(@Param(Constants.WRAPPER) Wrapper<T> queryWrapper) -> 传递进去的是就是当前Wrapper
        // 然后再 SelectList#injectMappedStatement(...) 中
        // %s SELECT %s FROM %s %s %s %s
        // 第一个%s为:
        // <if test=" ew != null and ew.sqlFirst != null">
        //      ${ew.sqlFirst}
        // </test>

        if (StringUtils.isNotBlank(sqlFirst.getStringValue())) {
            return StringEscape.escapeRawString(sqlFirst.getStringValue());
        }
        return null;
    }

    @Override
    public MergeSegments getExpression() {
        return expression;
    }

    public Map<String, Object> getParamNameValuePairs() {
        return paramNameValuePairs;
    }

    public String getParamAlias() {
        // 当前类作为参数时提供的别名
        // QueryWrapper作为MP注入的CRUD方法的形参是: @Pram.value() 就是 Constants.WRAPPER  表示形参名为 "ew"
        return paramAlias == null ? Constants.WRAPPER : paramAlias.getStringValue();
    }

    /**
     * 参数别名设置，初始化时优先设置该值、重复设置异常
     *
     * @param paramAlias 参数别名
     * @return Children
     */
    @SuppressWarnings("unused")
    public Children setParamAlias(String paramAlias) {
        Assert.notEmpty(paramAlias, "paramAlias can not be empty!");
        Assert.isEmpty(paramNameValuePairs, "Please call this method before working!");
        Assert.isNull(this.paramAlias, "Please do not call the method repeatedly!");
        this.paramAlias = new SharedString(paramAlias);
        return typedThis;
    }

    /**
     * 获取 columnName
     */
    protected final ISqlSegment columnToSqlSegment(R column) {
        // 内部Lambda形式: 将column转换为ISqlSegment

        return () -> columnToString(column);
    }

    /**
     * 获取 columnName
     */
    protected String columnToString(R column) {
        // note: LambdaQueryWrapper重写了该方法哦 ❗️❗️❗️❗️❗️❗️❗️❗️❗️
        return (String) column;
    }

    /**
     * 获取 columnNames
     */
    protected String columnsToString(R... columns) {
        return Arrays.stream(columns).map(this::columnToString).collect(joining(StringPool.COMMA));
    }

    /**
     * 多字段转换为逗号 "," 分割字符串
     *
     * @param columns 多字段
     */
    protected String columnsToString(List<R> columns) {
        // 多字段转换为逗号 "," 分割字符串
        return columns.stream().map(this::columnToString).collect(joining(StringPool.COMMA));
    }

    @Override
    @SuppressWarnings("all")
    public Children clone() {
        return SerializationUtils.clone(typedThis);
    }

    /**
     * 做事函数
     */
    @FunctionalInterface
    public interface DoSomething {

        void doIt();
    }
}
