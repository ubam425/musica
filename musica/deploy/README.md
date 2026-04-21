# MusicUbam Deploy

Estos archivos son para subir `MusicUbam` a un VPS Ubuntu por Git.

## Estructura recomendada en el VPS

```text
/opt/musicubam
  ├─ repo/          <- aqui va el git clone
  ├─ shared/        <- archivo .env y logs
  └─ current -> /opt/musicubam/repo
```

## 1. Clonar el repo

```bash
mkdir -p /opt/musicubam
cd /opt/musicubam
git clone TU_REPO_GIT repo
ln -sfn /opt/musicubam/repo /opt/musicubam/current
```

## 2. Crear archivo de variables

```bash
mkdir -p /opt/musicubam/shared
nano /opt/musicubam/shared/musicubam.env
```

Usa como base `deploy/musicubam.env.example`.

## 3. Construir

```bash
cd /opt/musicubam/current
chmod +x mvnw
./mvnw clean package -DskipTests
```

## 4. Instalar servicio

```bash
cp deploy/musicubam.service /etc/systemd/system/musicubam.service
systemctl daemon-reload
systemctl enable musicubam
systemctl restart musicubam
systemctl status musicubam
```

## 5. Instalar Nginx

```bash
cp deploy/nginx-musicubam.conf /etc/nginx/sites-available/musicubam
ln -sfn /etc/nginx/sites-available/musicubam /etc/nginx/sites-enabled/musicubam
nginx -t
systemctl restart nginx
```

## 6. Actualizar despues de cambios

```bash
cd /opt/musicubam/current
git pull
./mvnw clean package -DskipTests
systemctl restart musicubam
journalctl -u musicubam -f
```
