# 03 用 Docker 运行

目标：本机不装 JDK、不装 MySQL，一条命令把数据库和应用都跑起来。

## 前置条件

只有一条：装好 Docker Desktop（Windows / macOS）或 docker + compose 插件（Linux），并且 Docker 正在运行。

```bash
docker --version
docker compose version
```

两条命令都能输出版本号才可以用。

## 启动

在项目根目录（能看到 `compose.yaml` 的那一层）执行：

```bash
docker compose --profile full up -d --build
```

三个参数的作用：

- `--profile full`：让 compose 同时启动 `mysql` 和 `app` 两个服务。不加只会起数据库。
- `-d`：后台运行。
- `--build`：先构建应用镜像。**改了代码必须加**，否则会复用旧镜像。

首次构建要在容器里重新解析依赖，视网络情况约 5–15 分钟；之后再改代码重建只走编译层，快很多。

## 打开页面

等容器状态变为 `healthy`：

```bash
docker compose --profile full ps
```

然后访问 <http://localhost:8080/>，用 `admin` / `admin123` 登录。

看日志：

```bash
docker compose logs -f app
```

出现 `Started SpringBookApplication` 即启动完成。

## 配置

默认配置就能跑，不需要 `.env`：

| 项 | 默认值 | 改法 |
|---|---|---|
| 应用端口 | `8080` | 新建 `.env` 写 `APP_PORT=8081` |
| 数据库对外端口 | `3307` | `.env` 写 `MYSQL_PORT=3308` |
| 管理员口令 | `admin123` | `.env` 写 `ADMIN_PASSWORD=...` |

容器内部用服务名 `mysql:3306` 连库，不经过宿主机的 3307 映射，所以本机有没有 MySQL 都不影响。

## 停止与清理

```bash
docker compose --profile full stop     # 停容器，数据保留，start 可秒回
docker compose --profile full start
docker compose --profile full down     # 移除容器与网络，数据卷保留
docker compose --profile full down -v  # 连数据卷一起删，库清空
```

| 操作 | 容器 | 数据 |
|---|---|---|
| `stop` / `start` | 保留 | 保留 |
| `down` | 移除，`up` 时重建 | **保留** |
| `down -v` | 移除 | **删除** |

## 不加 `--profile full` 会怎样

这一点容易踩，单独说明：

| 命令 | 实际效果 |
|---|---|
| `docker compose up -d` | 只起 MySQL |
| `docker compose stop` | 只停 MySQL，应用容器还在跑，随后因连不上库报错 |
| `docker compose down` | 删掉 MySQL，应用变成孤儿容器，下次 `up` 重建库 |

要么每条命令都带 `--profile full`，要么在 `.env` 里写 `COMPOSE_PROFILES=full` 一次性解决。两条路不要混用。

更多细节（镜像构成、体积、验证记录）见 [开发者手册](开发者手册.md)。

## 和方式 ① 的关系

Docker 路线和直接运行 jar 用的是同一套代码、同一份迁移脚本，数据不互通：

- Docker 的数据在命名卷里。
- 方式 ① 的数据在你本机的 MySQL 里。

两边不要同时启动，会抢 8080 端口。
