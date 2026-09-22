// Danh sách các module cần load, theo đúng thứ tự menu
const MODULES = ['dashboard', 'accounts', 'players', 'giftcodes', 'items', 'shops', 'npcs'];

/**
 * Parse chuỗi HTML thành các node thực thi được:
 * - <script> → tạo lại bằng createElement để trình duyệt thực thi
 * - <link>   → tạo lại bằng createElement để trình duyệt load CSS
 * - Các node khác → giữ nguyên
 * Trả về mảng các Node đã sẵn sàng append vào DOM.
 */
function parsePartialHTML(html) {
    const tmp = document.createElement('div');
    tmp.innerHTML = html;

    const nodes = [];

    tmp.childNodes.forEach((node) => {
        if (node.nodeType === Node.ELEMENT_NODE) {
            const tag = node.tagName.toLowerCase();

            if (tag === 'script') {
                // innerHTML không chạy script → phải tạo lại
                const script = document.createElement('script');
                if (node.src) {
                    script.src = node.src;
                } else {
                    script.textContent = node.textContent;
                }
                Array.from(node.attributes).forEach((attr) => {
                    if (attr.name !== 'src') script.setAttribute(attr.name, attr.value);
                });
                nodes.push(script);
                return;
            }

            if (tag === 'link') {
                // innerHTML không load link → phải tạo lại
                const link = document.createElement('link');
                Array.from(node.attributes).forEach((attr) => {
                    link.setAttribute(attr.name, attr.value);
                });
                nodes.push(link);
                return;
            }
        }
        // Node thường (section, div, text…) giữ nguyên
        nodes.push(node.cloneNode(true));
    });

    return nodes;
}

/**
 * Load tất cả partial song song, giữ đúng thứ tự DOM,
 * thực thi <script> và load <link> CSS trong mỗi partial.
 */
async function loadAllPartials() {
    const container = document.getElementById('modules-container');
    if (!container) return;

    // Tạo placeholder giữ thứ tự trước khi fetch song song
    const placeholders = MODULES.map((key) => {
        const el = document.createElement('div');
        el.dataset.placeholder = key;
        container.appendChild(el);
        return el;
    });

    await Promise.all(
        MODULES.map(async (key, i) => {
            try {
                const res = await fetch(`/admin/partials/${key}.html`);
                if (!res.ok) throw new Error(`HTTP ${res.status}`);
                const html = await res.text();

                const nodes = parsePartialHTML(html);

                // Thay placeholder bằng fragment chứa tất cả node
                const fragment = document.createDocumentFragment();
                nodes.forEach((n) => fragment.appendChild(n));
                placeholders[i].replaceWith(fragment);

            } catch (err) {
                console.error(`Không thể load partial "${key}":`, err);
                placeholders[i].remove();
            }
        })
    );
}
