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
package com.baomidou.mybatisplus.core.metadata;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.incrementer.IKeyGenerator;
import com.baomidou.mybatisplus.core.toolkit.*;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.builder.StaticSqlSource;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.executor.keygen.SelectKeyGenerator;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.reflection.Reflector;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.SimpleTypeRegistry;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.stream.Collectors.toList;

/**
 * <p>
 * 实体类反射表辅助类
 * </p>
 *
 * @author hubin sjy
 * @since 2016-09-09
 */
public class TableInfoHelper {
    // 位于: com.baomidou.mybatisplus.core.metadata

    // 命名:
    // TableInfo Helper -> 工具类,主要看方法定义吧

    // 定义:
    // getAllFields(Class<?>):获取该类的所有属性列表
    // getTableInfo(Class<?>):获取实体映射表信息
    // getTableInfo(String):根据表名获取实体映射表信息
    // ....

    private static final Log logger = LogFactory.getLog(TableInfoHelper.class);

    // 储存实体类的class对应的反射类表信息
    // 以 实体类的class 为 key , TableInfo 为 value
    private static final Map<Class<?>, TableInfo> TABLE_INFO_CACHE = new ConcurrentHashMap<>();

    // 储存表名对应的反射类表信息
    // 以 tableName 为 key , TableInfo 为 value
    private static final Map<String, TableInfo> TABLE_NAME_INFO_CACHE = new ConcurrentHashMap<>();

    /**
     * 默认表主键名称
     */
    private static final String DEFAULT_ID_NAME = "id";

    /**
     * <p>
     * 获取实体映射表信息
     * </p>
     *
     * @param clazz 反射实体类
     * @return 数据库表反射信息
     */
    public static TableInfo getTableInfo(Class<?> clazz) {
        // 1. 检查clazz是不是有效的 -> 非空 非基本类型 非简单类型 非接口
        if (clazz == null || clazz.isPrimitive() || SimpleTypeRegistry.isSimpleType(clazz) || clazz.isInterface()) {
            return null;
        }
        // 2. 避免代理类
        Class<?> targetClass = ClassUtils.getUserClass(clazz);
        // 3. 缓存命中
        TableInfo tableInfo = TABLE_INFO_CACHE.get(targetClass);
        if (null != tableInfo) {
            return tableInfo;
        }
        // 4. 缓存失效 -> 开始尝试获取父类的TableInfo缓存 -> 递归获取
        Class<?> currentClass = clazz;
        while (null == tableInfo && Object.class != currentClass) {
            currentClass = currentClass.getSuperclass();
            tableInfo = TABLE_INFO_CACHE.get(ClassUtils.getUserClass(currentClass));
        }
        // 5. 如果查找到父类的TableInfo缓存 -> 缓存更新
        if (tableInfo != null) {
            TABLE_INFO_CACHE.put(targetClass, tableInfo);
        }
        return tableInfo;
    }

    /**
     * <p>
     * 根据表名获取实体映射表信息
     * </p>
     *
     * @param tableName 表名
     * @return 数据库表反射信息
     */
    public static TableInfo getTableInfo(String tableName) {
        if (StringUtils.isBlank(tableName)) {
            return null;
        }
        return TABLE_NAME_INFO_CACHE.get(tableName);
    }

    /**
     * <p>
     * 获取所有实体映射表信息
     * </p>
     *
     * @return 数据库表反射信息集合
     */
    public static List<TableInfo> getTableInfos() {
        return Collections.unmodifiableList(new ArrayList<>(TABLE_INFO_CACHE.values()));
    }

    /**
     * 清空实体表映射缓存信息
     *
     * @param entityClass 实体 Class
     */
    public static void remove(Class<?> entityClass) {
        TABLE_INFO_CACHE.remove(entityClass);
    }

    /**
     * <p>
     * 实体类反射获取表信息【初始化】
     * </p>
     *
     * @param clazz 反射实体类
     * @return 数据库表反射信息
     */
    public synchronized static TableInfo initTableInfo(MapperBuilderAssistant builderAssistant, Class<?> clazz) {
        // 唯一调用处: AbstractSqlInjector#inspectInject(..) -> 此刻就是刚好在MybatisAnnotationBuilder#parser()解析时触发的

        // 1. 缓存检查
        TableInfo targetTableInfo = TABLE_INFO_CACHE.get(clazz);
        final Configuration configuration = builderAssistant.getConfiguration();
        // 2. 缓存命中
        if (targetTableInfo != null) {
            // 2.1 获取TableInfo中的Configuration,如果和 builderAssistant.getConfiguration() 的 Configuration 不同,就需要重新初始话
            Configuration oldConfiguration = targetTableInfo.getConfiguration();
            if (!oldConfiguration.equals(configuration)) {
                // 2.2 不是同一个 Configuration,进行重新初始化
                targetTableInfo = initTableInfo(configuration, builderAssistant.getCurrentNamespace(), clazz);
            }
            return targetTableInfo;
        }
        // 3. 缓存未命中: 直接初始化出来一个TableInfo
        return initTableInfo(configuration, builderAssistant.getCurrentNamespace(), clazz);
    }

    /**
     * <p>
     * 实体类反射获取表信息【初始化】
     * </p>
     *
     * @param clazz 反射实体类
     * @return 数据库表反射信息
     */
    private synchronized static TableInfo initTableInfo(Configuration configuration, String currentNamespace, Class<?> clazz) {

        // 1. 新建TableInfo
        TableInfo tableInfo = new TableInfo(configuration, clazz);
        // 2. 设置命名空间
        tableInfo.setCurrentNamespace(currentNamespace);
        GlobalConfig globalConfig = GlobalConfigUtils.getGlobalConfig(configuration);

        // 3. 初始化表名
        // 简述initTableName(clazz, globalConfig, tableInfo)的流程 ❗️❗️❗️
        // 先阐述tableName的流程
        // 1. 实体类上@TableName存在
        //      1.1 @TableName的value值不为空
        //          1.1.1 如果同时@TableName的keepGlobalPrefix为false,就表示不需要表名前缀,将tablePrefixEffect设置为false
        //      1.2 @TableName的value值为空
        //          1.1.2 根据实体类的类名和DbConfig确定tableName
        //              a: 如果DbConfig开启类名驼峰命名转为下划线命名,是的话类名的驼峰命名转换为下划线命名
        //              b: 接着上面的步骤,如果DbConfig开启全部转为大写,就将表名继续处理,全部转换为大写字母,后者仅仅首字母小写
        // 2. 实体类上@TableName不存在
        //          1.1.2 根据实体类的类名和DbConfig确定tableName
        //              a: 如果DbConfig开启类名驼峰命名转为下划线命名,是的话类名的驼峰命名转换为下划线命名
        //              b: 接着上面的步骤,如果DbConfig开启全部转为大写,就将表名继续处理,全部转换为大写字母,后者仅仅首字母小写
        // 3. 通过1和2已经确定了表名
        //      3.1 如果tablePrefixEffect为true,且DbConfig中的tablePrefix有值,就继续在表名上加上全局表名前缀
        //      3.2 如果scheme不为空 [@TableName的scheme 优先级大于 全局DbConfig.schema值],继续拼接为 scheme + "." + tableName
        // 举例:
        // 类名为SysUser,@TableName的value为t_sys_user,其余默认 -> 最终表名: t_sys_user
        // 类名为SysUser,@TableName的value为t_sys_user,@TableName的schema为website, 其余默认 -> 最终表名: website.t_sys_user
        // 类名为SysUser,@TableName的keepGlobalPrefix为true,DbConfig的schema为website,DbConfig的tablePrefix为"t_", 其余默认 -> 最终表名: website.t_sys_user
        // 类名为SysUser,没有@TableName注解,DbConfig的schema为website,DbConfig的tablePrefix为"t_", 其余默认 -> 最终表名: website.t_sys_user
        // 类名为SysUser,有@TableName注解的,@TableName的scheme为developer,DbConfig的schema为website,DbConfig的tablePrefix为"t_", 其余默认 -> 最终表名: developer.sys_user
        // 类名为SysUser,也没有@TableName注解,其余默认 -> 最终表名: sys_user

        // 简述存在@TableName时的额外处理:
        // 1. @TableName是否有指定resultMap -> 有的话,加入到tableInfo中
        // 2. @TableName是否有指定autoResultMap -> 有的话,设置到tableInfo上
        // 3. DbConfig中如果有有有用户在ioc容器中注入的IKeyGenerator,就将实体类上的@KeySequence指定的class设置到tableInfo
        // 4. @TableName的excludeProperty -> 指定PO类中需要排除的属性 -> 需要当前方法最终返回出去
        final String[] excludeProperty = initTableName(clazz, globalConfig, tableInfo);

        // 4. excludeProperty 从 数组转为集合
        List<String> excludePropertyList = excludeProperty != null && excludeProperty.length > 0 ? Arrays.asList(excludeProperty) : Collections.emptyList();

        // 5. ❗️❗️❗️ 初始化实体类中的字段
        initTableFields(clazz, globalConfig, tableInfo, excludePropertyList);

        // 6. ❗️❗️❗️ 是否自动构建ResultMap
        tableInfo.initResultMapIfNeed();

        // 7. 存入两个缓存: TABLE_INFO_CACHE/TABLE_NAME_INFO_CACHE 中
        TABLE_INFO_CACHE.put(clazz, tableInfo);
        TABLE_NAME_INFO_CACHE.put(tableInfo.getTableName(), tableInfo);

        // 8.  实例化缓存Cache对象
        LambdaUtils.installCache(tableInfo);
        return tableInfo;
    }

    /**
     * <p>
     * 初始化 表数据库类型,表名,resultMap
     * </p>
     *
     * @param clazz        实体类
     * @param globalConfig 全局配置
     * @param tableInfo    数据库表反射信息
     * @return 需要排除的字段名
     */
    private static String[] initTableName(Class<?> clazz, GlobalConfig globalConfig, TableInfo tableInfo) {

        // 简述流程 ❗️❗️❗️
        // 先阐述tableName的流程
        // 1. 实体类上@TableName存在
        //      1.1 @TableName的value值不为空
        //          1.1.1 如果同时@TableName的keepGlobalPrefix为false,就表示不需要表名前缀,将tablePrefixEffect设置为false
        //      1.2 @TableName的value值为空
        //          1.1.2 根据实体类的类名和DbConfig确定tableName
        //              a: 如果DbConfig开启类名驼峰命名转为下划线命名,是的话类名的驼峰命名转换为下划线命名
        //              b: 接着上面的步骤,如果DbConfig开启全部转为大写,就将表名继续处理,全部转换为大写字母,后者仅仅首字母小写
        // 2. 实体类上@TableName不存在
        //          1.1.2 根据实体类的类名和DbConfig确定tableName
        //              a: 如果DbConfig开启类名驼峰命名转为下划线命名,是的话类名的驼峰命名转换为下划线命名
        //              b: 接着上面的步骤,如果DbConfig开启全部转为大写,就将表名继续处理,全部转换为大写字母,后者仅仅首字母小写
        // 3. 通过1和2已经确定了表名
        //      3.1 如果tablePrefixEffect为true,且DbConfig中的tablePrefix有值,就继续在表名上加上全局表名前缀
        //      3.2 如果scheme不为空 [@TableName的scheme 优先级大于 全局DbConfig.schema值],继续拼接为 scheme + "." + tableName
        // 举例:
        // 类名为SysUser,@TableName的value为t_sys_user,其余默认 -> 最终表名: t_sys_user
        // 类名为SysUser,@TableName的value为t_sys_user,@TableName的schema为website, 其余默认 -> 最终表名: website.t_sys_user
        // 类名为SysUser,@TableName的keepGlobalPrefix为true,DbConfig的schema为website,DbConfig的tablePrefix为"t_", 其余默认 -> 最终表名: website.t_sys_user
        // 类名为SysUser,没有@TableName注解,DbConfig的schema为website,DbConfig的tablePrefix为"t_", 其余默认 -> 最终表名: website.t_sys_user
        // 类名为SysUser,有@TableName注解的,@TableName的scheme为developer,DbConfig的schema为website,DbConfig的tablePrefix为"t_", 其余默认 -> 最终表名: developer.sys_user
        // 类名为SysUser,也没有@TableName注解,其余默认 -> 最终表名: sys_user

        // 简述存在@TableName时的额外处理:
        // 1. @TableName是否有指定resultMap -> 有的话,加入到tableInfo中
        // 2. @TableName是否有指定autoResultMap -> 有的话,设置到tableInfo上
        // 3. DbConfig中如果有有有用户在ioc容器中注入的IKeyGenerator,就将实体类上的@KeySequence指定的class设置到tableInfo
        // 4. @TableName的excludeProperty -> 指定PO类中需要排除的属性 -> 需要当前方法最终返回出去



        GlobalConfig.DbConfig dbConfig = globalConfig.getDbConfig();
        // 1. 获取实体类类上的@TableName属性
        TableName table = clazz.getAnnotation(TableName.class);

        // 2. 获取实体类的类名
        String tableName = clazz.getSimpleName();
        // 3. 全局配置中的表名前缀
        String tablePrefix = dbConfig.getTablePrefix();
        // 4. 全局配置的格式
        String schema = dbConfig.getSchema();
        boolean tablePrefixEffect = true;
        String[] excludeProperty = null;

        // 5. 实体类上有@TableName注解
        if (table != null) {
            // 5.1 @TableName的value值不为空
            if (StringUtils.isNotBlank(table.value())) {
                tableName = table.value();
                // 5.1.1. 表名牵走非空,且@TableNam的keepGlobalPrefix为false,表示不需要使用表名前缀
                if (StringUtils.isNotBlank(tablePrefix) && !table.keepGlobalPrefix()) {
                    tablePrefixEffect = false;
                }
            }
            // 5.2 @TableName的value值为空
            else {
                // 5.2.1 根据实体类的类名和DbConfig确定tableName
                // a: 是否开启类名驼峰命名转为下划线命名
                // b: 是否开启全部转为大写
                // 根据以上两步家伙加上类名确定最终使用的表名而已
                tableName = initTableNameWithDbConfig(tableName, dbConfig);
            }
            // 5.3 @TableName的schema
            if (StringUtils.isNotBlank(table.schema())) {
                // 5.3.1 @TableName的schema 优先级大于 DbConfig.schema
                schema = table.schema();
            }
           // 5.4. @TableName是否有指定resultMap() -> 有的话,加入到tableInfo中
            if (StringUtils.isNotBlank(table.resultMap())) {
                tableInfo.setResultMap(table.resultMap());
            }
            // 5.5 设置是否自动映射的信息
            tableInfo.setAutoInitResultMap(table.autoResultMap());
            // 5.6 @TableName的excludeProperty -> 指定PO类中需要排除的属性
            excludeProperty = table.excludeProperty();
        }
        // 6. 实体类上没有@TableName注解时,直接使用 initTableNameWithDbConfig(..)
        else {
            tableName = initTableNameWithDbConfig(tableName, dbConfig);
        }

        // 7. 对tableName加上 tablePrefix 以及 schema

        String targetTableName = tableName;
        // 7.1 tablePrefix前缀不为空,且@TableName不存在,或者@TableName的keepGlobalPrefix属性为true -> 进入下面的代码块 + 加上前缀
        if (StringUtils.isNotBlank(tablePrefix) && tablePrefixEffect) {
            targetTableName = tablePrefix + targetTableName;
        }
        // 7.2 schema不为空 [ 类上的@TableName的schema属性 优先级大于 DbConfig.schema字段值]
        if (StringUtils.isNotBlank(schema)) {
            targetTableName = schema + StringPool.DOT + targetTableName; // 奇奇怪怪
        }

        // 7.3 设置最终的表名
        tableInfo.setTableName(targetTableName);

        // 8. DbConfig中如果有有有用户在ioc容器中注入的IKeyGenerator,就设置
        if (CollectionUtils.isNotEmpty(dbConfig.getKeyGenerators())) {
            tableInfo.setKeySequence(clazz.getAnnotation(KeySequence.class));
        }

        // 9. 返回 @TableName的excludeProperty -> 指定PO类中需要排除的属性
        return excludeProperty;
    }

    /**
     * 根据 DbConfig 初始化 表名
     *
     * @param className 类名
     * @param dbConfig  DbConfig
     * @return 表名
     */
    private static String initTableNameWithDbConfig(String className, GlobalConfig.DbConfig dbConfig) {

        // 1. 拿到实体类的类名
        String tableName = className;
        // 2. DbConfig中是否开启表名下划线申明
        if (dbConfig.isTableUnderline()) {
            tableName = StringUtils.camelToUnderline(tableName);
        }
        // 3.1 DbConfig中是否开启大写命名判断
        if (dbConfig.isCapitalMode()) {
            tableName = tableName.toUpperCase();
        }
        // 3.2 首字母小写
        else {
            tableName = StringUtils.firstToLowerCase(tableName);
        }
        return tableName;
    }

    /**
     * <p>
     * 初始化 表主键,表字段
     * </p>
     *
     * @param clazz        实体类
     * @param globalConfig 全局配置
     * @param tableInfo    数据库表反射信息
     */
    private static void initTableFields(Class<?> clazz, GlobalConfig globalConfig, TableInfo tableInfo, List<String> excludeProperty) {
        // `初始化 表主键,表字段

        // 1. 初始化准备
        GlobalConfig.DbConfig dbConfig = globalConfig.getDbConfig();
        Reflector reflector = tableInfo.getReflector();
        // 2. 过滤出实体类中不带有@TableFiled注解和带有@TableFiled且exist属性为true的字段集合
        List<Field> list = getAllFields(clazz);
        // 2. 标记是否读取到主键
        boolean isReadPK = false;
        // 4. 是否存在 @TableId 注解
        boolean existTableId = isExistTableId(list);
        // 5. 是否存在 @TableLogic 注解
        boolean existTableLogic = isExistTableLogic(list);

        // 6. 遍历: 实体类中不带有@TableFiled注解和带有@TableFiled且exist属性为true的字段集合
        List<TableFieldInfo> fieldList = new ArrayList<>(list.size());
        for (Field field : list) {
            // 6.1 跳过实体类上@TableName的excludeProperty属性指定需要排除的字段
            if (excludeProperty.contains(field.getName())) {
                continue;
            }
            // 6.2 校验是否存在 @OrderBy 注解
            boolean isPK = false; // 是否为主键
            boolean isOrderBy = field.getAnnotation(OrderBy.class) != null; // 是否标记为@OrderBy

            // 6.3.1 存在@TableId注解时
            if (existTableId) {
                TableId tableId = field.getAnnotation(TableId.class);
                if (tableId != null) {
                    // 6.3.1.1 @TableId 在一个实体类中只能使用一次
                    if (isReadPK) {
                        throw ExceptionUtils.mpe("@TableId can't more than one in Class: \"%s\".", clazz.getName());
                    }
                    // 6.3.1.2 初始化 @TableId 标注的字段
                    initTableIdWithAnnotation(dbConfig, tableInfo, field, tableId);
                    isPK = isReadPK = true;
                }
            }
            // 6.3.2 不是@TableId注解的字段时 -> initTableIdWithoutAnnotation
            // 主要就是检查属性名是否为id -> 然后完成和initTableIdWithAnnotation相同的操作
            else if (!isReadPK) {
                isPK = isReadPK = initTableIdWithoutAnnotation(dbConfig, tableInfo, field);
            }

            // 6.5 如果当前读取的field是主键
            if (isPK) {
                // 6.5.1 同时,标记有@OrderBy注解, 就需要向 TableInfo 中 order by 关键字有关的字段集合,添加一个TableFiledInfo
                if (isOrderBy) {
                    tableInfo.getOrderByFields().add(new TableFieldInfo(dbConfig, tableInfo, field, reflector, existTableLogic, true));
                }
                // 6.5.2 结束 - over
                continue;
            }

            // 执行到下面: 说明当前遍历的field并不是主键 [1. 标有@TableId 2. 字段名默认等于DEFAULT_ID_NAME 两种情况都认为是主键]

            // 6.6
            // 非主键的字段尝试去拿到@TableField注解
            final TableField tableField = field.getAnnotation(TableField.class);

            // 6.7 有 @TableField 注解的字段初始化
            if (tableField != null) {
                fieldList.add(new TableFieldInfo(dbConfig, tableInfo, field, tableField, reflector, existTableLogic, isOrderBy));
                continue;
            }

            // 执行到这: 说明当前遍历的field不是主键,并且没有@TableFiled注解,并且没有被@TableName.excludeProperty()排除出去

            // 6.8 无 @TableField
            fieldList.add(new TableFieldInfo(dbConfig, tableInfo, field, reflector, existTableLogic, isOrderBy));
        }

        // 7. 字段列表
        tableInfo.setFieldList(fieldList);

        // 8. 未发现主键注解，提示警告信息
        if (!isReadPK) {
            logger.warn(String.format("Can not find table primary key in Class: \"%s\".", clazz.getName()));
        }
    }

    /**
     * <p>
     * 判断主键注解是否存在
     * </p>
     *
     * @param list 字段列表
     * @return true 为存在 {@link TableId} 注解;
     */
    public static boolean isExistTableId(List<Field> list) {
        return list.stream().anyMatch(field -> field.isAnnotationPresent(TableId.class));
    }

    /**
     * <p>
     * 判断逻辑删除注解是否存在
     * </p>
     *
     * @param list 字段列表
     * @return true 为存在 {@link TableLogic} 注解;
     */
    public static boolean isExistTableLogic(List<Field> list) {
        return list.stream().anyMatch(field -> field.isAnnotationPresent(TableLogic.class));
    }

    /**
     * <p>
     * 判断排序注解是否存在
     * </p>
     *
     * @param list 字段列表
     * @return true 为存在 {@link OrderBy} 注解;
     */
    public static boolean isExistOrderBy(List<Field> list) {
        return list.stream().anyMatch(field -> field.isAnnotationPresent(OrderBy.class));
    }

    /**
     * <p>
     * 主键属性初始化
     * </p>
     *
     * @param dbConfig  全局配置信息
     * @param tableInfo 表信息
     * @param field     字段
     * @param tableId   注解
     */
    private static void initTableIdWithAnnotation(GlobalConfig.DbConfig dbConfig, TableInfo tableInfo, Field field, TableId tableId) {
        // 初始化: 带有 @TableId 标注的字段

        // 1. 是否开启下划线转驼峰
        boolean underCamel = tableInfo.isUnderCamel();
        final String property = field.getName();
        if (field.getAnnotation(TableField.class) != null) {
            logger.warn(String.format("This \"%s\" is the table primary key by @TableId annotation in Class: \"%s\",So @TableField annotation will not work!",
                property, tableInfo.getEntityType().getName()));
        }
        /* 主键策略（ 注解 > 全局 ） */
        // 2. 设置 Sequence 其他策略无效
        if (IdType.NONE == tableId.type()) {
            tableInfo.setIdType(dbConfig.getIdType());
        } else {
            tableInfo.setIdType(tableId.type());
        }

        /* 字段 */
        // 3. 解析主键的列名
        //  a: @TableId.value属性 优先级大于 字段类名
        String column = property;
        // 3.1 @TableId.value y优先级更高
        if (StringUtils.isNotBlank(tableId.value())) {
            column = tableId.value();
        }
        // 3.2 没有@TableId.value时
        else {
            // 3.2.1 开启字段下划线申明
            if (underCamel) {
                column = StringUtils.camelToUnderline(column);
            }
            // 3.2.2 全局大写命名
            if (dbConfig.isCapitalMode()) {
                column = column.toUpperCase();
            }
        }

        // 4.  主键的类型
        final Class<?> keyType = tableInfo.getReflector().getGetterType(property);
        if (keyType.isPrimitive()) {
            logger.warn(String.format("This primary key of \"%s\" is primitive !不建议如此请使用包装类 in Class: \"%s\"",
                property, tableInfo.getEntityType().getName()));
        }
        // 5. 解析 -> 四个属性
        tableInfo.setKeyRelated(checkRelated(underCamel, property, column))
            .setKeyColumn(column)
            .setKeyProperty(property)
            .setKeyType(keyType);
    }

    /**
     * <p>
     * 主键属性初始化
     * </p>
     *
     * @param tableInfo 表信息
     * @param field     字段
     * @return true 继续下一个属性判断，返回 continue;
     */
    private static boolean initTableIdWithoutAnnotation(GlobalConfig.DbConfig dbConfig, TableInfo tableInfo, Field field) {
        // 当字段上没有@TableId时,也需要检查是否为table的id -> 主要就是通过默认的DEFAULT_ID_NAME判断
        final String property = field.getName();
        // 1. 默认的表主键就是 "id"
        if (DEFAULT_ID_NAME.equalsIgnoreCase(property)) {
            // 1.1 如果属性名为id,那么即使有@TableFiled有无后无效
            if (field.getAnnotation(TableField.class) != null) {
                logger.warn(String.format("This \"%s\" is the table primary key by default name for `id` in Class: \"%s\",So @TableField will not work!",
                    property, tableInfo.getEntityType().getName()));
            }
            // 1.2 大写默认
            String column = property;
            if (dbConfig.isCapitalMode()) {
                column = column.toUpperCase();
            }
            // 1.3 老样子: 构建 keyType\ key的Property属性名 \ key的column列名
            final Class<?> keyType = tableInfo.getReflector().getGetterType(property);
            if (keyType.isPrimitive()) {
                logger.warn(String.format("This primary key of \"%s\" is primitive !不建议如此请使用包装类 in Class: \"%s\"",
                    property, tableInfo.getEntityType().getName()));
            }
            tableInfo.setKeyRelated(checkRelated(tableInfo.isUnderCamel(), property, column))
                .setIdType(dbConfig.getIdType())
                .setKeyColumn(column)
                .setKeyProperty(property)
                .setKeyType(keyType);
            return true;
        }
        return false;
    }

    /**
     * 判定 related 的值
     * <p>
     * 为 true 表示不符合规则
     *
     * @param underCamel 驼峰命名
     * @param property   属性名
     * @param column     字段名
     * @return related
     */
    public static boolean checkRelated(boolean underCamel, String property, String column) {
        column = StringUtils.getTargetColumn(column);
        String propertyUpper = property.toUpperCase(Locale.ENGLISH);
        String columnUpper = column.toUpperCase(Locale.ENGLISH);
        if (underCamel) {
            // 开启了驼峰并且 column 包含下划线
            return !(propertyUpper.equals(columnUpper) ||
                propertyUpper.equals(columnUpper.replace(StringPool.UNDERSCORE, StringPool.EMPTY)));
        } else {
            // 未开启驼峰,直接判断 property 是否与 column 相同(全大写) -- 不相等,表示需要使用 as 关键字来做转换
            return !propertyUpper.equals(columnUpper);
        }
    }

    /**
     * <p>
     * 获取该类的所有属性列表
     * </p>
     *
     * @param clazz 反射类
     * @return 属性集合
     */
    public static List<Field> getAllFields(Class<?> clazz) {
        // 过滤出实体类中不带有@TableFiled注解和带有@TableFiled且exist属性为true的字段集合
        List<Field> fieldList = ReflectionKit.getFieldList(ClassUtils.getUserClass(clazz));
        return fieldList.stream()
            .filter(field -> {
                /* 过滤注解非表字段属性 */
                TableField tableField = field.getAnnotation(TableField.class);
                return (tableField == null || tableField.exist());
            }).collect(toList());
    }

    public static KeyGenerator genKeyGenerator(String baseStatementId, TableInfo tableInfo, MapperBuilderAssistant builderAssistant) {
        // 1. 找到Configuration对应的GlobalConfig,再找到GlobalConfig中的从ioc容器获取的List<IKeyGenerator>集合
        List<IKeyGenerator> keyGenerators = GlobalConfigUtils.getKeyGenerators(builderAssistant.getConfiguration());
        // 2. 如果有字段标记的@KeySequence.value()非空,但又不存在IKeyGenerator实现类直接报错
        if (CollectionUtils.isEmpty(keyGenerators)) {
            throw new IllegalArgumentException("not configure IKeyGenerator implementation class.");
        }
        IKeyGenerator keyGenerator = null;
        // 3. 多个主键生成器
        if (keyGenerators.size() > 1) {
            // 3.1 拿到实体类上的 @KeySequence
            KeySequence keySequence = tableInfo.getKeySequence();
            // 3.2 确定 @KeySequence 非空,并且 非DbType.OTHER
            if (null != keySequence && DbType.OTHER != keySequence.dbType()) {
                // ❗️❗️❗️ 3.3 要求: @KeySequence.dbType() 等于 IKeyGenerator#dbType()
                keyGenerator = keyGenerators.stream().filter(k -> k.dbType() == keySequence.dbType()).findFirst().get();
            }
        }
        // 4. keyGenerator还是空, 无法找到注解指定生成器，默认使用第一个生成器
        if (null == keyGenerator) {
            keyGenerator = keyGenerators.get(0);
        }
        // 4. 拿到Configuration
        Configuration configuration = builderAssistant.getConfiguration();
        // 5. 设置命名的id -> nameSpace + "." + baseStatementId + "!selectKey"
        String id = builderAssistant.getCurrentNamespace() + StringPool.DOT + baseStatementId + SelectKeyGenerator.SELECT_KEY_SUFFIX;
        ResultMap resultMap = new ResultMap.Builder(builderAssistant.getConfiguration(), id, tableInfo.getKeyType(), new ArrayList<>()).build();
        MappedStatement mappedStatement = new MappedStatement.Builder(builderAssistant.getConfiguration(), id,
            // 构建的: StaticSqlSource -> 传入 Configuration\Sql的字符串\SELECT
            new StaticSqlSource(configuration, keyGenerator.executeSql(tableInfo.getKeySequence().value())), SqlCommandType.SELECT)
            .keyProperty(tableInfo.getKeyProperty()) // 指定keyProperty [和KeyColumn对应 -> 主要是KeyGenerator中使用]
            .resultMaps(Collections.singletonList(resultMap))
            .build();
        configuration.addMappedStatement(mappedStatement);
        // 6. 构建一个: 前置执行的SelectKeyGenerator(..)
        // 更多说明: 请见 -> SelectKeyGenerator ❗️
        return new SelectKeyGenerator(mappedStatement, true);
    }

}
