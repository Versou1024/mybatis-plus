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
package com.baomidou.mybatisplus.extension.plugins.inner;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.core.toolkit.*;
import com.baomidou.mybatisplus.extension.plugins.pagination.DialectFactory;
import com.baomidou.mybatisplus.extension.plugins.pagination.DialectModel;
import com.baomidou.mybatisplus.extension.plugins.pagination.dialects.IDialect;
import com.baomidou.mybatisplus.extension.toolkit.JdbcUtils;
import com.baomidou.mybatisplus.extension.toolkit.PropertyMapper;
import com.baomidou.mybatisplus.extension.toolkit.SqlParserUtils;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.*;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 分页拦截器
 * <p>
 * 默认对 left join 进行优化,虽然能优化count,但是加上分页的话如果1对多本身结果条数就是不正确的
 *
 * @author hubin
 * @since 3.4.0
 */
@Data
@NoArgsConstructor
@SuppressWarnings({"rawtypes"})
public class PaginationInnerInterceptor implements InnerInterceptor {
    // 位于: extension模块plugins.inner内部插件包下

    // 作用:
    // 分页拦截器,实现了Mybaits-Plus提供的InnerInterceptor

    // 使用: ->
    // 目标之PaginationInnerInterceptor 如何生效
        //    @Bean
        //    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        //        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        //        PaginationInnerInterceptor paginationInterceptor = new PaginationInnerInterceptor(DbType.MYSQL);
        //        interceptor.addInnerInterceptor(paginationInterceptor);
        //        ....
        //        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor()); // 用户可以添加内置的或者用户自己的InnerInterceptor
        //        return interceptor;
        //    }

    // 获取jsqlparser中count的SelectItem
    protected static final List<SelectItem> COUNT_SELECT_ITEM = Collections.singletonList(
        new SelectExpressionItem(new Column().withColumnName("COUNT(*)")).withAlias(new Alias("total"))
    );

    // 缓存: key为 count sql 的MappedStatement的id
    protected static final Map<String, MappedStatement> countMsCache = new ConcurrentHashMap<>();
    protected final Log logger = LogFactory.getLog(this.getClass());


    // 溢出总页数后是否进行处理
    // 场景: 比如IPage#getCurrent()要求到查看第16页的数据,实际上通过计算total=54和size=5,发现只有11页数据,根本没有第16页的数据
    //  a: 默认overflow为false的时候,超出总页数,接下来的分页查询sql不再执行
    //  b: overflow设置为true的时候,超出总页数,会将 IPage.current 字段重定向到第一页,分页查询sql会继续执行的哦
    protected boolean overflow;

    // 单页分页条数限制
    protected Long maxLimit;

    // ---------------------
    // ❗️❗️❗️ IDialect的优先级比DbType高.两者的作用都是用来确定的count sql的IDialect
    //       当仅仅使用DbType时,会通过DialectFactory.getDialect(dbType)确定当前分页拦截器使用的IDialect
    // 原因: 不同的数据库,使用分页查询是不一样的,比如
    //      MySql -> LIMIT offset,limit
    // 通过IDialect#buildPaginationSql(..)可以确定该数据库类型是如何追加组装的分页语句的哦
    // ---------------------

    // 数据库类型
    private DbType dbType;

    // 方言实现类
    // 查看 findIDialect(Executor) 逻辑
    private IDialect dialect;

    // 生成 countSql 优化掉 join 现在只支持 left join
    protected boolean optimizeJoin = true;

    public PaginationInnerInterceptor(DbType dbType) {
        this.dbType = dbType;
    }

    public PaginationInnerInterceptor(IDialect dialect) {
        this.dialect = dialect;
    }

    /**
     * 这里进行count,如果count为0这返回false(就是不再执行sql了)
     */
    @Override
    public boolean willDoQuery(Executor executor, MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) throws SQLException {
        // 1. 检查请求参数中是否存在: IPage有的话,查找并返回
        IPage<?> page = ParameterUtils.findPage(parameter).orElse(null);
        // 2. page的size小于0,或者searchCount返回false,表示不需要执行 count sql
        if (page == null || page.getSize() < 0 || !page.searchCount()) {
            return true;
        }

        // 3. 开始构建 count sql 的 MappedStatement

        BoundSql countSql;
        // 3.1 构建countMs -> 方式一: 前提是: IPage.getCountId() 非空,并且存在countMsCache中存在countId对应的MappedStatement
        MappedStatement countMs = buildCountMappedStatement(ms, page.countId());
        // 3.2.1 countMs非空 ->
        if (countMs != null) {
            countSql = countMs.getBoundSql(parameter);
        }
        // 3.2.3 countMs如果为空 -> 方式二: 尝试自动构建 buildAutoCountMappedStatement(...)
        else {
            // 3.2.3.1 mp自动构建count sql的MappedStatement [❗️❗️❗️]
            countMs = buildAutoCountMappedStatement(ms);
            // 3.2.3.1 自动构建 count sql -> 需要传入IPage对象,以及当前被拦截的执行的boundSql [❗️❗️❗️ -> 实在是复杂到了机制]
            String countSqlStr = autoCountSql(page, boundSql.getSql());
            PluginUtils.MPBoundSql mpBoundSql = PluginUtils.mpBoundSql(boundSql);
            countSql = new BoundSql(countMs.getConfiguration(), countSqlStr, mpBoundSql.parameterMappings(), parameter);
            PluginUtils.setAdditionalParameter(countSql, mpBoundSql.additionalParameters());
        }

        // 3.3 创建CacheKey
        CacheKey cacheKey = executor.createCacheKey(countMs, parameter, rowBounds, countSql);
        // 3.4 执行查询 count sql -> executor.query(..)
        List<Object> result = executor.query(countMs, parameter, rowBounds, resultHandler, cacheKey, countSql);
        long total = 0;
        // 3.5 解析出查询出来的 count 数量
        if (CollectionUtils.isNotEmpty(result)) {
            // 个别数据库 count 没数据不会返回 0
            Object o = result.get(0);
            if (o != null) {
                total = Long.parseLong(o.toString());
            }
        }
        // 3.6 ❗️❗️❗️ -> 回填到形参IPage的total变量中
        page.setTotal(total);
        // 3.7 ❗️❗️❗️ count 查询之后,是否继续执行分页 -> // 场景: 比如IPage#getCurrent()要求到查看第16页的数据,实际上通过计算total=54和size=5,发现只有11页数据,根本没有第16页的数据,无须继续查询
        return continuePage(page);
    }

    @Override
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) throws SQLException {
        // 准备:执行分页查询

        // 1. 请求参数中是否存在IPage类型的形参 -> 没有就return
        IPage<?> page = ParameterUtils.findPage(parameter).orElse(null);
        if (null == page) {
            return;
        }

        // 2. 处理 orderBy 拼接 -> 前提是 IPage.orders() 有指定orderBy拼接哦
        boolean addOrdered = false;
        String buildSql = boundSql.getSql();
        List<OrderItem> orders = page.orders(); // IPage#orders()的作用 -> ❗️❗️❗️❗️❗️❗️
        if (CollectionUtils.isNotEmpty(orders)) {
            addOrdered = true;
            buildSql = this.concatOrderBy(buildSql, orders);
        }

        // 3. size 小于 0 且不限制返回值则不构造分页sql
        Long _limit = page.maxLimit() != null ? page.maxLimit() : maxLimit;
        if (page.getSize() < 0 && null == _limit) {
            if (addOrdered) {
                PluginUtils.mpBoundSql(boundSql).sql(buildSql);
            }
            return;
        }

        // 4. 处理超出分页条数限制maxLimit,将IPage.setSize()默认归为限制数maxLimit大小
        handlerLimit(page, _limit);

        // 5. 确定当前分页拦截器使用的IDialect,确定当前数据库的分页语句格式
        IDialect dialect = findIDialect(executor);

        // 6. 调用 IDialect#buildPaginationSql(..) 确定 分页的方言模型 DialectModel
        final Configuration configuration = ms.getConfiguration();
        DialectModel model = dialect.buildPaginationSql(buildSql, page.offset(), page.getSize());

        // 7.  从 BoundSql 转换为 MPBoundSql [关于MPBoundSql请见对应的代码介绍]
        PluginUtils.MPBoundSql mpBoundSql = PluginUtils.mpBoundSql(boundSql);

        // 8. 从BoundSql中拿到所有的ParameterMapping集合,以及additionalParameter字段值
        List<ParameterMapping> mappings = mpBoundSql.parameterMappings();
        Map<String, Object> additionalParameter = mpBoundSql.additionalParameters();
        // 9. 使用 分页方言 ->
        model.consumers(mappings, configuration, additionalParameter);
        // 10. 添加分页语句后,将sql语句重写到MPBoundSql上
        mpBoundSql.sql(model.getDialectSql());
        // 11. 重新设置回去
        mpBoundSql.parameterMappings(mappings);
    }

    /**
     * 获取分页方言类的逻辑
     *
     * @param executor Executor
     * @return 分页方言类
     */
    protected IDialect findIDialect(Executor executor) {
        // 1. 是否直接确定的dialect -> 很少这样使用
        if (dialect != null) {
            return dialect;
        }
        // 2. dbType不为空,一般情况创建:
        // [❗️❗️❗️]  PaginationInnerInterceptor paginationInterceptor = new PaginationInnerInterceptor(DbType.MYSQL);
        // 大部分都是 DbType.MYSQL 类型的
        if (dbType != null) {
            dialect = DialectFactory.getDialect(dbType);
            return dialect;
        }
        return DialectFactory.getDialect(JdbcUtils.getDbType(executor));
    }

    /**
     * 获取指定的 id 的 MappedStatement
     *
     * @param ms      MappedStatement
     * @param countId id
     * @return MappedStatement
     */
    protected MappedStatement buildCountMappedStatement(MappedStatement ms, String countId) {

        // 前提是: IPage.getCountId() 非空,并且存在countMsCache中存在countId对应的MappedStatement
        if (StringUtils.isNotBlank(countId)) {
            final String id = ms.getId();
            if (!countId.contains(StringPool.DOT)) {
                countId = id.substring(0, id.lastIndexOf(StringPool.DOT) + 1) + countId;
            }
            final Configuration configuration = ms.getConfiguration();
            try {
                return CollectionUtils.computeIfAbsent(countMsCache, countId, key -> configuration.getMappedStatement(key, false));
            } catch (Exception e) {
                logger.warn(String.format("can not find this countId: [\"%s\"]", countId));
            }
        }
        return null;
    }

    /**
     * 构建 mp 自用自动的 MappedStatement
     *
     * @param ms MappedStatement
     * @return MappedStatement
     */
    protected MappedStatement buildAutoCountMappedStatement(MappedStatement ms) {

        // 1. 确定countId -> 当前执行的分页查询方法的MappedStatement的id + "_mpCount";
        final String countId = ms.getId() + "_mpCount";
        // 2. 拿到MybatisConfiguration
        final Configuration configuration = ms.getConfiguration();
        // 3. 构建MappedStatement,并存入到countMsCache缓存中
        return CollectionUtils.computeIfAbsent(countMsCache, countId, key -> {
            MappedStatement.Builder builder = new MappedStatement.Builder(configuration, key, ms.getSqlSource(), ms.getSqlCommandType());
            builder.resource(ms.getResource());
            builder.fetchSize(ms.getFetchSize());
            builder.statementType(ms.getStatementType());
            builder.timeout(ms.getTimeout());
            builder.parameterMap(ms.getParameterMap());
            builder.resultMaps(Collections.singletonList(new ResultMap.Builder(configuration, Constants.MYBATIS_PLUS, Long.class, Collections.emptyList()).build()));
            builder.resultSetType(ms.getResultSetType());
            builder.cache(ms.getCache());
            builder.flushCacheRequired(ms.isFlushCacheRequired());
            builder.useCache(ms.isUseCache());
            return builder.build();
        });
    }

    /**
     * 获取自动优化的 countSql
     *
     * @param page 参数
     * @param sql  sql
     * @return countSql
     */
    protected String autoCountSql(IPage<?> page, String sql) {
        // 获取自动优化的 countSql

        // 1. 自动优化 COUNT SQL为false【 默认：true 】
        if (!page.optimizeCountSql()) {
            return lowLevelCountSql(sql);
        }
        // 2. 自动优化 count sql 为true
        try {
            // 2.1 解析出select
            Select select = (Select) CCJSqlParserUtil.parse(sql);
            // 2.2 获取select语句中的select主体
            // ❗️❗️❗️
            // 比如 select * from table_name where name like '%p%' and age > 45 order by age
            // selectBody 就是 -> " table_name where name like '%p%' and age > 45 order by age "
            SelectBody selectBody = select.getSelectBody();
            // 分页增加union语法支持
            if (selectBody instanceof SetOperationList) {
                return lowLevelCountSql(sql);
            }
            PlainSelect plainSelect = (PlainSelect) select.getSelectBody();
            // 2.3 拿到Distinct\GroupByElement\OrderByElement
            Distinct distinct = plainSelect.getDistinct();
            GroupByElement groupBy = plainSelect.getGroupBy();
            List<OrderByElement> orderBy = plainSelect.getOrderByElements();

            if (CollectionUtils.isNotEmpty(orderBy)) {
                boolean canClean = true;
                if (groupBy != null) {
                    // 包含groupBy 不去除orderBy
                    canClean = false;
                }
                if (canClean) {
                    for (OrderByElement order : orderBy) {
                        // order by 里带参数,不去除order by
                        Expression expression = order.getExpression();
                        if (!(expression instanceof Column) && expression.toString().contains(StringPool.QUESTION_MARK)) {
                            canClean = false;
                            break;
                        }
                    }
                }
                if (canClean) {
                    plainSelect.setOrderByElements(null);
                }
            }
            //#95 Github, selectItems contains #{} ${}, which will be translated to ?, and it may be in a function: power(#{myInt},2)
            for (SelectItem item : plainSelect.getSelectItems()) {
                if (item.toString().contains(StringPool.QUESTION_MARK)) {
                    return lowLevelCountSql(select.toString());
                }
            }
            // 包含 distinct、groupBy不优化
            if (distinct != null || null != groupBy) {
                return lowLevelCountSql(select.toString());
            }
            // 包含 join 连表,进行判断是否移除 join 连表
            if (optimizeJoin && page.optimizeJoinOfCountSql()) {
                List<Join> joins = plainSelect.getJoins();
                if (CollectionUtils.isNotEmpty(joins)) {
                    boolean canRemoveJoin = true;
                    String whereS = Optional.ofNullable(plainSelect.getWhere()).map(Expression::toString).orElse(StringPool.EMPTY);
                    // 不区分大小写
                    whereS = whereS.toLowerCase();
                    for (Join join : joins) {
                        if (!join.isLeft()) {
                            canRemoveJoin = false;
                            break;
                        }
                        FromItem rightItem = join.getRightItem();
                        String str = "";
                        if (rightItem instanceof Table) {
                            Table table = (Table) rightItem;
                            str = Optional.ofNullable(table.getAlias()).map(Alias::getName).orElse(table.getName()) + StringPool.DOT;
                        } else if (rightItem instanceof SubSelect) {
                            SubSelect subSelect = (SubSelect) rightItem;
                            /* 如果 left join 是子查询，并且子查询里包含 ?(代表有入参) 或者 where 条件里包含使用 join 的表的字段作条件,就不移除 join */
                            if (subSelect.toString().contains(StringPool.QUESTION_MARK)) {
                                canRemoveJoin = false;
                                break;
                            }
                            str = subSelect.getAlias().getName() + StringPool.DOT;
                        }
                        // 不区分大小写
                        str = str.toLowerCase();
                        String onExpressionS = join.getOnExpression().toString();
                        /* 如果 join 里包含 ?(代表有入参) 或者 where 条件里包含使用 join 的表的字段作条件,就不移除 join */
                        if (onExpressionS.contains(StringPool.QUESTION_MARK) || whereS.contains(str)) {
                            canRemoveJoin = false;
                            break;
                        }
                    }
                    if (canRemoveJoin) {
                        plainSelect.setJoins(null);
                    }
                }
            }
            // 优化 SQL
            plainSelect.setSelectItems(COUNT_SELECT_ITEM);
            return select.toString();
        } catch (JSQLParserException e) {
            // 无法优化使用原 SQL
            logger.warn("optimize this sql to a count sql has exception, sql:\"" + sql + "\", exception:\n" + e.getCause());
        } catch (Exception e) {
            logger.warn("optimize this sql to a count sql has error, sql:\"" + sql + "\", exception:\n" + e);
        }
        return lowLevelCountSql(sql);
    }

    /**
     * 无法进行count优化时,降级使用此方法
     *
     * @param originalSql 原始sql
     * @return countSql
     */
    protected String lowLevelCountSql(String originalSql) {
        // ❗️❗️❗️ -> 大部分情况都是当前情况哦
        // 无法进行count优化时,降级使用此方法
        // 就是返回: String.format("SELECT COUNT(*) FROM (%s) TOTAL", originalSql);
        return SqlParserUtils.getOriginalCountSql(originalSql);
    }

    /**
     * 查询SQL拼接Order By
     *
     * @param originalSql 需要拼接的SQL
     * @return ignore
     */
    public String concatOrderBy(String originalSql, List<OrderItem> orderList) {
        // 分页查询SQL拼接Order By

        try {
            Select select = (Select) CCJSqlParserUtil.parse(originalSql);
            SelectBody selectBody = select.getSelectBody();
            if (selectBody instanceof PlainSelect) {
                PlainSelect plainSelect = (PlainSelect) selectBody;
                List<OrderByElement> orderByElements = plainSelect.getOrderByElements();
                List<OrderByElement> orderByElementsReturn = addOrderByElements(orderList, orderByElements);
                plainSelect.setOrderByElements(orderByElementsReturn);
                return select.toString();
            } else if (selectBody instanceof SetOperationList) {
                SetOperationList setOperationList = (SetOperationList) selectBody;
                List<OrderByElement> orderByElements = setOperationList.getOrderByElements();
                List<OrderByElement> orderByElementsReturn = addOrderByElements(orderList, orderByElements);
                setOperationList.setOrderByElements(orderByElementsReturn);
                return select.toString();
            } else if (selectBody instanceof WithItem) {
                // todo: don't known how to resole
                return originalSql;
            } else {
                return originalSql;
            }
        } catch (JSQLParserException e) {
            logger.warn("failed to concat orderBy from IPage, exception:\n" + e.getCause());
        } catch (Exception e) {
            logger.warn("failed to concat orderBy from IPage, exception:\n" + e);
        }
        return originalSql;
    }

    protected List<OrderByElement> addOrderByElements(List<OrderItem> orderList, List<OrderByElement> orderByElements) {
        List<OrderByElement> additionalOrderBy = orderList.stream()
            .filter(item -> StringUtils.isNotBlank(item.getColumn()))
            .map(item -> {
                OrderByElement element = new OrderByElement();
                element.setExpression(new Column(item.getColumn()));
                element.setAsc(item.isAsc());
                element.setAscDescPresent(true);
                return element;
            }).collect(Collectors.toList());
        if (CollectionUtils.isEmpty(orderByElements)) {
            return additionalOrderBy;
        }
        // github pull/3550 优化排序，比如：默认 order by id 前端传了name排序，设置为 order by name,id
        additionalOrderBy.addAll(orderByElements);
        return additionalOrderBy;
    }

    /**
     * count 查询之后,是否继续执行分页
     *
     * @param page 分页对象
     * @return 是否
     */
    protected boolean continuePage(IPage<?> page) {
        // count 查询之后,是否继续执行分页

        if (page.getTotal() <= 0) {
            // 返回false -> 表示不再继续执行,包括接下来的分页的 select sql 都不会在执行啦
            return false;
        }
        if (page.getCurrent() > page.getPages()) {
            if (overflow) {
                //溢出总页数处理
                handlerOverflow(page);
            } else {
                // 超过最大范围，未设置溢出逻辑中断 list 执行 ->
                // 返回false -> 表示不再继续执行,包括接下来的分页的 select sql 都不会在执行啦
                return false;
            }
        }
        return true;
    }

    /**
     * 处理超出分页条数限制,默认归为限制数
     *
     * @param page IPage
     */
    protected void handlerLimit(IPage<?> page, Long limit) {
        // 当IPage.getSize()超过limit,将其设置为limit的值
        // 即处理超出分页条数限制maxLimit,默认归为限制数maxLimit

        final long size = page.getSize();
        if (limit != null && limit > 0 && (size > limit || size < 0)) {
            page.setSize(limit);
        }
    }

    /**
     * 处理页数溢出,默认设置为第一页
     *
     * @param page IPage
     */
    protected void handlerOverflow(IPage<?> page) {
        // 处理页数溢出,默认设置为第一页
        // ❗️❗️❗️ 建议开启
        page.setCurrent(1);
    }

    @Override
    public void setProperties(Properties properties) {
        // ❗️❗️❗️ -> 允许接受的属性值 -> overflow\dbType\dialect\maxLimit\optimizeJoin
        PropertyMapper.newInstance(properties)
            .whenNotBlank("overflow", Boolean::parseBoolean, this::setOverflow)
            .whenNotBlank("dbType", DbType::getDbType, this::setDbType)
            .whenNotBlank("dialect", ClassUtils::newInstance, this::setDialect)
            .whenNotBlank("maxLimit", Long::parseLong, this::setMaxLimit)
            .whenNotBlank("optimizeJoin", Boolean::parseBoolean, this::setOptimizeJoin);
    }
}
