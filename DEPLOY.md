# Despliegue en Oracle Cloud Always Free — "Pueblo Duerme"

## Opción A: VM Always Free (recomendada para control total)

### 1. Crear la VM
1. Ve a [Oracle Cloud Console](https://cloud.oracle.com) → Compute → Instances
2. Crea una instancia **Always Free Eligible**:
   - Shape: `VM.Standard.E2.1.Micro` (1 OCPU, 1 GB RAM)
   - Imagen: `Ubuntu 22.04` o `Oracle Linux 8`
   - Añade tu clave SSH pública
3. Abre el puerto 8080 en la Security List:
   - Networking → Virtual Cloud Networks → tu VPC → Security Lists
   - Añade regla de ingreso: TCP 8080 desde `0.0.0.0/0`

### 2. Conectar e instalar
```bash
ssh ubuntu@<IP_PUBLICA>

# Instalar Docker
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker ubuntu
newgrp docker

# Instalar Java 21 (alternativa sin Docker)
sudo apt update && sudo apt install -y openjdk-21-jre-headless
```

### 3. Desplegar con Docker
```bash
# En tu máquina local, copia el proyecto
scp -r . ubuntu@<IP_PUBLICA>:~/puebloduerme/

# En la VM
cd ~/puebloduerme
docker compose up -d --build
```

### 4. Desplegar sin Docker (distribución directa)
```bash
# En tu máquina local, genera la distribución
./gradlew :server:installDist

# Copia a la VM
scp -r server/build/install/server ubuntu@<IP_PUBLICA>:~/server

# En la VM
cd ~/server
nohup java -Xmx200m -Xms100m -cp "lib/*" com.puebloduerme.server.ApplicationKt > server.log 2>&1 &
```

### 5. Probar
```bash
# En tu máquina local
curl http://<IP_PUBLICA>:8080/
# Debería responder (aunque el WebSocket necesita upgrade)
```

---

## Opción B: Container Instances (más simple)

1. Ve a **Container Instances** en Oracle Cloud
2. Crea un contenedor con la imagen desde Docker Hub o Container Registry
3. Asigna 1 OCPU, 1 GB RAM (Always Free)
4. Expón el puerto 8080

Para subir la imagen a Oracle Container Registry:
```bash
docker login <region>.ocir.io
docker tag puebloduerme-server <region>.ocir.io/<namespace>/puebloduerme:latest
docker push <region>.ocir.io/<namespace>/puebloduerme:latest
```

---

## Variables de entorno

| Variable | Valor por defecto | Descripción |
|---|---|---|
| `PORT` | `8080` | Puerto HTTP/WebSocket |
| `JAVA_OPTS` | `-Xmx256m -Xms128m` | Opciones JVM |

---

## Uso desde la app Android

1. Abre la app "Pueblo Duerme"
2. Pulsa "Unirse a sala"
3. Introduce el código de sala
4. En "Servidor", escribe la URL: `ws://<IP_PUBLICA>:8080/game`
5. Conéctate
