// Chuyển sang tab được chọn
function showTab(target) {
    state.currentTab = target;

    const titleEl      = document.getElementById('page-title');
    const activeButton = document.querySelector(`.menu-item[data-target="${target}"]`);
    if (titleEl && activeButton) {
        titleEl.textContent = activeButton.textContent;
    }

    document.querySelectorAll('.menu-item').forEach((btn) => {
        btn.classList.toggle('active', btn.dataset.target === target);
    });
    document.querySelectorAll('.module').forEach((section) => {
        section.classList.toggle('active', section.id === target);
    });

    // Kích hoạt load dữ liệu riêng cho từng tab
    if (target === 'dashboard') {
        document.dispatchEvent(new Event('dashboardActivated'));
    }
}

// Chuyển tab và mở form thêm mới
function openAddForm(type) {
    showTab(type);
    openModal(type, null);
}

// Lấy toàn bộ dữ liệu từ các field trong form
function getFormData(form) {
    const data = {};
    Array.from(form.querySelectorAll('input, select')).forEach((field) => {
        if (!field.name) return;
        data[field.name] = field.value;
    });
    return data;
}

// Reset form theo id
function resetForm(formId) {
    const form = document.getElementById(formId);
    if (!form) return;
    form.reset();
    const hiddenId = form.querySelector('input[name="id"]');
    if (hiddenId) hiddenId.value = '';
}

// Tạo badge màu theo trạng thái (dùng khi cần hiển thị badge)
function formatStatus(value) {
    const colorMap = { active: 'success', locked: 'warning', banned: 'danger' };
    return `<span class="badge ${colorMap[value] || 'success'}">${value}</span>`;
}

/**
 * Toast notification
 * @param {string} msg     - Nội dung
 * @param {'success'|'error'|'warning'|'info'} type
 * @param {number} duration - ms, default 5000
 */
function toast(msg, type = 'info', duration = 5500) {
    const container = document.getElementById('toast-container');
    if (!container) return;

    const icons = { success: '✅', error: '❌', warning: '⚠️', info: 'ℹ️' };
    const titles = { success: 'Thành công', error: 'Lỗi', warning: 'Cảnh báo', info: 'Thông báo' };

    const el = document.createElement('div');
    el.className = `toast toast-${type}`;
    el.innerHTML = `
        <span class="toast-icon">${icons[type] || 'ℹ️'}</span>
        <div class="toast-body">
            <div class="toast-title">${titles[type] || 'Thông báo'}</div>
            <div class="toast-msg">${msg}</div>
        </div>
        <button class="toast-close" aria-label="Đóng">✕</button>
        <div class="toast-progress" style="animation-duration:${duration}ms"></div>
    `;

    container.appendChild(el);

    // Đóng khi nhấn ✕
    el.querySelector('.toast-close').addEventListener('click', () => dismiss(el));

    // Auto dismiss
    const timer = setTimeout(() => dismiss(el), duration);

    function dismiss(node) {
        clearTimeout(timer);
        node.classList.add('hide');
        node.addEventListener('animationend', () => node.remove(), { once: true });
    }
}
