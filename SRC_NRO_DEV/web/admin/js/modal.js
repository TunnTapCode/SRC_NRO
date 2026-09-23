// Mở modal thêm mới hoặc sửa dữ liệu
function openModal(type, row) {
    const modal     = document.getElementById('entity-modal');
    const title     = document.getElementById('modal-title');
    const form      = document.getElementById('modal-form');
    const container = document.getElementById('modal-fields');
    if (!modal || !title || !form || !container) return;

    const fields = FORM_CONFIG[type] || [];
    title.textContent = row ? 'Sửa dữ liệu' : 'Thêm mới';
    form.reset();

    const hiddenId = form.querySelector('input[name="id"]');
    if (hiddenId) hiddenId.value = row ? (row.id ?? '') : '';

    container.innerHTML = fields.map((field) => {
        const value    = row ? (row[field.name] ?? '') : '';
        const required = field.required ? ' required' : '';

        if (field.type === 'select') {
            const options = field.options.map((opt) =>
                `<option value="${opt.value}" ${String(value) === String(opt.value) ? 'selected' : ''}>${opt.text}</option>`
            ).join('');
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
                <input name="${field.name}" type="${field.type}" value="${value}"
                       placeholder="${field.placeholder || ''}"${required} />
            </label>
        `;
    }).join('');

    form.dataset.resource = type;
    modal.classList.remove('hidden');
    modal.setAttribute('aria-hidden', 'false');
}

// Đóng modal và reset form
function closeModal() {
    const modal = document.getElementById('entity-modal');
    if (!modal) return;
    modal.classList.add('hidden');
    modal.setAttribute('aria-hidden', 'true');
    const form = document.getElementById('modal-form');
    if (form) form.reset();
}
