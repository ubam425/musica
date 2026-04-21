#!/usr/bin/env bash
set -e

APP_ROOT="/opt/musicubam/current"

cd "$APP_ROOT"

chmod +x mvnw
./mvnw clean package -DskipTests
systemctl restart musicubam
systemctl status musicubam --no-pager
