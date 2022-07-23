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
package com.baomidou.mybatisplus.annotation;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;

import java.time.LocalDateTime;

/**
 * 字段填充策略枚举类
 *
 * <p>
 * 判断注入的 insert 和 update 的 sql 脚本是否在对应情况下忽略掉字段的 if 标签生成
 * <if test="...">......</if>
 * 判断优先级比 {@link FieldStrategy} 高
 * </p>
 *
 * @author hubin
 * @since 2017-06-27
 */
public enum FieldFill {
    // 理解其作用: FieldFill

    // 使用:
    // PO类中
    //      @TableField(fill = FieldFill.INSERT_UPDATE)
    //      private String operUser;
    //
    //      @TableField(fill = FieldFill.INSERT)
    //      private LocalDateTime gmtCreate;
    //
    //      @TableField(fill = FieldFill.INSERT_UPDATE)
    //      private LocalDateTime gmtModified;
    //
    // 处理器
    //      @Slf4j
    //      @Component
    //      public class MyMetaObjectHandler implements MetaObjectHandler {
    //
    //          /**
    //           * 插入时执行填充逻辑
    //           *
    //           * @param metaObject
    //           */
    //          @Override
    //          public void insertFill(MetaObject metaObject) {
    //              log.info("start insert fill ....");
    //              this.strictInsertFill(metaObject, "gmtCreate", LocalDateTime.class, LocalDateTime.now());     // 自动填充插入时间
    //              this.strictInsertFill(metaObject, "gmtModified", LocalDateTime.class, LocalDateTime.now());   // 自动填充更新时间
    //              this.strictInsertFill(metaObject, "operUser", String.class, "pengDong");                      // 自动填充操作人
    //          }
    //
    //          /**
    //           * 更新时执行填充逻辑
    //           *
    //           * @param metaObject
    //           */
    //          @Override
    //          public void updateFill(MetaObject metaObject) {
    //              log.info("start update fill ....");
    //              this.strictUpdateFill(metaObject, "gmtModified", LocalDateTime.class, LocalDateTime.now());   // 自动填充更新时间
    //              this.strictUpdateFill(metaObject, "operUser", String.class, "pengDong");                      // 自动更新操作人
    //          }
    //      }

    // note:
    //      填充原理是直接给entity的属性设置值!!!，自定义语句时无效。
    //      注解则是指定该属性在对应情况下必有值,如果无值则入库会是null
    //      MetaObjectHandler提供的默认方法的策略均为:如果属性有值则不覆盖,如果填充值为null则不填充
    //      字段必须声明TableField注解,属性fill选择对应策略,该声明告知Mybatis-Plus需要预留注入SQL字段
    //      填充处理器MyMetaObjectHandler在 Spring Boot 中需要声明@Component或@Bean注入
    //      要想根据注解FieldFill.xxx和字段名以及字段类型来区分必须使用父类的strictInsertFill或者strictUpdateFill方法
    //      不需要根据任何来区分可以使用父类的fillStrategy方法


    /**
     * 默认不处理
     */
    DEFAULT,
    /**
     * 插入时填充字段
     */
    INSERT,
    /**
     * 更新时填充字段
     */
    UPDATE,
    /**
     * 插入和更新时填充字段
     */
    INSERT_UPDATE
}
