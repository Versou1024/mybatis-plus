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

import com.baomidou.mybatisplus.core.conditions.segments.MergeSegments;
import com.baomidou.mybatisplus.core.conditions.segments.NormalSegmentList;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.*;

import java.util.Objects;

/**
 * 条件构造抽象类
 *
 * @author hubin
 * @since 2018-05-25
 */
@SuppressWarnings("all")
public abstract class Wrapper<T> implements ISqlSegment {
    // 条件构造抽象类

    // 泛型T:表示实体类

    // 两个实现类:
    //      AbstractWrapper:
    //      AbstractChainWrapper:


    // 拿到 entity\sqlSelect\sqlSet\sqlComment\sqlFirst
    // 对应的就是 #{ew.entity} #{ew.sqlSelect} #{sqlSet} #{sqlFirst}

    public abstract T getEntity();

    public String getSqlSelect() {
        return null;
    }

    public String getSqlSet() {
        return null;
    }

    public String getSqlComment() {
        return null;
    }

    public String getSqlFirst() {
        return null;
    }

    /**
     * 获取 MergeSegments
     */
    public abstract MergeSegments getExpression();

    /**
     * 获取自定义SQL 简化自定义XML复杂情况
     * <p>
     * 使用方法: `select xxx from table` + ${ew.customSqlSegment}
     * <p>
     * 注意事项:
     * 1. 逻辑删除需要自己拼接条件 (之前自定义也同样)
     * 2. 不支持wrapper中附带实体的情况 (wrapper自带实体会更麻烦)
     * 3. 用法 ${ew.customSqlSegment} (不需要where标签包裹,切记!)
     * 4. ew是wrapper定义别名,不能使用其他的替换
     */
    public String getCustomSqlSegment() {
        MergeSegments expression = getExpression();
        if (Objects.nonNull(expression)) {
            NormalSegmentList normal = expression.getNormal();
            String sqlSegment = getSqlSegment();
            if (StringUtils.isNotBlank(sqlSegment)) {
                if (normal.isEmpty()) {
                    return sqlSegment;
                } else {
                    return Constants.WHERE + StringPool.SPACE + sqlSegment;
                }
            }
        }
        return StringPool.EMPTY;
    }

    /**
     * 查询条件为空(包含entity)
     */
    public boolean isEmptyOfWhere() {
        // isEmptyOfEntity() = 空的entity对象 [即entity等于null,或者entity都是空属性,或者有属性但不满足whereStrategy]
        // 并且
        // isEmptyOfNormal() = 表示expression.NormalSqlSegment的集合为空的

        // 两者综合起来就是:  -> 没有where条件
        return isEmptyOfNormal() && isEmptyOfEntity();
    }

    /**
     * 查询条件不为空(包含entity)
     */
    public boolean nonEmptyOfWhere() {
        return !isEmptyOfWhere();
    }

    /**
     * 查询条件为空(不包含entity)
     */
    public boolean isEmptyOfNormal() {
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
        // 可以发现: nonEmptyOfNormal() 和 isEmptyOfNormal() 两个相反的值哦

        // 要求: expression.NormalSqlSegment 非空集合
        return CollectionUtils.isEmpty(getExpression().getNormal());
    }

    /**
     * 查询条件为空(不包含entity)
     */
    public boolean nonEmptyOfNormal() {
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

        return !isEmptyOfNormal();
    }

    /**
     * 深层实体判断属性
     *
     * @return true 不为空
     */
    public boolean nonEmptyOfEntity() {
        // 返回true: 表示实体类不为空或者不是空属性的实体类

        // 1. 拿到实体对象
        T entity = getEntity();
        // 2. 实体对象为空
        if (entity == null) {
            return false;
        }
        // 3. 实体类对应的TableInfo
        TableInfo tableInfo = TableInfoHelper.getTableInfo(entity.getClass());
        // 3.1 TableInfo一般不为空,除非不是实体类
        if (tableInfo == null) {
            return false;
        }
        // 3.2 实体类中只要有字段匹配不满足它的whereStrategy -> 也就是说这个字段可以作为where关键字筛选逻辑时返回true,就表示entity不是空属性对象
        // 接着直接返回true
        if (tableInfo.getFieldList().stream().anyMatch(e -> fieldStrategyMatch(tableInfo, entity, e))) {
            return true;
        }
        // 3.3 检查实体类是否有主键字段,如果有且主键属性的值非null,就表示当前对象非空属性的实体对象哦
        return StringUtils.isNotBlank(tableInfo.getKeyProperty()) ? Objects.nonNull(tableInfo.getPropertyValue(entity, tableInfo.getKeyProperty())) : false;
    }

    /**
     * 根据实体FieldStrategy属性来决定判断逻辑
     */
    private boolean fieldStrategyMatch(TableInfo tableInfo, T entity, TableFieldInfo e) {
        // 根据实体类的whereStrategy即FieldStrategy属性来决定判断逻辑
        switch (e.getWhereStrategy()) {
            case NOT_NULL:
                // 0. 该字段为null,表示该字段不参与where条件的筛选
                // 当该字段非null时,返回true表示该字段是有值且可以被引用,表示entity实体对象非空属性的对象
                return Objects.nonNull(tableInfo.getPropertyValue(entity, e.getProperty()));
            case IGNORED:
                // 1. 当前字段直接忽略,取决于与全局配置
                // 返回true.表示该字段是有实体值的,就返回ture,表示entity实体对象非空属性的对象
                return true;
            case NOT_EMPTY:
                // 2. 该字段为null或者为'',表示该字段不参与where条件的筛选
                // 如果该字段不为null或者''.表示该字段是有实体值的,就返回ture,表示entity实体对象非空属性的对象
                return StringUtils.checkValNotNull(tableInfo.getPropertyValue(entity, e.getProperty()));
            case NEVER:
                // 3. 该字段不参与实体类的where条件筛选 -> 返回false,表示还需要继续遍历后面的字段
                return false;
            default:
                // 4. 其余情况 -> 只要属性不是null就返回true
                return Objects.nonNull(tableInfo.getPropertyValue(entity, e.getProperty()));
        }
    }

    /**
     * 深层实体判断属性
     *
     * @return true 为空
     */
    public boolean isEmptyOfEntity() {
        return !nonEmptyOfEntity();
    }

    /**
     * 获取格式化后的执行sql
     *
     * @return sql
     * @since 3.3.1
     */
    public String getTargetSql() {
        return getSqlSegment().replaceAll("#\\{.+?}", "?");
    }

    /**
     * 条件清空
     *
     * @since 3.3.1
     */
    abstract public void clear();
}
