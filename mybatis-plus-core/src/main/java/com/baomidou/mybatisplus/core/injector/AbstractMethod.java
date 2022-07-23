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
package com.baomidou.mybatisplus.core.injector;

import com.baomidou.mybatisplus.core.enums.SqlMethod;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.toolkit.*;
import com.baomidou.mybatisplus.core.toolkit.sql.SqlScriptUtils;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.executor.keygen.NoKeyGenerator;
import org.apache.ibatis.jdbc.SQL;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.mapping.StatementType;
import org.apache.ibatis.scripting.LanguageDriver;
import org.apache.ibatis.session.Configuration;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;

/**
 * 抽象的注入方法类
 *
 * @author hubin
 * @since 2018-04-06
 */
@SuppressWarnings("serial")
public abstract class AbstractMethod implements Constants {
    // 位于: com.baomidou.mybatisplus.core.injector = core下injector包

    // 继承关系:
    // 是com.baomidou.mybatisplus.core.injector.method下所有类的超类

    //

    protected static final Log logger = LogFactory.getLog(AbstractMethod.class);

    // 三大类: Configuration\LanguageDriver\MapperBuilderAssistant

    protected Configuration configuration;
    protected LanguageDriver languageDriver;
    protected MapperBuilderAssistant builderAssistant;

    // methodName: 自动当前类映射到BaseMapper的方法名
    protected final String methodName;

    /**
     * @see AbstractMethod#AbstractMethod(java.lang.String)
     * @since 3.5.0
     */
    @Deprecated
    public AbstractMethod() {
        methodName = null;
    }

    /**
     * @param methodName 方法名
     * @since 3.5.0
     */
    protected AbstractMethod(String methodName) {
        Assert.notNull(methodName, "方法名不能为空");
        this.methodName = methodName;
    }

    /**
     * 注入自定义方法
     */
    public void inject(MapperBuilderAssistant builderAssistant, Class<?> mapperClass, Class<?> modelClass, TableInfo tableInfo) {
        this.configuration = builderAssistant.getConfiguration();
        this.builderAssistant = builderAssistant;
        this.languageDriver = configuration.getDefaultScriptingLanguageInstance();
        /* 注入自定义方法:抽象方法 -> 也是核心方法哦 */
        injectMappedStatement(mapperClass, modelClass, tableInfo); // ❗️❗️❗️ 抽象的核心方法
    }

    /**
     * 是否已经存在MappedStatement
     *
     * @param mappedStatement MappedStatement
     * @return true or false
     */
    private boolean hasMappedStatement(String mappedStatement) {
        return configuration.hasStatement(mappedStatement, false);
    }

    /**
     * SQL 更新 set 语句
     *
     * @param table 表信息
     * @return sql set 片段
     */
    protected String sqlLogicSet(TableInfo table) {
        // 设置逻辑字段为逻辑删除值,
        // 一般返回: set deleted = 1 或者 set deleted IS NULL

        return "SET " + table.getLogicDeleteSql(false, false);
    }

    /**
     * SQL 更新 set 语句
     *
     * @param logic  是否逻辑删除注入器
     * @param ew     是否存在 UpdateWrapper 条件
     * @param table  表信息
     * @param alias  别名
     * @param prefix 前缀
     * @return sql
     */
    protected String sqlSet(boolean logic, boolean ew, TableInfo table, boolean judgeAliasNull, final String alias,
                            final String prefix) {
        //SQL 更新 set 语句
        //Params: logic – 是否逻辑删除注入器
        //  ew – 是否存在 UpdateWrapper 条件
        //  table – 表信息
        //  alias – 别名 -> "et"
        //  prefix – 前缀 -> "et."
        // 假设:
        //  logic为true,ew为true,judgeAliasNull为true,alias为"et",prefix为"et."
            //  <set>
            //      <if test = 'et != null'>
            //          <if test='et.property1 != null'> column1 = #{et.property1} </if>                             当updateStrategy为FieldStrategy.NOE_NULL时
            //          <if test='et.property2 != null and et.property2 != '' '> column2 = #{et.property2} </if>     当updateStrategy为FieldStrategy.NOE_EMPTY时
            //          <if test='et.property3 != null'> column3 = column3 + 1 </if>                                 当@TableField.udpate() = "%s+1" 时
            //          <if test='et.property4 != null'> column4 = now() </if>                                       当@TableField.udpate() = "now()" 时
            //      </if>
            //      <if test=" ew != null and ew.sqlSet != null">
            //          ew.sqlSet                                                                                    这个sql片段取决于ew的boolean值
            //      </if>
            //  </set>



        // 1. 拿到所有需要set的字段的sql 脚本
            // <if test='et.property1 != null'> column1 = #{et.property1} </if>                             当updateStrategy为FieldStrategy.NOE_NULL时
            // <if test='et.property2 != null and et.property2 != '' '> column2 = #{et.property2} </if>     当updateStrategy为FieldStrategy.NOE_EMPTY时
            // <if test='et.property3 != null'> column3 = column3 + 1 </if>                                 当@TableField.udpate() = "%s+1" 时
            // <if test='et.property4 != null'> column4 = now() </if>                                       当@TableField.udpate() = "now()" 时
        String sqlScript = table.getAllSqlSet(logic, prefix);
        // 2. 是否判断别名"et"是否存在
            //  <if test = 'et != null'> sqlScript </if>
        if (judgeAliasNull) {
            sqlScript = SqlScriptUtils.convertIf(sqlScript, String.format("%s != null", alias), true);
        }
        // 3. 是否存在UpdateWrapper条件
        if (ew) {
            sqlScript += NEWLINE;
            // 3.1 追加:
            // <if test=" ew != null and ew.sqlSet != null"> ew.sqlSet </test> [❗️❗️❗️ ew.sqlSet]
            sqlScript += convertIfEwParam(U_WRAPPER_SQL_SET, false);
        }
        // 4. 添加<set>标签
        // <set> sqlScript <set>
        sqlScript = SqlScriptUtils.convertSet(sqlScript);
        return sqlScript;
    }

    /**
     * SQL 注释
     *
     * @return sql
     */
    protected String sqlComment() {
        // 构建: SQL 注释
        // Q_WRAPPER_SQL_COMMENT = "ew.sqlComment"
        // 最终返回:
            // <if test=" ew != null and ew.sqlComment != null">
            //      ${ew.sqlComment}
            // </if>
        return convertIfEwParam(Q_WRAPPER_SQL_COMMENT, true);
    }

    /**
     * SQL 注释
     *
     * @return sql
     */
    protected String sqlFirst() {
        // SQL 注释
        // Q_WRAPPER_SQL_FIRST = "ew.sqlFirst"
        // 最终返回:
            // <if test=" ew != null and ew.sqlFirst != null">
            //      ${ew.sqlFirst}
            // </test>
        return convertIfEwParam(Q_WRAPPER_SQL_FIRST, true);
    }

    protected String convertIfEwParam(final String param, final boolean newLine) {
        // 返回 <if test=" ew != null and param != null"> param </test>
        // 比如:
            // <if test=" ew != null and ew.sqlSet != null"> ew.sqlSet </test>      param = "ew.sqlSet"
            // <if test=" ew != null and ew.sqlComment != null"> ew.sqlComment </test>      param = "ew.sqlComment"
        return SqlScriptUtils.convertIf(SqlScriptUtils.unSafeParam(param),
            String.format("%s != null and %s != null", WRAPPER, param), newLine);
    }

    /**
     * SQL 查询所有表字段
     *
     * @param table        表信息
     * @param queryWrapper 是否为使用 queryWrapper 查询
     * @return sql 脚本
     */
    protected String sqlSelectColumns(TableInfo table, boolean queryWrapper) {
        // 最终结果: 常见的情况下->
        // a:  queryWrapper = true ->
            // <choose>
            // <when test=" ew != null and ew.sqlSelect != null"> ${ew.sqlSelect} </when>  [❗️❗️❗️ 也就是说如果QueryWrapper如果指定返回的列名,那么 ew.sqlSelect 就不为空,以此为对象]
            // <otherwise> idColumn as idProperty,column1 as property1, column2, column3 as property3 </otherwise> [❗️❗️❗️ 如果QueryWrapper如果没有指定返回的列名,那就以PO实体类确定]
            // </choose>

        // b: queryWrapper = false ->
            // idColumn as idProperty,column1 as property1, column2, column3 as property3


        // 1. 假设存在用户自定义的 resultMap 映射返回
        String selectColumns = ASTERISK;
        if (table.getResultMap() == null || table.isAutoInitResultMap()) {
            // 1.1 未设置 resultMap 或者 resultMap 是自动构建的,视为属于mp的规则范围内
            // 结果一般可以认为是: 形如 idColumn as idProperty,column1 as property1, column2, column3 as property3
            selectColumns = table.getAllSqlSelect();
        }
        // 2. 不为queryWrapper查询时,直接返回 selectColumns
        if (!queryWrapper) {
            return selectColumns;
        }
        // 3. convertChooseEwSelect(selectColumns)
        // <choose>
        // <when test="whenTest"> whenSqlScript </when>
        // <otherwise> otherwise </otherwise>
        // </choose>
        return convertChooseEwSelect(selectColumns);
    }

    /**
     * SQL 查询记录行数
     *
     * @return count sql 脚本
     */
    protected String sqlCount() {
        // <choose>
        // <when test=" ew != null and ew.sqlSelect != null"> ${ew.sqlSelect} </when>
        // <otherwise> * </otherwise>
        // </choose>
        return convertChooseEwSelect(ASTERISK);
    }

    /**
     * SQL 设置selectObj sql select
     *
     * @param table 表信息
     */
    protected String sqlSelectObjsColumns(TableInfo table) {
        return convertChooseEwSelect(table.getAllSqlSelect());
    }

    protected String convertChooseEwSelect(final String otherwise) {
        // <choose>
        // <when test=" ew != null and ew.sqlSelect != null"> ${ew.sqlSelect} </when>
        // <otherwise> otherwise </otherwise>
        // </choose>
        return SqlScriptUtils.convertChoose(String.format("%s != null and %s != null", WRAPPER, Q_WRAPPER_SQL_SELECT),
            SqlScriptUtils.unSafeParam(Q_WRAPPER_SQL_SELECT), otherwise);
    }

    /**
     * SQL map 查询条件
     */
    protected String sqlWhereByMap(TableInfo table) {
        // 1. 逻辑删除
            // <where>
            //  <if test=" cm != null and !cm.isEmpty">
            //      <foreach collection="cm" index="k" item="v" separator="AND">
            //          <choose>
            //              <when test="v == null"> ${k} IS NULL </when>        -> k和v来自于<foreach>标签的collection为"cm"的map时,index="k",item="v"
            //              <otherwise> ${k} = #{v} </otherwise>
            //          </choose>
            //      <foreach>
            //  </if>
            // and deleted = 0
            // <where>
        if (table.isWithLogicDelete()) {
            // 1.1
                // <choose>
                //      <when test="v == null"> ${k} IS NULL </when>        -> k和v来自于<foreach>标签的collection为"cm"的map时,index="k",item="v"
                //      <otherwise> ${k} = #{v} </otherwise>
                // </choose>
            String sqlScript = SqlScriptUtils.convertChoose("v == null", " ${k} IS NULL ",
                " ${k} = #{v} ");
            // 1.2
                // <foreach collection="cm" index="k" item="v" separator="AND">
                //      sqlScript
                // <foreach>
            sqlScript = SqlScriptUtils.convertForeach(sqlScript, COLUMN_MAP, "k", "v", "AND");
            // 1.3
                // <if test=" cm != null and !cm.isEmpty">
                //      sqlScript
                // </if>
            sqlScript = SqlScriptUtils.convertIf(sqlScript, String.format("%s != null and !%s.isEmpty", COLUMN_MAP, COLUMN_MAP), true);
            // 1.4 逻辑删除必备的
                //  and deleted = 0
            sqlScript += (NEWLINE + table.getLogicDeleteSql(true, true));
            // 1.4 加入<where>标签
                // <where> SQLScript <where>
            sqlScript = SqlScriptUtils.convertWhere(sqlScript);
            return sqlScript;
        }
        // 2. 物理删除
            // <where>
            //  <if test=" cm != null and !cm.isEmpty">
            //      <foreach collection="cm" index="k" item="v" separator="AND">
            //          <choose>
            //              <when test="v == null"> ${k} IS NULL </when>        -> k和v来自于<foreach>标签的collection为"cm"的map时,index="k",item="v"
            //              <otherwise> ${k} = #{v} </otherwise>
            //          </choose>
            //      <foreach>
            //  </if>
            // <where>
        else {
            String sqlScript = SqlScriptUtils.convertChoose("v == null", " ${k} IS NULL ",
                " ${k} = #{v} ");
            sqlScript = SqlScriptUtils.convertForeach(sqlScript, COLUMN_MAP, "k", "v", "AND");
            sqlScript = SqlScriptUtils.convertWhere(sqlScript);
            sqlScript = SqlScriptUtils.convertIf(sqlScript, String.format("%s != null and !%s", COLUMN_MAP,
                COLUMN_MAP_IS_EMPTY), true);
            return sqlScript;
        }
    }

    /**
     * EntityWrapper方式获取select where
     *
     * @param newLine 是否提到下一行
     * @param table   表信息
     * @return String
     */
    protected String sqlWhereEntityWrapper(boolean newLine, TableInfo table) {
        // EntityWrapper方式获取select where
        // 以逻辑删除字段为例: 最终获取的sql脚本片段
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


        // 1. 实体类有逻辑删除字段
        if (table.isWithLogicDelete()) {
            // 2.1
            // 这里传递进入的前缀是WRAPPER_ENTITY_DOT = "ew.entity." ❗️❗️❗️
            // 2.2 拿到TableInfo中所有字段的 sql where 脚本片段 -> sqlScript ❗️❗️❗️ -> 比如: keyProperty为主键属性名\property1为某个字段的属性名\el1为property1对应的信息
                //  <if test=" #{ew.entity. + keyProperty} != null "> keyColumn = #{ew.entity. + keyProperty} </if>
                //  <if test=" #{ew.entity.property1} != null " > AND column = #{ew.entity. + e1l} </if>   // ❗️❗️❗ <if>标签的test是收@TableField.whereStrategy()控制确定成立条件,AND后的比较逻辑是受到@TableField.condition()控制的️
                //  <if test=" #{ew.entity.property2} != null and #{ew.entity.property} != ''" > AND column != #{ew.entity. + el2} </if> //  [比如: condition为SQLCondition.EQUALS,whereStrategy=FieldStrategy.NOT_EMPTY]
                //  <if test=" #{ew.entity.property3} != null " > AND column LIKE concat('%',#{ew.entity. + el3},'%'} </if>   //  [比如: condition为SQLCondition.LIKE,whereStrategy=FieldStrategy.NOT_NULL]
            String sqlScript = table.getAllSqlWhere(true, true, WRAPPER_ENTITY_DOT);
            // 2.3 使用完成一层<if>标签封装起来 -> 必须非null才有效 -> 因为上面是从 ew.entity.xx 获取实体类的xx属性 ❗️❗️❗️
            // <if test="ew.entity != null"> sqlScript <!if>
            sqlScript = SqlScriptUtils.convertIf(sqlScript, String.format("%s != null", WRAPPER_ENTITY), true);
            // 2.3.1 加上逻辑删除字段 [当然: table.getLogicDeleteSql(true, true) 有可能返回空字符串的哦 ❗️❗️❗️]
            // 比如让 sqlScript 加上 AND deleted = 0 这种sql片段 -> [❗️❗️❗️-> 也就是说如果ew.entity为空的情况下,最终查询查询的就是 select table_name where deleted = 0 这种语句]
            sqlScript += (NEWLINE + table.getLogicDeleteSql(true, true) + NEWLINE);
            // ❗️❗️❗️ -> 可以添加 sqlSegment [用户或框架自定义sql片段 -> 只要不为空,就可以设置到where and 最后操作上面去]
            // 2.4 添加 <if test= "ew.sqlSegment != null and ew.sqlSegment != '' and "ew.nonEmptyOfNormal"" > AND ${ew.sqlSegment} </if>
            String normalSqlScript = SqlScriptUtils.convertIf(String.format("AND ${%s}", WRAPPER_SQLSEGMENT),
                String.format("%s != null and %s != '' and %s", WRAPPER_SQLSEGMENT, WRAPPER_SQLSEGMENT,
                    WRAPPER_NONEMPTYOFNORMAL), true);
            // 2.5 划行 -> <if test= "ew.sqlSegment != null and ew.sqlSegment != '' and "ew.emptyOfNormal"" > ${ew.sqlSegment} </if>
            normalSqlScript += NEWLINE;
            normalSqlScript += SqlScriptUtils.convertIf(String.format(" ${%s}", WRAPPER_SQLSEGMENT),
                String.format("%s != null and %s != '' and %s", WRAPPER_SQLSEGMENT, WRAPPER_SQLSEGMENT,
                    WRAPPER_EMPTYOFNORMAL), true);
            // 2.6  sqlScript += normalSqlScript
            sqlScript += normalSqlScript;
            // 2.7 sqlScript 包装在<choose>标签中 -> 上面2.3步骤,处理了 ew.entity 不为空,但没有处理 ew 为空的情况
            // <choose>
            //  <when test=" ew != null"> sqlScript </when>
            //  <otherwise> deleted = 0  </otherwise>
            // </choose>
            // [❗️❗️❗️-> 也就是说如果ew为空的情况下,最终查询查询的就是 select table_name where deleted = 0 这种语句]
            sqlScript = SqlScriptUtils.convertChoose(String.format("%s != null", WRAPPER), sqlScript,
                table.getLogicDeleteSql(false, true));
            // 2.8 将最终的结果保存到: <where> </where> 标签中 -> 它能够去除我们使用一直使用的前缀AND关键字哦
            sqlScript = SqlScriptUtils.convertWhere(sqlScript);
            return newLine ? NEWLINE + sqlScript : sqlScript;
        }
        // 2. 实体类没有逻辑删除字段 -> 同上面差不多
        else {
            String sqlScript = table.getAllSqlWhere(false, true, WRAPPER_ENTITY_DOT);
            sqlScript = SqlScriptUtils.convertIf(sqlScript, String.format("%s != null", WRAPPER_ENTITY), true);
            sqlScript += NEWLINE;
            sqlScript += SqlScriptUtils.convertIf(String.format(SqlScriptUtils.convertIf(" AND", String.format("%s and %s", WRAPPER_NONEMPTYOFENTITY, WRAPPER_NONEMPTYOFNORMAL), false) + " ${%s}", WRAPPER_SQLSEGMENT),
                String.format("%s != null and %s != '' and %s", WRAPPER_SQLSEGMENT, WRAPPER_SQLSEGMENT,
                    WRAPPER_NONEMPTYOFWHERE), true);
            sqlScript = SqlScriptUtils.convertWhere(sqlScript) + NEWLINE;
            sqlScript += SqlScriptUtils.convertIf(String.format(" ${%s}", WRAPPER_SQLSEGMENT),
                String.format("%s != null and %s != '' and %s", WRAPPER_SQLSEGMENT, WRAPPER_SQLSEGMENT,
                    WRAPPER_EMPTYOFWHERE), true);
            sqlScript = SqlScriptUtils.convertIf(sqlScript, String.format("%s != null", WRAPPER), true);
            return newLine ? NEWLINE + sqlScript : sqlScript;
        }
    }

    protected String sqlOrderBy(TableInfo tableInfo) {
        // 构建 order by 的 sql 脚本片段
        // 一般最终结果都是:
            // <if test=" ew == null or ew.useAnnotationOrderBy">
            //      ORDER BY column1 asc, column2 desc
            // </if>

        // 1.  获取标注有@OrderBy的字段的TableFieldInfo
        List<TableFieldInfo> orderByFields = tableInfo.getOrderByFields();
        // 2.  没有@OrderBy标注的字段,直接返回""
        if (CollectionUtils.isEmpty(orderByFields)) {
            return StringPool.EMPTY;
        }
        // 3. 进行一个排序 -> 取决于字段上的 @OrderBy.sort()
        orderByFields.sort(Comparator.comparingInt(TableFieldInfo::getOrderBySort));
        // 4. 开始构建 sql 脚本
        StringBuilder sql = new StringBuilder();
        // 4.1 追加 ORDER BY 关键字
        sql.append(NEWLINE).append(" ORDER BY ");
        // 4.2 追加 列名 排序规则asc/desc
        sql.append(orderByFields.stream().map(tfi -> String.format("%s %s", tfi.getColumn(),
            tfi.getOrderByType())).collect(joining(",")));
        /* 当wrapper中传递了orderBy属性，@orderBy注解失效 */
        // 4.3 <if test=" ew == null or ew.useAnnotationOrderBy"> sql </if>
        return SqlScriptUtils.convertIf(sql.toString(), String.format("%s == null or %s", WRAPPER,
            WRAPPER_EXPRESSION_ORDER), true);
    }

    /**
     * 过滤 TableFieldInfo 集合, join 成字符串
     */
    protected String filterTableFieldInfo(List<TableFieldInfo> fieldList, Predicate<TableFieldInfo> predicate,
                                          Function<TableFieldInfo, String> function, String joiningVal) {
        Stream<TableFieldInfo> infoStream = fieldList.stream();
        if (predicate != null) {
            return infoStream.filter(predicate).map(function).collect(joining(joiningVal));
        }
        return infoStream.map(function).collect(joining(joiningVal));
    }

    /**
     * 获取乐观锁相关
     *
     * @param tableInfo 表信息
     * @return String
     */
    protected String optlockVersion(TableInfo tableInfo) {
        // 返回结果:
        //  a: 有乐观锁
            // <if test=" et != null and et[property]  != null and et[property] != ''">     property为标记有@Versiond的字段名
            //      AND column_name = #{MP_OPTLOCK_VERSION_ORIGINAL}                        MP_OPTLOCK_VERSION_ORIGINAL为乐观锁字段哦
            // </if>
        //  b: 无乐观锁
            // ""

        // 1. 实体类是否有字段使用@Version标注乐观锁
        if (tableInfo.isWithVersion()) {
            return tableInfo.getVersionFieldInfo().getVersionOli(ENTITY, ENTITY_DOT);
        }
        // 2. 没有乐观锁 -> 返回 ""
        return EMPTY;
    }

    /**
     * 查询
     */
    protected MappedStatement addSelectMappedStatementForTable(Class<?> mapperClass, String id, SqlSource sqlSource,
                                                               TableInfo table) {
        // 添加查询的MappedStatement
        String resultMap = table.getResultMap();
        if (null != resultMap) {
            /* 返回 resultMap 映射结果集 */
            return addMappedStatement(mapperClass, id, sqlSource, SqlCommandType.SELECT, null,
                resultMap, null, NoKeyGenerator.INSTANCE, null, null);
        } else {
            /* 普通查询 */
            return addSelectMappedStatementForOther(mapperClass, id, sqlSource, table.getEntityType());
        }
    }

    /**
     * 查询
     * @since 3.5.0
     */
    protected MappedStatement addSelectMappedStatementForTable(Class<?> mapperClass, SqlSource sqlSource, TableInfo table) {
        return addSelectMappedStatementForTable(mapperClass, this.methodName, sqlSource, table);
    }

    /**
     * 查询
     */
    protected MappedStatement addSelectMappedStatementForOther(Class<?> mapperClass, String id, SqlSource sqlSource,
                                                               Class<?> resultType) {
        return addMappedStatement(mapperClass, id, sqlSource, SqlCommandType.SELECT, null,
            null, resultType, NoKeyGenerator.INSTANCE, null, null);
    }

    /**
     * 查询
     *
     * @since 3.5.0
     */
    protected MappedStatement addSelectMappedStatementForOther(Class<?> mapperClass, SqlSource sqlSource, Class<?> resultType) {
        return addSelectMappedStatementForOther(mapperClass, this.methodName, sqlSource, resultType);
    }

    /**
     * 插入
     */
    protected MappedStatement addInsertMappedStatement(Class<?> mapperClass, Class<?> parameterType, String id,
                                                       SqlSource sqlSource, KeyGenerator keyGenerator,
                                                       String keyProperty, String keyColumn) {
        // 插入一个 MappedStatement 并返回
        // mapperClass/id/sqlSource/SqlCommandType.INSERT/parameterType/null/Integer.class/keyGenerator/keyProperty/keyColumn
        return addMappedStatement(mapperClass, id, sqlSource, SqlCommandType.INSERT, parameterType, null,
            Integer.class, keyGenerator, keyProperty, keyColumn);
    }

    /**
     * 插入
     * @since 3.5.0
     */
    protected MappedStatement addInsertMappedStatement(Class<?> mapperClass, Class<?> parameterType,
                                                       SqlSource sqlSource, KeyGenerator keyGenerator,
                                                       String keyProperty, String keyColumn) {
        return addInsertMappedStatement(mapperClass, parameterType, this.methodName, sqlSource, keyGenerator, keyProperty, keyColumn);
    }


    /**
     * 删除
     */
    protected MappedStatement addDeleteMappedStatement(Class<?> mapperClass, String id, SqlSource sqlSource) {
        return addMappedStatement(mapperClass, id, sqlSource, SqlCommandType.DELETE, null,
            null, Integer.class, NoKeyGenerator.INSTANCE, null, null);
    }

    /**
     * @since 3.5.0
     */
    protected MappedStatement addDeleteMappedStatement(Class<?> mapperClass, SqlSource sqlSource) {
        return addDeleteMappedStatement(mapperClass, this.methodName, sqlSource);
    }

    /**
     * 更新
     */
    protected MappedStatement addUpdateMappedStatement(Class<?> mapperClass, Class<?> parameterType, String id,
                                                       SqlSource sqlSource) {
        // 添加更新的MappedStatement
        return addMappedStatement(mapperClass, id, sqlSource, SqlCommandType.UPDATE, parameterType, null,
            Integer.class, NoKeyGenerator.INSTANCE, null, null);
    }

    /**
     * 更新
     *
     * @since 3.5.0
     */
    protected MappedStatement addUpdateMappedStatement(Class<?> mapperClass, Class<?> parameterType,
                                                       SqlSource sqlSource) {
        return addUpdateMappedStatement(mapperClass, parameterType, this.methodName, sqlSource);
    }

    /**
     * 添加 MappedStatement 到 Mybatis 容器
     */
    protected MappedStatement addMappedStatement(Class<?> mapperClass, String id, SqlSource sqlSource,
                                                 SqlCommandType sqlCommandType, Class<?> parameterType,
                                                 String resultMap, Class<?> resultType, KeyGenerator keyGenerator,
                                                 String keyProperty, String keyColumn) {
        // 1. statementName = mapperClass.getName() + DOT + id
        // id 一般就是MP自动注入的方法: SqlMethod.method() 属性
        String statementName = mapperClass.getName() + DOT + id;
        // 2. 检查是否已经存在 为mapperClass自动注入的CRUD方法 同名statementName
        if (hasMappedStatement(statementName)) {
            logger.warn(LEFT_SQ_BRACKET + statementName + "] Has been loaded by XML or SqlProvider or Mybatis's Annotation, so ignoring this injection for [" + getClass() + RIGHT_SQ_BRACKET);
            return null;
        }
        // 3. 处理到 builderAssistant.addMappedStatement()中去
        boolean isSelect = sqlCommandType == SqlCommandType.SELECT;
        return builderAssistant.addMappedStatement(id, sqlSource, StatementType.PREPARED, sqlCommandType,
            null, null, null, parameterType, resultMap, resultType,
            null, !isSelect, isSelect, false, keyGenerator, keyProperty, keyColumn,
            configuration.getDatabaseId(), languageDriver, null);
    }

    /**
     * @since 3.5.0
     */
    protected MappedStatement addMappedStatement(Class<?> mapperClass, SqlSource sqlSource,
                                                 SqlCommandType sqlCommandType, Class<?> parameterType,
                                                 String resultMap, Class<?> resultType, KeyGenerator keyGenerator,
                                                 String keyProperty, String keyColumn) {
        return addMappedStatement(mapperClass, this.methodName, sqlSource, sqlCommandType, parameterType, resultMap, resultType, keyGenerator, keyProperty, keyColumn);
    }

    /**
     * 注入自定义 MappedStatement
     *
     * @param mapperClass mapper 接口
     * @param modelClass  mapper 泛型
     * @param tableInfo   数据库表反射信息
     * @return MappedStatement
     */
    public abstract MappedStatement injectMappedStatement(Class<?> mapperClass, Class<?> modelClass, TableInfo tableInfo);

    /**
     * 获取自定义方法名，未设置采用默认方法名
     * https://gitee.com/baomidou/mybatis-plus/pulls/88
     *
     * @return method
     * @author 义陆无忧
     * @see AbstractMethod#AbstractMethod(java.lang.String)
     * @deprecated 3.5.0
     */
    @Deprecated
    public String getMethod(SqlMethod sqlMethod) {
        return StringUtils.isBlank(methodName) ? sqlMethod.getMethod() : this.methodName;
    }

}
