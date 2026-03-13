package com.justrun.todo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.justrun.todo.entity.TodoTask;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TodoTaskMapper extends BaseMapper<TodoTask> {
}
