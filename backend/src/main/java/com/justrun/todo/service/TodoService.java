package com.justrun.todo.service;

import com.justrun.todo.common.PageResponse;
import com.justrun.todo.dto.todo.TodoBulkRequest;
import com.justrun.todo.dto.todo.TodoBulkStatusRequest;
import com.justrun.todo.dto.todo.TodoCreateRequest;
import com.justrun.todo.dto.todo.TodoDashboardVO;
import com.justrun.todo.dto.todo.TodoImportRequest;
import com.justrun.todo.dto.todo.TodoNotificationVO;
import com.justrun.todo.dto.todo.TodoQueryRequest;
import com.justrun.todo.dto.todo.TodoRecycleVO;
import com.justrun.todo.dto.todo.TodoReorderRequest;
import com.justrun.todo.dto.todo.TodoStatsVO;
import com.justrun.todo.dto.todo.TodoSubtaskCreateRequest;
import com.justrun.todo.dto.todo.TodoSubtaskUpdateRequest;
import com.justrun.todo.dto.todo.TodoSubtaskVO;
import com.justrun.todo.dto.todo.TodoUpdateRequest;
import com.justrun.todo.dto.todo.TodoVO;

import java.util.List;

public interface TodoService {
    TodoVO create(TodoCreateRequest request);

    TodoVO update(Long id, TodoUpdateRequest request);

    void delete(Long id);

    TodoVO detail(Long id);

    PageResponse<TodoVO> page(TodoQueryRequest request);

    TodoVO updateStatus(Long id, boolean done, Integer expectedVersion);

    int clearCompleted();

    TodoStatsVO stats();

    int bulkDelete(TodoBulkRequest request);

    int bulkUpdateStatus(TodoBulkStatusRequest request);

    void reorder(TodoReorderRequest request);

    TodoSubtaskVO createSubtask(Long taskId, TodoSubtaskCreateRequest request);

    TodoSubtaskVO updateSubtask(Long taskId, Long subtaskId, TodoSubtaskUpdateRequest request);

    void deleteSubtask(Long taskId, Long subtaskId);

    List<TodoRecycleVO> recycleList();

    TodoVO restoreRecycle(Long recycleId);

    void purgeRecycle(Long recycleId);

    List<String> groups();

    List<String> tags();

    TodoDashboardVO dashboard();

    List<TodoNotificationVO> notifications(int windowMinutes);

    List<TodoVO> exportAll();

    int importAll(TodoImportRequest request);
}
