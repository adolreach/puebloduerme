# Especificación de construcción — Juego de deducción social "Pueblo Duerme" (Android)

> **Instrucciones para quien construya:** Este documento es la especificación exacta y completa del juego. Constrúyelo siguiendo el stack, la arquitectura, las máquinas de estado, el contrato de mensajes y el catálogo de roles tal como aquí se definen. No improvises mecánicas: si algo no está especificado, márcalo como decisión abierta y pregunta. Sigue el roadmap de fases del final para ordenar el trabajo. Idioma de la interfaz y los textos: **español**.

---

## 1. Visión del producto

Juego móvil de deducción social tipo "el pueblo duerme / hombres lobo", jugable en línea entre allegados (familia y amigos), gratuito y sin registro. Tres bandos: **Pueblo**, **Lobos** y **Neutrales**. Partidas por rondas alternando fases de noche y día hasta que un bando cumple su condición de victoria. Pensado tanto para jugar en el mismo cuarto como en remoto.

Referencias de inspiración (solo para tono y mecánicas, no para copiar arte ni IP): Town of Salem 2 y Wolvesville.

---

## 2. Stack técnico (FIJADO, no negociable salvo aviso)

| Capa | Decisión |
|---|---|
| Plataforma | **Android nativo únicamente.** Distribución por **APK en GitHub** (sideload), sin tiendas, 0 €. |
| Cliente / UI | **Kotlin + Jetpack Compose** (MVVM). |
| Animaciones | **Rive** para piezas interactivas (transición sol→luna, muertes, conjuros). Animaciones de Compose para el resto. |
| Backend / lógica | **Motor propio host-autoritativo en Kotlin/Ktor.** |
| Transporte | **Ktor WebSockets** + **kotlinx.serialization** (JSON). |
| Modo local (por defecto) | El móvil **anfitrión** ejecuta un **servidor Ktor embebido**; los demás se unen por la **misma WiFi** mediante código de sala. Cero internet, cero hospedaje. |
| Modo remoto (opcional) | El **mismo motor** desplegado como servicio Ktor independiente en **Oracle Cloud Always Free** (o máquina propia). |
| Identidad | **Nombre + código de sala.** Sin registro, sin cuentas. |
| Persistencia | En memoria durante la partida. Sin base de datos en MVP (estadísticas quedan fuera de alcance inicial). |
| CI/CD | **GitHub Actions** → build de APK y publicación como *release*. |

### Principio de autoridad
El servidor (embebido o remoto) es la **única fuente de la verdad**. Calcula todo: asignación de roles, resolución de la noche, muertes, votaciones y victoria. El cliente solo **envía intenciones** ("voto a X", "inspecciono a Y") y **pinta** lo que el servidor le manda. Cada cliente recibe **solo su vista parcial** (niebla de guerra): nadie recibe en el dispositivo información secreta de otros (roles ajenos, voto de lobos, etc.), ni siquiera "oculta".

---

## 3. Arquitectura de módulos

Proyecto Gradle multi-módulo para que el motor sea idéntico en modo local y remoto, y 100 % testeable sin red:

```
:engine     -> Kotlin puro, SIN dependencias de Android ni de red.
               Contiene GameState, máquina de fases, resolución
               nocturna, reglas de roles y chequeo de victoria.
               Determinista y testeable con JUnit.

:protocol   -> Definiciones de los mensajes cliente<->servidor
               (data classes serializables con kotlinx.serialization).
               Compartido por :server y :app.

:server     -> Ktor standalone (modo REMOTO). Usa :engine + :protocol.

:app        -> Android (Jetpack Compose). Cliente del juego.
               Incrusta un Ktor embebido que usa :engine + :protocol
               para el modo HOST (LAN).
```

Regla de oro: **toda la lógica de juego vive en `:engine`**. Ni `:app` ni `:server` deciden reglas; solo transportan mensajes y, en el caso del host, hospedan el motor.

---

## 4. Conectividad

**Modo LAN (por defecto):**
1. El anfitrión crea la sala → su app levanta un Ktor embebido y muestra un **código de sala** (4–6 caracteres alfanuméricos).
2. Los demás introducen nombre + código y se conectan por WebSocket a la IP local del host (descubrimiento por **Android NSD/mDNS** opcional para no teclear IP; mínimo viable: el host muestra su IP local y el código).
3. El host arranca la partida cuando hay jugadores suficientes.

**Modo remoto (opcional):**
- Mismo flujo, pero el WebSocket apunta a la URL del `:server` desplegado en Oracle Cloud. El código de sala identifica la partida dentro del servidor.

**Reconexión:** al unirse, el servidor entrega un `playerToken`. Si un jugador se cae, puede reconectar con `roomCode + playerToken` y recupera su estado y su vista. Nada de bots sustitutos (delatarían roles).

---

## 5. Máquina de estados de las fases

```
LOBBY → NOCHE → DIA → DISCUSION → VOTACION → (¿fin?) → NOCHE → ...
                                                    └→ FIN
```

| Fase | Qué ocurre | Duración |
|---|---|---|
| LOBBY | Host configura roles e inicia. Jugadores entran/salen. | Manual |
| NOCHE | Jugadores "duermen". Roles con **efecto llamada** actúan. Lobos votan víctima en su canal. | Configurable (def. 40 s) |
| DIA | Se revelan muertes de la noche y efectos. Cazador (si murió) puede disparar. | Hasta resolver disparos |
| DISCUSION | Debate (chat de pueblo y/o presencial). Algunos roles actúan aquí. | **Fórmula §5.1** |
| VOTACION | Linchamiento por mayoría. | Configurable (def. 30 s) |
| FIN | Pantalla de resultados y roles revelados. | — |

### 5.1 Fórmula de duración de DISCUSION
Interpolación lineal según porcentaje de vivos sobre el total inicial, acotada a [45 s, 120 s]:

```
duracion_s = clamp(45 + (vivos_actuales / jugadores_iniciales) * 75, 45, 120)
```

Más gente viva ⇒ más tiempo (hasta 2 min); pocos vivos ⇒ hasta 45 s. Parámetros configurables por el host.

---

## 6. Orden de resolución nocturna (DETERMINISTA)

El servidor resuelve la noche en este orden exacto. Cada paso opera sobre el estado resultante del anterior:

1. **Limpieza:** retirar marcas temporales de la noche previa (silenciados, etc.).
2. **Información (no muta estado, se captura con los vivos actuales):**
   - **Vidente** elige objetivo → resultado privado solo para el vidente.
   - **Hombre lobo vidente** elige objetivo → resultado al canal de lobos + interfaz de lobos.
3. **Abuela gruñona** elige objetivo → marca *silenciado* para la próxima fase de día/discusión/votación.
4. **Lobos** resuelven su voto de víctima (mayoría interna; empate ⇒ aleatorio entre los más votados; sin votos ⇒ sin víctima de lobos esta noche).
5. **Aplicar muerte de lobos** sobre la víctima, con excepción:
   - Si la víctima es **Atormentado** → **no muere**: muta a **Lobo**; notificar **solo a los lobos**.
6. **Brujo** (si usa su conjuro único esta noche) → marca a un jugador para **revivir**. La reviviscencia se aplica **después** de las muertes, de modo que puede salvar incluso a la víctima de esta misma noche. Uso único en toda la partida.
7. **Calcular muertes netas** de la noche.
8. **Revelación en DIA:** anunciar muertes. Por defecto, al morir **se revela el rol** del fallecido (configurable), **salvo las excepciones del Sacerdote** (§7) y de la mutación del Atormentado (que nunca se anuncia públicamente).
9. **Cazador:** si murió esta noche, se le conceden **30 s** para elegir a quién matar; su disparo provoca muerte inmediata y puede encadenar (re-evaluar victoria). Si no elige, pierde el poder.
10. **Chequeo de victoria** (§9).

---

## 7. Catálogo de roles

Marca *(llamada)* = el rol actúa cada noche en la fase de noche.

### Pueblo
| Rol | Efecto | Notas de implementación |
|---|---|---|
| **Ciudadano** | Sin habilidad. | Voto normal. |
| **Abuela gruñona** *(llamada)* | Cada noche **silencia** a un jugador: no podrá hablar ni votar durante día, discusión y votación. | El silenciado **sabe** que está silenciado. No puede auto-silenciarse salvo que se configure lo contrario. |
| **Cazador** | Al morir, elige a un jugador para matar (30 s; si no elige, pierde el poder). | Disparo se resuelve en DIA tras su muerte; puede encadenar muertes. |
| **Brujo** | Puede chatear con los **muertos** vía app (canal muertos) estando vivo. Conjuro de **un solo uso** por la noche para **revivir** a la mañana siguiente. | Reviviscencia aplicada tras las muertes (§6.6). |
| **Vidente** *(llamada)* | Cada noche ve el **rol** de un jugador (solo él lo ve). | Resultado privado. |
| **Sacerdote** | Durante día/discusión/votación, **un solo uso** de **agua bendita** sobre un jugador. Si es **lobo** → muere el objetivo. Si **no** es lobo → muere el **sacerdote**, y el rol del objetivo **NO se revela**. | En el caso de acierto (era lobo), sí se revela que era lobo. En el fallo, no se filtra nada del objetivo. |
| **Chivato** | Al morir, **revela** el rol de un jugador vivo (a su elección) al resto de jugadores durante el resto de la partida. | Revelación pública persistente. |

### Lobos
| Rol | Efecto | Notas |
|---|---|---|
| **Hombre lobo** *(llamada)* | Cada noche vota con los demás lobos a quién matar. | Se ven entre ellos; canal de lobos. |
| **Hombre lobo vidente** *(llamada)* | Cada noche revela el rol de un jugador; aparece en la interfaz y el chat de lobos. | Resultado compartido solo con lobos. |

### Neutrales
| Rol | Condición / efecto | Notas |
|---|---|---|
| **Bufón** | Gana **solo** si es **linchado en votación diurna** (no si lo matan los lobos). | Su victoria es **individual** y **no termina** la partida; se registra y el juego continúa. |
| **Atormentado** | A priori pertenece al **pueblo**. Si los **lobos** lo matan, en vez de morir **muta a lobo** (notificado solo a lobos). | Tras mutar, gana con los lobos y vota con ellos **desde la noche siguiente**. Si nunca lo matan los lobos, cuenta como pueblo. |
| **Usurpador** | Al inicio recibe un **objetivo**. Si el objetivo muere, **adopta su rol** (hereda **equipo y poderes**). | Si el objetivo no muere en toda la partida, no cumple condición y **pierde** (configurable). |

> **Base ampliable:** el sistema de roles debe ser **modular** (interfaz `Role` con hooks `onNight`, `onDay`, `onDeath`, `onVote`…) para añadir roles nuevos sin tocar el núcleo.

---

## 8. Sistema de chat (canales con visibilidad acotada)

| Canal | Quién escribe | Quién lee | Cuándo |
|---|---|---|---|
| **Pueblo** | Vivos no silenciados | Todos los vivos | Discusión (opcional; el debate puede ser presencial) |
| **Lobos** | Lobos vivos | Lobos vivos | Noche (coordinación) |
| **Muertos** | Muertos + **Brujo vivo** | Muertos + Brujo | Siempre |

El servidor **filtra** cada mensaje por canal y entrega solo a destinatarios autorizados. El silenciado por la Abuela no puede escribir en Pueblo ni emitir voto durante esa ronda.

---

## 9. Votaciones y condiciones de victoria

### Votación diurna (linchamiento)
- Cada vivo no silenciado emite un voto (o se abstiene).
- Se lincha al más votado **si** alcanza **≥ 50 % de los vivos**. Si nadie llega al umbral, **no muere nadie**.
- **Empate** entre los más votados que superan el umbral ⇒ se lincha a **uno al azar**.

### Voto nocturno de lobos
- Mayoría interna entre lobos; empate ⇒ aleatorio entre los más votados; sin votos ⇒ sin víctima.

### Condiciones de victoria (chequear tras CADA muerte/resolución)
- **Pueblo:** no queda **ningún lobo vivo**.
- **Lobos:** `vivos_lobos / vivos_totales ≥ 0.5` (neutrales incluidos en el denominador).
- **Bufón:** linchado en votación diurna → victoria individual (no finaliza la partida).
- **Atormentado:** si mutó, gana con los lobos; si no, con el pueblo.
- **Usurpador:** gana según el bando/condición del rol que haya adoptado; sin adopción, pierde.

Orden de chequeo: (1) registrar victoria del Bufón si fue linchado; (2) victoria de Lobos; (3) victoria de Pueblo. La partida termina al dispararse una victoria de **bando**; las victorias individuales (Bufón) se acumulan al conjunto final de ganadores.

---

## 10. Contrato de mensajes (WebSocket, JSON)

Todos los mensajes llevan un campo `type`. Diseña data classes en `:protocol`.

**Cliente → Servidor**
```
CREATE_ROOM        { hostName, roleConfig }
JOIN_ROOM          { roomCode, playerName }
LEAVE_ROOM         { }
UPDATE_ROLE_CONFIG { roleConfig }            // solo host, en lobby
START_GAME         { }                       // solo host
NIGHT_ACTION       { actionType, targetId }  // WOLF_KILL_VOTE, SEER_INSPECT,
                                             // GRANDMA_SILENCE, WITCH_REVIVE,
                                             // WOLFSEER_INSPECT
DAY_ACTION         { actionType, targetId }  // PRIEST_HOLYWATER
HUNTER_SHOOT       { targetId }
CHIVATO_REVEAL     { targetId }
CAST_VOTE          { targetId | null }       // votación diurna
CHAT_MESSAGE       { channel, text }
RECONNECT          { roomCode, playerToken }
```

**Servidor → Cliente** (cada uno con vista parcial donde corresponda)
```
ROOM_STATE            { players[], roleConfig, phase, hostId }
ASSIGN_ROLE           { role, team, abilities }        // privado
PLAYER_TOKEN          { playerToken }                  // privado, para reconexión
PHASE_CHANGE          { phase, endsAt, publicInfo }
PRIVATE_PROMPT        { actionType, eligibleTargets[], deadlineMs }  // privado
SEER_RESULT           { targetId, revealedRole }       // privado al vidente
WOLF_CHANNEL_UPDATE   { wolves[], wolfSeerResult? }     // privado a lobos
DEATH_REVEAL          { deaths:[{ playerId, revealedRole|null, cause }] }
REVIVE                { playerId }
SILENCED              { playerId }                      // el silenciado lo sabe
VOTE_UPDATE           { tally }                         // durante votación
LYNCH_RESULT          { playerId|null, revealedRole|null }
CHIVATO_REVEAL_PUBLIC { targetId, role }
CHAT_BROADCAST        { channel, fromId, text }
GAME_OVER             { winningTeam, winners[], rolesSummary[] }
ERROR                 { code, message }
```

---

## 11. Modelo de estado (esbozo en `:engine`)

```kotlin
enum class Team { PUEBLO, LOBOS, NEUTRAL }
enum class Phase { LOBBY, NOCHE, DIA, DISCUSION, VOTACION, FIN }

data class Player(
    val id: String,
    val name: String,
    var role: Role,
    var alive: Boolean = true,
    var silencedThisRound: Boolean = false,
    val token: String,            // reconexión
    var connected: Boolean = true
)

data class GameState(
    val players: MutableList<Player>,
    var phase: Phase = Phase.LOBBY,
    var round: Int = 0,
    val roleConfig: RoleConfig,
    val pendingNightActions: MutableList<NightAction> = mutableListOf(),
    val votes: MutableMap<String, String?> = mutableMapOf(),
    val winners: MutableSet<String> = mutableSetOf()
)

interface Role {
    val team: Team
    val nightCaller: Boolean          // "efecto llamada"
    fun onNight(state: GameState, self: Player) {}
    fun onDay(state: GameState, self: Player) {}
    fun onDeath(state: GameState, self: Player) {}
}
```

---

## 12. Animaciones y UX

- **Transición de fase sol→luna:** archivo **Rive** con máquina de estados; el cliente dispara el estado `NIGHT`/`DAY` al recibir `PHASE_CHANGE`. El sol desciende, muta a luna llena y se dispersa; inverso al amanecer.
- **Muerte:** animación Rive al recibir `DEATH_REVEAL` (vela apagándose / lápida).
- **Conjuros:** feedback visual del Brujo (revivir), Sacerdote (agua bendita), Vidente (ojo).
- Resto de microtransiciones (cambios de pantalla, votos): **Compose Animation**.
- Mantener 60 FPS; Rive es GPU-acelerado y ligero, ideal para esto.

---

## 13. Configuración de roles por nº de jugadores (preset sugerido, editable por host)

| Jugadores | Lobos | Neutrales sugeridos | Resto Pueblo |
|---|---|---|---|
| 5–6 | 1 | 0–1 | resto |
| 7–9 | 2 | 1 | resto |
| 10–12 | 3 (posible Lobo vidente) | 1–2 | resto |
| 13–16 | 4 | 1–2 | resto |

El host puede activar/desactivar roles concretos y fijar cantidades en el lobby. Rango total recomendado: **5–16 jugadores**.

---

## 14. Reglas de borde fijadas (resumen)

- Sin mayoría del 50 % en votación diurna ⇒ **nadie muere**.
- **Bufón** linchado ⇒ gana él, la partida **continúa**.
- **Atormentado** mutado vota con lobos **desde la noche siguiente**.
- **Usurpador** hereda **equipo y poderes**; sin muerte del objetivo, **pierde**.
- Conteo de victoria de lobos sobre **vivos totales** (neutrales incluidos).
- Reviviscencia del Brujo se aplica **tras** las muertes (puede salvar a la víctima de esa noche).
- Agua bendita fallida **no revela** el rol del objetivo.
- Revelación de rol al morir: **activada por defecto**, configurable.

---

## 15. Roadmap de construcción (orden recomendado)

- **F0 — Andamiaje:** módulos Gradle (`:engine`, `:protocol`, `:server`, `:app`) + serialización.
- **F1 — Motor puro + tests:** fases, resolución nocturna, votaciones y victoria, sin red. JUnit cubriendo casos de borde de §6, §9 y §14.
- **F2 — LAN end-to-end básico:** Ktor embebido en `:app`, unión por código, ciclo de fases sin animaciones, con Ciudadano y Lobo.
- **F3 — Roles base:** Vidente, Cazador, Abuela gruñona.
- **F4 — Roles avanzados + chat:** Brujo, Sacerdote, Chivato, Lobo vidente + canales de chat (§8).
- **F5 — Neutrales:** Bufón, Atormentado, Usurpador + afinado de victoria.
- **F6 — Animaciones:** Rive (sol→luna, muertes, conjuros) + pulido UX.
- **F7 — Modo remoto:** desplegar `:server` en Oracle Cloud Always Free + reconexión.
- **F8 — Distribución:** presets de roles, balance, GitHub Actions → release de APK.

---

## 16. Decisiones abiertas (preguntar antes de cerrarlas)

1. ¿Descubrimiento LAN por mDNS/NSD automático o basta con mostrar IP + código en el MVP?
2. ¿Chat de pueblo activado por defecto o solo presencial (chat solo para muertos/lobos)?
3. ¿Estadísticas/persistencia en una fase posterior (requeriría almacenamiento)?
4. ¿Tiempos por defecto de noche/votación definitivos o ajustables siempre por el host?
5. ¿Tema visual/estética (medieval, moderno) para el set de Rive y la paleta?
