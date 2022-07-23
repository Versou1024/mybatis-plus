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
package com.baomidou.mybatisplus.extension.activerecord;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.enums.SqlMethod;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.*;
import com.baomidou.mybatisplus.extension.toolkit.SqlHelper;
import com.baomidou.mybatisplus.extension.toolkit.SqlRunner;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.session.SqlSession;
import org.mybatis.spring.SqlSessionUtils;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * ActiveRecord 模式 CRUD
 * <p>
 * 必须存在对应的原始mapper并继承baseMapper并且可以使用的前提下
 * 才能使用此 AR 模式 !!!
 * </p>
 *
 * @param <T>
 * @author hubin
 * @since 2016-11-06
 */
public abstract class Model<T extends Model<?>> implements Serializable {
    // 位于: extension扩展模块下的activerecord活动记录包下

    // Model是mybatisPlus自带的实体父类。
    // 直接定义实体类的时候实现Model类，该类的作用是能通过实体类直接进行crud操作，而不需要进行调用Mapper
    // 前提是“必须存在对应的原始mapper并继承baseMapper并且可以使用的前提下”。也就是说实际上行还是调用的mapper接口的方法。

    // 举例:
        // public class User extends Model<User> {
        //     private Long id;
        //     private String name;
        //     private Integer age;
        //     private String email;
        // }
    //必须写入mapper
        //public interface UserMapper extends BaseMapper<User> {...}
    // 调用:
    // user.insert() 等等

    // 定义:
    // Model类自身实现
    // insert()
    // insertOrUpdate()
    // deleteById(Serializable id) deleteById()
    // delete(Wrapper queryWrapper)
    // updateById() update(Wrapper updateWrapper)
    // selectAll()
    // selectList(Wrapper queryWrapper)
    // selectOne(Wrapper queryWrapper)
    // selectPage(E page, Wrapper queryWrapper)
    // selectCount(Wrapper queryWrapper)

    private static final long serialVersionUID = 1L;

    // 由实体类 extends 该对象
    private final transient Class<?> entityClass = this.getClass();

    /**
     * 插入（字段选择插入）
     */
    public boolean insert() {
        // 实际调用: SqlMethod.INSERT_ONE 方法
        // -> 拿到sqlSession
        // -> 拿到 SQLMethod.INSERT_ONE 在当前实体类下对应的 MappedStatement 的 id
        // -> 调用 SqlSession#insert(..) 插入
        // -> 使用SqlHelper.retBool(..)处理返回值
        // -> 关闭sqlSession
        SqlSession sqlSession = sqlSession();
        try {
            return SqlHelper.retBool(sqlSession.insert(sqlStatement(SqlMethod.INSERT_ONE), this));
        } finally {
            closeSqlSession(sqlSession);
        }
    }

    /**
     * 插入 OR 更新
     */
    public boolean insertOrUpdate() {
        // 简单 -> 检查主键值是否存在即可
        // pkValue(): 返回主键值
        return StringUtils.checkValNull(pkVal()) || Objects.isNull(selectById(pkVal())) ? insert() : updateById();
    }

    /**
     * 根据 ID 删除
     *
     * @param id 主键ID
     */
    public boolean deleteById(Serializable id) {
        // 实际调用: SqlMethod.DELETE_BY_ID
        SqlSession sqlSession = sqlSession();
        try {
            return SqlHelper.retBool(sqlSession.delete(sqlStatement(SqlMethod.DELETE_BY_ID), id));
        } finally {
            closeSqlSession(sqlSession);
        }
    }

    /**
     * 根据主键删除
     */
    public boolean deleteById() {
        // 不同于: deleteById(Serializable id)
        // 当前方法没有形参 -> 旨在通过实体类本身的id值去删除
        Assert.isFalse(StringUtils.checkValNull(pkVal()), "deleteById primaryKey is null.");
        return deleteById(pkVal());
    }

    /**
     * 删除记录
     *
     * @param queryWrapper 实体对象封装操作类（可以为 null）
     */
    public boolean delete(Wrapper<T> queryWrapper) {
        // 实际调用: SQLMethod.DELETE
        // ❗️❗️❗️
        // 帮忙转为Map结构:  map.put(Constants.WRAPPER, queryWrapper);
        // 因为SqlSession执行时的参数实际上大多数时候都会被转换为ParamMap类型的然后去执行的
        // 原因 -> 见Mybatis中的 MapperMethod#execute()
        // -> 等价于执行 method.convertArgsToSqlCommandParam(args) -> 即将args封装为相应的ParamMap结构哦
        Map<String, Object> map = CollectionUtils.newHashMapWithExpectedSize(1);
        map.put(Constants.WRAPPER, queryWrapper);
        SqlSession sqlSession = sqlSession();
        try {
            return SqlHelper.retBool(sqlSession.delete(sqlStatement(SqlMethod.DELETE), map));
        } finally {
            closeSqlSession(sqlSession);
        }
    }

    /**
     * 更新（字段选择更新）
     */
    public boolean updateById() {
        Assert.isFalse(StringUtils.checkValNull(pkVal()), "updateById primaryKey is null.");
        // updateById
        Map<String, Object> map = CollectionUtils.newHashMapWithExpectedSize(1);
        map.put(Constants.ENTITY, this);
        SqlSession sqlSession = sqlSession();
        try {
            return SqlHelper.retBool(sqlSession.update(sqlStatement(SqlMethod.UPDATE_BY_ID), map));
        } finally {
            closeSqlSession(sqlSession);
        }
    }

    /**
     * 执行 SQL 更新
     *
     * @param updateWrapper 实体对象封装操作类（可以为 null,里面的 entity 用于生成 where 语句）
     */
    public boolean update(Wrapper<T> updateWrapper) {
        Map<String, Object> map = CollectionUtils.newHashMapWithExpectedSize(2);
        map.put(Constants.ENTITY, this);
        map.put(Constants.WRAPPER, updateWrapper);
        // update
        SqlSession sqlSession = sqlSession();
        try {
            return SqlHelper.retBool(sqlSession.update(sqlStatement(SqlMethod.UPDATE), map));
        } finally {
            closeSqlSession(sqlSession);
        }
    }

    /**
     * 查询所有
     */
    public List<T> selectAll() {
        SqlSession sqlSession = sqlSession();
        try {
            return sqlSession.selectList(sqlStatement(SqlMethod.SELECT_LIST));
        } finally {
            closeSqlSession(sqlSession);
        }
    }

    /**
     * 根据 ID 查询
     *
     * @param id 主键ID
     */
    public T selectById(Serializable id) {
        SqlSession sqlSession = sqlSession();
        try {
            return sqlSession.selectOne(sqlStatement(SqlMethod.SELECT_BY_ID), id);
        } finally {
            closeSqlSession(sqlSession);
        }
    }

    /**
     * 根据主键查询
     */
    public T selectById() {
        Assert.isFalse(StringUtils.checkValNull(pkVal()), "selectById primaryKey is null.");
        return selectById(pkVal());
    }

    /**
     * 查询总记录数
     *
     * @param queryWrapper 实体对象封装操作类（可以为 null）
     */
    public List<T> selectList(Wrapper<T> queryWrapper) {
        Map<String, Object> map = CollectionUtils.newHashMapWithExpectedSize(1);
        map.put(Constants.WRAPPER, queryWrapper);
        SqlSession sqlSession = sqlSession();
        try {
            return sqlSession.selectList(sqlStatement(SqlMethod.SELECT_LIST), map);
        } finally {
            closeSqlSession(sqlSession);
        }
    }

    /**
     * 查询一条记录
     *
     * @param queryWrapper 实体对象封装操作类（可以为 null）
     */
    public T selectOne(Wrapper<T> queryWrapper) {
        return SqlHelper.getObject(() -> LogFactory.getLog(this.entityClass), selectList(queryWrapper));
    }

    /**
     * 翻页查询
     *
     * @param page         翻页查询条件
     * @param queryWrapper 实体对象封装操作类（可以为 null）
     */
    public <E extends IPage<T>> E selectPage(E page, Wrapper<T> queryWrapper) {
        Map<String, Object> map = CollectionUtils.newHashMapWithExpectedSize(2);
        map.put(Constants.WRAPPER, queryWrapper);
        map.put("page", page);
        SqlSession sqlSession = sqlSession();
        try {
            page.setRecords(sqlSession.selectList(sqlStatement(SqlMethod.SELECT_PAGE), map));
        } finally {
            closeSqlSession(sqlSession);
        }
        return page;
    }

    /**
     * 查询总数
     *
     * @param queryWrapper 实体对象封装操作类（可以为 null）
     */
    public long selectCount(Wrapper<T> queryWrapper) {
        Map<String, Object> map = CollectionUtils.newHashMapWithExpectedSize(1);
        map.put(Constants.WRAPPER, queryWrapper);
        SqlSession sqlSession = sqlSession();
        try {
            return SqlHelper.retCount(sqlSession.<Long>selectOne(sqlStatement(SqlMethod.SELECT_COUNT), map));
        } finally {
            closeSqlSession(sqlSession);
        }
    }

    /**
     * 执行 SQL
     */
    public SqlRunner sql() {
        return new SqlRunner(this.entityClass);
    }

    /**
     * 获取Session 默认自动提交
     */
    protected SqlSession sqlSession() {
        return SqlHelper.sqlSession(this.entityClass);
    }

    /**
     * 获取SqlStatement
     *
     * @param sqlMethod sqlMethod
     */
    protected String sqlStatement(SqlMethod sqlMethod) {
        return sqlStatement(sqlMethod.getMethod());
    }

    /**
     * 获取SqlStatement
     *
     * @param sqlMethod sqlMethod
     */
    protected String sqlStatement(String sqlMethod) {
        //无法确定对应的mapper，只能用注入时候绑定的了。
        return SqlHelper.table(this.entityClass).getSqlStatement(sqlMethod);
    }

    /**
     * 主键值
     */
    public Serializable pkVal() {
        // 1. 获取实体类对应的TableInfo
        TableInfo tableInfo = TableInfoHelper.getTableInfo(this.entityClass);
        // 2. 调用 TableInfo.getPropertyValue(..) : 获取主键 tableInfo.getKeyProperty() 的值
        return (Serializable) tableInfo.getPropertyValue(this, tableInfo.getKeyProperty());
    }

    /**
     * 释放sqlSession
     *
     * @param sqlSession session
     */
    protected void closeSqlSession(SqlSession sqlSession) {
        // note: 实际上,在Spring中就并不是直接释放掉SqlSession,因为这关系到事务同步机制的问题
        // 一旦有事务同步的存在,这里的释放SqlSession,实际上是将SqlSessionHolder的持有数量减去1
        SqlSessionUtils.closeSqlSession(sqlSession, GlobalConfigUtils.currentSessionFactory(this.entityClass));
    }
}
