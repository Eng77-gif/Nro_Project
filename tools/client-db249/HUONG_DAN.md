# Client Android DragonBoy249 (2.4.8 chính thức, sửa IP)

Client viết bằng Java (classes.dex không mã hoá chuỗi) nên đổi IP được bằng cách sửa chuỗi giữ nguyên độ dài.

## Đổi IP / ký lại

```
keytool -genkeypair -keystore key.p12 -storetype PKCS12 -alias nro -keyalg RSA -keysize 2048 -validity 10000 -dname CN=NRO   # chỉ lần đầu
python3 mod_apk.py DragonBoy249.apk 160.191.243.165 14445 DragonBoy249_mod.apk key.p12 <mật khẩu>
```

Cần `keytool`, `jarsigner` (JDK) và Python `cryptography`. Giữ `key.p12` cẩn thận, không đưa lên repo:
bản cập nhật sau phải ký cùng khoá mới cài đè được.

## Giao thức

Client gửi phiên bản 2.4.9 (đã sửa từ 2.4.8) → server bật `session.longNumbers` (Service.setClientType).
Đã đối chiếu bytecode của client với server:

- HP / KI / SĐ / sức mạnh / vàng / máu quái đọc 8 byte (long), dòng chỉ số đồ (short id, int param),
  id cờ / túi / quái / ảnh bang dạng short — giống hệt client J2ME 2.4.x, khớp chế độ long của server.
- Dữ liệu item (-28/8): client chỉ nhận MỘT gói loại 1 chứa toàn bộ item (bỏ qua gói loại 2), kích thước gói
  2 byte (≤ 65535) → server dùng nhánh `updateItemJ2me` (tên rút dấu khi cần, mô tả để trống).
- Gói độ dài 3 byte: -32, -66, -74, 11, -67, -87, 66 (và 12, server không gửi) — khớp MessageSendCollect.
- Gói client gửi lên: -63 (id cờ) short, 11 (id quái) short, -46 (ảnh bang) short — khớp Controller / ClanService.
