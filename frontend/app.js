const API_BASE = localStorage.getItem('todo-api-base') || 'http://127.0.0.1:8080/api';
const ACCESS_TOKEN_KEY = 'todo-access-token';
const REFRESH_TOKEN_KEY = 'todo-refresh-token';

let accessToken = localStorage.getItem(ACCESS_TOKEN_KEY) || '';
let refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY) || '';
let currentUser = null;

let todos = [];
let recycleItems = [];
let notifications = [];
let dashboard = null;
let filter = 'all';
let editingId = null;
let loadingTodos = false;
let todoError = '';
let refreshPromise = null;
let selectedIds = new Set();
let dragTaskId = null;

const searchState = {
  keyword: '',
  groupName: '',
  tag: '',
  sortBy: 'manual',
  sortOrder: 'desc'
};

const authScreen = document.getElementById('auth-screen');
const appRoot = document.getElementById('app-root');
const authMsg = document.getElementById('auth-msg');
const apiBaseHint = document.getElementById('api-base-hint');
const userNameEl = document.getElementById('user-name');

const tabLogin = document.getElementById('tab-login');
const tabRegister = document.getElementById('tab-register');
const tabReset = document.getElementById('tab-reset');

const loginForm = document.getElementById('login-form');
const registerForm = document.getElementById('register-form');
const resetForm = document.getElementById('reset-form');

const loginSubmit = document.getElementById('login-submit');
const registerSubmit = document.getElementById('register-submit');
const resetSubmit = document.getElementById('reset-submit');
const sendCodeBtn = document.getElementById('send-code-btn');
const logoutBtn = document.getElementById('logout-btn');
const exportBtn = document.getElementById('export-btn');
const importFileInput = document.getElementById('import-file');

const listEl = document.getElementById('todo-list');
const emptyEl = document.getElementById('empty-state');
const footerEl = document.getElementById('list-footer');
const inputEl = document.getElementById('new-todo');
const newGroupEl = document.getElementById('new-group');
const newTagsEl = document.getElementById('new-tags');
const recurringTypeEl = document.getElementById('new-recurring-type');
const recurringIntervalEl = document.getElementById('new-recurring-interval');
const priorityEl = document.getElementById('new-priority');
const dueAtEl = document.getElementById('new-due-at');
const remainEl = document.getElementById('remain-count');
const clearBtn = document.getElementById('clear-done');
const addBtn = document.getElementById('add-btn');

const keywordInput = document.getElementById('keyword-input');
const groupFilter = document.getElementById('group-filter');
const tagFilter = document.getElementById('tag-filter');
const sortFilter = document.getElementById('sort-filter');
const searchBtn = document.getElementById('search-btn');
const toggleRecycleBtn = document.getElementById('toggle-recycle');
const toggleNotifyBtn = document.getElementById('toggle-notify');

const bulkCountEl = document.getElementById('bulk-count');
const bulkDoneBtn = document.getElementById('bulk-done');
const bulkPendingBtn = document.getElementById('bulk-pending');
const bulkDeleteBtn = document.getElementById('bulk-delete');

const recyclePanel = document.getElementById('recycle-panel');
const recycleListEl = document.getElementById('recycle-list');
const refreshRecycleBtn = document.getElementById('refresh-recycle');

const notifyPanel = document.getElementById('notify-panel');
const notifyListEl = document.getElementById('notify-list');
const refreshNotifyBtn = document.getElementById('refresh-notify');

const dashTotalEl = document.getElementById('dash-total');
const dashCompletedEl = document.getElementById('dash-completed');
const dashOverdueEl = document.getElementById('dash-overdue');
const dashDueSoonEl = document.getElementById('dash-due-soon');

const fillEl = document.getElementById('progress-fill');
const ptextEl = document.getElementById('progress-text');
const pPctEl = document.getElementById('progress-pct');
const cntAll = document.getElementById('cnt-all');
const cntActive = document.getElementById('cnt-active');
const cntDone = document.getElementById('cnt-done');

const listStateEl = document.getElementById('list-state');
const listStateTextEl = document.getElementById('list-state-text');
const retryLoadBtn = document.getElementById('retry-load');

apiBaseHint.textContent = API_BASE;

function tickClock() {
  const now = new Date();
  const dateStr = now.toLocaleDateString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit'
  }).replace(/\//g, '-');
  const timeStr = now.toLocaleTimeString('zh-CN', {
    hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false
  });
  const dayNames = ['SUN', 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT'];
  document.getElementById('date-val').textContent = dateStr;
  document.getElementById('time-val').textContent = timeStr;
  document.getElementById('day-val').textContent = dayNames[now.getDay()];
}

tickClock();
setInterval(tickClock, 1000);

function setButtonBusy(btn, busy, busyText) {
  if (!btn) return;
  if (busy) {
    if (!btn.dataset.originalText) btn.dataset.originalText = btn.textContent;
    btn.disabled = true;
    btn.textContent = busyText || '处理中...';
  } else {
    btn.disabled = false;
    if (btn.dataset.originalText) btn.textContent = btn.dataset.originalText;
  }
}

function toLocalDateTimeValue(isoText) {
  if (!isoText) return '';
  return String(isoText).slice(0, 16);
}

function toBackendDateTime(value) {
  if (!value) return null;
  return `${value}:00`;
}

function formatDueAt(isoText) {
  if (!isoText) return '无截止';
  const dt = new Date(isoText);
  if (Number.isNaN(dt.getTime())) return isoText;
  return dt.toLocaleString('zh-CN', { hour12: false });
}

function parseTags(text) {
  if (!text) return [];
  return text.split(',').map(t => t.trim()).filter(Boolean);
}

function priorityLabel(priority) {
  if (priority === 1) return 'P1';
  if (priority === 3) return 'P3';
  return 'P2';
}

function validateUsername(username) {
  return /^[a-zA-Z0-9_]{3,32}$/.test(username);
}

function validatePassword(password) {
  return /^(?=.*[A-Za-z])(?=.*\d).{8,64}$/.test(password);
}

function showAuth(msg = '') {
  authScreen.style.display = 'grid';
  appRoot.style.display = 'none';
  authMsg.textContent = msg;
}

function showApp() {
  authScreen.style.display = 'none';
  appRoot.style.display = 'block';
  userNameEl.textContent = currentUser?.nickname || currentUser?.username || '用户';
}

function switchTab(mode) {
  tabLogin.classList.toggle('active', mode === 'login');
  tabRegister.classList.toggle('active', mode === 'register');
  tabReset.classList.toggle('active', mode === 'reset');

  loginForm.style.display = mode === 'login' ? 'grid' : 'none';
  registerForm.style.display = mode === 'register' ? 'grid' : 'none';
  resetForm.style.display = mode === 'reset' ? 'grid' : 'none';
  authMsg.textContent = '';
}

async function rawApi(path, options = {}, withAuth = true) {
  const headers = {
    'Content-Type': 'application/json',
    ...(options.headers || {})
  };

  if (withAuth && accessToken) headers.Authorization = `Bearer ${accessToken}`;

  const response = await fetch(`${API_BASE}${path}`, { ...options, headers });
  const payload = await response.json().catch(() => ({}));
  if (!response.ok || payload.code !== 0) {
    const err = new Error(payload.message || `请求失败(${response.status})`);
    err.code = payload.code || response.status;
    throw err;
  }
  return payload.data;
}

async function refreshAccessToken() {
  if (!refreshToken) throw new Error('登录已失效，请重新登录');
  if (refreshPromise) return refreshPromise;

  refreshPromise = rawApi('/auth/refresh', {
    method: 'POST',
    body: JSON.stringify({ refreshToken })
  }, false).then(data => {
    accessToken = data.accessToken;
    refreshToken = data.refreshToken;
    localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
    localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
    return data;
  }).finally(() => {
    refreshPromise = null;
  });

  return refreshPromise;
}

async function api(path, options = {}, retried = false) {
  try {
    return await rawApi(path, options, true);
  } catch (err) {
    const canRefresh = !retried && (err.code === 4010 || err.code === 4012) && !path.startsWith('/auth/');
    if (!canRefresh) throw err;
    await refreshAccessToken();
    return api(path, options, true);
  }
}

async function login(username, password) {
  const data = await rawApi('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password })
  }, false);

  accessToken = data.accessToken;
  refreshToken = data.refreshToken;
  localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
  localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
  currentUser = data.user;
  showApp();
  await bootstrapAll();
}

async function register(payload) {
  await rawApi('/auth/register', {
    method: 'POST',
    body: JSON.stringify(payload)
  }, false);
}

async function sendResetCode(username) {
  await rawApi('/auth/password/reset-code', {
    method: 'POST',
    body: JSON.stringify({ username })
  }, false);
}

async function resetPassword(payload) {
  await rawApi('/auth/password/reset', {
    method: 'POST',
    body: JSON.stringify(payload)
  }, false);
}

async function logout() {
  try {
    await rawApi('/auth/logout', {
      method: 'POST',
      headers: {
        Authorization: accessToken ? `Bearer ${accessToken}` : '',
        'X-Refresh-Token': refreshToken || ''
      }
    }, false);
  } catch (_) {
  }

  accessToken = '';
  refreshToken = '';
  currentUser = null;
  localStorage.removeItem(ACCESS_TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
  showAuth('你已退出登录');
}

async function restoreSession() {
  if (!accessToken) {
    showAuth();
    return;
  }
  try {
    currentUser = await api('/auth/me');
    showApp();
    await bootstrapAll();
  } catch (_) {
    try {
      await refreshAccessToken();
      currentUser = await api('/auth/me');
      showApp();
      await bootstrapAll();
    } catch (e) {
      accessToken = '';
      refreshToken = '';
      localStorage.removeItem(ACCESS_TOKEN_KEY);
      localStorage.removeItem(REFRESH_TOKEN_KEY);
      showAuth('登录已失效，请重新登录');
    }
  }
}

function getStatusByFilter() {
  if (filter === 'active') return 0;
  if (filter === 'done') return 1;
  return null;
}

function updateBulkCount() {
  bulkCountEl.textContent = `已选 ${selectedIds.size} 项`;
}

function renderListState() {
  if (loadingTodos) {
    listStateEl.style.display = 'flex';
    listStateTextEl.textContent = '加载中...';
    retryLoadBtn.style.display = 'none';
    return;
  }
  if (todoError) {
    listStateEl.style.display = 'flex';
    listStateTextEl.textContent = todoError;
    retryLoadBtn.style.display = 'inline-flex';
    return;
  }
  listStateEl.style.display = 'none';
}

async function loadAuxFilters() {
  const [groups, tags] = await Promise.all([api('/todos/groups'), api('/todos/tags')]);
  groupFilter.innerHTML = '<option value="">全部分组</option>';
  groups.forEach(g => {
    const op = document.createElement('option');
    op.value = g;
    op.textContent = g;
    groupFilter.appendChild(op);
  });

  tagFilter.innerHTML = '<option value="">全部标签</option>';
  tags.forEach(t => {
    const op = document.createElement('option');
    op.value = t;
    op.textContent = t;
    tagFilter.appendChild(op);
  });
}

async function loadDashboard() {
  dashboard = await api('/todos/dashboard');
  dashTotalEl.textContent = dashboard.total || 0;
  dashCompletedEl.textContent = dashboard.completed || 0;
  dashOverdueEl.textContent = dashboard.overdue || 0;
  dashDueSoonEl.textContent = dashboard.dueSoon || 0;
}

async function loadNotifications() {
  notifications = await api('/todos/notifications?windowMinutes=1440');
  notifyListEl.innerHTML = '';
  if (!notifications.length) {
    const li = document.createElement('li');
    li.textContent = '暂无提醒';
    notifyListEl.appendChild(li);
    return;
  }
  notifications.forEach(n => {
    const li = document.createElement('li');
    li.className = 'recycle-item';
    li.innerHTML = `<span>[${n.level}] ${n.title} · ${formatDueAt(n.dueAt)}</span>`;
    notifyListEl.appendChild(li);
  });
}

function collectQueryParams() {
  const params = new URLSearchParams({ pageNum: '1', pageSize: '200' });
  const status = getStatusByFilter();
  if (status !== null) params.set('status', String(status));
  if (searchState.keyword) params.set('keyword', searchState.keyword);
  if (searchState.groupName) params.set('groupName', searchState.groupName);
  if (searchState.tag) params.set('tag', searchState.tag);
  if (searchState.sortBy) params.set('sortBy', searchState.sortBy);
  if (searchState.sortOrder) params.set('sortOrder', searchState.sortOrder);
  return params;
}

async function loadTodos(showLoading = true) {
  if (showLoading) {
    loadingTodos = true;
    todoError = '';
    renderListState();
  }

  try {
    const page = await api(`/todos?${collectQueryParams().toString()}`);
    todos = page.records || [];
    selectedIds = new Set([...selectedIds].filter(id => todos.some(t => t.id === id)));
    todoError = '';
    await updateProgress();
    render();
  } catch (err) {
    todoError = err.message || '加载失败，请重试';
    render();
  } finally {
    loadingTodos = false;
    renderListState();
    updateBulkCount();
  }
}

async function updateProgress() {
  let stats = { total: 0, completed: 0, pending: 0 };
  try { stats = await api('/todos/stats'); } catch (_) {}
  const total = stats.total || 0;
  const done = stats.completed || 0;
  const activeCount = stats.pending || 0;
  const pct = total > 0 ? Math.round((done / total) * 100) : 0;

  fillEl.style.width = pct + '%';
  fillEl.dataset.empty = total === 0 ? 'true' : 'false';
  ptextEl.textContent = `${done} / ${total} COMPLETED`;
  pPctEl.textContent = total > 0 ? pct + '%' : '—';

  cntAll.textContent = total ? ` [${total}]` : '';
  cntActive.textContent = activeCount ? ` [${activeCount}]` : '';
  cntDone.textContent = done ? ` [${done}]` : '';
  remainEl.textContent = String(activeCount);
}

function buildMetaChips(todo) {
  const chips = [];
  chips.push(`<span class="priority-badge p${todo.priority || 2}">${priorityLabel(todo.priority || 2)}</span>`);
  chips.push(`<span class="due-chip">${formatDueAt(todo.dueAt)}</span>`);
  if (todo.groupName) chips.push(`<span class="tag-chip">组:${todo.groupName}</span>`);
  if (todo.recurringType && todo.recurringType !== 'NONE') {
    chips.push(`<span class="tag-chip">${todo.recurringType}/${todo.recurringInterval || 1}</span>`);
  }
  (todo.tags || []).forEach(tag => chips.push(`<span class="tag-chip">#${tag}</span>`));
  return chips.join('');
}

function renderSubtasks(todo) {
  const wrap = document.createElement('div');
  wrap.className = 'subtask-wrap';
  const ul = document.createElement('ul');
  ul.className = 'subtask-list';

  (todo.subtasks || []).forEach(sub => {
    const li = document.createElement('li');
    li.className = 'subtask-item';
    const chk = document.createElement('input');
    chk.type = 'checkbox';
    chk.checked = sub.status === 1;
    chk.addEventListener('change', async () => {
      await api(`/todos/${todo.id}/subtasks/${sub.id}`, { method: 'PATCH', body: JSON.stringify({ status: chk.checked ? 1 : 0 }) });
      await loadTodos(false);
    });

    const txt = document.createElement('span');
    txt.textContent = sub.title;
    if (sub.status === 1) txt.className = 'done';

    const del = document.createElement('button');
    del.type = 'button';
    del.textContent = 'x';
    del.addEventListener('click', async () => {
      await api(`/todos/${todo.id}/subtasks/${sub.id}`, { method: 'DELETE' });
      await loadTodos(false);
    });

    li.append(chk, txt, del);
    ul.appendChild(li);
  });

  const row = document.createElement('div');
  row.className = 'subtask-add-row';
  const input = document.createElement('input');
  input.placeholder = '新增子任务';
  const btn = document.createElement('button');
  btn.type = 'button';
  btn.textContent = '+';
  btn.addEventListener('click', async () => {
    const title = input.value.trim();
    if (!title) return;
    await api(`/todos/${todo.id}/subtasks`, { method: 'POST', body: JSON.stringify({ title }) });
    input.value = '';
    await loadTodos(false);
  });

  row.append(input, btn);
  wrap.append(ul, row);
  return wrap;
}

function renderTodoItem(todo, idx) {
  const li = document.createElement('li');
  li.className = 'todo-item';
  li.dataset.id = String(todo.id);
  li.draggable = searchState.sortBy === 'manual';

  li.addEventListener('dragstart', () => { dragTaskId = todo.id; li.classList.add('dragging'); });
  li.addEventListener('dragend', () => { li.classList.remove('dragging'); dragTaskId = null; });
  li.addEventListener('dragover', e => { if (searchState.sortBy === 'manual') e.preventDefault(); });
  li.addEventListener('drop', async e => {
    if (searchState.sortBy !== 'manual') return;
    e.preventDefault();
    if (!dragTaskId || dragTaskId === todo.id) return;

    const from = todos.findIndex(t => t.id === dragTaskId);
    const to = todos.findIndex(t => t.id === todo.id);
    if (from < 0 || to < 0) return;

    const clone = [...todos];
    const [moved] = clone.splice(from, 1);
    clone.splice(to, 0, moved);
    todos = clone;
    render();

    await api('/todos/reorder', { method: 'PATCH', body: JSON.stringify({ orderedIds: todos.map(t => t.id) }) });
    await loadTodos(false);
  });

  const bulkCk = document.createElement('input');
  bulkCk.type = 'checkbox';
  bulkCk.className = 'bulk-check';
  bulkCk.checked = selectedIds.has(todo.id);
  bulkCk.addEventListener('change', () => {
    if (bulkCk.checked) selectedIds.add(todo.id); else selectedIds.delete(todo.id);
    updateBulkCount();
  });

  const numEl = document.createElement('span');
  numEl.className = 'item-num';
  numEl.textContent = String(idx + 1).padStart(2, '0');

  const chkWrap = document.createElement('label');
  chkWrap.className = 'chk-wrap';
  const cb = document.createElement('input');
  cb.type = 'checkbox';
  cb.checked = todo.status === 1;
  cb.addEventListener('change', async () => {
    try {
      await api(`/todos/${todo.id}/status?done=${cb.checked}&expectedVersion=${todo.version ?? ''}`, { method: 'PATCH' });
      await loadTodos(false);
    } catch (e) {
      authMsg.textContent = e.message;
      await loadTodos(true);
    }
  });

  const face = document.createElement('span');
  face.className = 'chk-face';
  face.innerHTML = `<svg viewBox="0 0 11 9" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M1 4.5L4 7.5L10 1" stroke="#060610" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/></svg>`;
  chkWrap.append(cb, face);

  const content = document.createElement('div');
  content.className = 'todo-content';

  if (editingId === todo.id) {
    const titleInput = document.createElement('input');
    titleInput.className = 'edit-field';
    titleInput.value = todo.title;

    const row = document.createElement('div');
    row.className = 'todo-edit-row';

    const groupInput = document.createElement('input');
    groupInput.className = 'meta-input item-meta-input';
    groupInput.placeholder = '分组';
    groupInput.value = todo.groupName || '';

    const tagsInput = document.createElement('input');
    tagsInput.className = 'meta-input item-meta-input';
    tagsInput.placeholder = '标签,分隔';
    tagsInput.value = (todo.tags || []).join(',');

    const recurringType = document.createElement('select');
    recurringType.className = 'meta-select item-meta-select';
    ['NONE','DAILY','WEEKLY'].forEach(v => {
      const op = document.createElement('option');
      op.value = v;
      op.textContent = v;
      if ((todo.recurringType || 'NONE') === v) op.selected = true;
      recurringType.appendChild(op);
    });

    const recurringInterval = document.createElement('input');
    recurringInterval.className = 'meta-input item-meta-input';
    recurringInterval.type = 'number';
    recurringInterval.min = '1';
    recurringInterval.max = '30';
    recurringInterval.value = String(todo.recurringInterval || 1);

    const pr = document.createElement('select');
    pr.className = 'meta-select item-meta-select';
    [1, 2, 3].forEach(v => {
      const op = document.createElement('option');
      op.value = String(v);
      op.textContent = `P${v}`;
      if (v === (todo.priority || 2)) op.selected = true;
      pr.appendChild(op);
    });

    const due = document.createElement('input');
    due.type = 'datetime-local';
    due.className = 'due-input item-due-input';
    due.value = toLocalDateTimeValue(todo.dueAt);

    row.append(groupInput, tagsInput, recurringType, recurringInterval, pr, due);

    const action = document.createElement('div');
    action.className = 'item-actions item-actions-edit';

    const save = document.createElement('button');
    save.className = 'act';
    save.type = 'button';
    save.textContent = 'Save';
    save.addEventListener('click', async () => {
      const title = titleInput.value.trim();
      if (!title) { authMsg.textContent = '任务标题不能为空'; return; }
      try {
        await api(`/todos/${todo.id}`, {
          method: 'PUT',
          body: JSON.stringify({
            title,
            groupName: groupInput.value.trim() || null,
            tags: parseTags(tagsInput.value),
            recurringType: recurringType.value,
            recurringInterval: Number(recurringInterval.value || 1),
            priority: Number(pr.value),
            dueAt: toBackendDateTime(due.value),
            clearDueAt: !due.value,
            expectedVersion: todo.version
          })
        });
        editingId = null;
        await bootstrapAll(false);
      } catch (e) {
        authMsg.textContent = e.message;
      }
    });

    const cancel = document.createElement('button');
    cancel.className = 'act';
    cancel.type = 'button';
    cancel.textContent = 'Cancel';
    cancel.addEventListener('click', () => { editingId = null; render(); });

    action.append(save, cancel);
    content.append(titleInput, row, action);
  } else {
    const title = document.createElement('span');
    title.className = 'todo-text' + (todo.status === 1 ? ' done' : '');
    title.textContent = todo.title;

    const meta = document.createElement('div');
    meta.className = 'todo-meta-line';
    meta.innerHTML = buildMetaChips(todo);

    content.append(title, meta, renderSubtasks(todo));
  }

  const actions = document.createElement('div');
  actions.className = 'item-actions';
  const edit = document.createElement('button');
  edit.className = 'act';
  edit.type = 'button';
  edit.textContent = 'Edit';
  edit.addEventListener('click', () => { editingId = todo.id; render(); });

  const del = document.createElement('button');
  del.className = 'act del';
  del.type = 'button';
  del.textContent = 'Del';
  del.addEventListener('click', async () => {
    await api(`/todos/${todo.id}`, { method: 'DELETE' });
    selectedIds.delete(todo.id);
    await bootstrapAll(false);
    await loadRecycle();
  });

  actions.append(edit, del);
  li.append(bulkCk, numEl, chkWrap, content, actions);
  return li;
}

function renderRecycle() {
  recycleListEl.innerHTML = '';
  if (!recycleItems.length) {
    const li = document.createElement('li');
    li.textContent = '回收站为空';
    recycleListEl.appendChild(li);
    return;
  }

  recycleItems.forEach(item => {
    const li = document.createElement('li');
    li.className = 'recycle-item';
    li.innerHTML = `<span>${item.title} (${item.groupName || '未分组'})</span>`;

    const restore = document.createElement('button');
    restore.type = 'button';
    restore.textContent = '恢复';
    restore.addEventListener('click', async () => {
      await api(`/todos/recycle/${item.id}/restore`, { method: 'POST' });
      await bootstrapAll(false);
      await loadRecycle();
    });

    const purge = document.createElement('button');
    purge.type = 'button';
    purge.textContent = '彻底删除';
    purge.addEventListener('click', async () => {
      await api(`/todos/recycle/${item.id}`, { method: 'DELETE' });
      await loadRecycle();
    });

    li.append(restore, purge);
    recycleListEl.appendChild(li);
  });
}

function render() {
  listEl.innerHTML = '';
  renderListState();
  todos.forEach((todo, idx) => listEl.appendChild(renderTodoItem(todo, idx)));
  emptyEl.style.display = !loadingTodos && !todoError && todos.length === 0 ? 'block' : 'none';
  footerEl.style.display = !loadingTodos && todos.length > 0 ? 'flex' : 'none';
  updateBulkCount();
}

async function createTodo() {
  const title = inputEl.value.trim();
  if (!title) throw new Error('任务标题不能为空');
  if (title.length > 120) throw new Error('任务标题不能超过 120 字');

  await api('/todos', {
    method: 'POST',
    body: JSON.stringify({
      title,
      groupName: newGroupEl.value.trim() || null,
      tags: parseTags(newTagsEl.value),
      recurringType: recurringTypeEl.value,
      recurringInterval: Number(recurringIntervalEl.value || 1),
      priority: Number(priorityEl.value),
      dueAt: toBackendDateTime(dueAtEl.value)
    })
  });

  inputEl.value = '';
  newGroupEl.value = '';
  newTagsEl.value = '';
  recurringTypeEl.value = 'NONE';
  recurringIntervalEl.value = '1';
  dueAtEl.value = '';
  priorityEl.value = '2';
}

async function applyBulkStatus(status) {
  const ids = [...selectedIds];
  if (!ids.length) throw new Error('请先选择任务');
  await api('/todos/bulk/status', { method: 'POST', body: JSON.stringify({ ids, status }) });
  selectedIds.clear();
  await bootstrapAll(false);
}

async function applyBulkDelete() {
  const ids = [...selectedIds];
  if (!ids.length) throw new Error('请先选择任务');
  await api('/todos/bulk/delete', { method: 'POST', body: JSON.stringify({ ids }) });
  selectedIds.clear();
  await bootstrapAll(false);
  await loadRecycle();
}

async function loadRecycle() {
  recycleItems = await api('/todos/recycle');
  renderRecycle();
}

async function exportTasks() {
  const tasks = await api('/todos/export');
  const payload = {
    exportedAt: new Date().toISOString(),
    tasks: tasks.map(t => ({
      title: t.title,
      description: t.description,
      priority: t.priority,
      groupName: t.groupName,
      dueAt: t.dueAt,
      recurringType: t.recurringType,
      recurringInterval: t.recurringInterval,
      tags: t.tags || [],
      subtasks: (t.subtasks || []).map(s => s.title)
    }))
  };

  const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `todos-export-${Date.now()}.json`;
  a.click();
  URL.revokeObjectURL(url);
}

async function importTasksFromFile(file) {
  const text = await file.text();
  const parsed = JSON.parse(text);
  const tasks = Array.isArray(parsed) ? parsed : (parsed.tasks || []);
  if (!Array.isArray(tasks) || tasks.length === 0) throw new Error('导入文件没有有效任务');
  await api('/todos/import', { method: 'POST', body: JSON.stringify({ tasks }) });
  await bootstrapAll(true);
}

async function bootstrapAll(showLoading = true) {
  await Promise.all([loadTodos(showLoading), loadAuxFilters(), loadDashboard(), loadNotifications()]);
}

function bindEvents() {
  tabLogin.addEventListener('click', () => switchTab('login'));
  tabRegister.addEventListener('click', () => switchTab('register'));
  tabReset.addEventListener('click', () => switchTab('reset'));

  loginForm.addEventListener('submit', async e => {
    e.preventDefault();
    const username = document.getElementById('login-username').value.trim();
    const password = document.getElementById('login-password').value;
    if (!validateUsername(username)) { authMsg.textContent = '用户名需为 3-32 位字母、数字或下划线'; return; }

    setButtonBusy(loginSubmit, true, '登录中...');
    try { await login(username, password); } catch (err) { authMsg.textContent = err.message; }
    finally { setButtonBusy(loginSubmit, false); }
  });

  registerForm.addEventListener('submit', async e => {
    e.preventDefault();
    const username = document.getElementById('reg-username').value.trim();
    const password = document.getElementById('reg-password').value;
    const nickname = document.getElementById('reg-nickname').value.trim();

    if (!validateUsername(username)) { authMsg.textContent = '用户名需为 3-32 位字母、数字或下划线'; return; }
    if (!validatePassword(password)) { authMsg.textContent = '密码需 8-64 位，至少包含字母和数字'; return; }

    setButtonBusy(registerSubmit, true, '注册中...');
    try {
      await register({ username, password, nickname: nickname || undefined });
      authMsg.textContent = '注册成功，请登录';
      switchTab('login');
      document.getElementById('login-username').value = username;
    } catch (err) {
      authMsg.textContent = err.message;
    } finally {
      setButtonBusy(registerSubmit, false);
    }
  });

  sendCodeBtn.addEventListener('click', async () => {
    const username = document.getElementById('reset-username').value.trim();
    if (!validateUsername(username)) { authMsg.textContent = '请输入有效用户名后再获取验证码'; return; }
    setButtonBusy(sendCodeBtn, true, '发送中...');
    try { await sendResetCode(username); authMsg.textContent = '验证码已发送（开发模式请查看后端日志或 Redis）'; }
    catch (err) { authMsg.textContent = err.message; }
    finally { setButtonBusy(sendCodeBtn, false); }
  });

  resetForm.addEventListener('submit', async e => {
    e.preventDefault();
    const username = document.getElementById('reset-username').value.trim();
    const code = document.getElementById('reset-code').value.trim();
    const newPassword = document.getElementById('reset-password').value;
    const confirmPassword = document.getElementById('reset-password2').value;

    if (!validateUsername(username)) { authMsg.textContent = '用户名需为 3-32 位字母、数字或下划线'; return; }
    if (!/^\d{6}$/.test(code)) { authMsg.textContent = '验证码必须为 6 位数字'; return; }
    if (!validatePassword(newPassword)) { authMsg.textContent = '新密码需 8-64 位，至少包含字母和数字'; return; }
    if (newPassword !== confirmPassword) { authMsg.textContent = '两次输入的密码不一致'; return; }

    setButtonBusy(resetSubmit, true, '重置中...');
    try { await resetPassword({ username, code, newPassword, confirmPassword }); authMsg.textContent = '密码重置成功，请使用新密码登录'; switchTab('login'); }
    catch (err) { authMsg.textContent = err.message; }
    finally { setButtonBusy(resetSubmit, false); }
  });

  logoutBtn.addEventListener('click', async () => {
    setButtonBusy(logoutBtn, true, '退出中...');
    await logout();
    setButtonBusy(logoutBtn, false);
  });

  exportBtn.addEventListener('click', () => exportTasks().catch(e => authMsg.textContent = e.message));

  importFileInput.addEventListener('change', async () => {
    const file = importFileInput.files?.[0];
    if (!file) return;
    try { await importTasksFromFile(file); authMsg.textContent = '导入成功'; }
    catch (e) { authMsg.textContent = e.message; }
    finally { importFileInput.value = ''; }
  });

  addBtn.addEventListener('click', async () => {
    setButtonBusy(addBtn, true, '...');
    try { await createTodo(); await bootstrapAll(false); authMsg.textContent = ''; }
    catch (err) { authMsg.textContent = err.message; }
    finally { setButtonBusy(addBtn, false); }
  });

  inputEl.addEventListener('keydown', async e => {
    if (e.key !== 'Enter') return;
    try { await createTodo(); await bootstrapAll(false); }
    catch (err) { authMsg.textContent = err.message; }
  });

  clearBtn.addEventListener('click', async () => {
    try { await api('/todos/completed', { method: 'DELETE' }); await bootstrapAll(false); await loadRecycle(); }
    catch (err) { authMsg.textContent = err.message; }
  });

  retryLoadBtn.addEventListener('click', () => loadTodos(true).catch(() => {}));

  searchBtn.addEventListener('click', async () => {
    searchState.keyword = keywordInput.value.trim();
    searchState.groupName = groupFilter.value;
    searchState.tag = tagFilter.value;
    const [sortBy, sortOrder] = sortFilter.value.split(':');
    searchState.sortBy = sortBy;
    searchState.sortOrder = sortOrder || 'desc';
    await loadTodos(true);
  });

  toggleRecycleBtn.addEventListener('click', async () => {
    const visible = recyclePanel.style.display !== 'none';
    recyclePanel.style.display = visible ? 'none' : 'block';
    if (!visible) await loadRecycle();
  });

  toggleNotifyBtn.addEventListener('click', async () => {
    const visible = notifyPanel.style.display !== 'none';
    notifyPanel.style.display = visible ? 'none' : 'block';
    if (!visible) await loadNotifications();
  });

  refreshRecycleBtn.addEventListener('click', () => loadRecycle().catch(() => {}));
  refreshNotifyBtn.addEventListener('click', () => loadNotifications().catch(() => {}));

  bulkDoneBtn.addEventListener('click', () => applyBulkStatus(1).catch(e => authMsg.textContent = e.message));
  bulkPendingBtn.addEventListener('click', () => applyBulkStatus(0).catch(e => authMsg.textContent = e.message));
  bulkDeleteBtn.addEventListener('click', () => applyBulkDelete().catch(e => authMsg.textContent = e.message));

  document.querySelectorAll('.filter-btn').forEach(btn => {
    btn.addEventListener('click', async () => {
      document.querySelectorAll('.filter-btn').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      filter = btn.dataset.filter;
      await loadTodos(true);
    });
  });
}

bindEvents();
restoreSession();
