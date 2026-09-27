// ══════════════════════════════════════════════════════
//  Lớp xác thực phía client cho Admin Panel
//  - Tự gắn header X-CSRF-Token cho mọi request thay đổi dữ liệu
//  - Tự chuyển về trang đăng nhập khi phiên hết hạn (HTTP 401)
//  - Nạp thông tin admin + CSRF token trước khi panel tải dữ liệu
// ══════════════════════════════════════════════════════
(function () {
    'use strict';

    const CSRF_HEADER = 'X-CSRF-Token';
    const LOGIN_PATH  = '/admin/login';

    const nativeFetch = window.fetch.bind(window);

    window.__csrfToken = window.__csrfToken || null;
    window.__adminUser = window.__adminUser || null;

    function isStateChanging(method) {
        const normalized = String(method || 'GET').toUpperCase();
        return normalized !== 'GET' && normalized !== 'HEAD' && normalized !== 'OPTIONS';
    }

    function redirectToLogin() {
        if (window.location.pathname.indexOf(LOGIN_PATH) === 0) return;
        window.location.replace(LOGIN_PATH + '?expired=1');
    }

    // ── Bọc fetch để gắn CSRF token + xử lý phiên hết hạn tập trung ──
    window.fetch = function (input, init) {
        const options = Object.assign({}, init || {});
        options.credentials = options.credentials || 'same-origin';

        let method = options.method;
        if (!method && typeof input !== 'string' && input && input.method) {
            method = input.method;
        }

        const requestHeaders = options.headers || (typeof input !== 'string' && input ? input.headers : null);
        const headers = new Headers(requestHeaders || {});
        if (window.__csrfToken && isStateChanging(method)) {
            headers.set(CSRF_HEADER, window.__csrfToken);
        }
        options.headers = headers;

        return nativeFetch(input, options).then(function (response) {
            if (response.status === 401) {
                redirectToLogin();
                // Không trả về response để code phía sau không chạy tiếp trên phiên đã mất
                return new Promise(function () {});
            }
            if (response.status === 403) {
                // CSRF token có thể đã cũ (server restart) → làm mới cho lần gọi sau
                bootstrapAuth(true);
            }
            return response;
        });
    };

    /**
     * Lấy thông tin phiên hiện tại.
     * @param {boolean} silent chỉ làm mới token, không ghi log lỗi
     */
    async function bootstrapAuth(silent) {
        try {
            const response = await nativeFetch('/api/auth/session', {
                method: 'GET',
                credentials: 'same-origin',
                cache: 'no-store'
            });
            if (response.status === 401) {
                if (!silent) redirectToLogin();
                return null;
            }
            if (!response.ok) return null;
            const data = await response.json();
            if (data && data.authenticated) {
                window.__csrfToken = data.csrfToken || null;
                window.__adminUser = data.username || null;
                return data;
            }
        } catch (error) {
            if (!silent) {
                console.error('Không kiểm tra được phiên đăng nhập:', error);
            }
        }
        return null;
    }

    function onDomReady(callback) {
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', callback, { once: true });
        } else {
            callback();
        }
    }

    /** Đăng xuất: hủy phiên trên server rồi quay về trang đăng nhập. */
    window.logoutAdmin = async function () {
        try {
            await window.fetch('/api/auth/logout', { method: 'POST' });
        } catch (error) {
            // Dù lỗi mạng vẫn đưa người dùng về trang đăng nhập
        }
        window.__csrfToken = null;
        window.__adminUser = null;
        window.location.replace(LOGIN_PATH);
    };

    // main.js chờ promise này trước khi render dữ liệu
    window.__authReady = bootstrapAuth(false);

    onDomReady(function () {
        window.__authReady.then(function (session) {
            const nameEl = document.getElementById('admin-user-name');
            if (nameEl && session && session.username) {
                nameEl.textContent = session.username;
            }
            const chip = document.getElementById('admin-user');
            if (chip && session && session.username) {
                chip.title = 'Đang đăng nhập: ' + session.username;
            }
            const logoutBtn = document.getElementById('btn-logout');
            if (logoutBtn) {
                logoutBtn.addEventListener('click', function () {
                    window.logoutAdmin();
                });
            }
        });
    });
})();
