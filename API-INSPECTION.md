# Admin 3FORCE — Inspección de la API del panel (2026-10-30)

Inspección estrictamente de solo lectura, sesión como «Gennis» (admin),
30/10/2026 ~17:52–18:00 CDT. No se creó, editó, deshabilitó, eliminó ni marcó
nada. La cuenta SawVPN no se usó en esta inspección.

## Confirmado

- **Origen único:** https://reseller.3forcevpn.com, con DOS bases:
  - `/api/manager/v1/` — gestión del panel (cuentas, matriz, perfil manager).
  - `/api/v1/` — túneles y auth/sesión.
  Verificado en vivo: GETs sin credenciales a rutas de ambas bases devuelven
  `{"detail":"Las credenciales de autenticación no se proveyeron."}` (no 404).
  Developers describe el manager como «puerto 5001», pero la web app llama al
  origen público, sin puerto expuesto.
- **Auth (Developers, API v2.1.0, 148 endpoints):**
  - Login: `POST /api/v1/auth/login/` (público), body documentado
    `{ username, password }`.
  - Refresh: `POST /api/v1/auth/refresh/` (JWT). «Refresh rotativo con
    blacklist. Renueva el access (10 min). Rate-limit suave 30/min por IP.»
  - Sesión: `GET/POST /api/v1/auth/session/?context=client|manager|mobile`
    (JWT) — «devuelve requestData (user + flags)»; el manager usa
    `context=manager`.
- **Endpoints clave y rol documentado:**
  - `GET/POST /api/manager/v1/accounts/users/` — staff/reseller (reseller ve
    solo sus clientes; excluye al propio orderer).
  - `GET/PATCH /api/manager/v1/profile/` — JWT (perfil propio del manager).
  - `GET/POST /api/v1/tunnels/?personal=true|false` — JWT (scope reseller por
    ?personal). Detalle: `GET/PATCH/DELETE /api/v1/tunnels/<id>/`.
  - `GET /api/manager/v1/billing/manual-billing/matrix/` — staff/reseller.
  - `POST /api/manager/v1/billing/manual-billing/update/` — staff/reseller
    (NO ejecutado en esta inspección).
  - Relacionados: `GET /api/manager/v1/billing/user-transactions/?personal=`,
    `GET /api/manager/v1/billing/financial-report/` (staff/reseller),
    `GET /api/v1/invoices/?personal=true|false` (JWT).

## Estructura observada en la UI (etiquetas, NO claves JSON crudas)

- **Cuentas/Usuarios** (vista admin: 249 totales, 30 por página): columnas
  USUARIO, TIPO (Cliente/Reseller), **ALIAS** (existe; en el detalle «Alias
  P2P»), USUARIO DE TELEGRAM, ZONA, ESTADO (Activo), TÚNELES (conteo), STATS,
  ÚLTIMO LOGIN. Detalle: estado, Alias P2P, Telegram («No Vinculado»), Wallet
  (saldo y «Activa»), Zona, Túneles, «Cliente de» (reseller propietario),
  Último Login, Creado, Actualizado, Programa de Referidos. **No aparece
  campo de comentario/nota de cuenta** ni en la lista ni en el detalle (el
  formulario de edición no se abrió: su ausencia ahí queda sin verificar).
  Dato de Developers (Perfil v1): PATCH de perfil «edita alias (único,
  alfanumérico 2–20; requerido para P2P)».
- **Túneles** (vista admin: 171 totales, 167 activos, 1 expirado): columnas
  PROPIETARIO, NOMBRE, PROTOCOLO (WireGuard), HOST & SALIDA (etiqueta de host
  + «online» + país/región de salida), PLANES, PROVISIONING, ESTADO (ACTIVO).
  Detalle: id numérico, Nombre, **NFU (nombre para el usuario)**, Propietario,
  Alias del propietario, Telegram, Host, Estado «online», Zona, IP pública,
  Puerto WG, sección SALIDA, SUSCRIPCIONES (Plan / Precio / Inicio /
  Vencimiento / Estado: Activo, Expirado), CREADO · ACTUALIZADO. El tipo de
  facturación no es columna: PLANES muestra el nombre del plan con
  «Vence: <fecha>» o la etiqueta «Pago manual». **No aparece campo de
  comentario del túnel** ni en la lista ni en el detalle.
- **Matriz de facturación manual** («Control de Pagos»): filtros año/mes,
  «Todos los Túneles», «Cualquier Estado»; vista admin: 35 filas (el
  subconjunto de pago manual). Columnas: INFO (USER, TÚNEL, NFU, ESTADO:
  ACTIVO/DESHABILITADO) + ENE…DIC. Las celdas de mes son botones con iconos
  de estado de pago. **Las notas viven a nivel de PAGO/MES en la matriz:**
  «Las notas guardadas muestran un icono amarillo. Pasa el cursor sobre un
  pago para ver detalles.» El tooltip de un pago marcado muestra quién lo
  marcó y la fecha.
- **Dashboard** (vista admin): contadores CUENTAS ACTIVAS 249, FACTURAS
  PAGADAS 692, ANUNCIOS ACTIVOS 1. No se identificó un endpoint «dashboard»
  en Developers que los alimente (no confirmado; pueden agregarse de listas).

## NO confirmado (no inventar; verificar en la fase SawVPN)

- Nombre/formato exacto del encabezado del token (las herramientas no
  exponen cabeceras de red; «Authorization: Bearer» es la convención
  probable — el error de auth es formato DRF — pero NO está verificado).
- Nombres de campos de petición/respuesta del refresh.
- Claves JSON crudas y tipos de las respuestas (todo lo de arriba son
  etiquetas renderizadas por la UI).
- Nombres de campos JSON de nombre y rol en el perfil/sesión.
- Si el formulario de EDICIÓN de cuenta o el «Configurar» del túnel tienen
  campo de comentario (relevante: la spec asume nota = comentario web del
  túnel; en la UI de detalle no apareció ninguno).

## Fase SawVPN — RESULTADOS (2026-10-30 ~18:01–18:18 CDT, sin borrar nada)

Sesión reseller confirmada («Panel de Reseller», «Bienvenido, SawVPN»).

- **Totales reales de SawVPN:** 61 usuarios (60 reales + 1 de prueba creada en
  esta fase), 61 activos, 5 nuevos (30d); **32 túneles, todos activos**;
  matriz de facturación manual 2026/Oct: **32 filas**; cuota de túneles con
  pago manual: **32 de 100** (quedan 68). Dashboard reseller: MIS CLIENTES 61,
  TÚNELES ACTIVOS 32, FACTURAS PAGADAS 1, ANUNCIOS ACTIVOS 0, BALANCE $0.
- **Sondas API con cabeceras: NO ejecutables desde el navegador** (las
  herramientas no permiten peticiones en página con cabeceras personalizadas
  ni leer localStorage). El header exacto, el refresh y las claves JSON
  siguen sin confirmar por esta vía; se verificarán desde la propia app en el
  teléfono de Gennis (endpoints documentados + cabecera JWT estándar +
  diagnóstico de respuestas visibles, sin inventar datos).
- **Cuenta de prueba CREADA:** `prueba_claude_3010` (activa, zona «SawVPN
  Nacional + Cuba», 0 túneles, nota guardada). Formulario «Nuevo Usuario»:
  USUARIO *, CONTRASEÑA INICIAL *, USUARIO DE TELEGRAM (opc.), **ALIAS**
  (opc.; único, alfanumérico + guión bajo, 2–20; «Requerido para
  transferencias P2P»), ZONA (opc.), **NOTAS INTERNAS**, interruptor «Acceso
  Habilitado / Permitir login». El alias con espacios fue rechazado; se usó
  PRUEBA_CLAUDE_3010 y se muestra en minúsculas. **Conclusiones: el alias SÍ
  existe y es editable; el campo de nota de cuenta SÍ existe** («NOTAS
  INTERNAS» en creación y edición — pre-rellenada al editar — y sección
  «NOTAS ADMINISTRATIVAS» en el detalle). El ID numérico de la cuenta no se
  expone en la UI reseller. Menú de acciones reseller: Ver Detalles, Editar
  Datos, Contraseña, Eliminar (sin «Iniciar sesión como»).
- **Formulario «Crear Nuevo Túnel»** (pestañas Información General y
  Facturación). General: NOMBRE INTERNO *, PROPIETARIO * (buscador), ZONA,
  Estado del túnel (ACTIVO), «Generar configuración automáticamente» (por
  defecto; el backend elige host y credenciales de las zonas asignadas),
  PROTOCOLO * (WireGuard), NOMBRE PARA EL USUARIO, PERFIL QOS (OVERRIDE)
  («Heredar del plan») y **NOTAS ADMINISTRATIVAS — el comentario del túnel SÍ
  existe en la creación**. Sin generación automática aparece SERVIDOR (HOSTS
  DISPONIBLES) con hosts ONLINE (S1-MIAMI-WINDSCRIBE, S3-NEW YORK, S2-CUBA) y
  un panel «Host Seleccionado» que mostró «Límite máximo: 0 túneles» y
  «Activos / Inactivos: 0/0». Facturación: «Túneles manuales: 32 de 100» y el
  interruptor «Pago Manual» («El túnel será creado y activado inmediatamente
  sin requerir un plan inicial o pasarela de pago»).
- **LA CREACIÓN DE TÚNELES FALLA EN EL PANEL:** 5 intentos (variando zona,
  host automático/manual S1-MIAMI-WINDSCRIBE, nombre con/sin guion y nota),
  todos terminan en «Error de conexión / No se pudo conectar con el
  servidor»; el total se quedó en 32. El mismo aviso transitorio aparece al
  cargar la vista de Túneles (sugiere una llamada secundaria caída). Causa no
  confirmada; pendiente que Gennis lo compruebe desde su teléfono. Sin túnel
  de prueba no hubo detalle/«Configurar» que revisar ni fila en la matriz:
  **no se marcó ningún pago ni se forzó nada sobre clientes reales**.
- **Estado final dejado:** la cuenta de prueba activa y en su sitio, sin
  borrar ni deshabilitar nada; ningún cliente real tocado.

## Confirmado desde la app — prueba real de Gennis (2026-10-30, 18:28–18:29)

Diagnóstico API de la Lite v0.3.0 corriendo en su teléfono, sesión SawVPN:

- **`Authorization: Bearer <access>` CONFIRMADO**: usuarios, túneles y matriz
  devolvieron HTTP 200 con el token guardado por la app.
- **El refresh funciona en la práctica**: el access capturado a las 17:41
  expiró a las 17:51 y las llamadas de las 18:29 devolvieron 200, así que la
  renovación con el refresh guardado operó (las claves de su respuesta aún no
  se han capturado en el diagnóstico).
- **Sesión**: `GET /api/v1/auth/session/?context=manager` → **HTTP 405**
  («Método no permitido»): el endpoint es **POST**. Las claves del objeto
  usuario quedan por capturar con el POST.
- **Usuarios** `GET /api/manager/v1/accounts/users/` → 200. Sobre:
  `{count, next, previous, results, page_size, current_page, metadata}`.
  Claves del item: `id, is_telegram_verified, tunnels_count, clients_count,
  manual_tunnel_limit, referrals_count, wallet, zone, created_by_reseller,
  username, alias, email, full_name, profile_image, telegram_id,
  telegram_username, referral_code, was_active_before_reseller_deactivation,
  is_verified_email, is_active, is_staff, is_superuser, is_reseller, notes,
  last_login, created_at, updated_at, referred_by, groups, user_permissions`.
  → La cuenta trae **`alias`** y **`notes`** por API, y **`tunnels_count`**
  (el Home derivó Con túneles 32 · Sin túneles 29, total 61 ✓).
- **Túneles** `GET /api/v1/tunnels/?personal=true` → 200 pero **solo 3
  items** (no los 32 de los clientes: ese alcance parece ser los túneles
  propios del reseller). Claves del item: `id, name, protocol, status, host,
  created_at, can_download_config, l2tp_credentials, manual_payment, plans,
  provisioning_status, available_protocols`. **Sin campo propietario** en
  este alcance. Trae **`manual_payment`** (booleano) → el split
  manual/automático es derivable. Pendiente: probar `?personal=false` o sin
  parámetro para la lista de clientes (esperados 32).
- **Matriz** `GET /api/manager/v1/billing/manual-billing/matrix/` → 200.
  Sobre `{count, next, previous, results, metadata}`, page_size 30, 32 items
  en 2 páginas. Claves de la fila: `tunnel, user, months`. Octubre 2026: **27
  celdas reconocidas como pagadas y 5 filas sin celda del mes reconocible**
  (el Home mostró Pagados 27 · Pendientes 0; lo correcto exige conocer la
  forma de la celda no pagada). Pendiente: exponer en el diagnóstico la forma
  de `months` (una celda pagada y una no reconocida: tipo + claves).
- **Tasa** `GET https://www.cambiocup.com/api/history?coin=CUP` → 200,
  `{data: [{time, value}], coin}`; último valor 1106 CUP a las 18:29.

## Segunda prueba real — Lite v0.3.1 (2026-10-30, 18:34)

- **Sesión POST → 200.** Respuesta: `{user, stats, client_url, manager_url}`;
  el objeto usuario va en `user` con claves: `id, username, referral_code,
  alias, last_login, created_at, telegram_username, is_telegram_verified,
  support_telegram, wallet, referral_config, referral_earnings_30d,
  is_reseller, reseller_profile`. El Home ya muestra «SawVPN · Reseller».
  Existe un objeto **`stats`** (claves aún sin capturar; puede traer los
  contadores del dashboard).
- **Túneles `?personal=false` → 32 items y serializer COMPLETO**: `id, user,
  host, zone, subscriptions, l2tp_credentials, name, name_for_user,
  manual_payment, disabled_date, deleted_at, expiration_date, protocol,
  status, provisioning_status, provisioned_at, provisioning_error, notes,
  updated_at, created_at, address, state_hash, queue_profile, agent,
  mikrotik_device, interface`. → Este alcance trae el enlace al propietario
  (`user`), el NFU (`name_for_user`), las **notas del túnel** y
  `disabled_date`: todo lo que necesitan las vistas Cuentas y Túneles.
  (`?personal=true` = 3 items con serializer reducido, sin `user` ni
  `notes`.) Home ya correcto: Túneles 32, Activos 32.
- **Matriz — forma de `months` resuelta**: `months` es un ARREGLO de celdas
  (la 1ª fila traía 4). Celda: `{month, year, is_paid, notes, marked_at,
  marked_by}` — la celda pagada de octubre trae `is_paid=true`, y la celda
  lleva la **nota del pago** y quién/cuándo marcó. **Las 5 filas restantes NO
  tienen objeto de octubre en su arreglo** («sin celda del mes: 5»).
  Interpretación pendiente de confirmar con los nombres (vía diagnóstico
  completo): si esas 5 son los pendientes conocidos de octubre, la regla es
  «sin celda del mes = pendiente» (el panel crea la celda al marcar).
- **Automáticos = 0**, derivado de `manual_payment` (los 32 túneles de
  clientes son de pago manual; consistente con la matriz de 32 filas).
- Estado del Home tras v0.3.1: todo correcto salvo «Pendientes del mes»,
  que muestra 0 mientras las 5 filas sin celda no se interpreten.

## Diagnóstico completo v0.4.0 interpretado (2026-10-31, 08:49)

- **Refresh RESUELTO**: `POST /api/v1/auth/refresh/` con campo `refresh` →
  respuesta `{access, refresh}` (rotativo). Access: 10 min. **Refresh:
  ~14 días** (iat 31/10 08:48 → exp 14/11 07:48). La sesión capturada el
  30/10 17:41 se renovó sola el 31/10 08:48 — la ruta híbrida queda
  probada de un día para otro.
- **Stats de sesión**: `{clients_count, tunnels_count}` (61 / 32) —
  contadores directos para el Home.
- **Usuarios**: 61 activos (0 deshabilitados), 32 con túneles / 29 sin,
  **los 61 tienen alias**, solo 3 con `notes` no vacía.
- **Túneles**: `status` = «active» ×32; `manual_payment` true ×32;
  `notes` no vacía en 9; `disabled_date` en 0. **`host` es un objeto**
  `{id, type, name, status, agent_status, out, listen_port, is_active,
  mikrotik}` y su `name` es la etiqueta legible (16 servidores distintos
  entre los 32 túneles).
- **Matriz fila por fila**: `tunnel` = `{id, name, name_for_user, status}`,
  `user` = `{username, profile_image, is_active}`. Los arreglos `months`
  varían (huecos reales: Enrique no tiene 7/2026; X69 solo 3 celdas) → la
  celda existe solo cuando el mes quedó registrado en el panel.
- **REGLA DE PENDIENTES CONFIRMADA con nombres**: las 5 filas sin celda de
  10/2026 son **Cellmaster87, Savior, YUM4 y Mlealglez** (los pendientes
  de octubre ya conocidos, mlealglez con su arreglo de noviembre) **+
  Diego**, cuyo arreglo está COMPLETAMENTE vacío (nunca se le ha marcado
  un mes: está fuera de cobro por decisión de Gennis). Conclusión: para un
  túnel dentro de cobro, **sin celda del mes = pendiente**; el Home debe
  contar Pagados 27 · Pendientes 5 (verdad del panel, Diego incluido) o 4
  si Gennis decide excluir a Diego del conteo — decisión suya pendiente.
  La lista de pendientes debe ser visible (toque en la fila) para que el
  número sea transparente.
- Dato cruzado: RogerCarlos ya trae celda 11/2026 (pagó noviembre por
  adelantado — coincide con su registro). Astro, Jorsh y nino92 solo
  traen 10/2026 (túneles recientes).

## Creación de túneles: confirmación de Gennis (2026-10-31)

Gennis confirmó que la creación de túneles **también falla desde su
teléfono**: es un error de la web/panel, no de la ruta del navegador de la
VM. La cuenta `prueba_claude_3010` sigue sin túnel y la prueba de
escritura (marcar pagado) queda **aplazada por decisión suya** («podemos
probar eso más tarde»). El diagnóstico v0.5.0 del 31/10 09:00 no registra
ningún intento de escritura; todas las lecturas siguen sanas y la matriz
ya aplica la regla confirmada («sin celda del mes: 5 (pendientes por la
regla confirmada el 31/10)»).

## Primera escritura real — marcar pagado (2026-10-31, 09:54, Lite v0.6.1)

La hoja abrió (bug de capas de la v0.6.0 arreglado en la v0.6.1) y el
intento sobre el túnel de Diego (id **525**, Octubre 2026, nota «Prueba de
pago de diego») LLEGÓ al servidor:

- `POST /api/manager/v1/billing/manual-billing/update/`
- Enviado: `{"tunnel":525,"month":10,"year":2026,"is_paid":true,"notes":"Prueba de pago de diego"}`
- Respuesta: **HTTP 400** `{"tunnel_id":["Este campo es requerido."]}`

→ El campo correcto es **`tunnel_id`**, no `tunnel`. Ningún otro campo
fue señalado: month/year/is_paid/notes se aceptan con esos nombres. El
400 fue de validación — **nada quedó marcado** (Diego sigue SIN CELDA).
Hotfix v0.6.2 despachado con el renombre `tunnel` → `tunnel_id` y nada
más; la misma prueba sobre Diego fijará el esquema definitivo.

## MARCAR PAGADO — ESQUEMA CONFIRMADO (2026-10-31, 10:02, Lite v0.6.2) ✅

Segundo intento sobre Diego (tunnel_id 525), con el campo corregido:

- `POST /api/manager/v1/billing/manual-billing/update/`
- Cuerpo: `{"tunnel_id":525,"month":10,"year":2026,"is_paid":true,"notes":"Prueba de pago de diego"}`
- Respuesta: **HTTP 200** —
  `{"message":"Registro creado exitosamente.","data":{"tunnel_id":525,"year":2026,"month":10,"is_paid":true,"notes":"Prueba de pago de diego","marked_at":"2026-10-31T14:02:07.950067+00:00","marked_by":"SawVPN"}}`

La app recargó la matriz: **28 pagadas · 4 sin celda**, y la fila de Diego
pasó a `meses: 10/2026 · is_paid=true`. Gennis lo verificó en la web (la
celda quedó marcada y con su nota) y **desmarcó él mismo el pago desde
la página** para seguir probando: Diego vuelve a estar pendiente.

**Esquema definitivo de la escritura de pagos:**
`{tunnel_id, month, year, is_paid, notes}` → 200 `{message, data{…,
marked_at, marked_by}}`. La escritura principal de Admin 3FORCE queda
PROBADA de punta a punta.

## Ronda de acciones sobre Diego (2026-10-31, 10:18–10:20, Lite v0.6.2)

- ✅ **REAPROVISIONAR CONFIRMADO**: `POST /api/v1/tunnels/525/reprovision/`
  con `{}` → **200** `{"detail":"Operación de reaprovisionamiento enviada
  correctamente."}`. Las acciones de túnel viven bajo
  `/api/v1/tunnels/<id>/<acción>/`.
- **Servidores — la ruta existe y es por túnel**: `GET /api/v1/hosts/` →
  **400** `{"detail":"Se debe proporcionar 'tunnel_id'"}`. La lista se
  pide como `GET /api/v1/hosts/?tunnel_id=<id>` (forma por confirmar).
  `/api/manager/v1/hosts/` → 404 HTML; `/api/v1/tunnels/hosts/` → 404.
- **Descargar configuración — 4 candidatas 404**: `download_config/`,
  `download/`, `config/` (404 HTML) y `download-config/` (404 JSON).
  Falta probar: el DETALLE del túnel (nunca se ha leído un
  `GET /api/v1/tunnels/<id>/` — puede traer la config dentro) y la
  familia manager.
- **Regenerar — 404**: `POST /api/v1/tunnels/525/regenerate/` → 404 con
  el detail genérico «No se encontró la información indicada o no tiene
  permisos…». Siguiente candidata: `regenerate_config/`.
- **Editar nota (túnel) y Deshabilitar — 404**: `PATCH
  /api/v1/tunnels/525/` (con `{notes}` y con `{status:"disabled"}`) →
  el mismo 404 genérico. La ruta de detalle no acepta PATCH (o no
  existe tal cual); deshabilitar/habilitar probablemente sean acciones
  `disable/`/`enable/` como `reprovision/`, y la nota quizá vaya por la
  familia manager.
- Efecto lateral verificado: la desmarcada web de Gennis dejó en Diego
  una **celda explícita is_paid=false** para 10/2026 (la matriz lee
  27 pagadas · 1 pendiente con celda · 4 sin celda; pendientes totales
  = 5, como manda la regla).

## Ronda 2 de acciones sobre Diego (2026-10-31, 10:27–10:28, Lite v0.7.0)

- ✅ **SERVIDORES RESUELTO**: `GET /api/v1/hosts/?tunnel_id=525` → **200,
  14 hosts**, item `{id, name, name_for_user, protocol, status, allow_pf,
  out}`. (El cambio de servidor — POST reassign_host {host_id} — no se
  intentó en esta ronda.)
- **El DETALLE de túnel NO existe**: `GET /api/v1/tunnels/525/` → 404
  `{detail}`. La lista es todo lo que da la API de túneles; no hay
  config escondida en un detalle.
- **Descargar**: detalle 404 + `GET /api/manager/v1/tunnels/525/
  download_config/` 404 HTML. Sigue sin ruta.
- **Regenerar**: `POST .../regenerate_config/` → 404 **HTML** (ruta
  inexistente en el URLconf, distinto del 404 JSON genérico de
  `regenerate/` en la ronda 1).
- **Deshabilitar**: `POST .../disable/` → 404 **HTML** (ruta
  inexistente). Tampoco es acción suelta.
- **Editar nota (túnel)**: `PATCH /api/manager/v1/tunnels/525/` → 404
  JSON genérico: el recurso manager de túneles no existe con ese nombre.
- Reaprovisionar re-confirmado (200, segundo toque de Gennis, sin daño).
- Sin intentar aún: el cambio de servidor y la nota de la CUENTA.
- Siguiente paso de descubrimiento (en curso): leer las rutas reales en
  el propio código JS del panel (la SPA de Next.js llama a la misma API).

## Intentos 10:34–10:37 (Lite v0.7.1, bloque «Intentos» — el formato nuevo funciona)

- **Cambiar de servidor — la ruta `reassign_host/` NO existe**:
  `POST /api/v1/tunnels/525/reassign_host/` con `{"host_id":41}` →
  404 **HTML**. Diego no se movió de servidor. La lista (GET
  hosts?tunnel_id) se re-confirmó 200 dos veces.
- Reintentados con el mismo resultado: nota de túnel por manager (404
  JSON ×2), detalle (404), descarga (404/404), `disable/` (404 HTML).
- **Sigue sin probarse la nota de la CUENTA** (PATCH
  /api/manager/v1/accounts/users/<id>/): Gennis probó la del túnel,
  no la de la cuenta — son botones distintos en vistas distintas.
- Pendiente: la lectura del índice Developers (en curso vía navegador,
  como Gennis, solo lectura) para fijar las rutas que quedan.

## ÍNDICE DEVELOPERS LEÍDO (2026-10-31, 10:30–10:39, como Gennis, solo lectura) — LAS RUTAS DOCUMENTADAS

Sección «Túneles — v1/tunnels/» del propio panel (API v2.1.0):

- **Descargar**: `GET /api/v1/tunnels/<id>/download-config/` (JWT) —
  descarga el .conf WireGuard. **Con GUION**; el de guion bajo
  (`download_config/`) no existe.
- **Regenerar**: `POST /api/v1/tunnels/<id>/regenerate/` (JWT) —
  regenera claves/config, notifica a staff + reseller. Documentado,
  aunque el intento de la ronda 1 dio el 404 JSON genérico: a
  re-verificar en la ronda 3 (posible alcance/permiso del viewset).
- **Cambiar de servidor**: `POST /api/v1/tunnels/<id>/reassign-host/`
  (staff/reseller) — **con GUION**; el de guion bajo dio 404 HTML.
- **Detalle/editar/eliminar**: `GET/PATCH/DELETE
  /api/v1/tunnels/<id>/` — detalle, edita, elimina (soft-delete; el
  outbox propaga el remove al agente). Documentado, aunque el GET dio
  404 para el túnel de cliente 525 desde la app (posible alcance).
- **NO hay endpoint de notas** (la búsqueda «notes»/«nota» en la
  propia página: sin resultados): la nota del túnel se edita vía el
  PATCH del detalle, `{notes}`.
- **NO hay endpoint disable/enable**: deshabilitar/habilitar = PATCH
  del detalle con el estado (`{status}`), el mecanismo «edita»
  documentado.
- **Eliminar** = `DELETE /api/v1/tunnels/<id>/` (soft-delete).
- Cuentas: el índice lista `users/` como GET/POST; SÍ documenta
  `POST /api/manager/v1/accounts/users/<id>/reset-password/`
  (staff/reseller). El PATCH de la nota de cuenta queda como
  candidato genérico del viewset.
- Los ROLES del índice no son fiables: reprovision figura «staff» y
  funciona como reseller SawVPN (probado dos veces).
- Nada se tocó en la cuenta de Gennis: navegación y lectura solamente.

## Ronda 3 (2026-10-31, 11:06, Lite v0.8.0, rutas documentadas) — LA FAMILIA «DETALLE» REBOTA

Con las rutas EXACTAS del índice, sobre el túnel 525 (de cliente):

- `GET /api/v1/tunnels/525/` → 404 `{detail}`.
- `GET /api/v1/tunnels/525/download-config/` → **404 JSON** `{detail}`
  (la ruta EXISTE en el URLconf — no es el 404 HTML — pero el objeto
  no se encuentra / no hay permiso para este token).
- `PATCH /api/v1/tunnels/525/` con `{notes}` → 404 genérico.
- `PATCH /api/v1/tunnels/525/` con `{status:"disabled"}` → 404
  genérico.
- Mientras, en la misma sesión y token: lista `?personal=false` 200,
  `hosts?tunnel_id=525` 200, y `reprovision/` 200 (dos veces, rondas
  anteriores).

**Patrón**: toda la familia del detalle (retrieve / download-config /
PATCH) rebota para túneles DE CLIENTES con el JWT del reseller,
aunque el índice las documenta. Hipótesis de trabajo: ese viewset ve
solo los túneles PROPIOS de la cuenta (alcance «personal») salvo que
se le pida el alcance amplio — la lista lo hace con `?personal=false`.
`reprovision` vive en otra familia (alcance de gestión) y por eso
funciona. Prueba de un solo variable en la v0.8.1: añadir
`?personal=false` a la familia del detalle y, para descargar, un
segundo intento de lectura en la familia manager CON GUION
(`/api/manager/v1/tunnels/<id>/download-config/`, nunca probada).
Pendientes de esta ronda cuando se pegó el bloque: Regenerar y
Cambiar de servidor (familia de acciones; `reassign-host/` está
documentada staff/reseller) — Gennis las corre en la 0.8.0.

## Ronda 4 (2026-10-31, 11:12–11:15, Lite v0.8.1) — ✅ HIPÓTESIS CONFIRMADA: `?personal=false` lo desbloquea

La familia del detalle SÍ honra el parámetro de alcance de la lista.
Sobre Diego (525), con el JWT reseller de SawVPN:

- ✅ **NOTA DE TÚNEL**: `PATCH /api/v1/tunnels/525/?personal=false`
  `{"notes":"ns"}` → **200**. La respuesta es el detalle COMPLETO con
  serializer anidado (user{…, wallet}, host{…, mikrotik}, zone{hosts[]}).
- ✅ **DESCARGAR**: `GET /api/v1/tunnels/525/download-config/
  ?personal=false` → **200**, application/octet-stream, 263
  caracteres, texto WireGuard. (El segundo intento manager no hizo
  falta.)
- ✅ **DESHABILITAR**: `PATCH …/?personal=false {"status":"disabled"}`
  → **200**. Habilitar es el simétrico `{"status":"active"}` (misma
  familia ya probada; su toque quedó fuera de este bloque).
- ✅ **CAMBIAR DE SERVIDOR**: `POST /api/v1/tunnels/525/reassign-host/`
  `{"host_id":41}` → **200** SIN parámetro (familia de acciones, como
  reprovision). ⚠️ MATIZ: se envió host_id 41 (en la lista de la zona,
  S1-MIAMI-WINDSCRIBE) y el túnel quedó en host **47 «S3-CUBA»**
  (interfaz, mikrotik SAW-3). Antes estaba en la interfaz 45
  «S2-MIAMI-WINDSCRIBE». A verificar con Gennis qué nombre tocó en la
  lista: puede haber un mapeo zona→interfaz del panel o la lista por
  túnel numera distinto. La lista `hosts?tunnel_id` bajó de 14 a 13
  ítems entre rondas.
- ❌ **REGENERAR**: `POST …/regenerate/` SIN parámetro → 404 genérico
  (era el único de la familia que la 0.8.1 dejó sin tocar). Corrección
  obvia: añadir `?personal=false` — va en la v0.8.2.
- Estado de Diego tras la ronda: nota «ns», DESHABILITADO (11:13:34)
  y movido a S3-CUBA (11:15:36). Pendiente de Gennis: Habilitar y, si
  quiere, devolverlo a S2-MIAMI-WINDSCRIBE.
- Sin probar todavía: la nota de la CUENTA y el GET detalle con el
  parámetro (la sonda de Información; los PATCH ya devuelven el
  detalle, así que es menor).

## Verificación en el panel (2026-10-31, 11:17–11:19, como Gennis, SOLO LECTURA) — el «deshabilitar» NO se aplicó

Estado real del túnel 525 tras la ronda 4, visto en el panel web
(Tunnel Management → #525 — Diego):

- **Estado: ACTIVE** en todas partes — lista «ACTIVE», Details con
  insignia «Active» y campo Status = «online», y en Configure el
  interruptor **«Tunnel status» activado** con etiqueta «ACTIVE».
  → El PATCH `{"status":"disabled"}` de la ronda 4 devolvió 200 con
  el objeto, pero **no cambió nada**: `status` es un valor de salida
  (online) que la API ignora al escribir. La intuición de Gennis
  («lo del deshabilitar no probé») era correcta en el resultado.
- **Servidor: S3-CUBA** confirmado (lista, Details y Configure).
  El cambio de servidor SÍ se aplicó.
- **Nota: «ns»** visible en Details › NOTES y en Configure ›
  ADMINISTRATIVE NOTES. La nota SÍ se aplicó.
- El mecanismo real de deshabilitar es el interruptor «Tunnel
  status» de la pestaña General de Configure → en la API apunta a un
  campo booleano (candidato: `is_active`) sobre el mismo PATCH del
  detalle con `?personal=false`. Sonda en la v0.8.3 con registro de
  las claves de la respuesta para confirmarlo en un toque.
- Nada se tocó en esta verificación (el editor se cerró con Cancel).

## Creación de túneles: SIGUE FALLANDO (2026-10-31, 11:28)

Gennis probó él mismo desde su teléfono, con cualquier cuenta, y la
creación de túneles **sigue dando fallos** — el error del panel/web
continúa activo (confirmado ya el 30/10 en la VM y el 31/10 en su
teléfono; hoy re-confirmado por él en vivo). Mi intento paralelo en
el navegador se detuvo a su orden para no duplicar. Consecuencias:
la cuenta de prueba `prueba_claude_3010` sigue con 0 túneles; la
ejecución de Eliminar sobre Diego sigue en espera (sin forma de
recrear el túnel); y cualquier «Crear túnel» desde la app chocará
con el mismo muro del panel mientras dure.

## Ronda 5 (2026-10-31, 11:29–11:32, Lite v0.8.3) — sobre Cellmaster87 (túnel 411, cliente real)

Gennis corrió esta ronda sobre el túnel **411 de Cellmaster87** (no
sobre Diego). Resultados:

- ✅ **DETALLE (sonda)**: `GET /api/v1/tunnels/411/?personal=false`
  → 200, mismas claves que la lista. La sonda queda resuelta.
- ✅ **DESCARGA**: `GET …/411/download-config/?personal=false` → 200
  (262 caracteres). (Pendiente su palabra: si el archivo llegó .conf
  con el arreglo de la 0.8.3.)
- ✅✅ **REGENERAR**: `POST …/411/regenerate/?personal=false {}` →
  **200**. Era el último 404: regenerar queda RESUELTO con el
  parámetro de alcance.
- ✅ **NOTA**: PATCH `{"notes":"Notificado por WhatsApp 18/10/2026
  NS"}` → 200 (nota de negocio real, puesta por Gennis).
- ⚠️⚠️ **CAMBIO DE SERVIDOR NO HONRA EL HOST ELEGIDO** (reporte de
  Gennis, CONFIRMADO por el diagnóstico): dos veces el servidor
  resultante no es el enviado —
  `{"host_id":43}` → quedó en la interfaz 50 «S3-ATLANTA»;
  `{"host_id":47}` → quedó en la interfaz 39 «S1-CUBA».
  (En la ronda 4: `{"host_id":41}` → interfaz 47 «S3-CUBA».)
  Cada cambio además re-aprovisiona el túnel en el host nuevo.
  Causas candidatas: (a) `reassign-host/` ignora el host pedido y
  auto-asigna (el propio panel menciona auto-reasignación al host
  menos cargado); (b) el id que espera el endpoint no es el de la
  lista `hosts?tunnel_id`. El diagnóstico NO registra el NOMBRE que
  Gennis tocó en el selector — hueco de registro a cerrar.
  Nueva hipótesis fuerte: el formulario Configure de la web (pestaña
  Network, «Selected Host») guarda con el MISMO PATCH del detalle
  que ya probó `{notes}` → la v0.8.4 prueba `PATCH
  /api/v1/tunnels/<id>/?personal=false {"host": <id>}` y registra
  nombre+id seleccionados y el host resultante de la respuesta.
- **Efectos reales sobre Cellmaster87**: su túnel se movió dos veces
  (quedó en **S1-CUBA**), sus claves se **regeneraron** (su .conf
  anterior puede dejar de conectar hasta importar la nueva) y su
  nota quedó «Notificado por WhatsApp 18/10/2026 NS».
  Deshabilitar/Habilitar con `{is_active}` sigue SIN probarse (no
  entró en esta ronda) y la nota de cuenta tampoco.

- Verificación en el panel (2026-10-31, 11:33–11:35, como Gennis,
  solo lectura): el 411 está **ACTIVE**, host **S1-CUBA** y nota
  «Notificado por WhatsApp 18/10/2026 NS» — el panel confirma al
  detalle lo que dijo el diagnóstico de la app.

## Ronda 6 (2026-10-31, 11:43–11:44, Lite v0.8.4) — ✅ CAMBIO DE SERVIDOR RESUELTO (PATCH {"host"})

Otra vez sobre Cellmaster87 (411), por elección de Gennis:

- La lista `hosts?tunnel_id=411` ahora registra los PARES y cierra el
  misterio de los ids: **un solo espacio de ids** (el de los hosts,
  interfaces incluidas): 50=S3-ATLANTA · 54=S3-NEW YORK · 48=S3-MIAMI
  · 53=S2-NEW YORK · 49=S3-MIAMI-WINDSCRIBE · 46=S2-ATLANTA ·
  43=S2-CUBA · 47=S3-CUBA · 45=S2-MIAMI-WINDSCRIBE · 44=S2-MIAMI ·
  42=S1-ATLANTA · 41=S1-MIAMI-WINDSCRIBE · 40=S1-MIAMI ·
  35=Miami-Directo (y 39=S1-CUBA en la zona). Con esto, los
  «desajustes» de `reassign-host/` eran reales: el endpoint **no
  honra el host pedido** (auto-asigna). Veredicto final:
  `reassign-host/` queda retirado.
- ✅✅ **CAMBIO DE SERVIDOR**: PATCH `/api/v1/tunnels/411/
  ?personal=false` con `{"host":50}` → **200**, seleccionado
  S3-ATLANTA (id 50) = **host resultante 50 S3-ATLANTA**. La vía del
  formulario web honra el host exacto. RESUELTO.
- ✅ Regenerar re-confirmado (200; el host no se mueve al regenerar).
- ✅ Nota re-editada por Gennis: «Notificado por WhatsApp 18/10/2026
  BS1» → 200.
- Descargas del 411 ×2 → 200 (262 caracteres). Sin queja del nombre
  esta vez; la confirmación del «.conf» sigue pendiente de su palabra.
- Deshabilitar/Habilitar con `{is_active}`: OTRA ronda sin probarse.
  Dato nuevo del resumen enriquecido: el objeto túnel de la respuesta
  NO trae clave `is_active` de nivel superior (sí la traen user y
  host) — si el PATCH `{is_active}` también resultara ignorado, el
  campo del interruptor será otro; un toque sobre Diego lo decide.
- Estado del 411: ACTIVO en **S3-ATLANTA** (elegido deliberadamente
  por Gennis en esta ronda), claves regeneradas por segunda vez y
  nota «…BS1».

## Ronda 6b (2026-10-31, 11:56, Lite v0.8.4) — ❌ {is_active} TAMBIÉN ES NO-OP; Diego restaurado a S2-MIAMI-WINDSCRIBE

- ❌ **DESHABILITAR**: PATCH `/api/v1/tunnels/525/?personal=false`
  con `{"is_active":false}` → **200 sin cambiar nada**: la respuesta
  y la sonda posterior muestran `status=active`, `disabled_date=null`
  — igual que `{status:"disabled"}` en la ronda 4. El objeto túnel
  NO tiene campo `is_active` propio y la API ignora ambos payloads.
  El campo real del interruptor «Tunnel status» de la web sigue
  desconocido.
- ✅ **Cambio de servidor re-confirmado sobre Diego**: PATCH
  `{"host":45}` → 200, seleccionado S2-MIAMI-WINDSCRIBE (id 45) =
  resultante 45 S2-MIAMI-WINDSCRIBE. **Diego queda restaurado a su
  servidor original**, ACTIVO, con su nota «ns» y octubre pendiente.
- Respuesta de Gennis a la ronda: «resuélvelo de la mejor manera que
  puedas» + exigencia nueva: **los cambios deben mostrarse
  inmediatos** (con la recarga completa anterior no se sabía si el
  cambio se había aplicado).

## v0.8.5 (2026-10-31) — cambios al instante + Deshabilitar guiado por OPTIONS

- **Cambios al instante**: la respuesta de cada escritura se fusiona
  en el ítem local y la vista se repinta al momento; después UNA
  lectura puntual del detalle confirma el resultado real y el toast
  lo dice (si el panel no aplicó el cambio, avisa «SIN CAMBIO»). Al
  marcar pagado se recarga solo la matriz.
- **Deshabilitar/Habilitar guiado por la propia API**: al tocar el
  botón, la app primero pide el esquema con `OPTIONS` al detalle
  (DRF declara en «actions» los campos escribibles del PATCH, tipo,
  solo-lectura y opciones), elige el campo de estado escribible
  (booleano conocido → status/state con sus opciones →
  disabled_date) y envía UNA escritura con él. El esquema entero
  queda en Intentos («Esquema de estado del túnel»). Si el esquema
  no declara campo de estado escribible, no se envía nada.
- `statusOf` nuevo: `disabled_date`/`deleted_at` no vacíos mandan
  sobre el `status` de salida.

## Verificación web del estado de Diego (2026-10-31, 11:58–12:01, como Gennis, SOLO LECTURA)

- Diego (525) se muestra ACTIVO en todas partes: lista «STATUS =
  ACTIVE», Details con insignia «Active» y campo Status = «online»
  (ese «online» es el estado del HOST), y en Configure el
  interruptor «Tunnel status» activado con etiqueta «ACTIVE».
  Host confirmado: **S2-MIAMI-WINDSCRIBE** (IP pública
  152.206.201.34, puerto WG 27015); sin suscripciones («No active
  or past subscriptions»); nota «ns»; Updated 31 oct 2026, 11:56.
- El filtro de estado de la lista usa el vocabulario **Active /
  Grace / Disabled / Expired** — «Disabled» es una etiqueta real
  de la web, pero el PATCH `{status:"disabled"}` ya demostró ser
  ignorado: la etiqueta no es el campo escribible.
- El interruptor NO expone nombre de campo, valor ni código
  accesible (role=switch, sin name/value; la vista del navegador
  no muestra los JS del panel): **el campo de API que escribe el
  interruptor NO se pudo determinar desde la página**. La vía que
  queda es el esquema OPTIONS que la v0.8.5 consulta desde la app.
- Nada se tocó: Configure se cerró con Cancel.

## Ronda 7 (2026-10-31 12:48, v0.8.5, sobre Cellmaster87 411 — lo ejecutó Gennis)

- Refresh 200. El OPTIONS del detalle NO dejó «actions»
  utilizables en la app (patch == null → cayó el respaldo de la
  0.8.5) y su entrada de esquema no aparece en el bloque pegado.
- ⚠️ **PIEZA CLAVE**: `PATCH /api/v1/tunnels/411/?personal=false`
  con `{"status":"inactive"}` → **HTTP 400**
  `{"detail":"Status: \"inactive\" no es una elección válida."}`.
  Lectura: el `status` SÍ se valida contra sus opciones al
  escribir («inactive» no es una de ellas; «disabled» SÍ pasó la
  validación en la ronda 4 pero el estado no se movió) → todo
  apunta a que el modelo recalcula el status al guardar desde
  `disabled_date` (null = activo). Vocabulario probable de
  opciones (del filtro web): active / grace / disabled / expired.
- El 400 rechazó la escritura ANTES de aplicar: el túnel 411 quedó
  intacto (sonda posterior 200, mismas claves).
- **v0.8.6** (versionCode 18, construida y entregada el mismo día):
  deshabilitar = `PATCH {"disabled_date": <instante UTC>}`,
  habilitar = `PATCH {"disabled_date": null}`; el OPTIONS queda
  como lectura diagnóstica registrada (su resultado va también en
  la nota de la escritura) y ya no decide la escritura. Ronda 8:
  probar en **Diego (525)**, no en clientes reales.

## Ronda 8 (2026-10-31 14:07, v0.8.6, sobre el túnel NUEVO 558 «Prueba» de prueba_claude_3010)

- 🎉 **LA CREACIÓN DE TÚNELES VOLVIÓ**: el túnel 558 (usuario
  prueba_claude_3010, id de usuario 277) fue creado hoy
  2026-10-31T12:56:48-04:00 y existe también un 559. El fallo de
  creación del panel (confirmado 11:28) quedó atrás ~12:56. Hay
  túnel desechable de verdad para pruebas.
- Reconfirmado sobre túnel fresco: sonda 200 · descarga 200
  (262 caracteres) · hosts 14 · cambio de servidor PATCH
  {"host":49} → 200 con host resultante EXACTO 49
  S3-MIAMI-WINDSCRIBE · regenerar 200 · reaprovisionar 200.
- **OPTIONS = HTTP 405 «Método no permitido»**: el detalle NO
  soporta OPTIONS. Se cierra el misterio de la ronda 7 (nunca
  hubo esquema que leer). La app debe dejar de llamarlo.
- ❌ **disabled_date TAMBIÉN es ignorado**: PATCH
  {"disabled_date":"2026-10-31T18:07:51Z"} → 200, pero la
  respuesta trae disabled_date=null, status=active y el
  updated_at SIN mover (quedó en la hora del reaprovisionar):
  el objeto ni se tocó. TERCER no-op: status / is_active /
  disabled_date no son escribibles por el PATCH del detalle.
  La documentación del PATCH solo declara queue_profile; notas
  y host sí funcionan (probados). El interruptor «Tunnel status»
  de la web NO escribe por esos campos.
- **Siguiente paso decisivo (dif de la web)**: Gennis apaga el
  interruptor del 558 en el Configure de la web y guarda; luego
  en la app edita la nota del 558 (la respuesta trae el objeto
  entero) y se compara campo a campo contra la línea base de
  esta ronda (status=active, disabled_date=null,
  provisioning_status=provisioned, state_hash 33fc2bef…).
  Lo que cambie ahí revela el mecanismo real. Sin ese dato, no
  hay v0.8.7: sería otro tiro al aire.

## Ronda 9 — preparación (2026-10-31 14:18–14:20, v0.8.6 → v0.8.7)

- ✅ **Nota del TÚNEL reconfirmada** sobre el 558: PATCH
  {"notes":"nota ultima"} → 200 y la respuesta la trae aplicada.
  (La nota de la CUENTA sigue sin probarse.)
- El intento de deshabilitar de las 14:18:44 repite el no-op de
  la ronda 8 (disabled_date ignorado).
- **Gennis autorizó la prueba de ELIMINAR**: creó dos túneles
  desechables (558 y 559) «una para eliminarla». Instruido:
  borrar el 559 y conservar el 558 (lleva el historial y la nota).
- **v0.8.7** (versionCode 19): Eliminar tal cual (DELETE al
  detalle ?personal=false + guarda de nombre) con el aviso
  actualizado (la creación ya funciona; lo eliminado no se
  deshace desde la app); Deshabilitar/Habilitar ya no llaman al
  OPTIONS (405). Pendiente: su toque de Eliminar + el bloque.

## Dif de la web — paso 1 (2026-10-31 14:24–14:29, navegador de Muse como SawVPN)

- A petición de Gennis («revisa qué está pasando»), Muse apagó el
  558 desde el propio panel web (cuenta SawVPN, túnel de pruebas):
  Configure → «Información General» → interruptor «Estado del
  túnel» OFF («DESHABILITADO») → «Guardar Cambios Generales» →
  «Operación completada — Tunel actualizado correctamente.»
- Resultado en el panel: la lista muestra el 558 **INACTIVO** y el
  contador de activos bajó 34 → 33; el detalle trae la etiqueta
  **«disabled»** (su campo «Estado: online» sigue siendo el del
  host). El 559 («Prueba-t1») quedó ACTIVO, intacto.
- El 558 quedó APAGADO a propósito: falta el paso 2 — Gennis edita
  la nota del 558 en la app y la respuesta (objeto entero) se
  compara contra la línea base de la ronda 8 para identificar
  QUÉ campo(s) movió la web.

## Ronda 10 — EL DIF (2026-10-31 14:31, v0.8.7) — ✅ MECANISMO DE DESHABILITAR IDENTIFICADO

Objeto del 558 DESHABILITADO por la web (respuestas de la app a
las 14:31:02 y 14:31:28) contra la línea base activa:

- `status`: "active" → **"disabled"**
- `disabled_date`: null → **"2026-10-31T14:29:08.524116-04:00"**
  (la hora EXACTA del guardado web: la pone EL SERVIDOR)
- `state_hash`: cambió (33fc2bef… → 2e7611cf…)
- Todo lo demás igual (deleted_at null, expiration_date null,
  provisioning_status "provisioned", host 49).

Conclusiones:
1. **disabled_date es de SALIDA** (lo rellena el backend al
   cambiar el estado): por eso escribirlo se ignoró siempre, y
   por eso el «Habilitar» de la app de las 14:31:02
   ({"disabled_date":null}) tampoco lo limpió.
2. **El campo escribible es `status`**: lo prueba el 400 de la
   ronda 7 — un campo de solo lectura no valida opciones al
   escribir; «inactive» rebotó como elección inválida y
   «disabled» es elección válida (vocabulario del filtro web:
   active/grace/disabled/expired).
3. **Reinterpretación de la ronda 4**: el {"status":"disabled"}
   de la 0.8.1 (200 a las 11:13:34) casi seguro SÍ deshabilitó a
   Diego, y el cambio de servidor de las 11:15:36 (reassign-host
   reaprovisiona) lo reactivó antes de la verificación del panel
   de las 11:17. El veredicto «no se aplicó» queda CORREGIDO: no
   hubo ninguna lectura entre la escritura y el
   reaprovisionamiento que lo desmintiera. La memoria de Gennis
   («hubo una versión en que funcionó») era correcta.
4. La app LEE bien el estado: Gennis confirmó que el 558 «sale
   deshabilitado en la app» (statusOf con status="disabled").

**v0.8.8** (versionCode 20): Deshabilitar/Habilitar =
PATCH {"status":"disabled"} / {"status":"active"}. Prueba de
cierre sobre el 558 (sigue deshabilitado de la web): Habilitar →
Deshabilitar desde la app, SIN tocar servidor ni reaprovisionar
entre medias (la lección de la ronda 4).

## Ronda 11 — cierre, parte 1 (2026-10-31 14:34, v0.8.8) — ✅✅ HABILITAR CONFIRMADO

- `PATCH /api/v1/tunnels/558/?personal=false` con
  `{"status":"active"}` → **200**: la respuesta trae
  status="active" y **disabled_date=null** (el servidor limpió
  la fecha que la web había puesto), y el `state_hash` volvió a
  **33fc2bef507caa05c4ec8985b65e92eb** — EXACTAMENTE el de la
  línea base activa de las rondas 8/9: el túnel regresó
  bit a bit a su estado activo original.
- El mecanismo queda probado en la dirección difícil (la app
  deshizo el apagado hecho por la propia web). Falta solo el
  toque simétrico de Deshabilitar desde la app para cerrar el
  capítulo en las dos direcciones.

## Ronda 12 — cierre, parte 2 (2026-10-31 14:38, v0.8.8) — ✅✅ DESHABILITAR CONFIRMADO

- `PATCH /api/v1/tunnels/558/?personal=false` con
  `{"status":"disabled"}` → **200**: status="disabled",
  disabled_date="2026-10-31T14:38:17.344832-04:00" (puesta por
  el servidor) y `state_hash` **2e7611cfb5ada242e73fda94c55e74d3**
  — IDÉNTICO al que produjo el interruptor de la propia web en
  la ronda 10. La app deja el túnel bit a bit como lo deja el
  panel.
- **CAPÍTULO DE ACCIONES DE TÚNEL CERRADO**: marcar pagado,
  descargar, cambiar de servidor, regenerar, reaprovisionar,
  nota del túnel, deshabilitar y habilitar — todo probado en
  vivo en las dos direcciones donde aplica. Quedan fuera solo
  Eliminar (armado, autorizado, sin ejecutar: objetivo 559) y
  la nota de la CUENTA (sin probar).
- El 558 quedó DESHABILITADO por la app (es de pruebas; se
  habilita cuando Gennis quiera, desde la misma app).

## Ronda 13 (2026-10-31 14:41, v0.8.8) — ✅✅ ELIMINAR CONFIRMADO

- `DELETE /api/v1/tunnels/559/?personal=false` → **HTTP 204**
  (sin cuerpo): el soft-delete documentado, ejecutado por
  Gennis sobre el desechable 559 «Prueba-t1» con la guarda de
  nombre de la app. Era la última acción sin ejecutar.
- **TODAS las acciones de túnel están probadas en vivo.**
  Único cabo menor que queda: la nota de la CUENTA (implementada
  desde la 0.6.0, nunca ejecutada) y la confirmación verbal del
  nombre `.conf` en las descargas.

## Ronda 14 (2026-10-31 14:42, v0.8.8) — ✅ NOTA DE LA CUENTA CONFIRMADA

- `PATCH /api/manager/v1/accounts/users/277/` con
  `{"notes":"se pudo la nota de la cuenta"}` → **HTTP 200** con
  el objeto completo de la cuenta. Era la última acción sin
  ejecutar en toda la app. Dato cruzado: la respuesta trae
  `tunnels_count: 1` — tras el borrado del 559, a la cuenta de
  prueba solo le queda el 558.
- **CIERRE TOTAL de la fase de acciones**: no queda ni una
  escritura de la Lite sin probar en vivo. Siguiente etapa: SMS
  en Pagos › Recibidos (o la app nativa), a elección de Gennis.

## v0.9.0-lite CONSTRUIDA (2026-10-31) — creación + reset, a probar

Por la orden de Gennis («primero todo lo relacionado a la web y
después lo local») la 0.9.0 cierra la parte web de la app:

- **Crear cuenta**: POST documentado a `/api/manager/v1/accounts/
  users/` con los campos del formulario web (username, password,
  alias, telegram_username, zone?, notes, is_active). Botón
  «+ Crear» en Cuentas.
- **Crear túnel**: POST documentado a `/api/v1/tunnels/` SIN el
  parámetro de alcance (la creación no es una búsqueda en el
  viewset; si lo exige, la respuesta lo dirá y se añade en la
  siguiente build — una variable por ronda). Cuerpo: user, name,
  protocol="wireguard", manual_payment, name_for_user?, zone?,
  notes?. El servidor se asigna solo; se cambia después. Botón
  «+ Crear» en Túneles, con selector de propietario (las cuentas
  cargadas) y de zona (las zonas con id vistas en las cuentas).
- **Restablecer contraseña**: POST documentado a
  `/api/manager/v1/accounts/users/<id>/reset-password/` con {};
  botón en el detalle de la cuenta. Qué devuelve el panel
  (¿la contraseña nueva?) lo dirá la primera respuesta real.
- El DELETE de cuentas NO se construyó: la documentación dice
  que es en CASCADE (transfiere el saldo al sistema).
- Todo con el patrón seguro (un intento por toque, respuesta
  entera en Intentos, recarga real al éxito). Prueba prevista
  sobre desechables identificables (cuenta prueba_crear_3110).

## Ronda 15 (2026-10-31 14:50–14:52, v0.9.0) — CREAR CUENTA ✅ · túnel y reset enseñan lo que falta

- ✅✅ **CREAR CUENTA CONFIRMADO**: POST /api/manager/v1/
  accounts/users/ → **201**. Cuenta `prueba_crear_3110` id 278
  (wallet 272). Sin alias enviado, el servidor AUTO-GENERA uno
  («xea3rqgp»); zone quedó null y fue aceptado; notes e
  is_active aplicados; referral_code generado por el servidor.
- **CREAR TÚNEL — 400 con la lección exacta**:
  1. `{"detail":"Name_for_user: Asegúrese de que este campo no
     tenga más de 20 caracteres."}` — **name_for_user ≤ 20**.
     (El resto del cuerpo pasó: user, name de 18 caracteres,
     protocol, manual_payment y notes sin queja.)
  2. Con name_for_user corto: `{"detail":"Host no resuelto."}` —
     la creación necesita resolver un HOST y sin zona ni host en
     el cuerpo no puede (la cuenta 278 tampoco tiene zona). El
     formulario web no trae campo de host: trae ZONA. Siguiente
     variable (v0.9.1): selector de servidor inicial (envía
     `host`, el campo que el PATCH del detalle ya aceptó) + la
     zona, y validación de los 20 caracteres en el formulario.
  (El 401→refresh→reintento único funcionó en silencio en el
  primer intento: el patrón seguro aguantó.)
- **RESET PASSWORD — 400**: `{"error":"El campo 'password' es
  requerido."}` — el endpoint NO genera la contraseña: la fija
  el admin en el cuerpo. La v0.9.1 pide la contraseña nueva en
  la hoja y envía {"password": …}.

## Ronda 16 (2026-10-31 14:59, v0.9.1) — ✅✅ CREAR TÚNEL y RESET CONFIRMADOS · la parte WEB queda cerrada

- Intento 1: nombre «prueba_crear_3110» → **HTTP 500**
  `{"detail":"El nombre solo puede contener letras, números y
  guiones."}` — el nombre interno NO admite guiones bajos
  (validación de negocio devuelta como 500, no 400).
- Intento 2: nombre «prueba», zone=11, SIN host → 400 «Host no
  resuelto.» — **la zona sola NO resuelve el host**.
- ✅✅ Intento 3: nombre «prueba», zone=11, **host=49** →
  **HTTP 201**: túnel **id 560** creado sobre la cuenta 278, en
  S3-MIAMI-WINDSCRIBE (el host elegido, exacto), con la zona 11
  en la respuesta. **CREAR TÚNEL CONFIRMADO: el campo que
  resuelve es `host`, obligatorio en la práctica.** (La web debe
  enviar un host por debajo: su campo ZONA no basta por la API.)
- ✅✅ **RESET PASSWORD CONFIRMADO**: POST
  …/accounts/users/278/reset-password/ con {"password": <la
  fijada por el admin>} → **200** `{"message":"Contraseña
  actualizada correctamente."}`.
- **CIERRE DE LA PARTE WEB**: no queda ni una acción del panel
  sin probar en vivo — leer todo, marcar pagado, descargar,
  cambiar de servidor, regenerar, reaprovisionar, notas de
  túnel y de cuenta, deshabilitar, habilitar, eliminar túnel,
  CREAR CUENTA, CREAR TÚNEL y RESETEAR CONTRASEÑA. Lo que sigue
  es la parte LOCAL (orden de Gennis): SMS, tarjetas permitidas,
  plantillas, contactos, servidores locales, notificaciones y
  cola offline.
- Desechables tras la ronda: cuenta 278 con su túnel 560
  «prueba» (activo, S3-MIAMI-WINDSCRIBE) y el 558 deshabilitado.

## Ronda 17 (2026-10-31 15:04) — EL CÓDIGO JS DEL PANEL LO EXPLICA TODO: «is_auto_generated» y el cuerpo exacto de lotes

A petición de Gennis («si no se elige servidor se autoasignará el
host, el sistema se encarga en la web — revisa»), se descargaron
los chunks JS públicos de la SPA del panel (páginas de login y
de túneles) y se leyó el código de SUS formularios:

- **El automático de la web es la bandera `is_auto_generated`.**
  El submit individual construye: {name, name_for_user|null,
  zone|null, notes|null, manual_payment, queue_profile?,
  protocol, is_auto_generated, status, user} y **solo si NO es
  automático añade `host: <id>`**. Sin la bandera, el servidor
  asume modo manual y exige host → el «Host no resuelto» de las
  rondas 15–16. La validación web coincide (error «host
  requerido» si manual_payment && !auto && !host).
- El crear individual con pago manual va al POST documentado
  /api/v1/tunnels/ (el mismo de la app); el de planes va a
  **POST /api/v1/tunnels/order-create/?personal=false** con
  `target_user` (flujo de «solicitud» — no aplica a pago manual).
- **Cuerpo EXACTO de lotes** (del submit web): {user, count,
  protocol, status:"active", manual_payment:true, name_prefix,
  notes|null} + zone si hay + host SOLO si !is_auto_generated
  && host. Ruta: POST /api/v1/tunnels/bulk-create/.
- **v0.9.3-lite (vc 24)**: el formulario individual replica la
  bandera (automático = is_auto_generated:true y sin host;
  manual = false + host) y añade status:"active"; selector de
  modo «Un túnel / Por lotes» con el lote replicando el cuerpo
  web (cantidad 2–50 de la SPEC). Pendiente: la ronda de
  Gennis (un túnel en Automático + un lote de 2 desechables).

## Ronda 18 (2026-10-31 15:08–15:10, v0.9.3) — ✅✅ AUTOMÁTICO CONFIRMADO · lotes: error del backend por investigar

- ✅✅ **AUTOMÁTICO CONFIRMADO**: POST /api/v1/tunnels/ con
  `is_auto_generated:true`, zone=11 y SIN host → **201**: túnel
  **561** creado y el panel le asignó solo el host **39
  «S1-CUBA»** (SAW-1). La bandera leída del JS era exactamente
  el mecanismo. (Gennis borró el 561 y también el 560 tras
  habilitarlo: limpieza hecha por él mismo, ambos DELETE 204.)
- ❌ **LOTES — error interno del backend**: POST bulk-create
  (auto, zone=11, count 3 y count 2) → 400 `{"detail":"Error
  al crear los túneles: The QuerySet value for an exact lookup
  must be limited to one result using slicing."}` — un error de
  ORM de Django DENTRO del endpoint, no un rechazo de campos.
  Hipótesis principal: la ZONA (su objeto trae 9 hosts; una
  búsqueda «exacta» contra ese conjunto encaja con el texto del
  error). La web por defecto crea el lote SIN zona
  («bulkZoneNone»). Siguiente prueba SIN build nueva (la 0.9.3
  ya lo permite): lote en Automático con **Sin zona**; si
  repite, lote con servidor manual elegido y Sin zona.

## Ronda 17b (2026-10-31 15:04–15:15) — inspección VISUAL del formulario web (navegador, sin crear nada)

- El formulario «Crear Nuevo Túnel» confirma lo leído del JS:
  el automático es el interruptor **«Generar configuración
  automáticamente»** (ACTIVADO por defecto) con la ayuda «Si
  está activo, el backend selecciona host y credenciales
  disponibles de las zonas asignadas». El campo SERVIDOR solo
  aparece con el interruptor apagado (lista buscable de 15
  hosts; sin opción «Automático» dentro).
- **Existen DOS zonas**: «SawVPN Nacional» y «SawVPN Nacional
  + Cuba» (la 11, la de siempre). ZONA por defecto: «Sin zona».
- El interruptor **Pago Manual viene APAGADO por defecto** en
  la web (la app lo trae en Sí a propósito: es el negocio de
  Gennis). Protocolos: WireGuard y **L2TP** (con L2TP la lista
  de hosts sale vacía: no hay hosts L2TP). PERFIL QOS: solo
  «Heredar del plan». Cuota de manuales: 33/100 (la lista total
  quedó en 33 por el 558). Planes del flujo con plan: SAW BASIC
  1.00/30 días y SAW PLUS 2.50/90 días (vía order-create).
- No se creó ni modificó nada (el diálogo se cerró con
  Cancelar en ambas revisiones).

## Ronda 19 (2026-10-31 15:20, v0.9.3) — ✅ LOTES CONFIRMADOS con servidor manual · el automático de lotes está roto EN EL PANEL

- Lote Automático SIN zona → el MISMO error QuerySet (la zona
  NO era el disparador). **La resolución automática de host
  del bulk-create está rota en el backend**: la web manda el
  mismo cuerpo en automático, así que su lote automático
  chocaría con el mismo error — es un fallo del panel.
- ✅✅ **Lote con servidor manual** (host=42 S1-ATLANTA, sin
  zona) → **201** «Se crearon 2 túneles correctamente.»:
  túneles **562 «lote-1»** y **563 «lote-2»** (enumeración
  base-N), ambos en S1-ATLANTA, activos (provisioning
  «pending» al responder). **CREAR POR LOTES CONFIRMADO.**
- Arreglo del lado de la app (v0.9.4): en el lote, «Automático»
  pasa a elegirlo la APP — el servidor con menos túneles entre
  los cargados — y lo envía explícito, rotulado con honestidad.
  El automático individual sigue siendo el del panel
  (is_auto_generated, probado en la ronda 18).
- Desechables vivos tras la ronda: 562 y 563 en la cuenta 278.

## Ronda 20 (2026-10-31 15:23–15:25, v0.9.4) — el pool «en uso» mezcla otro registro de hosts

- Gennis eliminó él mismo los desechables 562 y 563 (204).
- ❌ Lote en Automático (de la app): eligió «S2-Cuba» **id 17**
  como el menos cargado → bulk-create respondió 400:
  «El host seleccionado no pertenece a las zonas asignadas a
  este reseller.» **Hay DOS registros de hosts**: los objetos
  host de algunos túneles cargados traen ids de OTRO registro
  (17, nombre «S2-Cuba» con minúsculas) que NO son válidos para
  crear; los válidos son los de la lista del panel por túnel
  (39–50, mayúsculas). El pool de creación debe ser ESA lista.
- Pedidos de Gennis en la misma ronda: (1) quitar la opción
  «sin servidor» — el formulario nace en AUTOMÁTICO hasta que
  se cambie; (2) buscador en el selector de propietario;
  (3) nota para todos los túneles del lote (el campo existía
  en el estado y se enviaba, pero no se veía en la vista).
- v0.9.5 aplica los tres pedidos + el pool = lista del panel
  en caché (respaldo «en uso» solo si aún no llegó, anotado).

## Ronda 21 (2026-10-31 15:57, v0.9.5) — ✅✅ LOTE EN AUTOMÁTICO DE LA APP + NOTA DEL LOTE: LA WEB QUEDA CERRADA AL 100%

- Lote Automático con prefijo «auto» → 400 con una validación
  NUEVA y clara del panel: «El nombre "auto-1" ya existe para
  este usuario. Use otro prefijo.» (existen túneles auto-1/2 en
  la 278 de algún intento no registrado; el bulk valida la
  unicidad del prefijo por usuario).
- ✅✅ Con prefijo «autor» → **201**: la app eligió **S1-CUBA
  (id 39)** de la LISTA DEL PANEL (pool correcto, anotado en el
  registro) como el de menos túneles cargados y el panel lo
  aceptó. Túneles **566 «autor-1» y 567 «autor-2»**, activos.
- ✅✅ **La nota del lote («test») quedó aplicada en CADA túnel
  creado** (`"notes":"test"` en los objetos de la respuesta).
- **LA PARTE WEB DE LA APP ESTÁ CERRADA Y VERIFICADA AL 100%.**
  Siguiente etapa por orden de Gennis: la parte LOCAL, empezando
  por SMS (Pagos › Recibidos).
- Desechables vivos en la 278: 566, 567 (y auto-1/auto-2 si los
  hay); Gennis los borra desde la app cuando quiera.

## Ronda 22 (2026-11-02, desde la app v0.69.0, diagnóstico de Gennis) — PATCH de cuenta

- `PATCH /api/manager/v1/accounts/users/260/` con `{"zone":11}` → **200**; la respuesta trae `zone` como OBJETO {id:11, hosts:[…]}. La zona de la cuenta se escribe por ID.
- `PATCH /api/manager/v1/accounts/users/278/` con `{"full_name":"Prueba"}` → **200** y la respuesta trae `"full_name":"Prueba"`: la API SÍ guarda full_name. PERO la web NO lo ofrece en su editor de cuenta (su editor da usuario/alias/telegram — palabra de Gennis tras revisar la web) y Gennis lo rechazó en la app: la v0.70.0 quitó el campo del formulario. No reponerlo sin su orden.
- La respuesta del detalle de cuenta trae además: wallet anidada {id, is_active, balance, user{…}}, clients_count, manual_tunnel_limit, referrals_count, referral_code, telegram_id, is_telegram_verified, created_by_reseller, was_active_before_reseller_deactivation.
- Pendiente de la misma familia: PATCH {"username"} (la v0.70.0 lo incluye en Editar cuenta; sin resultado en vivo aún) y alias/telegram por la misma vía (mismo PATCH que notes/zone/full_name, alta confianza pero sin 200 propio todavía).
- «Mi cuenta» v0.69.0: el trío GET /api/manager/v1/profile/ (+reseller-settings/, referral-stats/) NO bajó en su teléfono (la hoja quedó en ficha de sesión). La v0.70.0 encadena manager → /api/v1/profile/… en lectura. Sin veredicto en vivo aún.
