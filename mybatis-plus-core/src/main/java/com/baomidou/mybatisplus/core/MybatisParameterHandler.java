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
package com.baomidou.mybatisplus.core;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.exceptions.MybatisPlusException;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.mapping.*;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.SimpleTypeRegistry;
import org.apache.ibatis.type.TypeException;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 自定义 ParameterHandler 重装构造函数，填充插入方法主键 ID
 *
 * @author nieqiuqiu 2020/6/5
 * @since 3.4.0
 */
public class MybatisParameterHandler implements ParameterHandler {
    // 位于: com.baomidou.mybatisplus.core = 直接在core模块下哦

    // 区别:
    // 在Mybatis中ParameterHandler只有一个实现类:那就是DefaultParamHandler ->

    // 如何其作用:
    // 请见:
    // Mybatis的 BaseStatementHandler#BaseStatementHandler(..) 构造器中有如下代码:
        // { ...
        //     // 下面两个处理器Handler都是非常重要的
        //    this.parameterHandler = configuration.newParameterHandler(mappedStatement, parameterObject, boundSql);
        //    this.resultSetHandler = configuration.newResultSetHandler(executor, mappedStatement, rowBounds, parameterHandler, resultHandler, boundSql);
        // }
    // Mybatis的 Configuration#newParameterHandler(..) 代码如下:
        //  public ParameterHandler newParameterHandler(MappedStatement mappedStatement, Object parameterObject, BoundSql boundSql) {
        //    ParameterHandler parameterHandler = mappedStatement.getLang().createParameterHandler(mappedStatement, parameterObject, boundSql);
        //    parameterHandler = (ParameterHandler) interceptorChain.pluginAll(parameterHandler);
        //    return parameterHandler;
        //  }

    private final TypeHandlerRegistry typeHandlerRegistry;
    private final MappedStatement mappedStatement;
    private final Object parameterObject;
    private final BoundSql boundSql;
    private final Configuration configuration;
    private final SqlCommandType sqlCommandType;

    public MybatisParameterHandler(MappedStatement mappedStatement, Object parameter, BoundSql boundSql) {
        this.typeHandlerRegistry = mappedStatement.getConfiguration().getTypeHandlerRegistry();
        this.mappedStatement = mappedStatement;
        this.boundSql = boundSql;
        this.configuration = mappedStatement.getConfiguration();
        this.sqlCommandType = mappedStatement.getSqlCommandType();
        // 核心 ~~~ ❗️❗️❗️
        this.parameterObject = processParameter(parameter);
    }

    public Object processParameter(Object parameter) {
        // 1. 只处理插入或更新操作
        // 因为: MetaObjectHandler只能处理自动插入填充和自动更新填充
        if (parameter != null && (SqlCommandType.INSERT == this.sqlCommandType || SqlCommandType.UPDATE == this.sqlCommandType)) {
            // 1.1 检查 parameterObject如果是简单类型,直接跳过不处理 -> [因为MetaObjectHandler都是针对实体类进行字段注入的,像这种简单类型都无法注入,忽略吧]
            if (SimpleTypeRegistry.isSimpleType(parameter.getClass())) {
                return parameter;
            }
            // 1.2 调用 getParameters(parameter) -> 尝试获取单个形参\无@Param标注\形参类型为List或Array或Collection的形参值
            Collection<Object> parameters = getParameters(parameter);

            // 1.3.1 对集合中的每一个元素都进行处理哦
            if (null != parameters) {
                parameters.forEach(this::process);
            }
            // 1.3.2 不是集合的情况下,直接对parameter进行处理process()
            else {
                process(parameter);
            }
        }
        return parameter;
    }

    @Override
    public Object getParameterObject() {
        return this.parameterObject;
    }

    private void process(Object parameter) {

        // 1. 首先parameter不能为空哦
        if (parameter != null) {
            TableInfo tableInfo = null;
            Object entity = parameter;
            // 1.1  99%的情况都是Map结构,一般而言,对于mapper方法使用了@Param都是这种情况哦
            if (parameter instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) parameter;
                // 1.1.1 一般情况: MP自动注入的方法即BaseMapper下的方法,如果形参是实体类的话,就习惯设置@Param(Constants.ENTITY)
                // ❗️❗️❗️ -> 说明一个情况: 那就MetaObjectHandler对于自己的Mapper方法支持度并不高,除非你也使用@Param(Constants.ENTITY)
                if (map.containsKey(Constants.ENTITY)) {
                    Object et = map.get(Constants.ENTITY);
                    if (et != null) {
                        entity = et;
                        // 1.1.2 获取实体类对应的TableInfo
                        tableInfo = TableInfoHelper.getTableInfo(entity.getClass());
                    }
                }
            }
            // 1.2  1%的情况不是Map,那就是mapper方法中形参只有一个,且没有使用@Param参数,且非数组或集合的形参类型
            else {
                // 1.2.1 ❗️❗️❗️ 如果mapper方法只有一个参数,且该参数没有标注@Param,且该参数恰好就是实体类 -> 即可获取对应的TableInfo,否则无法通过下面的if判断哦
                tableInfo = TableInfoHelper.getTableInfo(parameter.getClass());
            }
            // 1.3 ❗️❗️❗️ 最终拿到实体对象对应的TableInfo
            // note: MetaObjectHandler仅仅对的实体类的字段自动填充有效哦
            if (tableInfo != null) {
                // 1.3.1 到这里就应该转换到实体参数对象了,因为填充和ID处理都是针对实体对象处理的,不用传递原参数对象下去.
                MetaObject metaObject = this.configuration.newMetaObject(entity);
                if (SqlCommandType.INSERT == this.sqlCommandType) {
                    // ❗️❗️❗️ 1.3.2 填充id
                    populateKeys(tableInfo, metaObject, entity);
                    insertFill(metaObject, tableInfo);
                } else {
                    // ❗️❗️❗️ 1.3.3 处理更新
                    updateFill(metaObject, tableInfo);
                }
            }
        }
    }


    protected void populateKeys(TableInfo tableInfo, MetaObject metaObject, Object entity) {
        // 填充主键 -> note❗️:当前方法生效的前提还是非常苛刻的哦

        // 1. 当前实体类的id类型
        final IdType idType = tableInfo.getIdType();
        // 2. 当前实体类的id属性名
        final String keyProperty = tableInfo.getKeyProperty();
        // 3. 当前实体类有指定的id,且IdType不是 IdType.AUTO || IdType.NONE || IdType.INPUT
        // ❗️ 以上三种IdType都是不需要 id生成器IdentifierGenerator 的
        if (StringUtils.isNotBlank(keyProperty) && null != idType && idType.getKey() >= 3) {
            // 3.1 拿到全局唯一的IdentifierGenerator
            final IdentifierGenerator identifierGenerator = GlobalConfigUtils.getGlobalConfig(this.configuration).getIdentifierGenerator();
            // 3.2 拿到主键的id值
            Object idValue = metaObject.getValue(keyProperty);
            // 3.3 ❗️️ 要求实体类的id值为空的哦
            if (StringUtils.checkValNull(idValue)) {
                // 3.3.1 ❗️ASSIGN_ID 类型 ->  IdentifierGenerator#nextId()
                if (idType.getKey() == IdType.ASSIGN_ID.getKey()) {
                    Class<?> keyType = tableInfo.getKeyType();
                    if (Number.class.isAssignableFrom(keyType)) {
                        Number id = identifierGenerator.nextId(entity);
                        if (keyType == id.getClass()) {
                            metaObject.setValue(keyProperty, id);
                        } else if (Integer.class == keyType) {
                            metaObject.setValue(keyProperty, id.intValue());
                        } else if (Long.class == keyType) {
                            metaObject.setValue(keyProperty, id.longValue());
                        } else if (BigDecimal.class.isAssignableFrom(keyType)) {
                            metaObject.setValue(keyProperty, new BigDecimal(id.longValue()));
                        } else if (BigInteger.class.isAssignableFrom(keyType)) {
                            metaObject.setValue(keyProperty, new BigInteger(id.toString()));
                        } else {
                            throw new MybatisPlusException("Key type '" + keyType + "' not supported");
                        }
                    }
                    else {
                        metaObject.setValue(keyProperty, identifierGenerator.nextId(entity).toString());
                    }
                }
                // 3.3.2 ❗️ASSIGN_UUID 类型 -> IdentifierGenerator#nextUUID()
                else if (idType.getKey() == IdType.ASSIGN_UUID.getKey()) {
                    metaObject.setValue(keyProperty, identifierGenerator.nextUUID(entity));
                }
            }
        }
    }


    protected void insertFill(MetaObject metaObject, TableInfo tableInfo) {
        // ❗️❗️❗️
        // 1. 如果ioc容器中存在注入的MetaObjectHandler,即Configuration对应的GlobalConfig的MetaObjectHandler也存在
        // 2. 使用MetaObjectHandler开始处理实体类的自动插入填充 ->
        //      2.1 metaObjectHandler.openInsertFill() && tableInfo.isWithInsertFill(): 检查MetaObjectHandler是否支持自动插入填充,并且实体类的TableInfo中有自动插入填充需求的字段
        //      2.2 满足上述条件,开始自动插入填充吧
        GlobalConfigUtils.getMetaObjectHandler(this.configuration).ifPresent(metaObjectHandler -> {
            if (metaObjectHandler.openInsertFill() && tableInfo.isWithInsertFill()) {
                metaObjectHandler.insertFill(metaObject);
            }
        });
    }

    protected void updateFill(MetaObject metaObject, TableInfo tableInfo) {
        // ❗️❗️❗️
        // 1. 如果ioc容器中存在注入的MetaObjectHandler,即Configuration对应的GlobalConfig的MetaObjectHandler也存在
        // 2. 使用MetaObjectHandler开始处理实体类的自动插入填充 ->
        //      2.1 metaObjectHandler.openUpdateFill() && tableInfo.isWithUpdateFill(): 检查MetaObjectHandler是否支持自动更新填充,并且实体类的TableInfo中有自动更新填充需求的字段
        //      2.2 满足上述条件,开始自动更新填充吧
        GlobalConfigUtils.getMetaObjectHandler(this.configuration).ifPresent(metaObjectHandler -> {
            if (metaObjectHandler.openUpdateFill() && tableInfo.isWithUpdateFill()) {
                metaObjectHandler.updateFill(metaObject);
            }
        });
    }

    /**
     * 处理正常批量插入逻辑
     * <p>
     * org.apache.ibatis.session.defaults.DefaultSqlSession$StrictMap 该类方法
     * wrapCollection 实现 StrictMap 封装逻辑
     * </p>
     *
     * @return 集合参数
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    protected Collection<Object> getParameters(Object parameterObject) {
        // 处理正常批量插入逻辑

        Collection<Object> parameters = null;
        // 1. 集合类型
        if (parameterObject instanceof Collection) {
            parameters = (Collection) parameterObject;
        }
        // 2. 99%的情况都是 Map 结构哦
        else if (parameterObject instanceof Map) {
            // 2.1 对于单个形参,且形参类型为List/Array/Collection,且没有使用@Param的形参而言
            // 传递过来的: parameterObject就是Map结构,且key为collection/list/array,value就是对应的单个形参值哦
            Map parameterMap = (Map) parameterObject;
            if (parameterMap.containsKey("collection")) {
                parameters = (Collection) parameterMap.get("collection");
            } if (parameterMap.containsKey(Constants.COLLECTION)) {
                // 兼容逻辑删除对象填充
                parameters = (Collection) parameterMap.get(Constants.COLLECTION);
            } else if (parameterMap.containsKey("list")) {
                parameters = (List) parameterMap.get("list");
            } else if (parameterMap.containsKey("array")) {
                parameters = Arrays.asList((Object[]) parameterMap.get("array"));
            }
        }
        // 3. 对于不是: 单个形参,且形参类型为List/Array/Collection,且没有使用@Param的形参而言 -> 最终都是返回null
        return parameters;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void setParameters(PreparedStatement ps) {
        // 核心❗️❗️❗️ -> 本质上和 Mybatis.DefaultParameterHandler#setParameters(..) 代码一样
        ErrorContext.instance().activity("setting parameters").object(this.mappedStatement.getParameterMap().getId());
        List<ParameterMapping> parameterMappings = this.boundSql.getParameterMappings();
        if (parameterMappings != null) {
            for (int i = 0; i < parameterMappings.size(); i++) {
                ParameterMapping parameterMapping = parameterMappings.get(i);
                if (parameterMapping.getMode() != ParameterMode.OUT) {
                    Object value;
                    String propertyName = parameterMapping.getProperty();
                    if (this.boundSql.hasAdditionalParameter(propertyName)) { // issue #448 ask first for additional params
                        value = this.boundSql.getAdditionalParameter(propertyName);
                    } else if (this.parameterObject == null) {
                        value = null;
                    } else if (this.typeHandlerRegistry.hasTypeHandler(this.parameterObject.getClass())) {
                        value = parameterObject;
                    } else {
                        MetaObject metaObject = this.configuration.newMetaObject(this.parameterObject);
                        value = metaObject.getValue(propertyName);
                    }
                    TypeHandler typeHandler = parameterMapping.getTypeHandler();
                    JdbcType jdbcType = parameterMapping.getJdbcType();
                    if (value == null && jdbcType == null) {
                        jdbcType = this.configuration.getJdbcTypeForNull();
                    }
                    try {
                        typeHandler.setParameter(ps, i + 1, value, jdbcType);
                    } catch (TypeException | SQLException e) {
                        throw new TypeException("Could not set parameters for mapping: " + parameterMapping + ". Cause: " + e, e);
                    }
                }
            }
        }
    }
}
