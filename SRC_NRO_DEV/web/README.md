# Web dang ky tai khoan

Server Java tu dong mo trang dang ky khi chay bang `ant run`.

## Chay cung server game

```powershell
cd D:\Documents\SRC_NRO\SRC_NRO_DEV
ant run
```

Mo trinh duyet tai:

```text
http://127.0.0.1:8080/register
```

Web dung cung database va JDBC cua server game, doc cac gia tri `database.*` trong `Config.properties`.
Khong dat thu muc `web` tren mot web server cong khai neu chua cau hinh HTTPS, firewall va tai khoan MySQL rieng co quyen toi thieu.
