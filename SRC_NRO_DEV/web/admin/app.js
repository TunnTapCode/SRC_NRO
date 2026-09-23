const RESOURCE_META = {
    accounts: { resource: 'accounts', columns: ['id', 'username', 'email', 'is_admin', 'active', 'ban'] },
    players: { resource: 'players', columns: ['id', 'account_id', 'name', 'head', 'gender', 'clan_id', 'rank', 'power'] },
    giftcodes: { resource: 'giftcodes', columns: ['id', 'code', 'count_left', 'detail', 'expired'] },
    items: { resource: 'items', columns: ['id', 'TYPE', 'NAME', 'description', 'level', 'icon_id', 'part', 'is_up_to_up', 'power_require', 'gold', 'gem', 'head', 'body', 'leg'] },
    shops: { resource: 'shops', columns: ['id', 'npc_id', 'tag_name', 'type_shop'] },
    npcs: { resource: 'npcs', columns: ['id', 'NAME', 'head', 'body', 'leg', 'avatar'] }
};

const FORM_CONFIG = {
    accounts: [
        { name: 'username', label: 'Username', type: 'text', required: true },
        { name: 'email', label: 'Email', type: 'email', required: true },
        { name: 'password', label: 'Mật khẩu', type: 'password', placeholder: 'Để trống nếu giữ nguyên' },
        { name: 'is_admin', label: 'Admin', type: 'select', options: [{value: '1', text: 'Có'}, {value: '0', text: 'Không'}] },
        { name: 'active', label: 'Active', type: 'select', options: [{value: '1', text: 'Có'}, {value: '0', text: 'Không'}] },
        { name: 'ban', label: 'Ban', type: 'select', options: [{value: '1', text: 'Có'}, {value: '0', text: 'Không'}] }
    ],
    players: [
        { name: 'account_id', label: 'Account ID', type: 'number', required: true },
        { name: 'name', label: 'Tên', type: 'text', required: true },
        { name: 'head', label: 'Head', type: 'number', required: true },
        { name: 'gender', label: 'Gender', type: 'number' },
        { name: 'clan_id', label: 'Clan ID', type: 'number' },
        { name: 'rank', label: 'Rank', type: 'number' },
        { name: 'power', label: 'Power', type: 'number' }
    ],
    giftcodes: [
        { name: 'code', label: 'Code', type: 'text', required: true },
        { name: 'count_left', label: 'Số lượng còn', type: 'number', required: true },
        { name: 'detail', label: 'Detail', type: 'text' },
        { name: 'expired', label: 'Expired', type: 'text' }
    ],
    items: [
        { name: 'TYPE', label: 'TYPE', type: 'text', required: true },
        { name: 'NAME', label: 'NAME', type: 'text', required: true },
        { name: 'description', label: 'Description', type: 'text' },
        { name: 'level', label: 'Level', type: 'number' },
        { name: 'icon_id', label: 'Icon ID', type: 'number' },
        { name: 'part', label: 'Part', type: 'number' },
        { name: 'is_up_to_up', label: 'Up to up', type: 'number' },
        { name: 'power_require', label: 'Power req', type: 'number' },
        { name: 'gold', label: 'Gold', type: 'number' },
        { name: 'gem', label: 'Gem', type: 'number' },
        { name: 'head', label: 'Head', type: 'number' },
        { name: 'body', label: 'Body', type: 'number' },
        { name: 'leg', label: 'Leg', type: 'number' }
    ],
    shops: [
        { name: 'npc_id', label: 'NPC ID', type: 'number', required: true },
        { name: 'tag_name', label: 'Tag name', type: 'text', required: true },
        { name: 'type_shop', label: 'Type shop', type: 'text', required: true }
    ],
    npcs: [
        { name: 'NAME', label: 'NAME', type: 'text', required: true },
        { name: 'head', label: 'Head', type: 'number' },
        { name: 'body', label: 'Body', type: 'number' },
        { name: 'leg', label: 'Leg', type: 'number' },
        { name: 'avatar', label: 'Avatar', type: 'number' }
    ]
};

const state = {
    currentTab: 'accounts',
    data: {}
};

async function apiRequest(resource, options = {}) {
    const response = await fetch(`/api/${resource}`, {
        headers: { 'Content-Type': 'application/json' },
        ...options
    });
    if (!response.ok) {
        const text = await response.text();
        throw new Error(`${resource}: ${response.status} ${text}`);
    }
    const contentType = response.headers.get('content-type') || '';
    if (contentType.includes('application/json')) {
        return response.json();
    }
    return response.text();
}

async function loadTableData(key) {
    const resource = RESOURCE_META[key].resource;
    try {
        const rows = await apiRequest(resource, { method: 'GET' });
        state.data[key] = Array.isArray(rows) ? rows : [];
        renderTable(key, state.data[key], RESOURCE_META[key].columns);
    } catch (error) {
        console.error(error);
        state.data[key] = [];
        renderTable(key, [], RESOURCE_META[key].columns);
    }
}

function normalizeRowValue(key, value) {
    if (value === null || value === undefined) return '';
    if (typeof value === 'boolean') return value ? 'true' : 'false';
    if (typeof value === 'number') return value;
    return String(value);
}

function saveData(key, value) {
    state.data[key] = value;
}

function formatStatus(value) {
    const colorMap = {
        active: 'success',
        locked: 'warning',
        banned: 'danger'
    };
    return `<span class="badge ${colorMap[value] || 'success'}">${value}</span>`;
}

function getFormData(form) {
    const data = {};
    Array.from(form.querySelectorAll('input, select')).forEach((field) => {
        if (!field.name) return;
        data[field.name] = field.value;
    });
    return data;
}

function renderTable(key, rows, columns) {
    const tbody = document.getElementById(`${key}-table`);
    if (!tbody) return;

    if (!rows || rows.length === 0) {
        tbody.innerHTML = `<tr><td colspan="${columns.length + 1}">Không có dữ liệu</td></tr>`;
        return;
    }

    tbody.innerHTML = rows.map((row) => {
        const cells = columns.map((column) => {
            const value = normalizeRowValue(key, row[column]);
            if (key === 'items' && column === 'icon_id') {
                const iconId = row.icon_id ?? row.iconID ?? 0;
                return `<td><img src="/data/icon/x4/${iconId}.png" alt="item-${iconId}" style="width: 32px; height: 32px; object-fit: contain; border-radius: 8px; background: rgba(255,255,255,0.04);" onerror="this.style.display='none'" /></td>`;
            }
            if (column === 'active' || column === 'is_admin' || column === 'ban') {
                return `<td>${String(value) === 'true' ? 'Active' : 'False'}</td>`;
            }
            return `<td>${value}</td>`;
        }).join('');

        return `
            <tr>
                ${cells}
                <td>
                    <button class="btn-action edit" data-type="${key}" data-id="${row.id}">Sửa</button>
                    <button class="btn-action delete" data-type="${key}" data-id="${row.id}">Xóa</button>
                </td>
            </tr>
        `;
    }).join('');
}

function renderAll() {
    Object.keys(RESOURCE_META).forEach((key) => {
        loadTableData(key);
    });
}

function resetForm(formId) {
    const form = document.getElementById(formId);
    if (!form) return;
    form.reset();
    const hiddenId = form.querySelector('input[name="id"]');
    if (hiddenId) hiddenId.value = '';
}

function showTab(target) {
    state.currentTab = target;
    const titleEl = document.getElementById('page-title');
    const activeButton = document.querySelector(`.menu-item[data-target="${target}"]`);
    if (titleEl && activeButton) {
        titleEl.textContent = activeButton.textContent;
    }

    document.querySelectorAll('.menu-item').forEach((button) => {
        button.classList.toggle('active', button.dataset.target === target);
    });
    document.querySelectorAll('.module').forEach((section) => {
        section.classList.toggle('active', section.id === target);
    });
}

function openAddForm(type) {
    showTab(type);
    openModal(type, null);
}

function openModal(type, row) {
    const modal = document.getElementById('entity-modal');
    const title = document.getElementById('modal-title');
    const form = document.getElementById('modal-form');
    const container = document.getElementById('modal-fields');
    if (!modal || !title || !form || !container) return;

    const fields = FORM_CONFIG[type] || [];
    title.textContent = row ? 'Sửa dữ liệu' : 'Thêm mới';
    form.reset();
    const hiddenId = form.querySelector('input[name="id"]');
    if (hiddenId) hiddenId.value = row ? (row.id ?? '') : '';
    container.innerHTML = fields.map((field) => {
        const value = row ? (row[field.name] ?? '') : '';
        const required = field.required ? ' required' : '';
        if (field.type === 'select') {
            const options = field.options.map((option) => `
                <option value="${option.value}" ${String(value) === String(option.value) ? 'selected' : ''}>${option.text}</option>
            `).join('');
            return `
                <label>
                    <span>${field.label}</span>
                    <select name="${field.name}"${required}>${options}</select>
                </label>
            `;
        }
        return `
            <label>
                <span>${field.label}</span>
                <input name="${field.name}" type="${field.type}" value="${value}" placeholder="${field.placeholder || ''}"${required} />
            </label>
        `;
    }).join('');
    form.dataset.resource = type;
    modal.classList.remove('hidden');
    modal.setAttribute('aria-hidden', 'false');
}

function closeModal() {
    const modal = document.getElementById('entity-modal');
    if (!modal) return;
    modal.classList.add('hidden');
    modal.setAttribute('aria-hidden', 'true');
    const form = document.getElementById('modal-form');
    if (form) form.reset();
}

async function addOrUpdateData(type, payload) {
    const id = payload.id ? Number(payload.id) : null;
    const payloadForApi = { ...payload };
    if (payloadForApi.id !== undefined) delete payloadForApi.id;

    try {
        if (id) {
            await apiRequest(RESOURCE_META[type].resource, {
                method: 'PUT',
                body: JSON.stringify(payloadForApi)
            });
        } else {
            await apiRequest(RESOURCE_META[type].resource, {
                method: 'POST',
                body: JSON.stringify(payloadForApi)
            });
        }
        await loadTableData(type);
    } catch (error) {
        console.error('Save failed', error);
        alert('Lưu dữ liệu thất bại: ' + error.message);
    }
}

async function deleteData(type, id) {
    try {
        await apiRequest(RESOURCE_META[type].resource + '/' + id, {
            method: 'DELETE'
        });
        await loadTableData(type);
    } catch (error) {
        console.error('Delete failed', error);
        alert('Xóa dữ liệu thất bại: ' + error.message);
    }
}

function editData(type, id) {
    const rows = state.data[type] || [];
    const row = rows.find((item) => Number(item.id) === Number(id));
    if (!row) return;
    showTab(type);
    openModal(type, row);
}

document.addEventListener('DOMContentLoaded', () => {
    const sidebar = document.getElementById('sidebar');
    const sidebarToggle = document.getElementById('sidebar-toggle');

    if (sidebarToggle && sidebar) {
        sidebarToggle.addEventListener('click', () => {
            sidebar.classList.toggle('collapsed');
        });
    }

    document.querySelectorAll('.menu-item').forEach((button) => {
        button.addEventListener('click', () => showTab(button.dataset.target));
    });

    const addButton = document.getElementById('btn-add');
    if (addButton) {
        addButton.addEventListener('click', () => openAddForm(state.currentTab));
    }

    document.getElementById('modal-form').addEventListener('submit', async (event) => {
        event.preventDefault();
        const form = event.target;
        const type = form.dataset.resource;
        const payload = getFormData(form);

        if (payload.password && payload.password.trim() !== '') {
            payload.password = payload.password.trim();
        } else {
            delete payload.password;
        }

        await addOrUpdateData(type, payload);
        closeModal();
    });

    document.querySelectorAll('[data-close="true"]').forEach((el) => {
        el.addEventListener('click', closeModal);
    });

    document.querySelector('.modal-close').addEventListener('click', closeModal);

    document.addEventListener('click', (event) => {
        const editButton = event.target.closest('.btn-action.edit');
        if (editButton) {
            const type = editButton.dataset.type;
            const id = editButton.dataset.id;
            editData(type, id);
            return;
        }

        const deleteButton = event.target.closest('.btn-action.delete');
        if (deleteButton) {
            const type = deleteButton.dataset.type;
            const id = deleteButton.dataset.id;
            if (window.confirm('Bạn có chắc chắn muốn xóa dữ liệu này?')) {
                deleteData(type, id);
            }
        }
    });

    renderAll();
    showTab('accounts');
});
