package com.justrun.todo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.justrun.todo.common.PageResponse;
import com.justrun.todo.common.UserContext;
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
import com.justrun.todo.entity.TodoRecycleBin;
import com.justrun.todo.entity.TodoSubtask;
import com.justrun.todo.entity.TodoTag;
import com.justrun.todo.entity.TodoTask;
import com.justrun.todo.entity.TodoTaskTag;
import com.justrun.todo.enums.TodoStatus;
import com.justrun.todo.exception.BizException;
import com.justrun.todo.mapper.TodoRecycleBinMapper;
import com.justrun.todo.mapper.TodoSubtaskMapper;
import com.justrun.todo.mapper.TodoTagMapper;
import com.justrun.todo.mapper.TodoTaskMapper;
import com.justrun.todo.mapper.TodoTaskTagMapper;
import com.justrun.todo.service.TodoService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TodoServiceImpl implements TodoService {

    private static final String RECUR_NONE = "NONE";
    private static final String RECUR_DAILY = "DAILY";
    private static final String RECUR_WEEKLY = "WEEKLY";

    private final TodoTaskMapper todoTaskMapper;
    private final TodoTagMapper todoTagMapper;
    private final TodoTaskTagMapper todoTaskTagMapper;
    private final TodoSubtaskMapper todoSubtaskMapper;
    private final TodoRecycleBinMapper recycleBinMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public TodoServiceImpl(
            TodoTaskMapper todoTaskMapper,
            TodoTagMapper todoTagMapper,
            TodoTaskTagMapper todoTaskTagMapper,
            TodoSubtaskMapper todoSubtaskMapper,
            TodoRecycleBinMapper recycleBinMapper,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper) {
        this.todoTaskMapper = todoTaskMapper;
        this.todoTagMapper = todoTagMapper;
        this.todoTaskTagMapper = todoTaskTagMapper;
        this.todoSubtaskMapper = todoSubtaskMapper;
        this.recycleBinMapper = recycleBinMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public TodoVO create(TodoCreateRequest request) {
        Long userId = requiredUserId();
        TodoTask task = new TodoTask();
        task.setUserId(userId);
        task.setTitle(request.getTitle().trim());
        task.setDescription(request.getDescription());
        task.setPriority(request.getPriority());
        task.setGroupName(cleanGroupName(request.getGroupName()));
        task.setStatus(TodoStatus.PENDING);
        task.setDueAt(request.getDueAt());
        task.setSortOrder(nextSortOrder(userId));
        task.setRecurringType(normalizeRecurringType(request.getRecurringType()));
        task.setRecurringInterval(safeRecurringInterval(request.getRecurringInterval()));
        task.setVersion(1);
        todoTaskMapper.insert(task);

        saveTaskTags(userId, task.getId(), request.getTags());
        evictStatsCache(userId);
        return detail(task.getId());
    }

    @Override
    public TodoVO update(Long id, TodoUpdateRequest request) {
        TodoTask task = getOwnedTask(id);
        assertVersion(task, request.getExpectedVersion());

        if (StringUtils.hasText(request.getTitle())) task.setTitle(request.getTitle().trim());
        if (request.getDescription() != null) task.setDescription(request.getDescription());
        if (request.getStatus() != null) task.setStatus(request.getStatus());
        if (request.getPriority() != null) task.setPriority(request.getPriority());
        if (request.getGroupName() != null) task.setGroupName(cleanGroupName(request.getGroupName()));
        if (request.getDueAt() != null) task.setDueAt(request.getDueAt());
        if (Boolean.TRUE.equals(request.getClearDueAt())) task.setDueAt(null);
        if (request.getRecurringType() != null) task.setRecurringType(normalizeRecurringType(request.getRecurringType()));
        if (request.getRecurringInterval() != null) task.setRecurringInterval(safeRecurringInterval(request.getRecurringInterval()));

        bumpVersion(task);
        todoTaskMapper.updateById(task);

        if (request.getTags() != null) saveTaskTags(task.getUserId(), task.getId(), request.getTags());

        evictStatsCache(task.getUserId());
        return detail(id);
    }

    @Override
    public void delete(Long id) {
        TodoTask task = getOwnedTask(id);
        archiveBeforeDelete(task);
        removeTaskWithRelations(task.getId());
        evictStatsCache(task.getUserId());
    }

    @Override
    public TodoVO detail(Long id) {
        TodoTask task = getOwnedTask(id);
        return enrichTodos(List.of(task)).get(0);
    }

    @Override
    public PageResponse<TodoVO> page(TodoQueryRequest request) {
        Long userId = requiredUserId();

        Set<Long> tagTaskIds = resolveTaskIdsByTag(userId, request.getTag());
        if (StringUtils.hasText(request.getTag()) && tagTaskIds.isEmpty()) {
            return new PageResponse<>(request.getPageNum(), request.getPageSize(), 0, Collections.emptyList());
        }

        LambdaQueryWrapper<TodoTask> wrapper = new LambdaQueryWrapper<TodoTask>()
                .eq(TodoTask::getUserId, userId)
                .eq(request.getStatus() != null, TodoTask::getStatus, request.getStatus())
                .eq(request.getPriority() != null, TodoTask::getPriority, request.getPriority())
                .eq(StringUtils.hasText(request.getGroupName()), TodoTask::getGroupName, request.getGroupName())
                .ge(request.getDueStart() != null, TodoTask::getDueAt, request.getDueStart())
                .le(request.getDueEnd() != null, TodoTask::getDueAt, request.getDueEnd())
                .in(StringUtils.hasText(request.getTag()), TodoTask::getId, tagTaskIds)
                .and(StringUtils.hasText(request.getKeyword()), w -> w.like(TodoTask::getTitle, request.getKeyword()).or().like(TodoTask::getDescription, request.getKeyword()));

        applySort(wrapper, request.getSortBy(), request.getSortOrder());

        Page<TodoTask> page = todoTaskMapper.selectPage(new Page<>(request.getPageNum(), request.getPageSize()), wrapper);
        List<TodoVO> records = enrichTodos(page.getRecords());
        return new PageResponse<>(page.getCurrent(), page.getSize(), page.getTotal(), records);
    }

    @Override
    public TodoVO updateStatus(Long id, boolean done, Integer expectedVersion) {
        TodoTask task = getOwnedTask(id);
        assertVersion(task, expectedVersion);
        task.setStatus(done ? TodoStatus.DONE : TodoStatus.PENDING);
        bumpVersion(task);
        todoTaskMapper.updateById(task);

        if (done) generateNextRecurringTaskIfNeeded(task);

        evictStatsCache(task.getUserId());
        return detail(id);
    }

    @Override
    public int clearCompleted() {
        Long userId = requiredUserId();
        List<TodoTask> tasks = todoTaskMapper.selectList(new LambdaQueryWrapper<TodoTask>()
                .eq(TodoTask::getUserId, userId)
                .eq(TodoTask::getStatus, TodoStatus.DONE));
        tasks.forEach(task -> {
            archiveBeforeDelete(task);
            removeTaskWithRelations(task.getId());
        });
        evictStatsCache(userId);
        return tasks.size();
    }

    @Override
    public TodoStatsVO stats() {
        Long userId = requiredUserId();
        String cacheKey = statsKey(userId);
        String cacheValue = redisTemplate.opsForValue().get(cacheKey);
        if (StringUtils.hasText(cacheValue)) {
            String[] arr = cacheValue.split(":");
            if (arr.length == 3) return new TodoStatsVO(Long.parseLong(arr[0]), Long.parseLong(arr[1]), Long.parseLong(arr[2]));
        }

        long total = todoTaskMapper.selectCount(new LambdaQueryWrapper<TodoTask>().eq(TodoTask::getUserId, userId));
        long completed = todoTaskMapper.selectCount(new LambdaQueryWrapper<TodoTask>().eq(TodoTask::getUserId, userId).eq(TodoTask::getStatus, TodoStatus.DONE));
        long pending = Math.max(total - completed, 0);

        redisTemplate.opsForValue().set(cacheKey, total + ":" + completed + ":" + pending, Duration.ofMinutes(5));
        return new TodoStatsVO(total, completed, pending);
    }

    @Override
    public int bulkDelete(TodoBulkRequest request) {
        List<TodoTask> tasks = getOwnedTasks(request.getIds());
        tasks.forEach(task -> {
            archiveBeforeDelete(task);
            removeTaskWithRelations(task.getId());
        });
        evictStatsCache(requiredUserId());
        return tasks.size();
    }

    @Override
    public int bulkUpdateStatus(TodoBulkStatusRequest request) {
        List<TodoTask> tasks = getOwnedTasks(request.getIds());
        for (TodoTask task : tasks) {
            task.setStatus(request.getStatus());
            bumpVersion(task);
            todoTaskMapper.updateById(task);
            if (request.getStatus() == TodoStatus.DONE) generateNextRecurringTaskIfNeeded(task);
        }
        evictStatsCache(requiredUserId());
        return tasks.size();
    }

    @Override
    public void reorder(TodoReorderRequest request) {
        Long userId = requiredUserId();
        List<Long> orderedIds = request.getOrderedIds();
        Map<Long, TodoTask> map = todoTaskMapper.selectList(new LambdaQueryWrapper<TodoTask>()
                        .eq(TodoTask::getUserId, userId)
                        .in(TodoTask::getId, orderedIds))
                .stream().collect(Collectors.toMap(TodoTask::getId, t -> t));

        int size = orderedIds.size();
        for (int i = 0; i < size; i++) {
            TodoTask task = map.get(orderedIds.get(i));
            if (task != null) {
                task.setSortOrder(size - i);
                bumpVersion(task);
                todoTaskMapper.updateById(task);
            }
        }
    }

    @Override
    public TodoSubtaskVO createSubtask(Long taskId, TodoSubtaskCreateRequest request) {
        TodoTask task = getOwnedTask(taskId);
        TodoSubtask subtask = new TodoSubtask();
        subtask.setUserId(task.getUserId());
        subtask.setTaskId(taskId);
        subtask.setTitle(request.getTitle().trim());
        subtask.setStatus(0);
        subtask.setSortOrder(nextSubtaskSortOrder(taskId));
        todoSubtaskMapper.insert(subtask);
        return toSubtaskVO(subtask);
    }

    @Override
    public TodoSubtaskVO updateSubtask(Long taskId, Long subtaskId, TodoSubtaskUpdateRequest request) {
        getOwnedTask(taskId);
        TodoSubtask subtask = getOwnedSubtask(taskId, subtaskId);
        if (StringUtils.hasText(request.getTitle())) subtask.setTitle(request.getTitle().trim());
        if (request.getStatus() != null) subtask.setStatus(request.getStatus());
        todoSubtaskMapper.updateById(subtask);
        return toSubtaskVO(subtask);
    }

    @Override
    public void deleteSubtask(Long taskId, Long subtaskId) {
        getOwnedTask(taskId);
        TodoSubtask subtask = getOwnedSubtask(taskId, subtaskId);
        todoSubtaskMapper.deleteById(subtask.getId());
    }

    @Override
    public List<TodoRecycleVO> recycleList() {
        Long userId = requiredUserId();
        List<TodoRecycleBin> bins = recycleBinMapper.selectList(new LambdaQueryWrapper<TodoRecycleBin>()
                .eq(TodoRecycleBin::getUserId, userId)
                .eq(TodoRecycleBin::getStatus, 0)
                .orderByDesc(TodoRecycleBin::getCreatedAt));

        List<TodoRecycleVO> result = new ArrayList<>();
        for (TodoRecycleBin bin : bins) {
            TodoVO snapshot = parseSnapshot(bin.getTaskSnapshot());
            TodoRecycleVO vo = new TodoRecycleVO();
            vo.setId(bin.getId());
            vo.setOriginalTaskId(bin.getOriginalTaskId());
            vo.setTitle(snapshot.getTitle());
            vo.setGroupName(snapshot.getGroupName());
            vo.setPriority(snapshot.getPriority());
            vo.setDueAt(snapshot.getDueAt());
            vo.setDeletedAt(bin.getCreatedAt());
            result.add(vo);
        }
        return result;
    }

    @Override
    public TodoVO restoreRecycle(Long recycleId) {
        Long userId = requiredUserId();
        TodoRecycleBin bin = recycleBinMapper.selectById(recycleId);
        if (bin == null || !Objects.equals(bin.getUserId(), userId) || !Objects.equals(bin.getStatus(), 0)) {
            throw new BizException(4044, "回收站记录不存在");
        }

        TodoVO snapshot = parseSnapshot(bin.getTaskSnapshot());
        TodoTask task = new TodoTask();
        task.setUserId(userId);
        task.setTitle(snapshot.getTitle());
        task.setDescription(snapshot.getDescription());
        task.setStatus(snapshot.getStatus());
        task.setPriority(snapshot.getPriority());
        task.setGroupName(snapshot.getGroupName());
        task.setDueAt(snapshot.getDueAt());
        task.setRecurringType(normalizeRecurringType(snapshot.getRecurringType()));
        task.setRecurringInterval(safeRecurringInterval(snapshot.getRecurringInterval()));
        task.setSortOrder(nextSortOrder(userId));
        task.setVersion(1);
        todoTaskMapper.insert(task);

        saveTaskTags(userId, task.getId(), snapshot.getTags());
        for (TodoSubtaskVO subtaskVO : snapshot.getSubtasks()) {
            TodoSubtask subtask = new TodoSubtask();
            subtask.setUserId(userId);
            subtask.setTaskId(task.getId());
            subtask.setTitle(subtaskVO.getTitle());
            subtask.setStatus(subtaskVO.getStatus());
            subtask.setSortOrder(subtaskVO.getSortOrder());
            todoSubtaskMapper.insert(subtask);
        }

        bin.setStatus(1);
        recycleBinMapper.updateById(bin);
        evictStatsCache(userId);
        return detail(task.getId());
    }

    @Override
    public void purgeRecycle(Long recycleId) {
        Long userId = requiredUserId();
        TodoRecycleBin bin = recycleBinMapper.selectById(recycleId);
        if (bin == null || !Objects.equals(bin.getUserId(), userId)) throw new BizException(4044, "回收站记录不存在");
        recycleBinMapper.deleteById(recycleId);
    }

    @Override
    public List<String> groups() {
        Long userId = requiredUserId();
        return todoTaskMapper.selectList(new LambdaQueryWrapper<TodoTask>()
                        .eq(TodoTask::getUserId, userId)
                        .isNotNull(TodoTask::getGroupName)
                        .orderByAsc(TodoTask::getGroupName))
                .stream().map(TodoTask::getGroupName).filter(StringUtils::hasText).distinct().collect(Collectors.toList());
    }

    @Override
    public List<String> tags() {
        Long userId = requiredUserId();
        return todoTagMapper.selectList(new LambdaQueryWrapper<TodoTag>()
                        .eq(TodoTag::getUserId, userId)
                        .orderByAsc(TodoTag::getName))
                .stream().map(TodoTag::getName).distinct().collect(Collectors.toList());
    }

    @Override
    public TodoDashboardVO dashboard() {
        Long userId = requiredUserId();
        List<TodoTask> all = todoTaskMapper.selectList(new LambdaQueryWrapper<TodoTask>().eq(TodoTask::getUserId, userId));
        LocalDateTime now = LocalDateTime.now();

        long total = all.size();
        long completed = all.stream().filter(t -> t.getStatus() == TodoStatus.DONE).count();
        long pending = total - completed;
        long overdue = all.stream().filter(t -> t.getStatus() == TodoStatus.PENDING && t.getDueAt() != null && t.getDueAt().isBefore(now)).count();
        long dueSoon = all.stream().filter(t -> t.getStatus() == TodoStatus.PENDING && t.getDueAt() != null && !t.getDueAt().isBefore(now) && !t.getDueAt().isAfter(now.plusHours(24))).count();

        LocalDate start = LocalDate.now().minusDays(6);
        Map<LocalDate, Long> completionMap = all.stream()
                .filter(t -> t.getStatus() == TodoStatus.DONE && t.getUpdatedAt() != null)
                .filter(t -> !t.getUpdatedAt().toLocalDate().isBefore(start))
                .collect(Collectors.groupingBy(t -> t.getUpdatedAt().toLocalDate(), Collectors.counting()));

        List<TodoDashboardVO.DailyCompletionPoint> points = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate d = start.plusDays(i);
            TodoDashboardVO.DailyCompletionPoint p = new TodoDashboardVO.DailyCompletionPoint();
            p.setDay(d.format(DateTimeFormatter.ISO_DATE));
            p.setCompleted(completionMap.getOrDefault(d, 0L));
            points.add(p);
        }

        Map<Integer, Long> priorityDist = all.stream().collect(Collectors.groupingBy(TodoTask::getPriority, Collectors.counting()));

        TodoDashboardVO vo = new TodoDashboardVO();
        vo.setTotal(total);
        vo.setCompleted(completed);
        vo.setPending(pending);
        vo.setOverdue(overdue);
        vo.setDueSoon(dueSoon);
        vo.setCompletedLast7Days(points.stream().mapToLong(TodoDashboardVO.DailyCompletionPoint::getCompleted).sum());
        vo.setPriorityDistribution(priorityDist);
        vo.setDailyCompletion(points);
        return vo;
    }

    @Override
    public List<TodoNotificationVO> notifications(int windowMinutes) {
        Long userId = requiredUserId();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime future = now.plusMinutes(Math.max(windowMinutes, 1));

        List<TodoTask> pendingTasks = todoTaskMapper.selectList(new LambdaQueryWrapper<TodoTask>()
                .eq(TodoTask::getUserId, userId)
                .eq(TodoTask::getStatus, TodoStatus.PENDING)
                .isNotNull(TodoTask::getDueAt));

        List<TodoNotificationVO> result = new ArrayList<>();
        for (TodoTask task : pendingTasks) {
            if (task.getDueAt().isBefore(now)) result.add(makeNotification(task, "OVERDUE", "任务已逾期，请尽快处理"));
            else if (!task.getDueAt().isAfter(future)) result.add(makeNotification(task, "DUE_SOON", "任务即将到期"));
        }
        result.sort((a, b) -> a.getDueAt().compareTo(b.getDueAt()));
        return result;
    }

    @Override
    public List<TodoVO> exportAll() {
        Long userId = requiredUserId();
        List<TodoTask> tasks = todoTaskMapper.selectList(new LambdaQueryWrapper<TodoTask>()
                .eq(TodoTask::getUserId, userId)
                .orderByDesc(TodoTask::getSortOrder)
                .orderByDesc(TodoTask::getCreatedAt));
        return enrichTodos(tasks);
    }

    @Override
    public int importAll(TodoImportRequest request) {
        Long userId = requiredUserId();
        int count = 0;
        for (TodoImportRequest.TodoImportTask item : request.getTasks()) {
            if (!StringUtils.hasText(item.getTitle())) continue;

            TodoTask task = new TodoTask();
            task.setUserId(userId);
            task.setTitle(item.getTitle().trim());
            task.setDescription(item.getDescription());
            task.setPriority(item.getPriority() == null ? 2 : item.getPriority());
            task.setGroupName(cleanGroupName(item.getGroupName()));
            task.setStatus(TodoStatus.PENDING);
            task.setDueAt(parseDateTime(item.getDueAt()));
            task.setRecurringType(normalizeRecurringType(item.getRecurringType()));
            task.setRecurringInterval(safeRecurringInterval(item.getRecurringInterval()));
            task.setSortOrder(nextSortOrder(userId));
            task.setVersion(1);
            todoTaskMapper.insert(task);

            saveTaskTags(userId, task.getId(), item.getTags());

            if (item.getSubtasks() != null) {
                int idx = 1;
                for (String sub : item.getSubtasks()) {
                    if (!StringUtils.hasText(sub)) continue;
                    TodoSubtask st = new TodoSubtask();
                    st.setUserId(userId);
                    st.setTaskId(task.getId());
                    st.setTitle(sub.trim());
                    st.setStatus(0);
                    st.setSortOrder(idx++);
                    todoSubtaskMapper.insert(st);
                }
            }
            count++;
        }

        evictStatsCache(userId);
        return count;
    }

    private void generateNextRecurringTaskIfNeeded(TodoTask task) {
        String rt = normalizeRecurringType(task.getRecurringType());
        if (RECUR_NONE.equals(rt)) return;

        LocalDateTime base = task.getDueAt() != null ? task.getDueAt() : LocalDateTime.now();
        int interval = safeRecurringInterval(task.getRecurringInterval());
        LocalDateTime nextDue = RECUR_DAILY.equals(rt) ? base.plusDays(interval) : base.plusWeeks(interval);

        TodoTask next = new TodoTask();
        next.setUserId(task.getUserId());
        next.setTitle(task.getTitle());
        next.setDescription(task.getDescription());
        next.setStatus(TodoStatus.PENDING);
        next.setPriority(task.getPriority());
        next.setGroupName(task.getGroupName());
        next.setDueAt(nextDue);
        next.setRecurringType(rt);
        next.setRecurringInterval(interval);
        next.setSortOrder(nextSortOrder(task.getUserId()));
        next.setVersion(1);
        todoTaskMapper.insert(next);

        List<String> tags = buildTaskTagMap(List.of(task.getId())).getOrDefault(task.getId(), Collections.emptyList());
        saveTaskTags(task.getUserId(), next.getId(), tags);
    }

    private void applySort(LambdaQueryWrapper<TodoTask> wrapper, String sortBy, String sortOrder) {
        boolean asc = "asc".equalsIgnoreCase(sortOrder);
        if ("dueAt".equals(sortBy)) {
            wrapper.orderBy(true, asc, TodoTask::getDueAt).orderByDesc(TodoTask::getCreatedAt);
            return;
        }
        if ("updatedAt".equals(sortBy)) {
            wrapper.orderBy(true, asc, TodoTask::getUpdatedAt);
            return;
        }
        if ("priority".equals(sortBy)) {
            wrapper.orderBy(true, asc, TodoTask::getPriority).orderByDesc(TodoTask::getCreatedAt);
            return;
        }
        if ("manual".equals(sortBy)) {
            wrapper.orderByDesc(TodoTask::getSortOrder).orderByDesc(TodoTask::getCreatedAt);
            return;
        }
        wrapper.orderByDesc(TodoTask::getCreatedAt);
    }

    private TodoTask getOwnedTask(Long id) {
        Long userId = requiredUserId();
        TodoTask task = todoTaskMapper.selectById(id);
        if (task == null || !userId.equals(task.getUserId())) throw new BizException(4042, "任务不存在");
        return task;
    }

    private List<TodoTask> getOwnedTasks(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return Collections.emptyList();
        Long userId = requiredUserId();
        return todoTaskMapper.selectList(new LambdaQueryWrapper<TodoTask>().eq(TodoTask::getUserId, userId).in(TodoTask::getId, ids));
    }

    private TodoSubtask getOwnedSubtask(Long taskId, Long subtaskId) {
        TodoSubtask subtask = todoSubtaskMapper.selectById(subtaskId);
        if (subtask == null || !Objects.equals(subtask.getTaskId(), taskId) || !Objects.equals(subtask.getUserId(), requiredUserId())) {
            throw new BizException(4043, "子任务不存在");
        }
        return subtask;
    }

    private Long requiredUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) throw new BizException(4010, "未登录或登录状态失效");
        return userId;
    }

    private void assertVersion(TodoTask task, Integer expectedVersion) {
        if (expectedVersion == null) return;
        if (!Objects.equals(task.getVersion(), expectedVersion)) {
            throw new BizException(4091, "任务已在其他端更新，请刷新后重试");
        }
    }

    private void bumpVersion(TodoTask task) {
        task.setVersion(task.getVersion() == null ? 1 : task.getVersion() + 1);
    }

    private String cleanGroupName(String groupName) {
        if (!StringUtils.hasText(groupName)) return null;
        return groupName.trim();
    }

    private String normalizeRecurringType(String recurringType) {
        if (!StringUtils.hasText(recurringType)) return RECUR_NONE;
        String rt = recurringType.trim().toUpperCase();
        if (RECUR_DAILY.equals(rt) || RECUR_WEEKLY.equals(rt)) return rt;
        return RECUR_NONE;
    }

    private Integer safeRecurringInterval(Integer interval) {
        if (interval == null || interval <= 0) return 1;
        return Math.min(interval, 30);
    }

    private Integer nextSortOrder(Long userId) {
        TodoTask top = todoTaskMapper.selectOne(new LambdaQueryWrapper<TodoTask>()
                .eq(TodoTask::getUserId, userId)
                .orderByDesc(TodoTask::getSortOrder)
                .last("limit 1"));
        return top == null || top.getSortOrder() == null ? 1 : top.getSortOrder() + 1;
    }

    private Integer nextSubtaskSortOrder(Long taskId) {
        TodoSubtask top = todoSubtaskMapper.selectOne(new LambdaQueryWrapper<TodoSubtask>()
                .eq(TodoSubtask::getTaskId, taskId)
                .orderByDesc(TodoSubtask::getSortOrder)
                .last("limit 1"));
        return top == null || top.getSortOrder() == null ? 1 : top.getSortOrder() + 1;
    }

    private Set<Long> resolveTaskIdsByTag(Long userId, String tagName) {
        if (!StringUtils.hasText(tagName)) return Collections.emptySet();
        TodoTag tag = todoTagMapper.selectOne(new LambdaQueryWrapper<TodoTag>()
                .eq(TodoTag::getUserId, userId)
                .eq(TodoTag::getName, tagName)
                .last("limit 1"));
        if (tag == null) return Collections.emptySet();

        return todoTaskTagMapper.selectList(new LambdaQueryWrapper<TodoTaskTag>().eq(TodoTaskTag::getTagId, tag.getId()))
                .stream().map(TodoTaskTag::getTaskId).collect(Collectors.toSet());
    }

    private List<TodoVO> enrichTodos(List<TodoTask> tasks) {
        if (tasks.isEmpty()) return Collections.emptyList();

        List<Long> taskIds = tasks.stream().map(TodoTask::getId).collect(Collectors.toList());
        Map<Long, List<String>> tagMap = buildTaskTagMap(taskIds);
        Map<Long, List<TodoSubtaskVO>> subtaskMap = buildTaskSubtaskMap(taskIds);

        return tasks.stream().map(task -> {
            TodoVO vo = new TodoVO();
            vo.setId(task.getId());
            vo.setTitle(task.getTitle());
            vo.setDescription(task.getDescription());
            vo.setStatus(task.getStatus());
            vo.setPriority(task.getPriority());
            vo.setGroupName(task.getGroupName());
            vo.setSortOrder(task.getSortOrder());
            vo.setDueAt(task.getDueAt());
            vo.setRecurringType(task.getRecurringType());
            vo.setRecurringInterval(task.getRecurringInterval());
            vo.setVersion(task.getVersion());
            vo.setCreatedAt(task.getCreatedAt());
            vo.setUpdatedAt(task.getUpdatedAt());
            vo.setTags(tagMap.getOrDefault(task.getId(), Collections.emptyList()));
            vo.setSubtasks(subtaskMap.getOrDefault(task.getId(), Collections.emptyList()));
            return vo;
        }).collect(Collectors.toList());
    }

    private Map<Long, List<String>> buildTaskTagMap(List<Long> taskIds) {
        List<TodoTaskTag> refs = todoTaskTagMapper.selectList(new LambdaQueryWrapper<TodoTaskTag>().in(TodoTaskTag::getTaskId, taskIds));
        if (refs.isEmpty()) return Collections.emptyMap();

        Set<Long> tagIds = refs.stream().map(TodoTaskTag::getTagId).collect(Collectors.toSet());
        Map<Long, String> tagNameById = todoTagMapper.selectList(new LambdaQueryWrapper<TodoTag>().in(TodoTag::getId, tagIds))
                .stream().collect(Collectors.toMap(TodoTag::getId, TodoTag::getName));

        Map<Long, List<String>> map = new HashMap<>();
        for (TodoTaskTag ref : refs) {
            String tagName = tagNameById.get(ref.getTagId());
            if (!StringUtils.hasText(tagName)) continue;
            map.computeIfAbsent(ref.getTaskId(), k -> new ArrayList<>()).add(tagName);
        }
        return map;
    }

    private Map<Long, List<TodoSubtaskVO>> buildTaskSubtaskMap(List<Long> taskIds) {
        List<TodoSubtask> subtasks = todoSubtaskMapper.selectList(new LambdaQueryWrapper<TodoSubtask>()
                .in(TodoSubtask::getTaskId, taskIds)
                .orderByDesc(TodoSubtask::getSortOrder));
        if (subtasks.isEmpty()) return Collections.emptyMap();

        Map<Long, List<TodoSubtaskVO>> map = new HashMap<>();
        for (TodoSubtask subtask : subtasks) {
            map.computeIfAbsent(subtask.getTaskId(), k -> new ArrayList<>()).add(toSubtaskVO(subtask));
        }
        return map;
    }

    private void saveTaskTags(Long userId, Long taskId, List<String> tags) {
        todoTaskTagMapper.delete(new LambdaQueryWrapper<TodoTaskTag>().eq(TodoTaskTag::getTaskId, taskId));
        List<String> cleaned = normalizeTags(tags);
        if (cleaned.isEmpty()) return;

        Map<String, TodoTag> existing = todoTagMapper.selectList(new LambdaQueryWrapper<TodoTag>()
                        .eq(TodoTag::getUserId, userId)
                        .in(TodoTag::getName, cleaned))
                .stream().collect(Collectors.toMap(TodoTag::getName, t -> t));

        for (String name : cleaned) {
            TodoTag tag = existing.get(name);
            if (tag == null) {
                tag = new TodoTag();
                tag.setUserId(userId);
                tag.setName(name);
                tag.setColor("#9b5de5");
                todoTagMapper.insert(tag);
            }

            TodoTaskTag ref = new TodoTaskTag();
            ref.setTaskId(taskId);
            ref.setTagId(tag.getId());
            todoTaskTagMapper.insert(ref);
        }
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) return Collections.emptyList();
        Set<String> set = new HashSet<>();
        for (String tag : tags) {
            if (!StringUtils.hasText(tag)) continue;
            String t = tag.trim();
            if (!t.isEmpty() && t.length() <= 32) set.add(t);
        }
        return new ArrayList<>(set);
    }

    private TodoSubtaskVO toSubtaskVO(TodoSubtask subtask) {
        TodoSubtaskVO vo = new TodoSubtaskVO();
        vo.setId(subtask.getId());
        vo.setTaskId(subtask.getTaskId());
        vo.setTitle(subtask.getTitle());
        vo.setStatus(subtask.getStatus());
        vo.setSortOrder(subtask.getSortOrder());
        return vo;
    }

    private TodoNotificationVO makeNotification(TodoTask task, String level, String message) {
        TodoNotificationVO vo = new TodoNotificationVO();
        vo.setTaskId(task.getId());
        vo.setTitle(task.getTitle());
        vo.setDueAt(task.getDueAt());
        vo.setLevel(level);
        vo.setMessage(message);
        return vo;
    }

    private void archiveBeforeDelete(TodoTask task) {
        try {
            TodoVO snapshot = detail(task.getId());
            TodoRecycleBin bin = new TodoRecycleBin();
            bin.setUserId(task.getUserId());
            bin.setOriginalTaskId(task.getId());
            bin.setTaskSnapshot(objectMapper.writeValueAsString(snapshot));
            bin.setStatus(0);
            recycleBinMapper.insert(bin);
        } catch (Exception e) {
            throw new BizException(5001, "归档删除任务失败");
        }
    }

    private TodoVO parseSnapshot(String json) {
        try {
            return objectMapper.readValue(json, TodoVO.class);
        } catch (Exception e) {
            throw new BizException(5002, "回收站数据损坏");
        }
    }

    private void removeTaskWithRelations(Long taskId) {
        todoTaskMapper.deleteById(taskId);
        todoSubtaskMapper.delete(new LambdaQueryWrapper<TodoSubtask>().eq(TodoSubtask::getTaskId, taskId));
        todoTaskTagMapper.delete(new LambdaQueryWrapper<TodoTaskTag>().eq(TodoTaskTag::getTaskId, taskId));
    }

    private LocalDateTime parseDateTime(String text) {
        if (!StringUtils.hasText(text)) return null;
        String normalized = text.trim().replace(' ', 'T');
        try {
            return LocalDateTime.parse(normalized);
        } catch (Exception e) {
            return null;
        }
    }

    private void evictStatsCache(Long userId) {
        redisTemplate.delete(statsKey(userId));
    }

    private String statsKey(Long userId) {
        return "todo:stats:" + userId;
    }
}
