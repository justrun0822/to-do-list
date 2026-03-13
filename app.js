// ═══════════════════════════════════════
//   SCI-FI PURPLE TASK SYSTEM — app.js
// ═══════════════════════════════════════

const STORAGE_KEY = 'jrun-todos-v4';
let todos     = JSON.parse(localStorage.getItem(STORAGE_KEY) || '[]');
let filter    = 'all';
let editingId = null;
let undoStack = null; // { todo, index } | null
let undoTimer = null;

// ── DOM refs ──
const listEl    = document.getElementById('todo-list');
const emptyEl   = document.getElementById('empty-state');
const footerEl  = document.getElementById('list-footer');
const inputEl   = document.getElementById('new-todo');
const remainEl  = document.getElementById('remain-count');
const clearBtn  = document.getElementById('clear-done');
const addBtn    = document.getElementById('add-btn');
const fillEl    = document.getElementById('progress-fill');
const ptextEl   = document.getElementById('progress-text');
const pPctEl    = document.getElementById('progress-pct');
const cntAll    = document.getElementById('cnt-all');
const cntActive = document.getElementById('cnt-active');
const cntDone   = document.getElementById('cnt-done');

// ── Clock ──
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
  document.getElementById('day-val').textContent  = dayNames[now.getDay()];
}
tickClock();
setInterval(tickClock, 1000);

// ── Utils ──
function save()  { localStorage.setItem(STORAGE_KEY, JSON.stringify(todos)); }
function genId() { return Date.now().toString(36) + Math.random().toString(36).slice(2); }

function filtered() {
  if (filter === 'active') return todos.filter(t => !t.done);
  if (filter === 'done')   return todos.filter(t =>  t.done);
  return todos;
}

function updateProgress() {
  const total = todos.length;
  const done  = todos.filter(t => t.done).length;
  const pct   = total > 0 ? Math.round((done / total) * 100) : 0;

  fillEl.style.width = pct + '%';
  fillEl.dataset.empty = total === 0 ? 'true' : 'false';
  ptextEl.textContent  = `${done} / ${total} COMPLETED`;
  pPctEl.textContent   = total > 0 ? pct + '%' : '—';

  const activeCount = todos.filter(t => !t.done).length;
  cntAll.textContent    = todos.length  ? ` [${todos.length}]`   : '';
  cntActive.textContent = activeCount   ? ` [${activeCount}]`     : '';
  cntDone.textContent   = done          ? ` [${done}]`            : '';
}

// ── Render ──
function render() {
  const list = filtered();
  listEl.innerHTML = '';

  list.forEach((todo, idx) => {
    const li = document.createElement('li');
    li.className = 'todo-item';
    li.dataset.id = todo.id;
    li.style.animationDelay = Math.min(idx * 0.04, 0.36) + 's';

    // Index
    const numEl = document.createElement('span');
    numEl.className = 'item-num';
    numEl.textContent = String(idx + 1).padStart(2, '0');

    // Checkbox
    const chkWrap = document.createElement('label');
    chkWrap.className = 'chk-wrap';
    chkWrap.title = todo.done ? '标为未完成' : '标为已完成';

    const cb = document.createElement('input');
    cb.type = 'checkbox';
    cb.checked = todo.done;
    cb.addEventListener('change', () => toggle(todo.id));

    const face = document.createElement('span');
    face.className = 'chk-face';
    face.innerHTML = `<svg viewBox="0 0 11 9" fill="none" xmlns="http://www.w3.org/2000/svg">
      <path d="M1 4.5L4 7.5L10 1" stroke="#060610" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>
    </svg>`;
    chkWrap.append(cb, face);

    // Text
    const textEl = document.createElement('span');
    textEl.className = 'todo-text' + (todo.done ? ' done' : '');
    textEl.textContent = todo.text;

    // Actions
    const actions = document.createElement('div');
    actions.className = 'item-actions';

    const editBtn = document.createElement('button');
    editBtn.className = 'act';
    editBtn.textContent = 'Edit';
    editBtn.addEventListener('click', () => startEdit(todo.id, textEl));

    const delBtn = document.createElement('button');
    delBtn.className = 'act del';
    delBtn.textContent = 'Del';
    delBtn.addEventListener('click', () => removeTodo(todo.id, li));

    actions.append(editBtn, delBtn);
    li.append(numEl, chkWrap, textEl, actions);
    listEl.appendChild(li);
  });

  const activeCount = todos.filter(t => !t.done).length;
  emptyEl.style.display  = list.length === 0 ? 'block' : 'none';
  footerEl.style.display = todos.length > 0  ? 'flex'  : 'none';
  remainEl.textContent   = activeCount;
  updateProgress();
}

// ── Actions ──
function addTodo(text) {
  text = text.trim();
  if (!text) return;
  todos.unshift({ id: genId(), text, done: false });
  save();
  render();
}

function toggle(id) {
  const t = todos.find(t => t.id === id);
  if (t) { t.done = !t.done; save(); render(); }
}

function removeTodo(id, li) {
  li.classList.add('removing');
  li.addEventListener('animationend', () => {
    const idx = todos.findIndex(t => t.id === id);
    const todo = todos[idx];
    todos = todos.filter(t => t.id !== id);
    save();
    render();
    showUndo(todo, idx);
  }, { once: true });
}

function showUndo(todo, idx) {
  clearTimeout(undoTimer);
  undoStack = { todo, idx };
  let bar = document.getElementById('undo-bar');
  if (!bar) {
    bar = document.createElement('div');
    bar.id = 'undo-bar';
    bar.innerHTML = `<span id="undo-msg"></span><button id="undo-btn">UNDO</button>`;
    document.querySelector('.container').appendChild(bar);
    document.getElementById('undo-btn').addEventListener('click', () => {
      if (!undoStack) return;
      todos.splice(undoStack.idx, 0, undoStack.todo);
      undoStack = null;
      save();
      render();
      hideUndo();
    });
  }
  document.getElementById('undo-msg').textContent = `已删除: ${todo.text.slice(0, 24)}${todo.text.length > 24 ? '…' : ''}`;
  bar.classList.add('visible');
  undoTimer = setTimeout(hideUndo, 4000);
}

function hideUndo() {
  const bar = document.getElementById('undo-bar');
  if (bar) bar.classList.remove('visible');
  undoStack = null;
}

function startEdit(id, spanEl) {
  if (editingId) return;
  editingId = id;
  const todo = todos.find(t => t.id === id);
  const input = document.createElement('input');
  input.type      = 'text';
  input.value     = todo.text;
  input.className = 'edit-field';
  input.maxLength = 200;
  spanEl.replaceWith(input);
  input.focus();
  input.select();

  const commit = () => {
    const val = input.value.trim();
    if (val) todo.text = val;
    save();
    editingId = null;
    render();
  };

  input.addEventListener('blur', commit);
  input.addEventListener('keydown', e => {
    if (e.key === 'Enter')  input.blur();
    if (e.key === 'Escape') { editingId = null; render(); }
  });
}

// ── Event Listeners ──
addBtn.addEventListener('click', () => {
  addTodo(inputEl.value);
  inputEl.value = '';
  inputEl.focus();
});

inputEl.addEventListener('keydown', e => {
  if (e.key === 'Enter') {
    addTodo(inputEl.value);
    inputEl.value = '';
  }
});

clearBtn.addEventListener('click', () => {
  todos = todos.filter(t => !t.done);
  save();
  render();
});

document.querySelectorAll('.filter-btn').forEach(btn => {
  btn.addEventListener('click', () => {
    document.querySelectorAll('.filter-btn').forEach(b => b.classList.remove('active'));
    btn.classList.add('active');
    filter = btn.dataset.filter;
    render();
  });
});

// ── Init ──
render();
