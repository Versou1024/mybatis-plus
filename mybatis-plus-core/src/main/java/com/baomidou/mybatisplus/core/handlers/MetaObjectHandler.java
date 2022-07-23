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
package com.baomidou.mybatisplus.core.handlers;

import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.reflection.MetaObject;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 元对象字段填充控制器抽象类，实现公共字段自动写入<p>
 * <p>
 * 所有入参的 MetaObject 必定是 entity 或其子类的 MetaObject
 *
 * @author hubin
 * @since 2016-08-28
 */
public interface MetaObjectHandler {
    // 元对象字段填充控制器抽象类，实现公共字段自动写入
    // 和数据自动填充有关
    // 这个接口提供了两个方法，一个是插入的时候填充，一个是更新的时候填充。
    // 我们调用填充的方法参数分别是：
    // 1. 实体对象，我们直接抄下来即可
    // 2. 需要填充的字段名称
    // 3. 需要填充的字段类型
    // 4. 填充的值
    // 举例链接：https://www.jianshu.com/p/8a060021c635

    // 重要:
    // 生效的前提: [详情见: MybatisParameterHandler]
    // 0. 最大前提就是: 要求使用的是MybatisParameterHandler,否则MetaObjectHandler无论如何都不会生效哦
    // 1. 生效的mapper方法的要求:
    //      1.1 形参是实体类的话,且标注有@Param(Constants.ENTITY),就会生效 -> 对应的就是 -> MP自动注入的方法即BaseMapper下的方法,如果形参是实体类的话,就习惯设置@Param(Constants.ENTITY)
    //      1.2 mapper方法只有一个形参,且是实体类的,且没有标注@Param注解
    //      note: ❗️❗️❗️ 只有以上两种Mapper方法会生效,其余情况MetaObjectHandler都无法生效哦
    // 2. MetaObjectHandler用户实现后需要注入到ioc容器中,且只能注入一个哦
    // 3. 使用时: 注意区分严格填充和普通填充



    // 表示当前MetaObjectHandler自动插入填充是否生效
    // 如果返回false,那么 void updateFill(MetaObject metaObject) 将不会被触发
    default boolean openInsertFill() {
        return true;
    }

    // 表示当前MetaObjectHandler自动更新填充是否生效
    // 如果返回false,那么 void updateFill(MetaObject metaObject) 将不会被触发
    default boolean openUpdateFill() {
        return true;
    }

    /**
     * 插入元对象字段填充（用于插入时对公共字段的填充）
     *
     * @param metaObject 元对象
     */
    void insertFill(MetaObject metaObject);

    /**
     * 更新元对象字段填充（用于更新时对公共字段的填充）
     *
     * @param metaObject 元对象
     */
    void updateFill(MetaObject metaObject);

    // ---------------------
    // 下面提供一些工具方法:
    //   比如: 通用的字段数据填充 \ 通用的字段值获取 \ 通用的获取TableInfo
    // ---------------------

    /**
     * 通用根据fieldName填充fieldVal到实体类的对应的字段上
     *
     * @param fieldName  java bean property name
     * @param fieldVal   java bean property value
     * @param metaObject meta object parameter
     */
    default MetaObjectHandler setFieldValByName(String fieldName, Object fieldVal, MetaObject metaObject) {
        // 当实体类对应的MetaObject有对应fieldName的set方法时,调用set方法为其设置fieldVal
        // 直接使用setFieldValByName(..)缺点:
        // 1. 相比于: 严格填充 -> 没有指定字段类型
        // 1. 相比于: 严格填充 -> 和@TableField.fill()没有任何关系
        // 1. 相比于: 严格填充 -> 会对实体类的字段值进行覆盖,即使该值存在
        if (Objects.nonNull(fieldVal) && metaObject.hasSetter(fieldName)) {
            metaObject.setValue(fieldName, fieldVal);
        }
        return this;
    }

    /**
     * 通用根据属性名fieldName获取字段值
     *
     * @param fieldName  java bean property name
     * @param metaObject parameter wrapper
     * @return 字段值
     */
    default Object getFieldValByName(String fieldName, MetaObject metaObject) {
        return metaObject.hasGetter(fieldName) ? metaObject.getValue(fieldName) : null;
    }

    /**
     * 根据实体类的MetaObject,拿到对应的TableInfo
     *
     * @param metaObject meta object parameter
     * @return TableInfo
     * @since 3.3.0
     */
    default TableInfo findTableInfo(MetaObject metaObject) {
        return TableInfoHelper.getTableInfo(metaObject.getOriginalObject().getClass());
    }

    // ---------------------
    // 推荐使用:严格插入填充,相比于setFieldValByName(..) -> 严格填充的特点有四个特殊点 [具体:见源代码,比较简单easy]
    // ❗️❗️❗️❗️❗️❗️
    // 1. 要求: 如果实体类中对应的字段和用户希望填充的字段名相同
    // 2. 要求: 如果实体类中对应的字段类型和用户指定字段类型相同
    // 3. 要求: 满足1和2的实体类字段,在自动插入填充时实要求标记有@TableField.fill()=FieldFill.INSERT,或者在自动更新填充时标记有@TableField.fill()=FieldFill.UPDATE
    // 4. 要求: 当前实体对象中对应的字段值为null才会进行填充
    // ---------------------

    /**
     * @param metaObject metaObject meta object parameter
     * @return this
     * @since 3.3.0
     */
    default <T, E extends T> MetaObjectHandler strictInsertFill(MetaObject metaObject, String fieldName, Class<T> fieldType, E fieldVal) {
        // 严格插入填充 -> 对数据类型进行严格检查
        return strictInsertFill(findTableInfo(metaObject), metaObject, Collections.singletonList(StrictFill.of(fieldName, fieldType, fieldVal)));
    }

    /**
     * @param metaObject metaObject meta object parameter
     * @return this
     * @since 3.3.0
     */
    default <T, E extends T> MetaObjectHandler strictInsertFill(MetaObject metaObject, String fieldName, Supplier<E> fieldVal, Class<T> fieldType) {
        // 严格插入填充 -> 对数据类型进行严格检查
        return strictInsertFill(findTableInfo(metaObject), metaObject, Collections.singletonList(StrictFill.of(fieldName, fieldVal, fieldType)));
    }

    /**
     * @param metaObject metaObject meta object parameter
     * @return this
     * @since 3.3.0
     */
    default MetaObjectHandler strictInsertFill(TableInfo tableInfo, MetaObject metaObject, List<StrictFill<?, ?>> strictFills) {
        return strictFill(true, tableInfo, metaObject, strictFills);
    }

    /**
     * @param metaObject metaObject meta object parameter
     * @return this
     * @since 3.3.0
     */
    default <T, E extends T> MetaObjectHandler strictUpdateFill(MetaObject metaObject, String fieldName, Supplier<E> fieldVal, Class<T> fieldType) {
        return strictUpdateFill(findTableInfo(metaObject), metaObject, Collections.singletonList(StrictFill.of(fieldName, fieldVal, fieldType)));
    }

    /**
     * @param metaObject metaObject meta object parameter
     * @return this
     * @since 3.3.0
     */
    default <T, E extends T> MetaObjectHandler strictUpdateFill(MetaObject metaObject, String fieldName, Class<T> fieldType, E fieldVal) {
        return strictUpdateFill(findTableInfo(metaObject), metaObject, Collections.singletonList(StrictFill.of(fieldName, fieldType, fieldVal)));
    }

    /**
     * @param metaObject metaObject meta object parameter
     * @return this
     * @since 3.3.0
     */
    default MetaObjectHandler strictUpdateFill(TableInfo tableInfo, MetaObject metaObject, List<StrictFill<?, ?>> strictFills) {
        return strictFill(false, tableInfo, metaObject, strictFills);
    }

    /**
     * 严格填充,只针对非主键的字段,只有该表注解了fill 并且 字段名和字段属性 能匹配到才会进行填充(null 值不填充)
     *
     * @param insertFill  是否验证在 insert 时填充
     * @param tableInfo   cache 缓存
     * @param metaObject  metaObject meta object parameter
     * @param strictFills 填充信息
     * @return this
     * @since 3.3.0
     */
    default MetaObjectHandler strictFill(boolean insertFill, TableInfo tableInfo, MetaObject metaObject, List<StrictFill<?, ?>> strictFills) {
        // 严格模式填充策略,默认有值不覆盖,如果提供的值为null也不填充

        // 1.
        //  insertFill 为true,表示插入填充,相应检查对应实体类是否有自动插入填充需求[即是否有字段标注为@TableField.fill()=FieldFill.INSERT],如果没有自动插入填充需求,就不允许做更改
        //  insertFill 为false,表示更新填充,相应检查对应实体类是否有自动更新填充需求[即是否有字段标注为@TableField.fill()=FieldFill.UPDATE],如果没有自动更新填充需求,就不允许做更改
        if ((insertFill && tableInfo.isWithInsertFill()) || (!insertFill && tableInfo.isWithUpdateFill())) {
            // 1.1 strictFills 就是用户期望填充的 字段\字段值\字段类型
            strictFills.forEach(i -> {
                // 1.1.1 拿到用户希望填充的字段名\字段类型进行检查
                final String fieldName = i.getFieldName();
                final Class<?> fieldType = i.getFieldType();
                // 1.1.2 要求: 如果实体类中的字段和用户希望填充的字段名相同\字段类型相同,且在自动插入填充时标记有@TableField.fill()=FieldFill.INSERT,或者在自动更新填充时标记有@TableField.fill()=FieldFill.UPDATE
                // 同时要求 MetaObject 中当前值为nul,才会进行填充
                tableInfo.getFieldList().stream()
                    .filter(j -> j.getProperty().equals(fieldName) && fieldType.equals(j.getPropertyType()) &&
                        ((insertFill && j.isWithInsertFill()) || (!insertFill && j.isWithUpdateFill()))).findFirst()
                    .ifPresent(j -> strictFillStrategy(metaObject, fieldName, i.getFieldVal()));
            });
        }
        return this;
    }

    /**
     * 填充策略,默认有值不覆盖,如果提供的值为null也不填充
     *
     * @param metaObject metaObject meta object parameter
     * @param fieldName  java bean property name
     * @param fieldVal   java bean property value of Supplier
     * @return this
     * @since 3.3.0
     */
    default MetaObjectHandler fillStrategy(MetaObject metaObject, String fieldName, Object fieldVal) {
        if (getFieldValByName(fieldName, metaObject) == null) {
            setFieldValByName(fieldName, fieldVal, metaObject);
        }
        return this;
    }

    /**
     * 严格模式填充策略,默认有值不覆盖,如果提供的值为null也不填充
     *
     * @param metaObject metaObject meta object parameter
     * @param fieldName  java bean property name
     * @param fieldVal   java bean property value of Supplier
     * @return this
     * @since 3.3.0
     */
    default MetaObjectHandler strictFillStrategy(MetaObject metaObject, String fieldName, Supplier<?> fieldVal) {
        // 严格模式填充策略,默认有值不覆盖,如果提供的值为null也不填充
        if (metaObject.getValue(fieldName) == null) {
            Object obj = fieldVal.get();
            if (Objects.nonNull(obj)) {
                metaObject.setValue(fieldName, obj);
            }
        }
        return this;
    }
}
