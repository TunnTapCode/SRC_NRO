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

/**
 * Xác nhận 2 bước bằng toast góc phải màn hình — không dùng confirm()/alert() của trình duyệt.
 * Lần 1 hiện toast cảnh báo + đổi nhãn nút, lần 2 mới chạy.
 * @param {HTMLElement} btn  - Nút cần xác nhận
 * @param {string} message   - Nội dung toast cảnh báo
 * @param {Function} onYes   - Hàm chạy khi xác nhận lần 2
 * @param {string} label     - Nhãn sau khi bấm lần 1
 */
/**
 * Popup xác nhận dạng modal — thay thế confirm() trình duyệt & confirmByToast 2-click.
 * Trả về Promise<boolean>: true nếu user bấm Xác nhận, false nếu Hủy / Esc / click backdrop.
 * @param {Object} opts
 * @param {string} opts.title       - Tiêu đề modal (default 'Xác nhận xóa?')
 * @param {string} opts.message     - Nội dung chi tiết
 * @param {string} opts.confirmText - Nhãn nút xác nhận (default 'Xóa')
 * @param {string} opts.cancelText  - Nhãn nút hủy (default 'Hủy bỏ')
 * @param {boolean} opts.danger     - true = nút xác nhận màu đỏ (default true)
 * @param {string} opts.icon        - Emoji icon hiển thị (default '🗑️')
 * @returns {Promise<boolean>}
 */
function showConfirmModal({
    title = 'Xác nhận xóa?',
    message = 'Bạn có chắc chắn muốn thực hiện hành động này?',
    confirmText = 'Xóa',
    cancelText = 'Hủy bỏ',
    danger = true,
    icon = '🗑️',
} = {}) {
    return new Promise((resolve) => {
        // Dọn modal cũ nếu còn sót (tránh chồng nhiều lớp)
        document.getElementById('confirm-modal')?.remove();

        const overlay = document.createElement('div');
        overlay.id = 'confirm-modal';
        overlay.className = 'confirm-modal';
        overlay.setAttribute('role', 'dialog');
        overlay.setAttribute('aria-modal', 'true');
        overlay.innerHTML = `
            <div class="confirm-modal-backdrop" data-confirm-close></div>
            <div class="confirm-modal-box ${danger ? 'is-danger' : 'is-info'}" role="document">
                <div class="confirm-modal-icon">${icon}</div>
                <h3 class="confirm-modal-title"></h3>
                <p class="confirm-modal-msg"></p>
                <div class="confirm-modal-actions">
                    <button type="button" class="confirm-modal-btn confirm-modal-cancel"></button>
                    <button type="button" class="confirm-modal-btn confirm-modal-ok ${danger ? 'is-danger' : 'is-info'}"></button>
                </div>
            </div>
        `;
        // Gán text bằng textContent để tránh XSS từ tên bản đồ / NPC
        overlay.querySelector('.confirm-modal-title').textContent = title;
        overlay.querySelector('.confirm-modal-msg').textContent = message;
        overlay.querySelector('.confirm-modal-cancel').textContent = cancelText;
        overlay.querySelector('.confirm-modal-ok').textContent = confirmText;

        document.body.appendChild(overlay);
        // Khóa scroll nền trong lúc xác nhận
        const prevOverflow = document.body.style.overflow;
        document.body.style.overflow = 'hidden';

        const okBtn = overlay.querySelector('.confirm-modal-ok');
        const cancelBtn = overlay.querySelector('.confirm-modal-cancel');
        let settled = false;

        function cleanup(result) {
            if (settled) return;
            settled = true;
            document.removeEventListener('keydown', onKey, true);
            document.body.style.overflow = prevOverflow;
            overlay.classList.add('closing');
            // Đợi animation đóng xong mới remove khỏi DOM
            setTimeout(() => overlay.remove(), 140);
            resolve(result);
        }

        function onKey(e) {
            if (e.key === 'Escape') { e.stopPropagation(); cleanup(false); }
            else if (e.key === 'Enter') { e.stopPropagation(); cleanup(true); }
        }

        okBtn.addEventListener('click', () => cleanup(true));
        cancelBtn.addEventListener('click', () => cleanup(false));
        overlay.querySelector('[data-confirm-close]').addEventListener('click', () => cleanup(false));

        document.addEventListener('keydown', onKey, true);
        // Focus nút Hủy trước để tránh Enter vô tình xóa
        setTimeout(() => cancelBtn.focus(), 30);
    });
}

/**
 * Xác nhận 2 bước bằng toast góc phải màn hình — không dùng confirm()/alert() của trình duyệt.
 * Lần 1 hiện toast cảnh báo + đổi nhãn nút, lần 2 mới chạy.
 * @param {HTMLElement} btn  - Nút cần xác nhận
 * @param {string} message   - Nội dung toast cảnh báo
 * @param {Function} onYes   - Hàm chạy khi xác nhận lần 2
 * @param {string} label     - Nhãn sau khi bấm lần 1
 */
function confirmByToast(btn, message, onYes, label = '⚠ Bấm lần nữa để xác nhận') {
    if (!btn) return;
    clearTimeout(Number(btn.dataset.confirmTimer) || 0);

    if (btn.dataset.confirming === '1') {
        delete btn.dataset.confirming;
        btn.textContent = btn.dataset.originLabel || btn.textContent;
        onYes();
        return;
    }

    btn.dataset.originLabel = btn.textContent;
    btn.dataset.confirming = '1';
    btn.textContent = label;
    toast(message, 'warning', 4000);

    btn.dataset.confirmTimer = String(setTimeout(() => {
        delete btn.dataset.confirming;
        btn.textContent = btn.dataset.originLabel;
    }, 4000));
}
