# Ghi chú cho Claude (Nro_Project)

- Mỗi lần sửa trang web người chơi (`data/web/`), sau khi đẩy lên main thì tự đưa lên Cloudflare luôn:
  `cd cloudflare && CF_API_TOKEN=proxy-injected CF_ACCOUNT_ID=7e6662f0bd4990e012ecf6c24cbb0b23 bash deploy_worker.sh && bash build.sh`
  (token do proxy gắn, không ghi token thật vào repo). Kiểm tra lại https://nro-web.dov77471.workers.dev.
