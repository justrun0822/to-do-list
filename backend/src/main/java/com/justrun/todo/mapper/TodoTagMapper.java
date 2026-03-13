package com.justrun.todo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.justrun.todo.entity.TodoTag;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TodoTagMapper extends BaseMapper<TodoTag> {
}
