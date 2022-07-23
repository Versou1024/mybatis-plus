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
package com.baomidou.mybatisplus.core.toolkit.support;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

/**
 * @author miemie
 * @since 2018-12-30
 */
@Data
@AllArgsConstructor
public class ColumnCache implements Serializable {
    // 位于: com.baomidou.mybatisplus.core.toolkit.support = core项目下的toolkit.support包下

    private static final long serialVersionUID = -4586291538088403456L;

    // 使用时的列名 column
    // 1. @TableField.value属性 > 属性名 column = @TableField.value > 属性名
    // 2. tableInfo.isUnderCamel()开启 -> 驼峰转下划线 column = StringUtils.camelToUnderline(column)
    // 3. dbConfig.isCapitalMode()开启 -> 全部大写模式 column = column.toUpperCase()
    // 4. GlobalConfig.getColumnFormat()非空 -> 格式化模式 column = String.format(columnFormat, column)
    private String column;

    // 查询 column
    // 对应 TableFieldInfo.sqlSelect
    // 当注入的方法上没有指定使用ResultMap [@TableName.resultMap为空],并且没有有要求自动初始化ResultMap [@TableName.autoResultMap()为false]
    // 并且property和column需要使用AS关键字映射 [即property和column a:在驼峰命名转下划线下不相等  b:没有开启驼峰命名转换下不相等]
    // 此刻: sqlSelect = "as" + 需要转换过来的属性名
    private String columnSelect;

    // 列对应的 mapping
    // 对应 TableFieldInfo.mapping值
    // 当TableFieldInfo.el表达式"userName,javaType=String,jdbcType=VARCHAR,typeHandler=StringTypeHandler"为例
    // 相应的 mapping 就是 "javaType=String,jdbcType=VARCHAR,typeHandler=StringTypeHandler" 部分哦
    private String mapping;

    public ColumnCache(String column, String columnSelect) {
        this.column = column;
        this.columnSelect = columnSelect;
    }
}
