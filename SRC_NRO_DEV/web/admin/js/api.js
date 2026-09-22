// Gọi API REST, trả về JSON hoặc text tùy content-type
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
