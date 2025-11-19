FROM python:3.9-slim

WORKDIR /app

# 暴露端口
EXPOSE ${APP_PORT}

# 启动脚本
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

ENTRYPOINT ["/entrypoint.sh"]

