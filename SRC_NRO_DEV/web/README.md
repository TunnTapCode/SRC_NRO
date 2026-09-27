# Web dang ky tai khoan

Trang dang ky da duoc **tich hop vao Admin Panel** (tab "Dang ky" tren trang dang nhap).
Server Java van mo duong dan `/register` khi chay bang `ant run` nhung chi de **chuyen huong 302**
sang trang moi, de link in-game van dung duoc.

## Chay cung server game

```powershell
cd D:\Documents\SRC_NRO\SRC_NRO_DEV
ant run
```

Mo trinh duyet tai:

```text
http://127.0.0.1:8080/register            → redirect sang trang Dang ky (Admin Panel)
http://127.0.0.1:8081/admin/login         → tab Đăng nhập
http://127.0.0.1:8081/admin/login?mode=register → tab Đăng ký
```

API dang ky: `POST /api/auth/register { username, password, confirm, email, nonce }`
(dung chung nonce mot lan voi form dang nhap, co chong spam theo IP).

Web dung cung database va JDBC cua server game, doc cac gia tri `database.*` trong `Config.properties`.
Khong dat thu muc `web` tren mot web server cong khai neu chua cau hinh HTTPS, firewall va tai khoan MySQL rieng co quyen toi thieu.

## Trang dang nhap Admin Panel

Admin Panel (`/admin`) **bat buoc dang nhap** bang tai khoan co quyen quan tri:

```text
http://127.0.0.1:8081/admin/login
```

- Chi tai khoan trong bang `account` co `is_admin = 1`, `ban = 0` va `active <> 0` moi dang nhap duoc.
- Phien lam viec luu o server, gan voi cookie `NRO_ADMIN_SESSION` (HttpOnly, SameSite=Strict).
- Moi request thay doi du lieu (POST/PUT/DELETE) phai kem header `X-CSRF-Token`; form dang nhap dung nonce dung mot lan.
- Sai qua nhieu lan se bi tam khoa theo cap (username + IP).
- Bam **Dang xuat** o goc tren phai de huy phien.

### Cau hinh trong `Config.properties`

```properties
admin.auth.enabled=true      # false = tat xac thuc (chi nen dung khi dev)
admin.session.minutes=120    # thoi gian song cua phien
admin.cookie.secure=false    # dat true khi chay sau HTTPS
admin.login.maxattempts=5    # so lan sai truoc khi bi khoa tam
admin.login.lockminutes=5    # thoi gian khoa tam (phut)
admin.trust.proxy=false      # true = tin header X-Forwarded-For
```

### Cap quyen admin cho mot tai khoan

```sql
UPDATE account SET is_admin = 1, ban = 0, active = 1 WHERE username = 'ten_tai_khoan';
```

Sau khi doi `is_admin` trong database, tai khoan do phai **dang nhap lai** moi co hieu luc.
