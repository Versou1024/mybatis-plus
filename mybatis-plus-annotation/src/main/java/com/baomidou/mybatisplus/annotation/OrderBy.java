package com.baomidou.mybatisplus.annotation;

import java.lang.annotation.*;

/**
 * 自动排序，用法与SpringDtaJpa的OrderBy类似
 * 在执行MybatisPlus的方法selectList(),Page()等非手写查询时自动带上.
 * @author Dervish
 * @date 2021-04-13
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.ANNOTATION_TYPE})
public @interface OrderBy {
    // 在执行MybatisPlus的方法selectList(),Page()等非手写查询时自动带上.
    // 描述：内置 SQL 默认指定排序，优先级低于 wrapper 条件查询
    // 属性	    类型	    必须指定	    默认值	        描述
    // isDesc	boolean	否	        是	            是否倒序查询
    // sort	    short	否	        Short.MAX_VALUE	数字越小越靠前

    /**
     * 默认倒序，设置 true 顺序
     */
    boolean asc() default false;

    @Deprecated
    boolean isDesc() default true;

    /**
     * 数字越小越靠前
     */
    short sort() default Short.MAX_VALUE;
    // 场景:
    // 比如要想实现 order columnA,columnB,columnC; -> 对应 propertyA,propertyB,propertyC
    // 必须要求 -> 对应属性propertyA,propertyB,propertyC标有@OrderBy,且sort()值越小在order by关键字后排序越靠前

}
