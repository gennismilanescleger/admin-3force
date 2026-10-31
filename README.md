# Admin 3FORCE — proyecto completo (documento de traspaso)

> **Si eres una herramienta de IA que acaba de llegar a este repositorio: LEE ESTE DOCUMENTO ENTERO ANTES DE TOCAR NADA.** Aquí está todo: qué es el proyecto, qué está probado en vivo, qué está prohibido, cómo se compila, dónde quedó el trabajo y cuál es el siguiente paso exacto. El detalle fino está en los documentos enlazados; este README es el mapa y el estado.

---

## 1. Qué es esto

**Admin 3FORCE** es una app Android para que **Gennis** (el propietario) gestione desde su teléfono su negocio de VPN en Cuba, **SAWVPN**, que opera sobre el panel reseller **3ForceVPN** (`https://reseller.3forcevpn.com`; portal de clientes: `https://3forcevpn.com`).

La app sirve para: ver cuentas y túneles, ver la matriz de facturación manual, **marcar pagos**, y ejecutar acciones de túnel (descargar la configuración WireGuard, cambiar de servidor, regenerar claves, reaprovisionar, editar notas, deshabilitar/habilitar). En una etapa posterior leerá los SMS de Transfermóvil/EnZona del teléfono para presentar los pagos recibidos.

**Estado general:** la app está en su línea **Lite** (app de pruebas funcional, con datos reales del panel) mientras se prueba cada flujo contra la API real. Cuando todos los flujos estén probados, se construirá la **app nativa definitiva** con el mismo comportamiento. El prototipo web de diseño está congelado como referencia visual (no está en este repo; sus decisiones están consolidadas en la SPEC).

**Dispositivo de pruebas:** el teléfono del propietario (Galaxy S20 FE, Android 12 / API 31). Él instala cada APK y ejecuta las rondas de prueba; los resultados vuelven como bloques de diagnóstico de la propia app.

---

## 2. Mapa del repositorio

| Archivo / carpeta | Qué es |
|---|---|
| `README.md` | Este documento: indicaciones completas + estado actual. |
| `ADMIN-3FORCE-SPEC.md` | La especificación cerrada de la app (diseño, pantallas, lógica de pagos, notificaciones, ajustes). Es la fuente de verdad del COMPORTAMIENTO. |
| `API-WEB-3FORCE.md` | La API de la web TAL COMO la documenta el propio panel (página Developers, API v2.1.0, 148 endpoints). Solo documentación oficial, sin nada de la app. |
| `API-INSPECTION.md` | El registro interno de trabajo sobre la API: cada ronda de pruebas con rutas, payloads, respuestas literales y veredictos. Es la fuente de verdad de QUÉ ESTÁ PROBADO y qué no. |
| `lite/` | **La app actual.** Java puro, cero dependencias. `lite/build.sh` compila el APK. `lite/BUILD-HOME.md` y `lite/BUILD-LITE.md` son su bitácora de construcción versión por versión. |
| `android/` | El primer intento en Kotlin (v0.1.0): su APK no instaló en el teléfono del propietario y la línea se abandonó. Se conserva como referencia. NO es la app actual. |
| APKs `Admin3FORCE-<versión>.apk` | Los entregables de cada versión Lite. **No están en GitHub** (la vía de subida no admite binarios); se recompilan desde `lite/` con `build.sh`. La última es la **0.9.5-lite**. |

Quedan fuera del repo a propósito: los intermedios de compilación (`lite/build/`, `android/build/`, que incluye librerías de terceros extraídas) — todo regenerable.

---

## 3. DÓNDE QUEDÓ EL TRABAJO (2026-10-31, ~15:30, hora de La Habana)

**Última versión entregada: Lite v0.9.5 (versionCode 26).** Este README se actualizó para la segunda subida a GitHub (2026-10-31 ~15:35), que pone el repo al día hasta la ronda 20. **La parte WEB de la app está CERRADA al 100%**: todas las acciones probadas en vivo, incluida la creación en todas sus formas (cuenta, túnel individual manual y automático, y por lotes). Lo que sigue es la **parte LOCAL** (etapa SMS primero, §9), por orden expresa del propietario: primero todo lo de la web, después lo local.

**Novedad mayor (2026-10-31 ~12:56): LA CREACIÓN DE TÚNELES DEL PANEL VOLVIÓ A FUNCIONAR.** El propietario creó los túneles 558 («Prueba») y 559 en la cuenta `prueba_claude_3010`: ya hay túneles desechables de verdad para probar, incluido un eventual Eliminar (solo con su orden expresa).

**✅ RESUELTO (2026-10-31, rondas 10–12): DESHABILITAR/HABILITAR FUNCIONAN EN LA APP (v0.8.8).** El «dif de la web» (Muse apagó el 558 desde el panel como SawVPN y se comparó el objeto) reveló el mecanismo: al apagar desde la web, el objeto queda `status="disabled"` con `disabled_date` puesta **por el servidor** (la hora exacta del guardado). Es decir: `disabled_date` es un campo de SALIDA que el backend rellena — por eso escribirlo desde la app se ignoró siempre — y **el campo escribible es `status`** (lo probaba ya el 400 de la ronda 7: un campo de solo lectura no valida opciones al escribir). La app escribe `PATCH {"status":"disabled"}` / `{"status":"active"}` y quedó confirmado en las dos direcciones sobre el 558: al deshabilitar, el `state_hash` resultante (2e7611cf…) fue **idéntico** al del apagado hecho por la propia web. Reinterpretación de la ronda 4: el `{"status":"disabled"}` de la 0.8.1 casi seguro SÍ deshabilitó a Diego, y el cambio de servidor de minutos después (reassign-host reaprovisiona) lo reactivó antes de la verificación del panel — el veredicto «no se aplicó» estaba mal. **El capítulo de acciones de túnel está CERRADO** (marcar pagado, descargar, cambiar de servidor, regenerar, reaprovisionar, nota del túnel, deshabilitar, habilitar — todo probado en vivo).

**Pendientes inmediatos:**
1. **Ronda 21 (verificación con la v0.9.5)**: un lote de 2 en Automático sobre `prueba_crear_3110`, con una nota escrita — debe verificar dos cosas a la vez: que el automático de la app elige un host VÁLIDO (pool = lista del panel, corrección de la ronda 20) y que la nota del lote queda aplicada en cada túnel creado (se ve en la respuesta).
2. Confirmar con palabras del propietario que las descargas llegan como `.conf` (pendiente desde la v0.8.3; descargó varias veces y nunca lo confirmó ni se quejó).
3. La etapa grande siguiente: **la parte LOCAL, empezando por SMS en Pagos › Recibidos** (ver §9).

**Cabos sueltos menores, en orden:**

- **Etapa SMS (Pagos › Recibidos)**: leer y parsear en el teléfono los SMS de Transfermóvil/EnZona (importe, remitente, ID de transacción), mostrarlos como recibidos sin marcar nada. Es el siguiente paso tras cerrar la web (orden del propietario, 2026-10-31).
- Después de la Lite: **construir la app nativa definitiva** con todo lo probado.

---

## 4. Qué está PROBADO en vivo contra la API real

Todo esto está ejecutado y verificado (detalle y respuestas literales en `API-INSPECTION.md`):

- **Login híbrido**: WebView del panel (el humano resuelve el captcha de Turnstile), captura del JWT desde `localStorage` (clave `access_token`) y guardado cifrado. Access = 10 min; refresh ≈ 14 días: `POST /api/v1/auth/refresh/` con el campo **`refresh`** → rota tokens.
- **Sesión**: `POST /api/v1/auth/session/?context=manager` → `{user, stats}` (`stats.clients_count` / `stats.tunnels_count`).
- **Cuentas**: `GET /api/manager/v1/accounts/users/` (el reseller ve solo sus clientes). Campos: `id, username, alias, full_name, notes, tunnels_count, is_active, is_reseller, zone, telegram_username, last_login, created_at`. El **alias viene de la web**, no es configuración local.
- **Túneles**: `GET /api/v1/tunnels/?personal=false` trae los túneles de los CLIENTES con el serializer completo (con `?personal=true` solo trae los 3 propios del reseller, reducidos). `host` es un OBJETO; su `name` es la etiqueta legible del servidor. ⚠️ **Hay DOS registros de hosts** (ronda 20): los objetos `host` de algunos túneles traen ids de otro registro (p. ej. id 17 «S2-Cuba») que la CREACIÓN rechaza («no pertenece a las zonas asignadas a este reseller»); el pool válido para crear es la **lista del panel por túnel** (`GET /api/v1/hosts/?tunnel_id=…`, ids 39–50).
- **LA CLAVE DEL ALCANCE**: toda la familia del detalle (`GET/PATCH/DELETE /api/v1/tunnels/<id>/`, `download-config/`, `regenerate/`) responde 404 genérico para túneles de clientes **si no se añade `?personal=false`**. Con el parámetro, funciona.
- **Matriz de facturación manual**: `GET /api/manager/v1/billing/manual-billing/matrix/`. Cada fila trae `months` como ARREGLO de celdas `{month, year, is_paid, notes, marked_at, marked_by}`; solo existen celdas de meses registrados. **Regla de pendientes confirmada**: sin celda del mes = pendiente; celda `is_paid=false` = pendiente; `is_paid=true` = pagado.
- ✅ **Marcar pagado**: `POST /api/manager/v1/billing/manual-billing/update/` con `{tunnel_id, month, year, is_paid, notes}` → 200 «Registro creado exitosamente». (Ojo: el campo es `tunnel_id`, no `tunnel`.)
- ✅ **Descargar configuración**: `GET /api/v1/tunnels/<id>/download-config/?personal=false` → 200, texto WireGuard.
- ✅ **Nota del túnel**: `PATCH /api/v1/tunnels/<id>/?personal=false` con `{notes}` → 200.
- ✅ **Cambiar de servidor**: `PATCH /api/v1/tunnels/<id>/?personal=false` con `{"host": <id>}` → 200 y el túnel aterriza EXACTAMENTE en el host elegido (probado en dos túneles).
- ✅ **Regenerar claves**: `POST /api/v1/tunnels/<id>/regenerate/?personal=false` con `{}` → 200. (Ojo: regenerar cambia las claves; la `.conf` anterior del cliente deja de funcionar hasta que importe la nueva.)
- ✅ **Reaprovisionar**: `POST /api/v1/tunnels/<id>/reprovision/` con `{}` → 200 (sin parámetro de alcance).
- ✅ **Lista de servidores por túnel**: `GET /api/v1/hosts/?tunnel_id=<id>` → 200 (sin `tunnel_id` responde 400 pidiéndolo).
- ✅ **Detalle (sonda)**: `GET /api/v1/tunnels/<id>/?personal=false` → 200.
- ✅ **Deshabilitar/Habilitar**: `PATCH /api/v1/tunnels/<id>/?personal=false` con `{"status":"disabled"}` / `{"status":"active"}` → 200, confirmado en las dos direcciones (rondas 10–12; el `state_hash` al deshabilitar fue idéntico al del interruptor de la web).
- ✅ **Eliminar túnel**: `DELETE /api/v1/tunnels/<id>/?personal=false` → 204 (soft-delete; probado en desechables, rondas 13 y 19–20).
- ✅ **Nota de la CUENTA**: `PATCH /api/manager/v1/accounts/users/<id>/` con `{notes}` → 200 (ronda 14).
- ✅ **Crear cuenta**: `POST /api/manager/v1/accounts/users/` → 201 (ronda 15; el servidor autogenera el alias si no se envía; zona null aceptada).
- ✅ **Restablecer contraseña**: `POST /api/manager/v1/accounts/users/<id>/reset-password/` con `{"password": …}` → 200 «Contraseña actualizada correctamente» (ronda 16; el admin FIJA la contraseña, no se genera).
- ✅ **Crear túnel (manual)**: `POST /api/v1/tunnels/` con `host` explícito → 201, aterriza exactamente en el host elegido (ronda 16). La zona sola NO resuelve el host («Host no resuelto»). `name_for_user` ≤ 20 caracteres; el nombre interno solo admite letras, números y guiones (guion bajo → 500).
- ✅ **Crear túnel (automático, como la web)**: `POST /api/v1/tunnels/` con `is_auto_generated:true`, zona y SIN host → 201; el panel asigna el host solo (ronda 18: túnel 561 → S1-CUBA). La forma exacta se leyó del propio código JS de la web (ronda 17): en automático NO se envía `host`; el interruptor de la web es «Generar configuración automáticamente».
- ✅ **Crear por lotes**: `POST /api/v1/tunnels/bulk-create/` con `{user, count, protocol, status:"active", manual_payment:true, name_prefix, notes}` + `host` explícito → 201 «Se crearon N túneles correctamente» y el panel enumera `base-1`, `base-2`… (ronda 19).

### Lo que NO funciona / está RETIRADO (no reintentar)

- ❌ `POST /api/v1/tunnels/<id>/reassign-host/` con `{host_id}`: responde 200 pero **NO honra el host pedido** (auto-asigna otro). Retirado de la app; el cambio de servidor va por el PATCH `{host}`.
- ❌ Deshabilitar con `{is_active:false}` o escribiendo `disabled_date`: 200 sin efecto — ambos se ignoran (`disabled_date` lo pone el servidor; es campo de salida). El campo escribible es `status` (ver §4 y la historia en §5). NUNCA cambiar de servidor ni reaprovisionar entre un deshabilitar y su verificación: el reaprovisionamiento lo reactiva (ese fue el falso «no se aplicó» de la ronda 4).
- ❌ **Crear por lotes en automático del panel** (bulk-create sin `host`): el backend responde 400 con su error interno «The QuerySet value for an exact lookup must be limited to one result using slicing», con y sin zona (rondas 18–19). La web manda el mismo cuerpo: es un bug del PANEL. En la app, el «Automático» del lote lo resuelve la propia app: elige de la lista del panel el servidor con menos túneles cargados y lo envía explícito (v0.9.4–0.9.5), rotulado como lo que es.
- ❌ Rutas con guion bajo (`download_config/`, `reassign_host/`, `regenerate_config/`), `disable/`/`enable/` como POST, `download/`/`config/`, y el PATCH a `/api/manager/v1/tunnels/<id>/`: todas 404; no existen.
- ❌ No hay endpoint dedicado de notas ni de disable/enable en los 148 documentados.
- Distinción útil: un 404 con JSON `{detail: …}` = la ruta existe pero falla el objeto/permiso/alcance; un 404 HTML = la ruta no existe.

---

## 5. Cómo resuelve la v0.8.5 el deshabilitar (y los cambios inmediatos)

> **Actualización v0.8.6 (ronda 7):** el mecanismo OPTIONS de abajo quedó como lectura diagnóstica — el detalle no devuelve «actions» utilizables y el respaldo `{status:"inactive"}` dio 400 «no es una elección válida». La v0.8.6 escribe `disabled_date` (instante UTC al deshabilitar; null al habilitar), que es el campo del que el modelo parece derivar el status al guardar.
>
> **Resolución final (v0.8.8, rondas 10–12):** ni OPTIONS ni `disabled_date`: el dif de la web probó que `disabled_date` lo pone el servidor y que el campo escribible es `status`. La app escribe `{"status":"disabled"}` / `{"status":"active"}` — confirmado en vivo en las dos direcciones, con el mismo `state_hash` final que produce el interruptor de la web. Lo de abajo queda como historia del proceso.

- **Esquema OPTIONS**: al tocar Deshabilitar/Habilitar, la app pide `OPTIONS /api/v1/tunnels/<id>/?personal=false`. En DRF, el cuerpo trae `actions` con los campos escribibles del PATCH: nombre, tipo, si es de solo lectura y sus valores posibles (`choices`). La app elige UNA escritura: (1) un booleano escribible (`is_active`, `enabled`, `is_enabled`, `active`; o `is_disabled` invertido); (2) un `status`/`state` escribible usando sus `choices` (si no declara, «inactive»/«active»); (3) un `disabled_date` escribible (fecha UTC actual / null). Si el esquema no declara ningún campo de estado escribible, **no se envía nada** y la app lo explica: deshabilitar usaría otra vía, no este PATCH. El esquema entero queda registrado en el Diagnóstico («Esquema de estado del túnel»).
- **Cambios al instante** (pedido expreso del propietario): la respuesta de cada escritura se fusiona en el ítem local y la vista se repinta al momento; después UNA lectura puntual del detalle confirma el estado real y el aviso dice la verdad («…confirmado en el panel» o «SIN CAMBIO: el panel sigue mostrando el túnel ACTIVO»). Al marcar pagado se recarga solo la matriz, no todo el Home.
- `HomeStats.statusOf`: un `disabled_date` o `deleted_at` no vacío manda sobre el `status` de salida.

---

## 6. Cómo se compila la Lite

La VM de desarrollo **no puede correr Gradle** (su capa de permisos bloquea el TCP de los procesos Java y el daemon no arranca), así que la Lite usa una **cadena manual reproducible** que hace `lite/build.sh`:

```
aapt2 link → javac (source/target 8) → d8 → zipalign → apksigner (v1+v2)
```

Toolchain usado (rutas de la VM original; en otra máquina, equivalentes):

- JDK Temurin 17.
- Android SDK: plataforma 34 y build-tools 34.0.0.
- R8 8.5.35 como respaldo de d8.
- Keystore debug (pass `android`) para firmar.

La app: **Java puro, solo framework Android, CERO dependencias**, paquete `com.gennis.admin3force`, minSdk 21, targetSdk 34. UI 100% programática (sin XML de layouts ni `res/`).

**Versionado (regla del propietario)**: el APK entregado se nombra **`Admin3FORCE-<versionName>.apk`** (p. ej. `Admin3FORCE-0.8.5-lite.apk`) para que el teléfono no añada (1), (2)… al descargar; la etiqueta de la app queda siempre «Admin 3FORCE». Al subir versión hay que cambiar TRES sitios a la vez: `AndroidManifest.xml` (versionCode/versionName), `lite/build.sh` (`VERSION_NAME` y `--version-code`). Historial: 0.2.0 (login) → 0.3.x (Home) → 0.4.0 (Cuentas/Túneles lectura) → 0.5.0 (Pagos + marcar pagado) → 0.6.x (menú de acciones; 0.6.2 fijó `tunnel_id`) → 0.7.x (segunda ronda de rutas; 0.7.1 dividió el Diagnóstico en 3 bloques) → 0.8.0 (rutas documentadas) → 0.8.1 (alcance `?personal=false`) → 0.8.2 (regenerate con alcance) → 0.8.3 (descarga `.conf` + `{is_active}`) → 0.8.4 (cambio de servidor por PATCH `{host}`) → 0.8.5 (cambios al instante + deshabilitar por esquema OPTIONS) → 0.8.6–0.8.7 (intentos de deshabilitar) → 0.8.8 (deshabilitar/habilitar por `{status}`, confirmado) → 0.9.0 (crear cuenta/túnel + restablecer contraseña) → 0.9.1–0.9.2 (validaciones de creación) → 0.9.3 (automático fiel a la web + modo por lotes) → 0.9.4 (automático de lote resuelto por la app) → **0.9.5 (crear nace en automático, buscador de propietario, nota del lote visible, pool de servidores = lista del panel)**.

**Patrón de escritura segura (OBLIGATORIO en todo el código):**

- Escrituras SIN reintentos automáticos: si la petición salió y la respuesta se perdió, el servidor PUDO aplicarla → se marca «ambigua», jamás se reenvía; se recarga y se muestra el estado real.
- La única re-llamada permitida: tras un 401 (la autenticación se rechaza ANTES de ejecutar la vista), refrescar el token y reintentar UNA vez.
- UNA sola ruta candidata por toque para escrituras (las lecturas sí pueden encadenar candidatos).
- Cada intento queda registrado ENTERO en el Diagnóstico de la app.
- Los errores del servidor se muestran literales; éxito = aviso + datos reales recargados, nunca un cambio local fingido.
- PATCH se envía como POST + cabecera `X-HTTP-Method-Override: PATCH` (HttpURLConnection no soporta PATCH).

**Diagnóstico de la app (regla del propietario)**: se copia en TRES bloques separados — «Copiar intentos y escrituras» (el único que hace falta pegar tras cada ronda), «Copiar matriz» y «Copiar diagnóstico general». Nunca volcar contenido sensible: las configuraciones WireGuard y los tokens solo se describen por tipo/tamaño, jamás se anotan.

---

## 7. Reglas de trabajo (para cualquier IA que continúe)

- **Dos cuentas del panel, misma contraseña, cambia solo el usuario:**
  - **`Gennis`** (admin): SOLO para VER (la API/Developers y verificaciones de estado). **No tocar nada** con esta cuenta.
  - **`SawVPN`** (reseller): la cuenta de las PRUEBAS de la app y de las marcas de pago del negocio.
- **NO BORRAR NADA REAL** en el panel, en ninguna cuenta. Eliminar túnel (soft-delete) quedó probado en desechables (rondas 13, 19–20) y el propietario borra los desechables desde la app él mismo; nada de clientes reales se borra sin su orden expresa. El DELETE de CUENTAS no está construido a propósito: la documentación dice que es en CASCADE (transfiere el saldo al sistema y borra todo).
- **La creación de túneles del panel estuvo CAÍDA** (fallaba en la propia web, desde cualquier cuenta y dispositivo, confirmado el 2026-10-30 y el 2026-10-31 por la mañana) y **volvió a funcionar el 2026-10-31 ~12:56**. No era fallo de la app.
- **Entidades de prueba:**
  - Cuenta `prueba_claude_3010` (id **277**, alias igual, nota actual «se pudo la nota de la cuenta», activa): la cuenta de pruebas original. Su único túnel vivo es el **558 «Prueba»** (en S3-MIAMI-WINDSCRIBE), que quedó **DESHABILITADO desde la app** a propósito (ronda 12); el propietario puede habilitarlo cuando quiera. El 559 fue eliminado (ronda 13).
  - Cuenta `prueba_crear_3110` (id **278**, alias autogenerado `xea3rqgp`): creada POR LA APP (ronda 15). Hoy tiene **0 túneles** (el 560, 561, 562 y 563 fueron eliminados por el propio propietario desde la app). Es la cuenta de las pruebas de creación.
  - Túnel **Diego (id 525)**: el banco de pruebas de las acciones («todo lo que se pueda hacer, se prueba en Diego»). Su estado al 2026-10-31 12:01: **ACTIVO, en S2-MIAMI-WINDSCRIBE, nota «ns», octubre 2026 pendiente** (su marca de prueba se revirtió a propósito; tiene una celda explícita `is_paid=false`).
  - Ojo: el propietario también ha probado acciones sobre el túnel real **Cellmaster87 (id 411)** por decisión suya; hoy está ACTIVO en S3-ATLANTA con las claves regeneradas dos veces y la nota «Notificado por WhatsApp 18/10/2026 BS1». Si ese cliente reporta que no conecta, es por la regeneración: necesita importar su configuración nueva.
- **El dinero y los mensajes no se tocan sin la palabra del propietario**: la app marca pagos SOLO con su toque; ninguna IA marca, envía ni cambia nada por su cuenta.
- **Datos honestos**: nada de gráficos ni métricas inventadas; un dato no disponible se muestra como no disponible, jamás como cero falso. Las cifras de demostración del prototipo NO son datos del negocio.
- **Diseño**: app Android nativa en sensación, móvil primero, tarjetas compactas, iconos consistentes (nunca emojis como iconos), y los textos explicativos largos van detrás de un botón «i» junto al título de la sección, no en el cuerpo. El detalle completo de diseño y lógica está en `ADMIN-3FORCE-SPEC.md` — incluye la lógica de pagos cerrada el 2026-10-30 (facturación por mes calendario, día oficial de cobro, días de gracia, sin pagos parciales, duplicado = mismo ID de transacción, cola sin conexión, etc.).

---

## 8. La API en breve

- Origen único: `https://reseller.3forcevpn.com`. Bases: `/api/v1/` (auth, túneles, hosts) y `/api/manager/v1/` (cuentas, matriz). Auth: `Authorization: Bearer <access>`.
- La documentación oficial completa (148 endpoints) está en `API-WEB-3FORCE.md`. Avisos: los roles que el índice declara son poco fiables (p. ej. `reprovision` dice «staff» y funciona como reseller), y el índice NO documenta los campos de escritura del PATCH de túneles — esos se fijaron empíricamente, ronda a ronda (los escribibles son `status`, `host` y `notes`; ver `API-INSPECTION.md`).
- En la web, el estado del túnel se ve como: lista «STATUS: ACTIVE», detalle con insignia «Active» (el «Status: online» del detalle es del HOST) e interruptor «Tunnel status» en Configure. El vocabulario del filtro de estados es **Active / Grace / Disabled / Expired**. La página no expone qué campo escribe ese interruptor (verificado el 2026-10-31: interruptor sin nombre ni valor accesibles, y los JS del panel no son inspeccionables desde el navegador usado).

---

## 9. Pendientes completos (más allá del siguiente paso)

1. ✅ Hecho (rondas 10–12): deshabilitar/habilitar probados en las dos direcciones.
2. ✅ Hecho (ronda 14): nota de la CUENTA probada.
3. ✅ Hecho (ronda 13): Eliminar túnel probado (204).
4. ✅ Hecho (v0.9.0–0.9.5, rondas 15–20, 2026-10-31): **la CREACIÓN completa** — cuenta (201), túnel con host explícito (201), túnel automático como la web (`is_auto_generated`, 201; el panel asignó solo S1-CUBA), lotes con host explícito (201, enumeración `base-N`), restablecer contraseña (200). El automático de LOTES del panel está roto en el backend (error QuerySet): en la app lo resuelve la propia app eligiendo de la **lista del panel** el servidor con menos túneles (la ronda 20 probó que el pool «en uso» mezcla otro registro de hosts inválido para crear). Pedidos del propietario aplicados en la v0.9.5: crear nace en Automático, sin opción «sin servidor»; buscador en el selector de propietario; nota visible para todos los túneles del lote. **Queda por verificar en la ronda 21**: el lote en automático con el pool nuevo + la nota aplicada en cada túnel. (El DELETE de cuentas sigue sin construirse a propósito: es en CASCADE.)
5. Confirmar que las descargas llegan como `.conf` (pendiente de palabra del propietario desde la v0.8.3).
6. **Etapa LOCAL (lo que sigue)**: etapa SMS primero — Pagos › Recibidos (lectura/parseo local de SMS de Transfermóvil/EnZona, validación contra la lista de tarjetas permitidas configurada a mano en la app, deduplicación por ID de transacción, no identificados a su sección); después tarjetas permitidas, plantillas, contactos, vista local de servidores, notificaciones y cola sin conexión, según la SPEC.
7. Construir la **app nativa definitiva** con todos los flujos ya probados en la Lite.

---

*Documento de traspaso generado el 2026-10-31 y actualizado el mismo día (~15:35) para la segunda subida a GitHub (estado: v0.9.5, ronda 20). La fuente viva del detalle es `API-INSPECTION.md` (registro de rondas) y `lite/BUILD-HOME.md` (bitácora de construcción).*
