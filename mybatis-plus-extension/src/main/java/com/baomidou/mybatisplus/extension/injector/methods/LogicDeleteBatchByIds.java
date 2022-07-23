package com.baomidou.mybatisplus.extension.injector.methods;

import com.baomidou.mybatisplus.core.enums.SqlMethod;
import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import com.baomidou.mybatisplus.core.injector.AbstractSqlInjector;
import com.baomidou.mybatisplus.core.injector.ISqlInjector;
import com.baomidou.mybatisplus.core.injector.methods.*;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.core.toolkit.sql.SqlScriptUtils;
import org.apache.ibatis.annotations.Param;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

/**
 * 试验功能,不做太复杂的功能,逻辑删除增加填充功能
 *
 * 如果想做的通用点的话,delete的时候如果是主键,在service层转换实体进行删除,这样根据主键删除的也能自动填充
 *
 * 如果是逻辑删除且标记有填充字段的情况下,以第一条记录的填充字段为准(一切以当前的时间点为基准,如果无法接受记录记录时间不准确请使用循环删除)
 * 由于本身SQL更新的限制限,这里记录集合不能为空,也不支持第一条记录删除人是A或者时间是A时间,第二条记录的时间是B时间
 * update table set (填充字段1,填充字段2,逻辑删除字段) where id in (主键1,主键2,主键3)
 * 用法:
 * <pre>
 *     使用默认deleteBatchIds方法
 *     注入方法: new LogicDeleteBatchByIds()
 * </pre>
 * <pre>
 * 自定义Mapper方法名:
 * 注入方法: new LogicDeleteBatchByIds("testDeleteBatch")
 * 增加Mapper方法: int testDeleteBatch(@Param(Constants.COLLECTION) List<Entity> entityList);
 * </pre>
 *
 * @author nieqiurong
 * @since 3.5.0
 */
public class LogicDeleteBatchByIds extends DeleteBatchByIds {
    // 使用方法
    // CustomSqlInjector.java
        // public class CustomSqlInjector extends AbstractSqlInjector {
        //     @Override
        //     public List<AbstractMethod> getMethodList() {
        //         return Stream.of(
        //             new Delete(),
        //             new DeleteBatchByIds(),
        //             new DeleteById(),
        //             new DeleteByMap(),
        //             new Insert(),
        //             new SelectBatchByIds(),
        //             new SelectById(),
        //             new SelectByMap(),
        //             new SelectCount(),
        //             new SelectList(),
        //             new SelectMaps(),
        //             new SelectMapsPage(),
        //             new SelectObjs(),
        //             new SelectOne(),
        //             new SelectPage(),
        //             new Update(),
        //             new UpdateById(),
        //             new LogicDeleteBatchByIds()  [❗️❗️❗️]
        //         ).collect(Collectors.toList());
        //     }
        // }
    // 注入到ioc容器
        // @Bean
        // public ISqlInjector sqlInjector() { [❗️❗️❗️ 注入CustomSqlInjector]
        //     return new CustomSqlInjector();
        // }
    // CustomBaseMapper.java
        // public CustomBaseMapper<T> extends BaseMapper<T>{
        //     int logicDeleteBatchByIds(@Param(Constants.COLLECTION) Collection<?> idList); // [❗️❗️❗️ 额外扩展: logicDeleteBatchByIds]
        // }
    // UserMapper.java
        // @Repository
        // public interface UserMapper extends CustomBaseMapper<User> { // [❗️❗️❗️ 使用CustomBaseMapper]
        //
        // }

    // note: 这个就是用户注入的AbstractMethod的方式

    public LogicDeleteBatchByIds() {
        super();
    }

    public LogicDeleteBatchByIds(String name) {
        super(name);
    }

    @Override
    public String logicDeleteScript(TableInfo tableInfo, SqlMethod sqlMethod) {
        // 1. 过滤有自动更新填充的字段,且非逻辑删除的字段集合
        List<TableFieldInfo> fieldInfos = tableInfo.getFieldList().stream()
            .filter(TableFieldInfo::isWithUpdateFill)
            .filter(f -> !f.isLogicDelete())
            .collect(toList());
        // 2.1 有自动更新填充的需求
        if (CollectionUtils.isNotEmpty(fieldInfos)) {
            String sqlScript = fieldInfos.stream()
                .map(i -> i.getSqlSet(COLLECTION + "[0].")).collect(joining(EMPTY));
            String sqlSet = "SET " + SqlScriptUtils.convertIf(sqlScript, "!@org.apache.ibatis.type.SimpleTypeRegistry@isSimpleType(_parameter.getClass())", true)
                + tableInfo.getLogicDeleteSql(false, false);
            return String.format(sqlMethod.getSql(), tableInfo.getTableName(), sqlSet, tableInfo.getKeyColumn(),
                SqlScriptUtils.convertForeach(
                    SqlScriptUtils.convertChoose("@org.apache.ibatis.type.SimpleTypeRegistry@isSimpleType(item.getClass())",
                        "#{item}", "#{item." + tableInfo.getKeyProperty() + "}"),
                    COLLECTION, null, "item", COMMA),
                tableInfo.getLogicDeleteSql(true, true));
        }
        // 2.2 没有自动更新填充的需求,调用  super.logicDeleteScript(tableInfo, sqlMethod)
        else {
            return super.logicDeleteScript(tableInfo, sqlMethod);
        }
    }


}
