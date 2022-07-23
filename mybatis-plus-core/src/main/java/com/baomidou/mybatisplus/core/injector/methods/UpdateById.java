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
package com.baomidou.mybatisplus.core.injector.methods;

import com.baomidou.mybatisplus.core.enums.SqlMethod;
import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlSource;

/**
 * 根据 ID 更新有值字段
 *
 * @author hubin
 * @since 2018-04-06
 */
public class UpdateById extends AbstractMethod {

    public UpdateById() {
        super(SqlMethod.UPDATE_BY_ID.getMethod());
    }

    /**
     * @since 3.5.0
     * @param name 方法名
     */
    public UpdateById(String name) {
        super(name);
    }

    @Override
    public MappedStatement injectMappedStatement(Class<?> mapperClass, Class<?> modelClass, TableInfo tableInfo) {
        // 对应: BaseMapper#updateById(@Param(Constants.ENTITY) T entity) -> 形参名为"et"
        // 对应: SqlMethod.UPDATE_BY_ID
        SqlMethod sqlMethod = SqlMethod.UPDATE_BY_ID;
        // 1. sql更新脚本结构:
        // <script>
        //      UPDATE %s %s WHERE %s=#{%s} %s
        //  </script>

        // 1.1
            // <if test=" et != null and et[property]  != null and et[property] != ''">     property为标记有@Versiond的字段名
            //      AND column_name = #{MP_OPTLOCK_VERSION_ORIGINAL}                        MP_OPTLOCK_VERSION_ORIGINAL为乐观锁字段哦
            // </if>
            // AND deleted = 0
        final String additional = optlockVersion(tableInfo) + tableInfo.getLogicDeleteSql(true, true);
        // 1.2
        // 第1个%s: tableInfo.getTableName() -> 表名
        // 第2个%s: set 关键字的部分 ->
            //  <set>
            //     由于et必不为空,因此不需要使用 <if test = 'et != null'> <if> 来包裹下面几段话哦
            //     <if test='et.property1 != null'> column1 = #{et.property1} </if>                             当updateStrategy为FieldStrategy.NOE_NULL时
            //     <if test='et.property2 != null and et.property2 != '' '> column2 = #{et.property2} </if>     当updateStrategy为FieldStrategy.NOE_EMPTY时
            //     <if test='et.property3 != null'> column3 = column3 + 1 </if>                                 当@TableField.udpate() = "%s+1" 时
            //     <if test='et.property4 != null'> column4 = now() </if>                                       当@TableField.udpate() = "now()" 时
            //  </set>
        // 第3个%s: tableInfo.getKeyColumn() -> 主键列名
        // 第4个%s: "et." + tableInfo.getKeyProperty() -> #{主键属性} -> 拿到传入的T实体类的id值
        // 第5个%s: additional -> 附加的where筛选条件 [乐观锁 + 逻辑删除]
            // <if test=" et != null and et[property]  != null and et[property] != ''">     property为标记有@Versiond的字段名
            //      AND column_name = #{MP_OPTLOCK_VERSION_ORIGINAL}                        MP_OPTLOCK_VERSION_ORIGINAL为乐观锁字段哦
            // </if>
            // AND deleted = 0
        String sql = String.format(sqlMethod.getSql(), tableInfo.getTableName(),
            sqlSet(tableInfo.isWithLogicDelete(), false, tableInfo, false, ENTITY, ENTITY_DOT),
            tableInfo.getKeyColumn(), ENTITY_DOT + tableInfo.getKeyProperty(), additional);
        SqlSource sqlSource = languageDriver.createSqlSource(configuration, sql, modelClass);
        return addUpdateMappedStatement(mapperClass, modelClass, getMethod(sqlMethod), sqlSource);
    }
}
