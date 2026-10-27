FROM caddy:2.10-alpine
COPY docker-cloud/Caddyfile /etc/caddy/Caddyfile
EXPOSE 80
