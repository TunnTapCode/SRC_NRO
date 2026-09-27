// ══════════════════════════════════════════════════════
//  Trang Đăng nhập / Đăng ký tài khoản — Modern Dragon Boy UI
//  - POST /api/auth/login    { username, password, nonce }
//  - POST /api/auth/register { username, password, confirm, email, nonce }
//  - Nonce một lần chống replay/CSRF (dùng chung cho 2 form)
// ══════════════════════════════════════════════════════
(function () {
    'use strict';

    const form          = document.getElementById('login-form');
    const alertBox      = document.getElementById('login-alert');
    const submitBtn     = document.getElementById('login-submit');
    const nonceInput    = document.getElementById('login-nonce');
    const usernameInput = document.getElementById('username');
    const passwordInput = document.getElementById('password');
    const reloadBtn     = document.getElementById('btn-reload');
    const card          = document.getElementById('login-card');

    // ── Đăng ký (thay thế trang /register cũ) ──
    const registerForm  = document.getElementById('register-form');
    const registerBtn   = document.getElementById('register-submit');
    const regUsername   = document.getElementById('reg-username');
    const regPassword   = document.getElementById('reg-password');
    const regConfirm    = document.getElementById('reg-password-confirm');
    const regEmail      = document.getElementById('reg-email');

    // ── Chuyển đổi chế độ ──
    const tabLogin     = document.getElementById('tab-login');
    const tabRegister  = document.getElementById('tab-register');
    const authTitle    = document.getElementById('auth-title');
    const authSub      = document.getElementById('auth-sub');

    const MODE_COPY = {
        login: {
            title: 'Đăng nhập quản trị',
            sub: 'Hệ thống xác thực bảo mật đa tầng: <strong>HttpOnly Session</strong>, <strong>Anti-CSRF</strong> &amp; <strong>Brute-Force Guard</strong>.',
            document: 'Đăng nhập quản trị — SRC NRO'
        },
        register: {
            title: 'Đăng ký tài khoản',
            sub: 'Tạo tài khoản để đăng nhập game. Dữ liệu được bảo vệ bằng <strong>Nonce chống replay</strong> &amp; <strong>chống spam theo IP</strong>.',
            document: 'Đăng ký tài khoản — SRC NRO'
        }
    };

    let busy = false;
    let mode = 'login';

    function showAlert(message, type, title) {
        if (!alertBox) return;
        const iconEl    = alertBox.querySelector('.alert-icon');
        const titleEl   = alertBox.querySelector('.alert-title');
        const messageEl = alertBox.querySelector('.alert-message');

        const kind = type || 'error';
        const icons = { error: '⚠️', warning: '⏳', success: '✅' };
        const titles = {
            error:   title || (mode === 'register' ? 'Đăng ký không thành công' : 'Đăng nhập không thành công'),
            warning: title || 'Cảnh báo phiên',
            success: title || 'Thành công'
        };

        if (iconEl)    iconEl.textContent = icons[kind] || 'ℹ️';
        if (titleEl)   titleEl.textContent = titles[kind];
        if (messageEl) messageEl.textContent = message;
        else alertBox.textContent = message;

        alertBox.className = 'login-alert ' + kind + ' shake';
        alertBox.hidden = false;
        setTimeout(() => alertBox.classList.remove('shake'), 500);
    }

    function hideAlert() {
        if (alertBox) alertBox.hidden = true;
    }

    // Hiệu ứng tương tác 3D nhẹ khi di chuột
    if (card && window.matchMedia('(pointer: fine)').matches) {
        card.addEventListener('mousemove', function (e) {
            const rect = card.getBoundingClientRect();
            const x = e.clientX - rect.left - rect.width / 2;
            const y = e.clientY - rect.top - rect.height / 2;
            const rx = (-y / rect.height) * 6;
            const ry = (x / rect.width) * 6;
            card.style.transform = `perspective(1000px) rotateX(${rx.toFixed(2)}deg) rotateY(${ry.toFixed(2)}deg) translateY(-2px)`;
        });
        card.addEventListener('mouseleave', function () {
            card.style.transform = '';
        });
    }

    if (reloadBtn) {
        reloadBtn.addEventListener('click', () => window.location.reload());
    }

    // ── Chuyển đổi giữa 2 chế độ: Đăng nhập ↔ Đăng ký ──
    function setMode(next, skipUrl) {
        if (next !== 'login' && next !== 'register') return;
        mode = next;
        const isRegister = mode === 'register';

        if (form) form.hidden = isRegister;
        if (registerForm) registerForm.hidden = !isRegister;

        if (tabLogin) {
            tabLogin.classList.toggle('active', !isRegister);
            tabLogin.setAttribute('aria-selected', String(!isRegister));
        }
        if (tabRegister) {
            tabRegister.classList.toggle('active', isRegister);
            tabRegister.setAttribute('aria-selected', String(isRegister));
        }

        const copy = MODE_COPY[mode];
        if (authTitle) authTitle.textContent = copy.title;
        if (authSub) authSub.innerHTML = copy.sub;
        document.title = copy.document;

        hideAlert();

        if (!skipUrl) {
            try {
                const url = new URL(window.location.href);
                if (isRegister) url.searchParams.set('mode', 'register');
                else url.searchParams.delete('mode');
                window.history.replaceState(null, '', url);
            } catch (e) { /* URL không hỗ trợ — bỏ qua */ }
        }

        const focusTarget = isRegister ? regUsername : usernameInput;
        if (focusTarget) focusTarget.focus({ preventScroll: true });
    }

    if (tabLogin) tabLogin.addEventListener('click', () => setMode('login'));
    if (tabRegister) tabRegister.addEventListener('click', () => setMode('register'));

    // Hiện / ẩn mật khẩu (hỗ trợ mọi ô mật khẩu qua data-toggle, mặc định ô login)
    document.addEventListener('click', function (event) {
        const btn = event.target.closest('.toggle-password');
        if (!btn) return;
        const targetId = btn.getAttribute('data-toggle') || 'password';
        const input = document.getElementById(targetId);
        if (!input) return;
        const showing = input.type === 'text';
        input.type = showing ? 'password' : 'text';
        btn.setAttribute('aria-label', showing ? 'Hiện mật khẩu' : 'Ẩn mật khẩu');
        input.focus();
    });

    async function refreshNonce() {
        try {
            const response = await fetch('/api/auth/nonce', { method: 'GET', cache: 'no-store' });
            if (!response.ok) return;
            const data = await response.json();
            if (data && data.nonce) {
                nonceInput.value = data.nonce;
            }
        } catch (e) { /* giữ nonce cũ */ }
    }

    function setSubmitting(loading, text, button) {
        const target = button || submitBtn;
        busy = loading;
        target.disabled = loading;
        target.classList.toggle('loading', loading);
        const textEl = target.querySelector('.btn-text');
        if (textEl) textEl.textContent = text;
        else target.textContent = text;
    }

    form.addEventListener('submit', async function (event) {
        event.preventDefault();
        if (busy) return;
        hideAlert();

        const username = usernameInput.value.trim();
        const password = passwordInput.value;
        if (!username || !password) {
            showAlert('Vui lòng nhập đầy đủ tài khoản và mật khẩu.', 'error', 'Thiếu thông tin');
            if (!username) usernameInput.focus();
            else passwordInput.focus();
            return;
        }

        setSubmitting(true, 'Đang xác thực...');

        try {
            const response = await fetch('/api/auth/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'same-origin',
                cache: 'no-store',
                body: JSON.stringify({ username: username, password: password, nonce: nonceInput.value })
            });

            let data = {};
            try { data = await response.json(); } catch (e) { data = {}; }

            if (response.ok && data.success) {
                setSubmitting(true, 'Thành công! Đang chuyển hướng...');
                showAlert('Xác thực thành công. Đang tải trang quản trị...', 'success', 'Chào mừng!');
                setTimeout(() => window.location.replace('/admin/'), 350);
                return;
            }

            showAlert(data.message || 'Đăng nhập thất bại. Vui lòng kiểm tra lại.', 'error');
            await refreshNonce();
            passwordInput.value = '';
            passwordInput.focus();
        } catch (error) {
            showAlert('Không kết nối được server. Kiểm tra server game đã chạy chưa.', 'error', 'Lỗi kết nối');
        } finally {
            setSubmitting(false, 'Đăng nhập hệ thống');
        }
    });

    // ── Submit đăng ký (thay thế trang /register cũ) ──
    if (registerForm) {
        registerForm.addEventListener('submit', async function (event) {
            event.preventDefault();
            if (busy) return;
            hideAlert();

            const username = regUsername.value.trim().toLowerCase();
            const password = regPassword.value;
            const confirm  = regConfirm.value;
            const email    = regEmail.value.trim();

            if (!username) {
                showAlert('Vui lòng nhập tên tài khoản.', 'error', 'Thiếu thông tin');
                regUsername.focus();
                return;
            }
            if (!/^[a-z0-9]{4,20}$/.test(username)) {
                showAlert('Tài khoản chỉ gồm chữ thường và số, dài từ 4 đến 20 ký tự.', 'error', 'Tên tài khoản không hợp lệ');
                regUsername.focus();
                return;
            }
            if (password.length < 4 || password.length > 100) {
                showAlert('Mật khẩu phải dài từ 4 đến 100 ký tự.', 'error', 'Mật khẩu không hợp lệ');
                regPassword.focus();
                return;
            }
            if (password !== confirm) {
                showAlert('Mật khẩu nhập lại không khớp.', 'error', 'Xác nhận mật khẩu');
                regConfirm.focus();
                return;
            }
            if (email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
                showAlert('Email không hợp lệ.', 'error', 'Email không hợp lệ');
                regEmail.focus();
                return;
            }

            setSubmitting(true, 'Đang tạo tài khoản...', registerBtn);

            try {
                const response = await fetch('/api/auth/register', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    credentials: 'same-origin',
                    cache: 'no-store',
                    body: JSON.stringify({
                        username: username,
                        password: password,
                        confirm: confirm,
                        email: email,
                        nonce: nonceInput.value
                    })
                });

                let data = {};
                try { data = await response.json(); } catch (e) { data = {}; }

                if (response.ok && data.success) {
                    showAlert(data.message || 'Đăng ký thành công. Bạn có thể đăng nhập ngay.', 'success', 'Đăng ký thành công!');
                    // Điền sẵn tên tài khoản và chuyển về tab đăng nhập
                    usernameInput.value = username;
                    regPassword.value = '';
                    regConfirm.value = '';
                    regEmail.value = '';
                    await refreshNonce();
                    setTimeout(() => setMode('login'), 1500);
                    return;
                }

                showAlert(data.message || 'Đăng ký thất bại. Vui lòng thử lại.', 'error');
                await refreshNonce();
            } catch (error) {
                showAlert('Không kết nối được server. Kiểm tra server game đã chạy chưa.', 'error', 'Lỗi kết nối');
            } finally {
                setSubmitting(false, 'Tạo tài khoản', registerBtn);
            }
        });
    }

    // ── Khởi tạo mode từ URL (?mode=register — link /register cũ chuyển hướng tới) ──
    const initParams = new URLSearchParams(window.location.search);
    setMode(initParams.get('mode') === 'register' ? 'register' : 'login', true);
    if (initParams.get('expired') === '1') {
        showAlert('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.', 'warning', 'Hết hạn phiên');
    }
})();
