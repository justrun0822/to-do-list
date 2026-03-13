package com.justrun.todo.controller;

import com.justrun.todo.common.ApiResponse;
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
import com.justrun.todo.service.TodoService;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/todos")
public class TodoController {

    private final TodoService todoService;

    public TodoController(TodoService todoService) {
        this.todoService = todoService;
    }

    @PostMapping
    public ApiResponse<TodoVO> create(@Valid @RequestBody TodoCreateRequest request) {
        return ApiResponse.success(todoService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<TodoVO> update(@PathVariable Long id, @Valid @RequestBody TodoUpdateRequest request) {
        return ApiResponse.success(todoService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        todoService.delete(id);
        return ApiResponse.success("删除成功", null);
    }

    @GetMapping("/{id}")
    public ApiResponse<TodoVO> detail(@PathVariable Long id) {
        return ApiResponse.success(todoService.detail(id));
    }

    @GetMapping
    public ApiResponse<PageResponse<TodoVO>> page(@Valid @ModelAttribute TodoQueryRequest request) {
        return ApiResponse.success(todoService.page(request));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<TodoVO> updateStatus(
            @PathVariable Long id,
            @RequestParam boolean done,
            @RequestParam(required = false) Integer expectedVersion) {
        return ApiResponse.success(todoService.updateStatus(id, done, expectedVersion));
    }

    @DeleteMapping("/completed")
    public ApiResponse<Map<String, Integer>> clearCompleted() {
        int removed = todoService.clearCompleted();
        return ApiResponse.success(Map.of("removed", removed));
    }

    @PostMapping("/bulk/delete")
    public ApiResponse<Map<String, Integer>> bulkDelete(@Valid @RequestBody TodoBulkRequest request) {
        int affected = todoService.bulkDelete(request);
        return ApiResponse.success(Map.of("affected", affected));
    }

    @PostMapping("/bulk/status")
    public ApiResponse<Map<String, Integer>> bulkStatus(@Valid @RequestBody TodoBulkStatusRequest request) {
        int affected = todoService.bulkUpdateStatus(request);
        return ApiResponse.success(Map.of("affected", affected));
    }

    @PatchMapping("/reorder")
    public ApiResponse<Void> reorder(@Valid @RequestBody TodoReorderRequest request) {
        todoService.reorder(request);
        return ApiResponse.success("排序已更新", null);
    }

    @PostMapping("/{id}/subtasks")
    public ApiResponse<TodoSubtaskVO> createSubtask(@PathVariable Long id, @Valid @RequestBody TodoSubtaskCreateRequest request) {
        return ApiResponse.success(todoService.createSubtask(id, request));
    }

    @PatchMapping("/{id}/subtasks/{subtaskId}")
    public ApiResponse<TodoSubtaskVO> updateSubtask(
            @PathVariable Long id,
            @PathVariable Long subtaskId,
            @Valid @RequestBody TodoSubtaskUpdateRequest request) {
        return ApiResponse.success(todoService.updateSubtask(id, subtaskId, request));
    }

    @DeleteMapping("/{id}/subtasks/{subtaskId}")
    public ApiResponse<Void> deleteSubtask(@PathVariable Long id, @PathVariable Long subtaskId) {
        todoService.deleteSubtask(id, subtaskId);
        return ApiResponse.success("子任务删除成功", null);
    }

    @GetMapping("/recycle")
    public ApiResponse<List<TodoRecycleVO>> recycle() {
        return ApiResponse.success(todoService.recycleList());
    }

    @PostMapping("/recycle/{recycleId}/restore")
    public ApiResponse<TodoVO> restore(@PathVariable Long recycleId) {
        return ApiResponse.success(todoService.restoreRecycle(recycleId));
    }

    @DeleteMapping("/recycle/{recycleId}")
    public ApiResponse<Void> purgeRecycle(@PathVariable Long recycleId) {
        todoService.purgeRecycle(recycleId);
        return ApiResponse.success("回收站记录已删除", null);
    }

    @GetMapping("/groups")
    public ApiResponse<List<String>> groups() {
        return ApiResponse.success(todoService.groups());
    }

    @GetMapping("/tags")
    public ApiResponse<List<String>> tags() {
        return ApiResponse.success(todoService.tags());
    }

    @GetMapping("/notifications")
    public ApiResponse<List<TodoNotificationVO>> notifications(@RequestParam(defaultValue = "1440") int windowMinutes) {
        return ApiResponse.success(todoService.notifications(windowMinutes));
    }

    @GetMapping("/dashboard")
    public ApiResponse<TodoDashboardVO> dashboard() {
        return ApiResponse.success(todoService.dashboard());
    }

    @GetMapping("/export")
    public ApiResponse<List<TodoVO>> exportAll() {
        return ApiResponse.success(todoService.exportAll());
    }

    @PostMapping("/import")
    public ApiResponse<Map<String, Integer>> importAll(@Valid @RequestBody TodoImportRequest request) {
        int imported = todoService.importAll(request);
        return ApiResponse.success(Map.of("imported", imported));
    }

    @GetMapping("/stats")
    public ApiResponse<TodoStatsVO> stats() {
        return ApiResponse.success(todoService.stats());
    }
}
