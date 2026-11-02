# Client Unity 2.4.7 (Thời Đại Ngọc Rồng) - bản sửa cho server này

Chép đè 3 file trong `Assets/Scripts` vào project Unity `Project-2.4.7-Thoidaingocrong/Assets/Scripts`, rồi build APK như bình thường
(File > Build Profiles > Android > Build). Chi tiết thay đổi xem `thay_doi.patch`.

- `ServerListScreen.cs`: IP máy chủ `160.191.243.165:14445`, luôn dùng IP này (bỏ IP cũ lưu trong máy).
- `ModFunc.cs`: bỏ logo "Thời Đại Ngọc Rồng" vẽ trên màn hình trong game.
- `Service.cs`: gửi thêm `|long` khi kết nối để server biết client này đọc giao thức số lớn
  (mã chỉ số 2 byte, giá trị chỉ số 4 byte, HP / SĐ 8 byte) - không còn giới hạn 255 chỉ số và 32.767 giá trị.

Server cần bản `20.jar` từ Đợt 73 trở đi và `Config.properties` có `server.ip=160.191.243.165`.
