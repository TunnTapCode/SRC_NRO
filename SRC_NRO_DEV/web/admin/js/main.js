// Khởi động ứng dụng sau khi DOM sẵn sàng
document.addEventListener('DOMContentLoaded', async () => {

    // 1. Load toàn bộ partial HTML trước khi làm bất cứ điều gì khác
    await loadAllPartials();

    // --- Sidebar toggle ---
    const sidebar       = document.getElementById('sidebar');
    const sidebarToggle = document.getElementById('sidebar-toggle');
    const mobileBtn     = document.getElementById('mobile-menu-btn');
    const sidebarClose  = document.getElementById('sidebar-close-btn');

    function syncSidebarButtonState() {
        const isCollapsed = sidebar.classList.contains('collapsed');
        if (mobileBtn) {
            mobileBtn.textContent = isCollapsed ? '☰' : '✕';
            mobileBtn.setAttribute('aria-label', isCollapsed ? 'Mở menu' : 'Đóng menu');
        }
        if (sidebarClose) {
            sidebarClose.textContent = '✕';
            sidebarClose.setAttribute('aria-label', isCollapsed ? 'Mở sidebar' : 'Đóng sidebar');
        }
    }

    function toggleSidebar() {
        if (!sidebar) return;
        sidebar.classList.toggle('collapsed');
        syncSidebarButtonState();
    }

    if (sidebarToggle && sidebar) {
        sidebarToggle.addEventListener('click', toggleSidebar);
    }
    if (mobileBtn && sidebar) {
        mobileBtn.addEventListener('click', toggleSidebar);
    }
    if (sidebarClose && sidebar) {
        sidebarClose.addEventListener('click', toggleSidebar);
    }

    syncSidebarButtonState();

    // --- Điều hướng menu ---
    document.querySelectorAll('.menu-item').forEach((btn) => {
        btn.addEventListener('click', () => showTab(btn.dataset.target));
    });

    // --- Nút thêm mới ---
    const addButton = document.getElementById('btn-add');
    if (addButton) {
        addButton.addEventListener('click', () => openAddForm(state.currentTab));
    }

    // --- Submit form trong modal ---
    document.getElementById('modal-form').addEventListener('submit', async (event) => {
        event.preventDefault();
        const form    = event.target;
        const type    = form.dataset.resource;
        const payload = getFormData(form);

        // Xử lý mật khẩu: bỏ qua nếu để trống
        if (payload.password && payload.password.trim() !== '') {
            payload.password = payload.password.trim();
        } else {
            delete payload.password;
        }

        await addOrUpdateData(type, payload);
        closeModal();
    });

    // --- Đóng modal ---
    document.querySelectorAll('[data-close="true"]').forEach((el) => {
        el.addEventListener('click', closeModal);
    });
    document.querySelector('.modal-close').addEventListener('click', closeModal);

    // --- Delegate click cho nút Sửa / Xóa trong bảng ---
    document.addEventListener('click', (event) => {
        const editBtn = event.target.closest('.btn-action.edit');
        if (editBtn) {
            editData(editBtn.dataset.type, editBtn.dataset.id);
            return;
        }

        const deleteBtn = event.target.closest('.btn-action.delete');
        if (deleteBtn && window.confirm('Bạn có chắc chắn muốn xóa dữ liệu này?')) {
            deleteData(deleteBtn.dataset.type, deleteBtn.dataset.id);
        }
    });

    // 2. Load dữ liệu và hiển thị tab đầu tiên
    renderAll();
    showTab('dashboard');
    // Trigger load dashboard
    document.dispatchEvent(new Event('dashboardActivated'));
});
