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

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.baomidou.mybatisplus.annotation.IEnum;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.ExceptionUtils;
import com.baomidou.mybatisplus.core.toolkit.ReflectionKit;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.ReflectorFactory;
import org.apache.ibatis.reflection.invoker.Invoker;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自定义枚举属性转换器
 *
 * @author hubin
 * @since 2017-10-11
 */
public class MybatisEnumTypeHandler<E extends Enum<E>> extends BaseTypeHandler<E> {
    // 位于: com.baomidou.mybatisplus.core.handlers = core项目下handler

    // 命名:
    // Mybatis Enum TypeHandler = Mybatis提供对枚举的属性转换器

    // 作用:
    // 为指定typeEnumsPackage时下的所有枚举类,且实现了IEnum或者有@EnumValue标注的注解的枚举类
    // 去TypeHandlerRegistry注册响应的类型转换器


    // 全局缓存: TABLE_METHOD_OF_ENUM_TYPES以枚举类的className为key,对应@EnumValue标注的字段名为value
    private static final Map<String, String> TABLE_METHOD_OF_ENUM_TYPES = new ConcurrentHashMap<>();
    private static final ReflectorFactory REFLECTOR_FACTORY = new DefaultReflectorFactory();

    // 实现了IEnum或者有@EnumValue标注的注解的枚举类
    private final Class<E> enumClassType;

    // Xxx就是IEnum中的value,或者@EnumValue标注的字段的字段名 ->
    // 对应的getInvoker即getXxx()方法在IEnum就是getValue,在@EnumValue就是字段的get方法
    // 对应的propertyType在IEnum就是getValue的返回值类型对应的就是IEnum的参数类型,在@EnumValue就是字段类型

    // 枚举类中对应的的getXxx方法的返回值类型
    private final Class<?> propertyType;
    // 枚举类中对应的的getXxx方法
    private final Invoker getInvoker;

    // 为指定typeEnumsPackage时下的所有枚举类,且实现了IEnum或者有@EnumValue标注的注解的枚举类
    // 去TypeHandlerRegistry注册响应的类型转换器
    public MybatisEnumTypeHandler(Class<E> enumClassType) {
        if (enumClassType == null) {
            throw new IllegalArgumentException("Type argument cannot be null");
        }
        // 1. 指定: 实现了IEnum或者有@EnumValue标注的注解的枚举类
        this.enumClassType = enumClassType;
        MetaClass metaClass = MetaClass.forClass(enumClassType, REFLECTOR_FACTORY);
        // 2. 确定 TypeHandler 需要调用的getXxx的Xxx名 ->
        // 2.1 默认就是IEnum接口下的getValue()的value名
        // 2.2 当enumClassType没有实现IEnum接口时
        // 上述说明 -> ❗️❗️❗️ IEnum 优先级大于 @EnumValue -> 其实Spring中也通常是同功能下时接口大于注解的优先级
        String name = "value";
        if (!IEnum.class.isAssignableFrom(enumClassType)) {
            name = findEnumValueFieldName(this.enumClassType).orElseThrow(() -> new IllegalArgumentException(String.format("Could not find @EnumValue in Class: %s.", this.enumClassType.getName())));
        }
        // 2. 获取枚举类中对应的的getXxx方法的返回值类型
        this.propertyType = ReflectionKit.resolvePrimitiveIfNecessary(metaClass.getGetterType(name));
        // 3. 获取枚举类中对应的的getXxx方法
        this.getInvoker = metaClass.getGetInvoker(name);
    }

    /**
     * 查找标记标记EnumValue字段
     *
     * @param clazz class
     * @return EnumValue字段
     * @since 3.3.1
     */
    public static Optional<String> findEnumValueFieldName(Class<?> clazz) {
        // 查找标记@EnumValue的字段

        // 1. clazz不为空,至少为枚举类型
        if (clazz != null && clazz.isEnum()) {
            String className = clazz.getName();
            // 2. 从TABLE_METHOD_OF_ENUM_TYPES以className为key,查找是否存在对应的缓存
            // 3. 缓存不存在,执行 findEnumValueAnnotationField(clazz) -> 查找有@EnumValue的
            return Optional.ofNullable(CollectionUtils.computeIfAbsent(TABLE_METHOD_OF_ENUM_TYPES, className, key -> {
                Optional<Field> fieldOptional = findEnumValueAnnotationField(clazz);
                return fieldOptional.map(Field::getName).orElse(null);
            }));
        }
        return Optional.empty();
    }

    private static Optional<Field> findEnumValueAnnotationField(Class<?> clazz) {
        // 遍历clazz中所有的字段field,找到第一个标记有@EnumValue的字段
        // note: 意思是多个标记有@Enumvalue的字段只有其中一个会生效哦

        // 链式调用:值得借鉴
        return Arrays.stream(clazz.getDeclaredFields()).filter(field -> field.isAnnotationPresent(EnumValue.class)).findFirst();
    }

    /**
     * 判断是否为MP枚举处理
     *
     * @param clazz class
     * @return 是否为MP枚举处理
     * @since 3.3.1
     */
    public static boolean isMpEnums(Class<?> clazz) {
        // 检查是否为MP枚举处理 -> clazz本身是枚举类,实现了IEnum或者有@EnumValue注解在类上 -> 返回true
        return clazz != null && clazz.isEnum() && (IEnum.class.isAssignableFrom(clazz) || findEnumValueFieldName(clazz).isPresent());
    }

    // ---------------------
    // 实现 BaseTypeHandler的系列接口
    // ---------------------


    @SuppressWarnings("Duplicates")
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, E parameter, JdbcType jdbcType)
        throws SQLException {
        if (jdbcType == null) {
            ps.setObject(i, this.getValue(parameter));
        } else {
            // see r3589
            ps.setObject(i, this.getValue(parameter), jdbcType.TYPE_CODE);
        }
    }

    @Override
    public E getNullableResult(ResultSet rs, String columnName) throws SQLException {
        Object value = rs.getObject(columnName, this.propertyType);
        if (null == value && rs.wasNull()) {
            return null;
        }
        return this.valueOf(value);
    }

    @Override
    public E getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        Object value = rs.getObject(columnIndex, this.propertyType);
        if (null == value && rs.wasNull()) {
            return null;
        }
        return this.valueOf(value);
    }

    @Override
    public E getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        Object value = cs.getObject(columnIndex, this.propertyType);
        if (null == value && cs.wasNull()) {
            return null;
        }
        return this.valueOf(value);
    }

    private E valueOf(Object value) {
        // ❗️❗️❗️ -> 注意: 两点
        // a: 数据库中的值如果在枚举中找不到不会报错会给出默认值null
        // b: 枚举常量中如果多个枚举常量存入数据库的值是一样的,那么ordinal高的会提前展示出来

        // 1. 拿到枚举类enumClassType中所有的枚举常量es
        E[] es = this.enumClassType.getEnumConstants();
        // 2. 遍历所有的枚举常量: 将获取当前遍历的枚举常量存入数据库的值 pk 当前数据库中取出来的值value 是否相等
        // 相等就立即返回哦
        return Arrays.stream(es).filter((e) -> equalsValue(value, getValue(e))).findAny().orElse(null);
    }

    /**
     * 值比较
     *
     * @param sourceValue 数据库字段值
     * @param targetValue 当前枚举属性值
     * @return 是否匹配
     * @since 3.3.0
     */
    protected boolean equalsValue(Object sourceValue, Object targetValue) {
        String sValue = StringUtils.toStringTrim(sourceValue);
        String tValue = StringUtils.toStringTrim(targetValue);
        if (sourceValue instanceof Number && targetValue instanceof Number
            && new BigDecimal(sValue).compareTo(new BigDecimal(tValue)) == 0) {
            return true;
        }
        return Objects.equals(sValue, tValue);
    }

    private Object getValue(Object object) {
        try {
            // 调用其枚举类的getXxx()方法,获取枚举对象要存入数据库的值
            return this.getInvoker.invoke(object, new Object[0]);
        } catch (ReflectiveOperationException e) {
            throw ExceptionUtils.mpe(e);
        }
    }
}
