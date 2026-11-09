# Image chạy máy chủ NRO từ 20.jar đã ký sẵn trong repo (server từ chối jar chưa ký, nên không build lại ở đây).
FROM eclipse-temurin:21-jre
WORKDIR /srv/nro
COPY 20.jar Config.properties maintenanceConfig.txt run_vps_yeu.sh ./
COPY data ./data
EXPOSE 14445 8088
ENV RAM_MAX=448m
# Sửa cấu hình (MySQL, mật khẩu panel) bằng cách mount Config.properties của bạn đè lên /srv/nro/Config.properties
CMD ["sh", "run_vps_yeu.sh"]
