package com.justrun.todo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("todo_task_tag")
public class TodoTaskTag {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private Long tagId;
}
