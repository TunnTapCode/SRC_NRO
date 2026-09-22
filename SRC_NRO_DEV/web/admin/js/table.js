// Chuẩn hóa giá trị ô trong bảng
function normalizeRowValue(key, value) {
    if (value === null || value === undefined) return '';
    if (typeof value === 'boolean') return value ? 'true' : 'false';
    if (typeof value === 'number') return value;
    return String(value);
}

// Render dữ liệu vào tbody tương ứng
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

            // Hiển thị icon cho cột icon_id của items
            if (key === 'items' && column === 'icon_id') {
                const iconId = row.icon_id ?? row.iconID ?? 0;
                return `<td>
                    <img src="/data/icon/x1/${iconId}.png" alt="item-${iconId}"
                         style="width:32px;height:32px;object-fit:contain;border-radius:8px;background:rgba(255,255,255,0.04);"
                         onerror="this.style.display='none'" />
                </td>`;
            }

            // Hiển thị Active/False cho các cột boolean
            if (column === 'active' || column === 'is_admin' || column === 'ban') {
                return `<td>${String(value) === 'true' ? 'Active' : 'False'}</td>`;
            }

            return `<td>${value}</td>`;
        }).join('');

        return `
            <tr>
                ${cells}
                <td>
                    <button class="btn-action edit"   data-type="${key}" data-id="${row.id}">Sửa</button>
                    <button class="btn-action delete" data-type="${key}" data-id="${row.id}">Xóa</button>
                </td>
            </tr>
        `;
    }).join('');
}

// Load dữ liệu từ API rồi render bảng
async function loadTableData(key) {
    const resource = RESOURCE_META[key].resource;
    try {
        const rows = await apiRequest(resource, { method: 'GET' });
        state.data[key] = Array.isArray(rows) ? rows : [];
    } catch (error) {
        console.error(error);
        state.data[key] = [];
    }

    // Các module có renderer riêng sẽ dùng hàm custom thay vì renderTable chung
    if (key === 'players' && typeof window.renderPlayersCustom === 'function') {
        window.renderPlayersCustom(state.data[key]);
        return;
    }

    renderTable(key, state.data[key], RESOURCE_META[key].columns);
}

// Render tất cả các bảng
function renderAll() {
    Object.keys(RESOURCE_META).forEach((key) => loadTableData(key));
}
