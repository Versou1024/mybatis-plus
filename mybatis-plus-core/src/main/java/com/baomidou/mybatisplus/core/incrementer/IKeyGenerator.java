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
package com.baomidou.mybatisplus.core.incrementer;


import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.annotation.KeySequence;

/**
 * 表主键生成器接口 (sql)
 *
 * @author hubin
 * @since 2017-05-08
 */
public interface IKeyGenerator {

    // 例如: 主要是和@TableId中的type = IdType.INPUT搭配,并且需要指定dbType
    //  @KeySequence(value = "SEQ_ORACLE_STRING_KEY", dbType = DbType.ORACLE)
    //  public class YourEntity {
    //
    //      @TableId(value = "ID_STR", type = IdType.INPUT)
    //      private String idStr;
    //
    //  }
    // 导入 OracleKeyGenerator
    // @Bean
    // public IKeyGenerator keyGenerator() {
    //     // 选择你要使用的KeyGenerator
    //     return new OracleKeyGenerator();
    // }
    // OracleKeyGenerator 的类
    // public class OracleKeyGenerator implements IKeyGenerator {
    //
    //     @Override
    //     public String executeSql(String incrementerName) {
    //         return "SELECT " + incrementerName + ".NEXTVAL FROM DUAL";
    //     }
    //
    //     @Override
    //     public DbType dbType() {
    //         return DbType.ORACLE;
    //     }
    // }

    // @KeySequence查找IKeyGenerator的规则:
    // 1. 当ioc容器存在多个IKeyGenerator的实现类时
    //      1.1 要求ioc容器存在IKeyGenerator,且对应的IKeyGenerator#dbType()和@KeySequence.dbType()相等哦
    // 2. 如果容器ioc只有一个IKeyGenerator,或者有多个IKeyGenerator但是都无法和@KeySequence.dbType()匹配
    //      2.1 直接使用ioc容器查找出来的集合中第一个IKeyGenerator
    // 这里最终会查找到OracleKeyGenerator,在使用时，会将@Sequence.value()传递到IKeyGenerator#executeSql(String)中
    // 调用SELECT my_oracle_sequence.NEXTVAL FROM DUAL来获取一个序列填充到主键中。

    // note: 如果你是MySQL，MP没有提供默认支持的KeyGenerator可以使用

    // 使用出:
    // 请见 -> TableInfoHelper#genKeyGenerator(..) -> 最终是附加到 mybatis 的 KeyGenerator


    // 表主键生成器接口 (sql)

    /**
     * 执行 key 生成 SQL
     *
     * @param incrementerName 序列名称(对应类上注解 {@link KeySequence#value()} 的值)
     * @return sql
     */
    String executeSql(String incrementerName);
    // 执行 key 生成 SQL
    // Params:
    // incrementerName – 序列名称(对应类上注解 @KeySequence.value() 的值)

    /**
     * 数据库类型
     */
    DbType dbType();
}
