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
| APKs `Admin3FORCE-<versión>.apk` | Los entregables de cada versión Lite. **No están en GitHub** (la vía de subida no admite binarios); se recompilan desde `lite/` con `build.sh`. La última es la **0.8.5-lite**. |

Quedan fuera del repo a propósito: los intermedios de compilación (`lite/build/`, `android/build/`, que incluye librerías de terceros extraídas) — todo regenerable.

---

## 3. DÓNDE QUEDÓ EL TRABAJO (2026-10-31, ~12:10, hora de La Habana)

**Última versión entregada: Lite v0.8.5 (versionCode 17).** El propietario la tiene pendiente de instalar y probar.

**El siguiente paso exacto** (lo hace el propietario en su teléfono, ~1 minuto):

1. Instalar `Admin3FORCE-0.8.5-lite.apk`.
2. Ir al túnel **Diego** → Acciones → **Deshabilitar**. La v0.8.5 primero lee el esquema OPTIONS de la API, escribe el campo de estado que la API declare escribible, y la lista debe pintarlo INACTIVO al momento con el aviso «confirmado en el panel».
3. **Habilitar** de nuevo, igual.
4. Pegarle a la IA el bloque **«Intentos y escrituras»** del Diagnóstico de la app (trae el esquema OPTIONS completo + las dos escrituras + las verificaciones).

**Por qué ese paso importa:** deshabilitar/habilitar es LA ÚNICA acción de túnel que queda sin resolver. Dos payloads ya devolvieron HTTP 200 sin cambiar nada (`{status:"disabled"}` en la ronda 4 y `{is_active:false}` en la ronda 6): son campos de salida que la API ignora al escribir. La v0.8.5 deja de adivinar: pregunta el esquema a la propia API (ver §5).

**Cabos sueltos menores, en orden:**

- **Nota de la CUENTA**: implementada desde la v0.6.0 (Cuentas → cuenta → «Editar nota», PATCH a `/api/manager/v1/accounts/users/<id>/` con `{notes}`) pero NUNCA probada. Probarla sobre la cuenta de prueba `prueba_claude_3010`. (La nota del TÚNEL sí está probada.)
- **Confirmar el nombre de las descargas**: desde la v0.8.3 la configuración se guarda como `<túnel>.conf`; el propietario descargó dos veces con la v0.8.4 pero aún no ha confirmado si el archivo llegó `.conf` o `.txt`.
- **Eliminar túnel**: ruta documentada y botón con guarda (escribir el nombre exacto), pero NO ejecutarlo: la creación de túneles del panel está caída (ver §7) y un túnel borrado no se podría recrear. Esperar la orden expresa del propietario.
- **Etapa SMS (Pagos › Recibidos)**: leer y parsear en el teléfono los SMS de Transfermóvil/EnZona (importe, remitente, ID de transacción), mostrarlos como recibidos sin marcar nada. Aplazada por decisión del propietario hasta cerrar las acciones de túnel.
- Después de la Lite: **construir la app nativa definitiva** con todo lo probado.

---

## 4. Qué está PROBADO en vivo contra la API real

Todo esto está ejecutado y verificado (detalle y respuestas literales en `API-INSPECTION.md`):

- **Login híbrido**: WebView del panel (el humano resuelve el captcha de Turnstile), captura del JWT desde `localStorage` (clave `access_token`) y guardado cifrado. Access = 10 min; refresh ≈ 14 días: `POST /api/v1/auth/refresh/` con el campo **`refresh`** → rota tokens.
- **Sesión**: `POST /api/v1/auth/session/?context=manager` → `{user, stats}` (`stats.clients_count` / `stats.tunnels_count`).
- **Cuentas**: `GET /api/manager/v1/accounts/users/` (el reseller ve solo sus clientes). Campos: `id, username, alias, full_name, notes, tunnels_count, is_active, is_reseller, zone, telegram_username, last_login, created_at`. El **alias viene de la web**, no es configuración local.
- **Túneles**: `GET /api/v1/tunnels/?personal=false` trae los túneles de los CLIENTES con el serializer completo (con `?personal=true` solo trae los 3 propios del reseller, reducidos). `host` es un OBJETO; su `name` es la etiqueta legible del servidor. Hay UN solo espacio de ids de hosts.
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

### Lo que NO funciona / está RETIRADO (no reintentar)

- ❌ `POST /api/v1/tunnels/<id>/reassign-host/` con `{host_id}`: responde 200 pero **NO honra el host pedido** (auto-asigna otro). Retirado de la app; el cambio de servidor va por el PATCH `{host}`.
- ❌ Deshabilitar con `{status:"disabled"}` o con `{is_active:false}`: 200 sin efecto. Ambos campos son de salida. (El mecanismo nuevo de la v0.8.5 está en el §5.)
- ❌ Rutas con guion bajo (`download_config/`, `reassign_host/`, `regenerate_config/`), `disable/`/`enable/` como POST, `download/`/`config/`, y el PATCH a `/api/manager/v1/tunnels/<id>/`: todas 404; no existen.
- ❌ No hay endpoint dedicado de notas ni de disable/enable en los 148 documentados.
- Distinción útil: un 404 con JSON `{detail: …}` = la ruta existe pero falla el objeto/permiso/alcance; un 404 HTML = la ruta no existe.

---

## 5. Cómo resuelve la v0.8.5 el deshabilitar (y los cambios inmediatos)

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

**Versionado (regla del propietario)**: el APK entregado se nombra **`Admin3FORCE-<versionName>.apk`** (p. ej. `Admin3FORCE-0.8.5-lite.apk`) para que el teléfono no añada (1), (2)… al descargar; la etiqueta de la app queda siempre «Admin 3FORCE». Al subir versión hay que cambiar TRES sitios a la vez: `AndroidManifest.xml` (versionCode/versionName), `lite/build.sh` (`VERSION_NAME` y `--version-code`). Historial: 0.2.0 (login) → 0.3.x (Home) → 0.4.0 (Cuentas/Túneles lectura) → 0.5.0 (Pagos + marcar pagado) → 0.6.x (menú de acciones; 0.6.2 fijó `tunnel_id`) → 0.7.x (segunda ronda de rutas; 0.7.1 dividió el Diagnóstico en 3 bloques) → 0.8.0 (rutas documentadas) → 0.8.1 (alcance `?personal=false`) → 0.8.2 (regenerate con alcance) → 0.8.3 (descarga `.conf` + `{is_active}`) → 0.8.4 (cambio de servidor por PATCH `{host}`) → **0.8.5 (cambios al instante + deshabilitar por esquema OPTIONS)**.

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
- **NO BORRAR NADA** en el panel, en ninguna cuenta. El único borrado previsto (Eliminar túnel, soft-delete documentado) espera la orden expresa del propietario y que la creación de túneles vuelva a funcionar.
- **PROBLEMA DEL PANEL**: crear túneles FALLA en la propia web (error de conexión), desde cualquier cuenta y dispositivo, confirmado el 2026-10-30 y el 2026-10-31. No es fallo de la app. Por eso no hay túnel de pruebas desechable.
- **Entidades de prueba:**
  - Cuenta `prueba_claude_3010` (alias igual, nota «Cuenta de prueba de Muse — 30/10/2026», 0 túneles, activa): la cuenta de pruebas.
  - Túnel **Diego (id 525)**: el banco de pruebas de las acciones («todo lo que se pueda hacer, se prueba en Diego»). Su estado al 2026-10-31 12:01: **ACTIVO, en S2-MIAMI-WINDSCRIBE, nota «ns», octubre 2026 pendiente** (su marca de prueba se revirtió a propósito; tiene una celda explícita `is_paid=false`).
  - Ojo: el propietario también ha probado acciones sobre el túnel real **Cellmaster87 (id 411)** por decisión suya; hoy está ACTIVO en S3-ATLANTA con las claves regeneradas dos veces y la nota «Notificado por WhatsApp 18/10/2026 BS1». Si ese cliente reporta que no conecta, es por la regeneración: necesita importar su configuración nueva.
- **El dinero y los mensajes no se tocan sin la palabra del propietario**: la app marca pagos SOLO con su toque; ninguna IA marca, envía ni cambia nada por su cuenta.
- **Datos honestos**: nada de gráficos ni métricas inventadas; un dato no disponible se muestra como no disponible, jamás como cero falso. Las cifras de demostración del prototipo NO son datos del negocio.
- **Diseño**: app Android nativa en sensación, móvil primero, tarjetas compactas, iconos consistentes (nunca emojis como iconos), y los textos explicativos largos van detrás de un botón «i» junto al título de la sección, no en el cuerpo. El detalle completo de diseño y lógica está en `ADMIN-3FORCE-SPEC.md` — incluye la lógica de pagos cerrada el 2026-10-30 (facturación por mes calendario, día oficial de cobro, días de gracia, sin pagos parciales, duplicado = mismo ID de transacción, cola sin conexión, etc.).

---

## 8. La API en breve

- Origen único: `https://reseller.3forcevpn.com`. Bases: `/api/v1/` (auth, túneles, hosts) y `/api/manager/v1/` (cuentas, matriz). Auth: `Authorization: Bearer <access>`.
- La documentación oficial completa (148 endpoints) está en `API-WEB-3FORCE.md`. Avisos: los roles que el índice declara son poco fiables (p. ej. `reprovision` dice «staff» y funciona como reseller), y el índice NO documenta los campos de escritura del PATCH de túneles — de ahí el esquema OPTIONS de la v0.8.5.
- En la web, el estado del túnel se ve como: lista «STATUS: ACTIVE», detalle con insignia «Active» (el «Status: online» del detalle es del HOST) e interruptor «Tunnel status» en Configure. El vocabulario del filtro de estados es **Active / Grace / Disabled / Expired**. La página no expone qué campo escribe ese interruptor (verificado el 2026-10-31: interruptor sin nombre ni valor accesibles, y los JS del panel no son inspeccionables desde el navegador usado).

---

## 9. Pendientes completos (más allá del siguiente paso)

1. Probar en Diego el deshabilitar/habilitar de la v0.8.5 y leer el esquema OPTIONS (paso inmediato, §3).
2. Probar la nota de la CUENTA sobre `prueba_claude_3010`.
3. Confirmar que las descargas llegan como `.conf`.
4. Etapa SMS: Pagos › Recibidos (lectura/parseo local de SMS de Transfermóvil/EnZona, validación contra la lista de tarjetas permitidas configurada a mano en la app, deduplicación por ID de transacción, no identificados a su sección).
5. Crear cuentas/túneles desde la app (bloqueado por el fallo de creación del panel).
6. Eliminar túnel (solo con orden expresa y con la creación reparada).
7. Construir la **app nativa definitiva** con todos los flujos ya probados en la Lite.

---

*Documento de traspaso generado el 2026-10-31. El estado descrito es el de ese momento; la fuente viva del detalle es `API-INSPECTION.md` (registro de rondas) y `lite/BUILD-HOME.md` (bitácora de construcción).*
