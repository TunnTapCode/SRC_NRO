# Setup server tren may moi

## 1. Cai moi truong

Cai cac phan mem sau:

- Git
- JDK 17
- Apache Ant
- MySQL Server

Kiem tra:

```powershell
java -version
javac -version
ant -version
```

Project yeu cau Java 17.

## 2. Clone hoac pull code

Neu chua co project:

```powershell
git clone https://github.com/TunnTapCode/SRC_NRO.git
cd SRC_NRO\SRC_NRO_DEV
```

Neu da co project:

```powershell
git pull origin main
cd SRC_NRO_DEV
```

## 3. Chuan bi asset

Cac asset game khong duoc commit len GitHub vi dung luong lon. Can copy thu cong thu muc `data` tu may cu sang `SRC_NRO_DEV` tren may moi, dac biet:

```text
SRC_NRO_DEV/data/icon/x4
SRC_NRO_DEV/data/img_by_name
SRC_NRO_DEV/data/item_bg_temp
SRC_NRO_DEV/data/map
SRC_NRO_DEV/data/mob
SRC_NRO_DEV/data/res
SRC_NRO_DEV/data/update_data
```

Neu thieu asset, server co the loi khi doc map, item, mob hoac du lieu cap nhat.

## 4. Tao database

Tao database co ten trung voi `Config.properties`:

```sql
CREATE DATABASE nro_db CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

Import du lieu:

```powershell
mysql -u root -p nro_db < b.sql
```

Neu may khong nhan lenh `mysql`, dung MySQL Workbench de mo va chay file `b.sql`.

## 5. Cau hinh server

Mo file:

```text
SRC_NRO_DEV/Config.properties
```

Cau hinh database:

```properties
database.driver=com.mysql.cj.jdbc.Driver
database.host=localhost
database.port=3306
database.name=nro_db
database.user=root
database.pass=MAT_KHAU_MYSQL
```

Cau hinh server local:

```properties
server.name=Black Goku
server.ip=127.0.0.1
server.port=14445
server.sv1=Black Goku 01:127.0.0.1:14445:0,0,0
```

Neu cho nguoi choi ket noi tu Internet, thay `server.ip` bang IP public cua VPS va mo port `14445/TCP` tren firewall.

## 6. Build project

Luon chay lenh trong thu muc co file `build.xml`:

```powershell
cd SRC_NRO_DEV
ant clean jar
```

Neu build thanh cong se co file:

```text
SRC_NRO_DEV/dist/NgocRongOnline.jar
```

## 7. Chay server

```powershell
cd SRC_NRO_DEV
ant run
```

Hoac:

```powershell
java -server -jar SRC_NRO.jar
```

Khuyen nghi dung `ant run` vi lenh nay dung classpath va cac thu vien trong `lib`.

## 8. Them nhieu server vao danh sach

Danh sach server duoc doc tu `Config.properties`. Co the them `server.sv2` den `server.sv5`:

```properties
server.sv1=Black Goku 01:IP_SERVER:14445:0,0,0
server.sv2=Black Goku 02:IP_SERVER:14446:0,0,0
server.sv3=Black Goku 03:IP_SERVER:14447:0,0,0
```

Moi server phai chay mot process rieng va dung port rieng. Chi them dong cau hinh khong tu dong tao server moi.

## 9. Neu gap loi

- `Buildfile: build.xml does not exist`: dang chay sai thu muc, can vao `SRC_NRO_DEV`.
- `The import lombok cannot be resolved`: mo dung workspace, sau do chay `Java: Clean Java Language Server Workspace` trong VS Code.
- `caching_sha2_password`: project da dung Connector/J moi va driver `com.mysql.cj.jdbc.Driver`; kiem tra lai user, mat khau va quyen MySQL.
- Loi thieu file `data/...`: copy asset tu may cu.
- Loi port dang duoc su dung: doi `server.port` hoac dung process dang chiem port.

## 10. Luu y bao mat

Khong commit mat khau MySQL that len GitHub. File `Config.properties` nen duoc cau hinh rieng tren tung may chu.
