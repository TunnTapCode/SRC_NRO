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
