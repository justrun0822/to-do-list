package com.justrun.todo.dto.todo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Data
public class TodoQueryRequest {
    @Min(value = 1, message = "页码不能小于 1")
    private long pageNum = 1;

    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = 100, message = "每页条数不能大于 100")
    private long pageSize = 10;

    @Min(value = 0, message = "状态不合法")
    @Max(value = 1, message = "状态不合法")
    private Integer status;

    @Min(value = 1, message = "优先级不合法")
    @Max(value = 3, message = "优先级不合法")
    private Integer priority;

    private String groupName;

    private String tag;

    private String keyword;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime dueStart;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime dueEnd;

    private String sortBy;

    private String sortOrder;
}
