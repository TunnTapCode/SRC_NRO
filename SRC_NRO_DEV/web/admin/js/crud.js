// Thêm mới hoặc cập nhật một bản ghi
async function addOrUpdateData(type, payload) {
    const id = payload.id ? Number(payload.id) : null;
    const body = { ...payload };
    delete body.id;

    try {
        if (id) {
            // PUT phải gửi id trong URL: /api/{resource}/{id}
            await apiRequest(`${RESOURCE_META[type].resource}/${id}`, {
                method: 'PUT',
                body: JSON.stringify(body)
            });
        } else {
            await apiRequest(RESOURCE_META[type].resource, {
                method: 'POST',
                body: JSON.stringify(body)
            });
        }
        await loadTableData(type);
    } catch (error) {
        console.error('Save failed', error);
        alert('Lưu dữ liệu thất bại: ' + error.message);
    }
}

// Xóa một bản ghi theo id
async function deleteData(type, id) {
    try {
        await apiRequest(`${RESOURCE_META[type].resource}/${id}`, {
            method: 'DELETE'
        });
        await loadTableData(type);
    } catch (error) {
        console.error('Delete failed', error);
        alert('Xóa dữ liệu thất bại: ' + error.message);
    }
}

// Tìm bản ghi trong cache rồi mở modal sửa
function editData(type, id) {
    const rows = state.data[type] || [];
    const row  = rows.find((item) => Number(item.id) === Number(id));
    if (!row) return;
    showTab(type);
    openModal(type, row);
}
