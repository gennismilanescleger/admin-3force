# Admin 3FORCE Lite — hito HOME (v0.3.0-lite)

Construido el 2026-10-30, encima del hito Login ya probado en el teléfono
(ver BUILD-LITE.md). Misma línea: **Java puro, solo framework Android, cero
dependencias**, minSdk 21, misma cadena manual (aapt2 → javac → d8 →
zipalign → apksigner v1+v2; Gradle no corre en esta VM).

- **APK:** `~/workspace/admin-3force/Admin3FORCE-lite.apk` — **41 387 bytes**
- **Paquete:** `com.gennis.admin3force` · versionCode 3 · versionName `0.3.0-lite`
- **Permisos:** INTERNET, USE_BIOMETRIC, USE_FINGERPRINT (sin cambios)
- **Reproducir:** `cd ~/workspace/admin-3force/lite && ./build.sh`

Verificado al construir: `apksigner verify` → v1 **true**, v2 **true**
(v3 también true), 1 firmante (clave debug del toolchain). `aapt2 dump
badging` → versionCode 3 / 0.3.0-lite, sdkVersion 21, targetSdk 34. El dex
contiene las 11 clases (las 7 del Login + `ApiClient`, `ApiDiag`,
`JsonHelp`, `HomeStats`).

## Qué hace el Home

Tras el desbloqueo biométrico (o tras capturar la sesión en un login nuevo),
la pantalla principal pasa a ser el **Home**; la antigua pantalla de sesión
queda como **«Mi sesión»** (mismos datos reales del token + Cerrar sesión +
«Abrir panel» + volver al inicio). El Home carga **una vez al entrar**, en un
hilo de fondo, con el access guardado, y cada card se rellena cuando llega su
respuesta:

1. **Encabezado** — usuario y rol leídos del endpoint de sesión
   (`GET /api/v1/auth/session/?context=manager`). Si la respuesta no trae
   nombre, queda «Sesión activa»; el rol solo se muestra si viene un string
   de rol de verdad (ver supuestos).
2. **Card Cuentas** — total de la lista de usuarios
   (`GET /api/manager/v1/accounts/users/`, siguiendo la paginación). Con/Sin
   túneles solo si cada usuario trae un conteo de túneles identificable; si
   no, el total va solo y el detalle queda «Con/Sin túneles: —».
3. **Card Túneles** — total / activos / inactivos de
   `GET /api/v1/tunnels/?personal=true`, por el campo de estado del item.
4. **Tira de tasa** — `GET https://www.cambiocup.com/api/history?coin=CUP`
   (sin auth): último valor del arreglo `data`, redondeado → «1 USDT = X
   CUP». Al tocarla copia el mensaje completo con la plantilla de la spec
   (fecha/hora del momento + enlace de verificación). Si la red falla:
   «Tasa no disponible».
5. **Card Facturación (mes actual)** — desde la matriz
   (`GET /api/manager/v1/billing/manual-billing/matrix/`): túneles manuales
   = nº de filas; pagados/pendientes = celdas del mes en curso según su
   estado; automáticos solo si se derivan de la lista de túneles (marcador
   de plan vs «manual»). Lo no derivable se muestra «—» y el porqué queda en
   el Diagnóstico.
6. **Línea «Datos actualizados automáticamente · HH:mm:ss»** con la hora en
   que terminó la carga real. (Sin botón de refrescar, como pidió Gennis: la
   carga es al entrar; el refresco periódico de la spec queda para la
   nativa.)
7. **Botón «Diagnóstico API»** — pantalla con cada llamada de la sesión:
   método + URL, código HTTP, duración y un resumen de **claves** de la
   respuesta (o el error literal, con cualquier forma de token tapada). El
   detalle es seleccionable para poder copiarlo y pasarlo. Nunca muestra
   valores de tokens ni nombres de clientes.

## Cliente API y refresh

`ApiClient` (HttpURLConnection; connect 12 s / read 20 s):

- Toda llamada autenticada lleva `Authorization: Bearer <access guardado>`
  y `Accept: application/json`.
- Ante un **401** intenta el refresh **una sola vez**:
  `POST /api/v1/auth/refresh/` con `{"refresh": "<refresh guardado>"}`. Si
  la respuesta trae JWT nuevos, los guarda en la sesión cifrada
  identificándolos **por las claves que la respuesta use de verdad**
  (pista «access»/«refresh» en el nombre; si no, por la vida del token) y
  reintenta la llamada original una vez.
- Si el refresh no renueva (o el reintento sigue en 401), la app vuelve al
  WebView de login con el mensaje correspondiente — ruta híbrida de la spec,
  sin inventar otro mecanismo.
- Cambio asociado: con access vencido **pero refresh guardado**, la app ya
  no va directa al login al abrir: entra al Home y deja que el cliente
  renueve; desde «Mi sesión», la cuenta atrás al vencer intenta también la
  renovación antes de pedir un login nuevo.

## Supuestos de parsing EXPLÍCITOS (a fijar con la prueba real)

Las claves JSON crudas siguen sin estar documentadas (API-INSPECTION.md);
todo esto es tolerante y, si la realidad difiere, la app muestra «—»/«sin
datos» y el Diagnóstico guarda la forma real:

- **Sobre de lista:** `{count, results}` de DRF, o `{data: [...]}`,
  `{items|rows|matrix|records}`, o el arreglo pelado; paginación por el
  campo `next` (máx. 12 páginas); total = `count`/`total` del sobre si
  viene, si no, el nº de items leídos.
- **Encabezado del token:** `Authorization: Bearer` (convención JWT/DRF;
  el error de auth del panel es formato DRF, pero la cabecera NO está
  confirmada).
- **Refresh:** body `{"refresh": ...}`; respuesta con los tokens como
  strings JWT en claves de primer nivel (o dentro de `data`).
- **Sesión:** usuario en `username|name|preferred_username|email|sub` y rol
  en `role|rol|user_type|tipo|type`, en la raíz o dentro de
  `user|requestData|data` (un rol «Bearer»/«JWT» se descarta por técnico).
- **Conteo de túneles por usuario:** alguna de `tunnels_count`,
  `tunnel_count`, `tunnelsCount`, `num_tunnels`, `total_tunnels`,
  `tunnels_total`, `count_tunnels`, `n_tunnels` (numérica), o `tunnels`
  como arreglo (su longitud) / número / `{count}`. Se exige en TODOS los
  items; si falta en alguno, Con/Sin queda «—».
- **Estado del túnel:** primera clave presente entre `status`, `state`,
  `estado`, `is_active`, `isActive`, `active`, `enabled`, `online`;
  cuentan como activo: booleano true, número ≠ 0, o ACTIVO/ACTIVE/ONLINE/
  TRUE/ON/ENABLED/HABILITADO…; como inactivo: INACTIVO/OFFLINE/FALSE/
  DISABLED/EXPIRADO… Valores no reconocidos no fuerzan el conteo y se
  anotan en el Diagnóstico.
- **Automáticos:** un túnel es «manual» si algún campo de pago/plan
  (`billing_type`, `payment_type`, `plan`, `plan_name`, `subscription`…)
  contiene «manual», y automático si trae nombre de plan no vacío. Basta un
  túnel sin clasificar para que el total de automáticos quede «—».
- **Celda del mes en la matriz:** se busca por claves directas de la fila
  (número del mes con o sin cero —«3»/«03», «10»—, formas `2026-10`,
  `10/2026`, abreviatura `oct` o nombre `octubre`…), por objeto anidado indexado por
  mes, o por arreglo de celdas con su campo de mes (`month|mes|period`…);
  un arreglo de exactamente 12 celdas sin claves de mes se lee posicional.
  Estado de la celda: booleano `paid|pagado|is_paid`, string
  `status|state|estado` (PAGADO/PAID… vs PENDIENTE/UNPAID…), fecha en
  `paid_at|marked_at|fecha_pago` (→ pagado), o la celda misma como
  booleano/número/string. Celda no reconocible = no cuenta ni como pagada
  ni como pendiente, y el Diagnóstico anota cuántas se reconocieron.
- **Tasa:** último elemento de `data`; si es objeto, su primer número entre
  `value|rate|price|close|promedio|avg|valor|tasa`.

## Qué debe mirar Gennis en su primera prueba (Diagnóstico API)

Instalar el APK (actualiza la Lite anterior conservando la sesión) y abrir
la app. Si todo va bien, el Home mostrará sus números reales de SawVPN
(referencia de la fase SawVPN: 61 usuarios, 32 túneles activos, matriz con
32 filas). Después, abrir **«Diagnóstico API»** y revisar/anotar:

1. **Sesión** — ¿HTTP 200? ¿Qué claves trae? (fija dónde viven usuario/rol;
   si el encabezado no muestra nombre/rol, esas claves lo explican).
2. **Usuarios / Túneles / Matriz** — ¿HTTP 200 con `Bearer`? Si alguna da
   **401 incluso tras el refresh**, la cabecera no es Bearer y el error
   literal de esa entrada lo dirá: esa línea es LA que hay que pasar de
   vuelta para fijar el formato.
3. **Refresh** (solo si algún access venció durante la prueba) — ¿200? ¿Qué
   claves devolvió? (confirma el campo `refresh` y los nombres reales de
   los tokens nuevos).
4. Las **claves del primer item** de Usuarios y Túneles: con ellas se fija
   si Con/Sin túneles y Activos/Inactivos salen de la clave esperada o hay
   que ajustar el mapeo; y la nota de derivación de la **Matriz** dice
   cuántas celdas del mes se reconocieron (si son 0, la forma de la celda
   es otra y su clave queda registrada en la entrada).

Si una card sale en «—» o «sin datos», no es un fallo silencioso: la causa
está escrita en su entrada del Diagnóstico.

## Limitaciones honestas

- **Nada de esto está probado contra la API real todavía**: la cabecera, el
  refresh y las claves JSON se confirman (o se corrigen) con la primera
  prueba de Gennis en su teléfono; el Diagnóstico existe exactamente para
  eso. Lo que sí está verificado es la compilación, la firma y que el APK
  instala sobre la misma línea que la 0.2.0 ya probada.
- El Home carga al entrar; **no** hay aún refresco periódico en segundo
  plano ni caché sin conexión (spec: vienen con la nativa / hitos
  siguientes).
- La matriz se lee sin parámetros de año/mes (la página del panel muestra
  el año en curso por defecto); si la API exigiera parámetros, la entrada
  de la Matriz en el Diagnóstico mostrará el error y se ajusta.
- APK **debug**, icono genérico, bloqueo biométrico solo en API 28+ —
  mismas limitaciones aceptadas de la línea Lite (ver BUILD-LITE.md).


---

# v0.3.1-lite — correcciones de la primera prueba real

Construida el 2026-10-30 sobre la v0.3.0, a partir del Diagnóstico real de
Gennis en su teléfono (sesión SawVPN; ver «Confirmado desde la app» en
API-INSPECTION.md: Bearer confirmado con 200 en usuarios/túneles/matriz y el
refresh operando). **Solo cambian tres cosas; Login, captura, cifrado,
biometría, «Mi sesión» y la tira de tasa quedan intactos.**

- **APK entregado:** `~/workspace/admin-3force/Admin3FORCE-0.3.1-lite.apk` —
  **45 483 bytes**
- **Paquete:** `com.gennis.admin3force` · versionCode 4 · versionName `0.3.1-lite`
- **Etiqueta del lanzador:** «Admin 3FORCE» a secas (sin cambios).
- **Nombre del archivo (patrón nuevo para TODAS las builds, pedido del
  propietario):** `Admin3FORCE-<versionName>.apk` — la versión va en el
  nombre del archivo para que el teléfono no añada (1), (2)… al descargar
  varias builds; la etiqueta de la app NO lleva versión. `build.sh` lo
  aplica desde la variable `VERSION_NAME`.
- **Verificado al construir:** `apksigner verify` → v1 **true**, v2 **true**
  (v3 también true), 1 firmante. `aapt2 dump badging` → versionCode 4 /
  0.3.1-lite, label «Admin 3FORCE», sdkVersion 21, targetSdk 34.

## Las tres correcciones

1. **Sesión por POST.** La v0.3.0 llamaba con GET y la API devolvió
   **HTTP 405** («Método no permitido»). Ahora:
   `POST /api/v1/auth/session/?context=manager` con cuerpo JSON vacío `{}`.
   El Diagnóstico de la entrada **Sesión** añade la ruta y las claves del
   objeto de usuario localizado (raíz, `user`, `requestData`,
   `requestData.user`, `data` o `data.user`): «objeto usuario en «ruta»
   {claves}». El encabezado del Home muestra el nombre (`username` o
   `full_name`) y el rol solo si se derivan: campo literal `role`/`rol`, o
   los booleanos del usuario — `is_superuser` → «Administración»,
   `is_reseller` → «Reseller», `is_staff` → «Staff». Si no se deriva nada,
   el encabezado se queda «Sesión activa».
2. **Túneles en dos alcances.** La v0.3.0 solo leía `?personal=true`, que
   resultó ser los túneles **propios** del reseller (3), no los de los
   clientes. Ahora se leen **los dos** alcances, cada uno con su entrada en
   el Diagnóstico («Túneles (personal=true)» y «Túneles (personal=false)»,
   con su conteo de items y claves), y la card Túneles usa **el alcance con
   más items** (esperado: personal=false con 32); la entrada usada lleva la
   nota «alcance usado por el Home». Activos/Inactivos siguen por la clave
   `status`. **Split manual/automático nuevo:** si TODOS los items del
   alcance elegido traen el booleano real `manual_payment`, Facturación
   muestra «Automáticos» = conteo de `manual_payment=false` (y, solo si la
   matriz fallara, «Manuales (lista de túneles)» = conteo de
   `manual_payment=true`); si el booleano falta en alguno, Automáticos
   sigue en «—». (Sustituye a la heurística anterior por nombre de plan.)
3. **Matriz: forma de `months` expuesta y regla de pendientes corregida.**
   El Diagnóstico de la entrada **Matriz** añade ahora:
   - las **claves del objeto `months`** de la primera fila, tal como vengan
     («2026-10», «10», índices…);
   - para el mes actual, la **forma de una celda reconocida como pagada** y
     de **una celda NO reconocida como pagada**: tipo y, si es objeto, sus
     claves + el valor solo de campos booleanos o de estado corto
     (`paid`/`status`/`state`…); si es primitivo, su tipo y, si es una
     cadena corta de estado, su valor. Sin importes ni datos de clientes.
   Parser corregido: **pagado** = la celda indica pagado; **pendiente** =
   existe celda del mes en cualquier otro estado explícito (booleano,
   número, cadena de estado, u objeto con clave de estado); **fila sin
   celda del mes** = no cuenta ni como pagado ni como pendiente y queda
   anotada («sin celda del mes: N»); celda presente pero opaca (sin ningún
   marcador de estado) tampoco cuenta y queda anotada como «no reconocida»,
   con su forma expuesta para fijarla en la siguiente vuelta. Con la
   referencia de la prueba anterior (27 pagadas + 5 filas sin celda
   reconocible), si esas 5 filas traen celda en estado explícito no pagado,
   ahora contarán **Pagados 27 · Pendientes 5** en vez de 27 · 0.

## Qué debe mostrar el Diagnóstico nuevo en la próxima prueba de Gennis

1. **Sesión** — HTTP **200** (ya no 405) y la línea «objeto usuario en
   «…» {…}»: esa ruta + claves fijan dónde viven `username`/`full_name` y
   los booleanos de rol; el Home debería mostrar ya su nombre y rol reales
   (en SawVPN: su usuario y «Reseller»).
2. **Túneles (personal=true)** y **Túneles (personal=false)** — dos entradas
   con sus conteos (esperado: 3 y 32); la de 32 con la nota «alcance usado
   por el Home» y el split por «manual_payment»; la card Túneles debe
   mostrar **32** con sus Activos/Inactivos.
3. **Matriz** — la nota con el conteo ya corregido («celdas del mes: N
   pagadas · M pendientes · sin celda del mes: K») y la línea con las claves
   de «months» de la 1ª fila + la forma de la celda pagada y de la no
   reconocida como pagada: con esas dos formas se fija definitivamente la
   celda no pagada de octubre (las 5 filas que faltaban).
4. **Facturación** — «Automáticos» ya con número (desde `manual_payment`
   del alcance de clientes) en vez de «—», si todos los items traen el
   booleano.

Si algo sigue en «—», la causa y la forma real quedan escritas en su
entrada del Diagnóstico, como hasta ahora.

## Limitaciones (sin cambios respecto a la v0.3.0)

- La forma exacta de la celda no pagada de la matriz se confirma con esta
  prueba; el parser ya cuenta como pendiente cualquier estado explícito no
  pagado, y el Diagnóstico expone la forma para el ajuste fino.
- Sin refresco periódico ni caché sin conexión todavía; APK debug, icono
  genérico, biometría solo en API 28+.


---

# v0.4.0-lite — diagnóstico completo copiable + vistas Cuentas y Túneles

Construida el 2026-10-30 sobre la v0.3.1, por orden de Gennis («añade más
cosas de una sola… aplica un botón para copiar los diagnósticos completos y
tú interpretas; añade más cosas para agilizar todo lo que se pueda»). Una
sola entrega grande: diagnóstico completo copiable, la línea honesta «Sin
registro del mes» en Facturación y las vistas Cuentas y Túneles de solo
lectura con barra de pestañas.

- **APK entregado:** `~/workspace/admin-3force/Admin3FORCE-0.4.0-lite.apk` —
  **57 771 bytes**
- **Paquete:** `com.gennis.admin3force` · versionCode 5 · versionName `0.4.0-lite`
- **Etiqueta del lanzador:** «Admin 3FORCE» a secas (sin cambios).
- **Verificado al construir:** `apksigner verify` → v1 **true**, v2 **true**
  (v3 también true), 1 firmante. `aapt2 dump badging` → versionCode 5 /
  0.4.0-lite, label «Admin 3FORCE», sdkVersion 21, targetSdk 34.
- **Archivos nuevos:** `DiagFull.java` (arma el diagnóstico completo),
  `DataHelp.java` (etiquetas legibles compartidas: host/zona/propietario/
  fechas). `HomeStats` gana `MatrixStats.sinRegistro`, `statusOf()` público
  (el mismo mapeo de estado del Home, reutilizado por la vista Túneles) y
  `sessionStatsDiag()` (claves de «stats» + sus valores numéricos/booleanos).

## A) Diagnóstico completo + botón de copiar

- La pantalla **Diagnóstico API** lleva el botón **«Copiar diagnóstico
  completo» arriba y abajo**: copia UN solo texto plano con todo y muestra
  el toast «Diagnóstico copiado». En pantalla se ven las mismas secciones,
  además de las entradas de llamadas de siempre.
- El texto copiado trae, por secciones:
  1. **App:** versionName/versionCode, API de Android, fecha-hora de
     generación y host del panel.
  2. **Sesión guardada:** por token (access y refresh), presencia y fuente
     (origen + clave), y sus claims **iat/exp** como fechas. **Nunca valores
     de tokens.**
  3. **Llamadas:** cada entrada registrada (Sesión, Usuarios, Túneles
     personal=true, Túneles personal=false, Matriz, Tasa — y **Refresh si
     ocurrió**, con su HTTP y las claves de su respuesta): método+URL, HTTP,
     ms y el detalle con claves del sobre, count y claves del primer item.
  4. **Sesión (detalle):** ruta y claves del objeto usuario, y del objeto
     **«stats»** sus claves **y los valores de los campos numéricos/
     booleanos** (contadores del negocio).
  5. **Usuarios — agregados:** total leído (y el del sobre), `is_active`
     true/false, con túneles / sin túneles (por `tunnels_count`), con alias,
     con `notes` no vacía.
  6. **Túneles — agregados** (alcance usado, personal=false): total,
     valores distintos de `status` con conteo, `manual_payment` true/false,
     con `notes` no vacía, con `disabled_date`, y de `host`: los tipos
     vistos, la forma (tipo + claves si es objeto) y, **solo si trae
     etiqueta legible** (cadena o campo nombre/label), los valores distintos
     con conteo — un id nunca se presenta como etiqueta.
  7. **Matriz fila por fila:** cabecera con el tipo (+claves si es objeto)
     de «tunnel» y «user» y la forma de «months»; por cada fila: nombre del
     túnel (del objeto «tunnel» si lo trae; si es id, el id), usuario
     (username del objeto «user» si lo trae; si es id, el id), la lista de
     meses presentes en su arreglo (M/AAAA) y, del mes actual,
     **is_paid=true/false** o **SIN CELDA**. Totales al final: filas,
     pagadas, con celda no pagada, sin celda.

## B) Home — «Sin registro del mes»

Facturación añade la línea **«Sin registro del mes»** con el conteo de
filas de la matriz sin objeto del mes actual, **solo si es > 0** (en
octubre debería salir 5). Pagados y Pendientes quedan como estaban:
pendiente = celda presente en estado explícito no pagado; las filas sin
celda no se cuentan ni como pagadas ni como pendientes hasta interpretar
el diagnóstico completo.

## C) Vistas Cuentas y Túneles (SOLO LECTURA)

Barra inferior de 3 pestañas — **Inicio · Cuentas · Túneles** — visible en
las tres vistas. Ambas listas se alimentan de las mismas cargas que ya
hace el Home (usuarios y túneles personal=false): **sin llamadas nuevas**.
Ningún botón cambia nada en el panel.

- **Cuentas:** búsqueda por username/alias/full_name y filtro **Todas /
  Con túneles / Sin túneles / Deshabilitadas** (deshabilitada =
  `is_active` en falso); grupos con esas etiquetas (el grupo vacío se
  oculta). Card: título = username, debajo «Alias: X» si el alias no está
  vacío, línea «N túneles · Activa/Deshabilitada» (por `tunnels_count`) y
  chip «Nota» si `notes` no está vacía. Tocar la card abre la **hoja de
  detalle de solo lectura**: username, alias, full_name, zona si es
  legible, estado, túneles, creada, último login, telegram si lo hay y el
  texto completo de `notes`.
- **Túneles** (alcance personal=false): búsqueda por name /
  name_for_user / propietario y filtro **Todos / Activos / Inactivos** con
  el MISMO mapeo de `status` del Home (el que dio 32 activos); grupos
  **Activos / Desactivados** (y «Sin estado reconocido» solo si algún
  túnel no trajera estado mapeable). Card: título = name, debajo el
  propietario («user» objeto → su username; id → resuelto contra la lista
  de usuarios; sin resolver → «Usuario #id»), línea con el host (etiqueta
  legible si la hay; si no, «Host: —»), chip de estado, «El cliente lo ve
  como: name_for_user» si difiere de name, «Creado: DD/MM/AAAA» desde
  `created_at`, y chip «Nota» si `notes` no está vacía. La hoja de detalle
  (solo lectura) muestra id, protocolo, estado, `provisioning_status`,
  zona legible, host, expiración/deshabilitación si las hay y `notes`
  completo.
- Lo ilegible se muestra como «—» y su forma queda anotada en el
  diagnóstico completo (sección de agregados). Sin datos simulados.

## Qué debe pegarme Gennis tras instalarla

1. Abrir la app (entra con la sesión guardada), dejar que el Home cargue.
2. Entrar en **Diagnóstico API** y tocar **«Copiar diagnóstico completo»**.
3. **Pegar aquí el texto completo copiado.** Con la sección 7 (matriz fila
   por fila, con los nombres) se interpreta por fin si las 5 filas sin
   celda de octubre son los pendientes conocidos — y se fija la regla
   definitiva de «Pendientes del mes» —; con la 6 se confirma la forma real
   de «host»; con la 4, qué trae «stats».
4. De paso, echar un vistazo a las pestañas **Cuentas** y **Túneles**:
   deben listar las 61 cuentas y los 32 túneles reales, con búsqueda y
   filtros funcionando y las hojas de detalle al tocar una card.


---

# v0.5.0-lite — regla de pendientes confirmada, vista Pagos y la primera ESCRITURA

Construida el 2026-10-31 sobre la v0.4.0, tras interpretar el diagnóstico
completo que Gennis pegó en el chat (31/10 08:49). Esa interpretación fijó
la **regla de pendientes con nombres**: en la matriz, la celda de un mes
existe solo si ese mes quedó registrado en el panel; para el mes actual,
fila **sin celda = pendiente**. Las 5 filas sin celda de 10/2026 son
Cellmaster87, Savior, YUM4, Mlealglez (pendientes conocidos) + Diego (su
arreglo viene vacío; queda contado como pendiente, como sale del panel).
Octubre real: **Pagados 27 · Pendientes 5**.

- **APK entregado:** `~/workspace/admin-3force/Admin3FORCE-0.5.0-lite.apk` —
  **65 963 bytes**
- **Paquete:** `com.gennis.admin3force` · versionCode 6 · versionName `0.5.0-lite`
- **Etiqueta del lanzador:** «Admin 3FORCE» a secas (sin cambios).
- **Verificado al construir:** `apksigner verify` → v1 **true**, v2 **true**
  (v3 también true), 1 firmante. `aapt2 dump badging` → versionCode 6 /
  0.5.0-lite, label «Admin 3FORCE», sdkVersion 21, targetSdk 34. El dex
  contiene la ruta `manual-billing/update/`, el botón «Confirmar pago» y la
  etiqueta de diagnóstico «Escritura — marcar pagado»; la línea vieja «Sin
  registro del mes» ya no existe en el binario.
- **Sin archivos nuevos:** todo va en `MainActivity`, `HomeStats`,
  `ApiClient` (nuevo `postWrite`), `ApiDiag` (nuevo `truncateRaw`) y
  `PanelConfig` (nueva `UPDATE_URL`).

## A) Home corregido

- **Pendientes del mes** = celdas del mes con `is_paid=false` **+ filas sin
  celda del mes** (regla confirmada). Octubre debe mostrar **5**.
- **Desaparece** la línea «Sin registro del mes».
- La fila **«Pendientes del mes» es TOCABLE** (el valor sale como «5 ›») y
  abre la vista **Pagos**, cuya primera sección es exactamente esa lista
  (nombre del túnel + cuenta): el número queda transparente.

## B) Vista Pagos (4ª pestaña: Inicio · Cuentas · Túneles · Pagos)

Alimentada por la matriz ya cargada, **solo datos reales**:

- **Pendientes:** primero el mes actual (regla confirmada: celda
  `is_paid=false` o fila sin celda) y debajo los meses anteriores que
  traigan celdas explícitas `is_paid=false`, agrupados por mes (más
  reciente primero). Fila: túnel + cuenta, con botón **«Marcar pagado»**
  (solo si la fila trae el id numérico del túnel).
- **Pagados:** todas las celdas `is_paid=true` de todos los meses
  presentes, agrupadas por mes descendente. Fila: túnel + cuenta; al
  tocarla se abre el detalle de la celda con lo que la matriz trae:
  mes, estado, `marked_at` (formateado), `marked_by` y la nota si la hay.
- **Recibidos:** estado honesto, sin datos ni fingimiento: «La lectura de
  SMS llega en la próxima etapa de la Lite…».

## C) Marcar pagado — la escritura real

Endpoint documentado: **POST `/api/manager/v1/billing/manual-billing/update/`**
(staff/reseller). Su payload **nunca se ha ejecutado**: esta build lo
descubre con intentos reales y el diagnóstico completo.

- **Accesos:** botón «Marcar pagado» en cada fila de Pagos › Pendientes y
  en la hoja de detalle de un túnel (vista Túneles; solo si el túnel trae
  id numérico).
- **Hoja de confirmación:** túnel y cuenta; **selector de meses en UN
  desplegable** con los 4 meses nombrables (anterior, actual, siguiente y
  el siguiente, formato «Octubre 2026») con **multiselección** — los meses
  elegidos se unen con « + » en la cabecera y hace falta al menos uno; el
  propietario elige los meses, la app **no sugiere por importe**. Al abrir
  desde una fila se preselecciona el mes de esa fila si está entre los 4
  (si es más viejo que el mes anterior, se preselecciona el actual);
  campo opcional **«Nota del pago»**; botón **«Confirmar pago»**.
- **Al confirmar:** UN POST por cada mes elegido, en orden cronológico,
  con el cuerpo más probable derivado de la forma de la celda:
  `{"tunnel": <id>, "month": <nº>, "year": <aaaa>, "is_paid": true, "notes": "<nota o cadena vacía>"}`.
- **Sin reintentos automáticos** (`ApiClient.postWrite`): la única
  re-llamada es tras un **401** — el rechazo JWT ocurre antes de ejecutar
  la vista, así que esa petición no se aplicó; tras refrescar se reenvía
  UNA vez. Si la petición salió y la **respuesta se perdió** (error de
  red), el resultado se marca ambiguo: la app **no reenvía**, recarga la
  matriz y muestra el estado real con un aviso.
- **El intento queda registrado ENTERO en el Diagnóstico** con la etiqueta
  **«Escritura — marcar pagado»**: URL, cuerpo enviado completo, HTTP y el
  **cuerpo de respuesta literal** (recortado a ~1500 caracteres, sin formas
  de token). Con UN intento, falle o acierte, se fija el esquema definitivo
  del endpoint.
- **Si responde 2xx:** toast «Pago marcado» y recarga completa de los
  datos (Home, Pagos y las demás vistas reflejan la matriz nueva).
  **Si falla (HTTP ≥ 400):** el error literal del servidor se muestra en la
  propia hoja y **nada cambia localmente** — no se finge ninguna marca.

## Cómo probar la escritura de forma segura

⚠️ **Un acierto es una marca REAL en el panel, sobre datos reales.** Elige
conscientemente dónde disparar el primer intento:

1. **La opción segura:** la cuenta de prueba **`prueba_claude_3010`**, SI
   Gennis logra crearle un túnel desde su teléfono (la creación de túneles
   desde el navegador de la VM falló en la fase de pruebas del 30/10 con
   «Error de conexión»). Con un túnel de prueba en la matriz, su fila sale
   en Pagos › Pendientes y el intento no toca a ningún cliente.
2. **Si no hay túnel de prueba:** el primer intento sobre un pendiente real
   (p. ej. uno de los 5 de octubre) marcará ese mes como pagado DE VERDAD
   si el payload es correcto. Hazlo solo con un pago que ya recibiste y
   quieras marcar de todos modos.
3. Si el payload es incorrecto, el panel responderá un error (4xx) y no se
   aplicará nada: ese error literal es exactamente lo que necesitamos ver.

## Qué pegar en el chat tras el intento

1. Instalar la 0.5.0 encima de la anterior y abrir la app.
2. Comprobar el Home: Facturación de octubre debe decir **Pagados 27 ·
   Pendientes 5**, sin línea «Sin registro del mes»; tocar la fila de
   pendientes debe abrir Pagos con los 5 (Cellmaster87, Savior, YUM4,
   Mlealglez, Diego).
3. Hacer **UN** intento de «Marcar pagado» (mejor sobre el túnel de prueba
   si existe).
4. Entrar en **Diagnóstico API** → **«Copiar diagnóstico completo»** y
   **pegar el texto completo en el chat**. La entrada clave es la de
   **«Escritura — marcar pagado»** (sección 3, Llamadas): con su HTTP y su
   cuerpo de respuesta se fija el esquema definitivo del endpoint, acierte
   o falle.

# v0.6.0-lite — ACCIONES de túnel (habilitadas en todos; Diego = banco de pruebas)

Construida el 2026-10-31. Orden final de Gennis: «habilita las opciones
con cualquiera»; Diego queda como el túnel donde se prueba todo.

- **APK:** `~/workspace/admin-3force/Admin3FORCE-0.6.0-lite.apk` — **78 251 bytes**
- **Paquete:** `com.gennis.admin3force` · versionCode 7 · versionName `0.6.0-lite`
- **Permisos:** INTERNET, USE_BIOMETRIC, USE_FINGERPRINT +
  WRITE_EXTERNAL_STORAGE solo hasta Android 28 (la descarga en Android 10+
  usa MediaStore, sin permiso).
- **Reproducir:** `cd ~/workspace/admin-3force/lite && ./build.sh`

Verificado al construir: `apksigner verify` → v1 **true**, v2 **true**
(v3 también true), 1 firmante. `aapt2 dump badging` → versionCode 7 /
0.6.0-lite, sdkVersion 21, targetSdk 34, label «Admin 3FORCE». El dex
contiene las rutas candidatas (`download_config/`, `download-config/`,
`reassign_host/`, `regenerate/`, `reprovision/`, los tres `/hosts/`) y las
9 etiquetas «Escritura — …».

## Contexto: las rutas de acción NO están documentadas con exactitud

La documentación del panel (Developers, 148 endpoints) confirma que las
acciones EXISTEN (regenerar, reassign-host, reaprovisionar, descarga de
config, soft-delete) pero no da rutas ni esquemas. Estrategia de la build:

- **Lecturas/descubrimiento:** se prueban las candidatas EN ORDEN y cada
  intento queda en el Diagnóstico con su HTTP y la forma de la respuesta.
- **Escrituras:** como máximo **UNA candidata por toque** (la más
  probable), con el patrón seguro de `ApiClient.write` (generalización del
  `postWrite` de la v0.5.0 a POST/PATCH/DELETE): sin reintentos, UNA
  re-llamada tras refresh si hay 401, intento entero en el Diagnóstico como
  «Escritura — <acción>», error literal visible y recarga real tras éxito.
  Si la candidata falla, el error enseña el ajuste para la siguiente build
  — nunca se disparan dos escrituras candidatas por un mismo toque.
- **PATCH:** `HttpURLConnection` rechaza el método PATCH antes de conectar;
  en ese caso la petición sale como POST con cabecera
  `X-HTTP-Method-Override: PATCH` (la petición original nunca salió, así
  que no hay duplicación) y el Diagnóstico lo anota.

## Lo construido (A–G)

- **A) Menú de acciones del túnel** — botón «Acciones» en la card de
  Túneles y en su hoja de detalle. Orden: **Información** (primero, abre el
  detalle) · Descargar configuración · Cambiar de servidor · Regenerar
  túnel · Reaprovisionar · Editar nota · Deshabilitar/Habilitar (según el
  estado; si no se reconoce, la hoja ofrece ambas) · Eliminar. Habilitado
  en **todos** los túneles.
- **B) Descargar configuración** (lectura): si el item trae
  `can_download_config=false`, avisa y no intenta (ojo: el serializer de
  clientes `personal=false` no trae esa clave; viene en el de
  `personal=true`). Candidatas GET en orden: `download_config/`,
  `download/`, `config/`, `download-config/` bajo
  `/api/v1/tunnels/<id>/`. Se acepta texto con marcadores WireGuard o un
  JSON con la config en un campo de texto. El archivo se guarda como
  `<tunel>.conf` en **Descargas** (MediaStore) con botón **«Compartir»**;
  el Diagnóstico anota de cada intento solo tipo de contenido, tamaño y
  forma — **el contenido nunca se anota**.
- **C) Cambiar de servidor**: la hoja primero CARGA la lista de servidores
  del panel probando `GET /api/v1/hosts/`, `/api/manager/v1/hosts/` y
  `/api/v1/tunnels/hosts/`. Si ninguna responde, muestra los **servidores
  en uso** (hosts distintos vistos en los túneles cargados) etiquetados
  como respaldo, no como lista del panel. El servidor actual va marcado;
  cada otro servidor con id trae «Cambiar a este servidor» → confirmación
  → UN POST a `/api/v1/tunnels/<id>/reassign_host/` con `{"host": <id>}`.
- **D) Regenerar / Reaprovisionar**: confirmación con la consecuencia real
  (regenerar cambia las claves: el cliente debe importar la config nueva;
  reaprovisionar recrea el túnel en el host) → UN POST con cuerpo `{}` a
  `regenerate/` o `reprovision/`.
- **E) Deshabilitar / Habilitar**: confirmación → UN PATCH a
  `/api/v1/tunnels/<id>/` con `{"status": "disabled"|"active"}`. El estado
  mostrado sale siempre del refetch posterior, nunca de un cambio local.
- **F) Editar nota**: editor con el texto actual → UN PATCH con
  `{"notes": <texto>}` al detalle del túnel; y en la hoja de detalle de
  **cuenta**, el botón equivalente PATCH a
  `/api/manager/v1/accounts/users/<id>/`.
- **G) Eliminar**: presente en el menú con **guarda extra**: hay que
  ESCRIBIR el nombre exacto del túnel para habilitar el botón, con el
  aviso de que —aunque la doc diga soft-delete— con la creación de
  túneles caída el túnel no se podría recrear. El intento sería
  `DELETE /api/v1/tunnels/<id>/`. **NO se prueba en esta entrega**: queda
  listo y guardado.

## Orden de prueba sugerido sobre DIEGO

0. Si aún no se hizo: la **marca de pago** de la v0.5.0 (Pagos ›
   Pendientes → Diego → «Marcar pagado», nota «PRUEBA»).
1. **Descargar configuración** — Túneles → Diego → Acciones → Descargar
   configuración. Debe guardar `Diego.conf` en Descargas y ofrecer
   Compartir.
2. **Cambiar de servidor (solo ver)** — Acciones → Cambiar de servidor:
   mirar si carga la lista del panel o el respaldo «en uso». NO hace falta
   cambiarlo de verdad en esta ronda.
3. **Editar nota** — Acciones → Editar nota: escribir una nota de prueba
   (p. ej. «PRUEBA») y guardar; comprobar que aparece en el detalle.
4. Al final, las de consecuencia: **Regenerar**, **Reaprovisionar** y
   **Deshabilitar → Habilitar** (en ese orden, y deshabilitar solo para
   volver a habilitar enseguida y verificar el estado real al recargar).
5. **Eliminar: NO probarlo.** Está listo y guardado a propósito.

## Qué pegar en el chat tras cada intento

Tras CADA acción probada: **Diagnóstico API → «Copiar diagnóstico
completo» → pegar el texto en el chat.** Las entradas clave de la
sección 3 (Llamadas) son las de la acción: «Descarga de configuración»,
«Servidores del panel» y las «Escritura — …» (cambiar de servidor,
regenerar túnel, reaprovisionar, editar nota, deshabilitar/habilitar
túnel): con su HTTP y su cuerpo de respuesta se fija la ruta y el esquema
definitivos de cada acción, acierte o falle el primer intento.

# v0.6.1-lite — FIX: los overlays nacían fuera de pantalla (raíz FrameLayout)

**Entregable:** `Admin3FORCE-0.6.1-lite.apk` (versionCode 8, verificado con
apksigner v1+v2 y `aapt2 dump badging`: paquete `com.gennis.admin3force`,
etiqueta «Admin 3FORCE»). Sin cambios de lógica de datos ni de escrituras:
solo el contenedor raíz, los LayoutParams de primer nivel y la versión
(también en `AndroidManifest.xml`, que es el que manda sobre las banderas
de aapt2).

## El bug (reporte de Gennis, v0.6.0, 31/10 09:36)

«Solo me sale un botón de acciones en los túneles, no puedo ver las
opciones ni marcar a Diego como pagado». Su diagnóstico no registraba NI
UN intento de escritura: los toques SÍ ejecutaban los listeners, pero las
hojas nunca se veían.

- En `MainActivity.buildUi()` el contenedor raíz era un **LinearLayout
  VERTICAL**. Todos los contenedores de pantalla (login, lock, session,
  main, diag, detail, sheet, menu, act) eran hijos de ese raíz con
  LayoutParams de altura MATCH_PARENT.
- mainContainer, al ser el hijo VISIBLE, ocupaba toda la altura. Los
  overlays (detail, sheet «Marcar pagado», menu «Acciones», act) se abren
  SIN ocultar main —por diseño son hojas encima—, pero en un LinearLayout
  vertical quedaban apilados DEBAJO de mainContainer, fuera de pantalla.
  El toque en «Acciones» ejecutaba `openTunnelMenu()`, pero el menú nacía
  bajo el borde inferior: invisible e intocable. Lo mismo le pasaba a la
  hoja «Marcar pagado» desde la v0.5.0.
- El Diagnóstico y «Mi sesión» sí funcionaban porque esos flujos llaman a
  `hideAll()` (main pasa a GONE) antes de mostrarse: por eso todas las
  lecturas siempre fueron sanas y el fallo parecía «solo de las acciones».

**Discrepancia con el diagnóstico previo (líneas exactas):** se había
dicho que mainContainer se añadía al raíz con `(MATCH_PARENT, 0, 1f)`. En
el código real, mainContainer se añadía con
`LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)` (antiguas líneas
601–602). Los params `(MATCH_PARENT, 0, 1f)` de esa zona son los de
**homeContainer dentro de mainContainer** (antigua línea 557–558):
internos a un LinearLayout vertical, donde el peso es válido — NO se han
tocado. La causa raíz y el arreglo son los mismos; lo que cambia es solo
esa precisión sobre qué params había en el nivel raíz (todos ya eran
MATCH_PARENT×MATCH_PARENT en tamaño; lo incorrecto era el TIPO de
contenedor y de params).

## El arreglo

1. `buildUi()`: el raíz pasa a ser un **FrameLayout**. Los hijos se apilan
   en Z por orden de inserción, que ya era el correcto: login < lock <
   session < main < diag < detail < sheet < menu < act (act queda el más
   alto). Los overlays se dibujan ENCIMA de la pantalla base y reciben los
   toques.
2. Auditados TODOS los `root.addView(...)` de primer nivel (buildLoginUi,
   buildLockUi, buildSessionUi, buildMainUi, buildDiagUi, buildDetailUi,
   buildSheetUi, buildMenuUi, buildActionUi): sus LayoutParams pasan a
   `FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)`. Las firmas de
   esos nueve métodos ahora reciben `FrameLayout`; buildHomeUi /
   buildAccountsUi / buildTunnelsUi / buildPaymentsUi siguen recibiendo el
   mainContainer (LinearLayout) y sus pesos INTERNOS no se tocaron.
3. `hideAll()` ya era correcto y NO se tocó: pone en GONE los nueve
   contenedores y en falso `detailOpen`/`sheetOpen`/`menuOpen`/`actOpen`
   (y `actBusy`). Todos los flujos de pantalla base pasan por él antes de
   mostrarse — `showLogin`, `openPanel`, `showLock`, `showSession`,
   `showMain` (y con él `showHomeScreen`/`enterApp`) y `showDiag` — así
   que ningún overlay puede quedar VISIBLE tapando una base.

## Traza estática de los 4 caminos de toque (sin emulador: leída del código)

Orden Z del FrameLayout = orden de inserción en `buildUi()`:
login(1) < lock(2) < session(3) < **main(4)** < diag(5) < **detail(6)** <
**sheet(7)** < **menu(8)** < **act(9)**. Un overlay se ve y recibe toques
si su contenedor está VISIBLE y todos los que tiene ENCIMA están GONE.

### (a) Túneles → card → «Acciones» → menú con sus 8 opciones

La card del túnel (`tunnelCard`) trae su propio botón «Acciones» →
`openTunnelMenu(t, ownerMap)`: puebla `menuBody` con las 8 opciones
(Información, Descargar configuración, Cambiar de servidor, Regenerar
túnel, Reaprovisionar, Editar nota, Deshabilitar/Habilitar, Eliminar),
pone `menuContainer` VISIBLE y `menuOpen=true`. VISIBLES: main(4, con
tunnelsContainer) + menu(8). Encima de menu solo está act(9), que está
GONE. → El menú se ve a pantalla completa y sus botones reciben el toque.
**Cierra.**

### (b) Pagos › Pendientes → fila de Diego → «Marcar pagado» → escritura (DE PUNTA A PUNTA)

Es la prueba nº 1 del propietario («lo de marcar pagado, a ver si
funciona»). Eslabón por eslabón:

1. `showMain(3)` → `renderPagos()`: la sección Pendientes sale de
   `HomeStats.pendingEntries(dataMatrix, año, mes)` con la regla
   confirmada (sin celda del mes = pendiente): la fila de **Diego** para
   Octubre 2026 está en la lista.
2. `pendingRow(e)` añade el botón «Marcar pagado» porque el id del túnel
   de la fila es válido: `HomeStats` extrae `tunnelId` del objeto
   `tunnel{id, name, name_for_user, status}` de la fila de la matriz
   (Diego lo trae; si no hubiera id, el botón no se ofrece). El listener
   llama a `openMarkSheet(e.tunnelId, e.tunnelName, e.userName, 10, 2026)`.
3. `openMarkSheet(...)`: guarda túnel/cuenta, construye las 4 opciones de
   mes (anterior, actual, siguiente, siguiente) y **preselecciona Octubre
   2026** (el mes de la fila); puebla `sheetBody` con Túnel/Cuenta, el
   desplegable de meses y el campo «Nota del pago»; al final pone
   `sheetContainer` VISIBLE y `sheetOpen=true`. VISIBLES: main(4) +
   sheet(7); encima de sheet quedan menu(8)/act(9), ambos GONE. → La hoja
   se ve con sus meses y su nota. **Cierra.**
4. «Confirmar pago» (listener fijado en `buildSheetUi`) →
   `confirmMarkPaid()`: anti-doble-toque (`writeInFlight`), exige ≥1 mes
   elegido e id ≥ 0, lee la nota y, en segundo plano, por CADA mes dispara
   UN POST con el cuerpo derivado de la celda
   `{tunnel, month, year, is_paid: true, notes}` a
   `ApiClient.postWrite("Escritura — marcar pagado", PanelConfig.UPDATE_URL, body)`
   con `UPDATE_URL = /api/manager/v1/billing/manual-billing/update/`.
5. `ApiClient.write()` (el patrón seguro): UNA sola llamada; si hay 401,
   refresh y UN reenvío (el 401 se rechaza en autenticación, antes de
   aplicarse); si la respuesta se pierde, marca `ambiguous` y NUNCA
   reenvía. En TODOS los desenlaces termina en
   `ApiDiag.add("Escritura — marcar pagado", ...)` con el cuerpo enviado
   completo y el cuerpo de respuesta literal (recortado, sin tokens): la
   entrada queda registrada en el Diagnóstico **pase lo que pase** —
   acierto (2xx), rechazo HTTP del panel, 401 sin renovación o respuesta
   ambigua. (Única salida sin entrada: un fallo local al construir el JSON
   del cuerpo, con tipos simples — no alcanzable en la práctica; ningún
   intento que salga al panel queda sin registrar.)
6. Desenlace en pantalla: 2xx en todos los meses → toast «Pago marcado»,
   se cierra la hoja y `loadHomeData()` recarga: Diego sale de Pendientes
   y Pagados pasa a 28. Error HTTP → el error literal del servidor queda
   en la hoja y nada cambia localmente. Ambigua → toast + recarga para
   mostrar el estado real. 401 sin renovación → se cierra la hoja y el
   flujo de sesión vencida toma el control.

### (c) Detalle del túnel → «Marcar pagado» y «Acciones»

`openTunnelDetail(t, ownerMap)` puebla el detalle y, si el túnel trae id
numérico, añade AMBOS botones al final del cuerpo: «Marcar pagado»
(`openMarkSheet` con el mes actual preseleccionado) y «Acciones»
(`openTunnelMenu`); después `showDetail()` pone `detailContainer` VISIBLE
y `detailOpen=true`. VISIBLES: main(4) + detail(6). Desde ahí, «Marcar
pagado» abre sheet(7) —encima de detail, GONE menu/act no estorban— y
«Acciones» abre menu(8) —también encima de detail—. Ambos caminos son los
de (b) y (a) a partir de la hoja/menú. **Cierra.**

### (d) Menú → cualquier acción → hoja de acción

Cada opción de escritura/lectura del menú abre la hoja genérica con
`openAct(título)`: Descargar configuración (→ `startDownload`), Cambiar
de servidor (→ `openServers`), Regenerar túnel, Reaprovisionar,
Deshabilitar/Habilitar (→ `openToggle`), Editar nota y Eliminar (→
`openDeleteGuard`). `openAct` limpia la hoja, restaura sus botones y pone
`actContainer` VISIBLE con `actOpen=true`. VISIBLES: main(4) + menu(8,
queda debajo, abierto) + act(9, el más alto de todos). → La hoja de
acción se ve y recibe los toques; al cancelarla (`closeAct`) se vuelve al
menú, que seguía abierto debajo. La opción «Información» es la excepción
deliberada: cierra el menú y abre el detalle (camino c). **Cierra.**

## Orden de prueba al instalar la 0.6.1 (sobre DIEGO)

1. **PRUEBA Nº 1 — la marca de pago.** Pagos › Pendientes → Diego →
   «Marcar pagado» → mes **Octubre 2026**, nota **«PRUEBA»** → Confirmar
   pago. Señales de acierto: toast «Pago marcado», Diego sale de
   Pendientes y Pagados pasa a 28. Después: Diagnóstico API → «Copiar
   diagnóstico completo» → pegar en el chat (la entrada «Escritura —
   marcar pagado» fija el esquema definitivo del endpoint, acierte o
   falle). La celda de Diego se revierte luego desde la matriz web.
2. Recién después, las acciones nuevas, en el orden de la v0.6.0:
   Descargar configuración → Cambiar de servidor (solo ver la lista) →
   Editar nota → Regenerar / Reaprovisionar / Deshabilitar→Habilitar.
3. **Eliminar: NO probarlo** (sigue listo y guardado; con la creación de
   túneles caída, Diego quedaría sin túnel y sin forma de recrearlo).

## v0.6.2 — hotfix del campo de la marca de pago (2026-10-31)

**Evidencia (diagnóstico real de Gennis, 31/10 09:54, con la 0.6.1):** la
hoja «Marcar pagado» ya abre y la escritura llegó al servidor:

    POST /api/manager/v1/billing/manual-billing/update/ → HTTP 400
    cuerpo enviado: {"tunnel":525,"month":10,"year":2026,"is_paid":true,"notes":"Prueba de pago de diego"}
    respuesta: {"tunnel_id":["Este campo es requerido."]}

El servidor pide el campo **`tunnel_id`** (no `tunnel`). Ningún otro
campo fue señalado: `month`/`year`/`is_paid`/`notes` se aceptan tal cual.
Fue un 400 de validación — nada quedó marcado (Diego sigue sin celda).

**El cambio (único):** en el cuerpo que arma `confirmMarkPaid()` la clave
`tunnel` pasa a `tunnel_id`; todo lo demás queda idéntico (endpoint,
patrón seguro, hoja y entrada de Diagnóstico). versionCode 9 /
versionName 0.6.2-lite; manifiesto y build.sh alineados. APK verificado:
apksigner v1 true, v2 true (v3 también), 1 firmante debug; badging
confirma código 9 / 0.6.2-lite.

**Prueba nº 1 (la misma, sobre DIEGO):** instalar encima → Pagos ›
Pendientes → Diego → «Marcar pagado» → **Octubre 2026**, nota **«PRUEBA»**
→ Confirmar pago. Señales de acierto: toast «Pago marcado», Diego sale
de Pendientes y Pagados pasa a 28. Después: Diagnóstico API → «Copiar
diagnóstico completo» → pegar en el chat — la entrada «Escritura —
marcar pagado» traerá el resultado (éxito o el nuevo error literal) y con
eso se confirma el esquema definitivo. La celda de Diego se revierte
luego desde la matriz web.

## v0.7.0 — acciones de túnel, ronda 2 guiada por la evidencia (2026-10-31)

Segunda ronda sobre las acciones de la v0.6.0. Cada ajuste nace de un
resultado REAL de la ronda 1 (diagnóstico de Gennis, 31/10 10:18–10:20,
sobre Diego #525; registro completo en API-INSPECTION.md, sección «Ronda
de acciones sobre Diego»). El patrón seguro de escrituras no cambia: sin
reintentos, UNA candidata por toque, intento entero en el Diagnóstico,
error literal visible y recarga tras éxito. Si una candidata vuelve a
fallar, su error literal ES el resultado válido de la ronda — no se
encadenan escrituras.

**Lo que NO se toca:** Reaprovisionar (CONFIRMADO en la ronda 1: POST
/api/v1/tunnels/<id>/reprovision/ {} → 200) y Marcar pagado (esquema
{tunnel_id, month, year, is_paid, notes} probado).

### Ajustes por acción

- **Información — NUEVA sonda de detalle (LECTURA).** Evidencia: el
  detalle de un túnel NUNCA se ha leído y puede traer la configuración
  dentro. Al abrir «Información», además de la hoja actual, la app hace
  GET /api/v1/tunnels/<id>/ en segundo plano y registra la entrada
  «Detalle de túnel (sonda)» en el Diagnóstico: HTTP, claves completas de
  la respuesta, qué claves son nuevas frente a la lista y, de cualquier
  clave que huela a configuración (config, wireguard, qr, conf, file…),
  solo su TIPO y longitud — el contenido nunca se anota. Si el detalle
  trae campos que la lista no trae, la hoja los muestra (valores legibles
  no sensibles; claves sensibles solo como «presente en el detalle»); la
  config cruda no se vuelca en la hoja: solo aparece la fila
  «Configuración: Disponible en el detalle» si el contenido WireGuard
  está presente (decidido por contenido, no por el nombre de la clave).
- **Descargar configuración.** Evidencia: download_config/, download/,
  config/ y download-config/ bajo /api/v1/tunnels/<id>/ → 404 las cuatro
  (no se reintentan). Nueva cadena, en orden: (1) el propio detalle del
  túnel — si la sonda encuentra la config como campo, se extrae de ahí;
  (2) GET /api/manager/v1/tunnels/<id>/download_config/. Se mantiene
  guardar en Descargas + «Compartir» y el registro por intento con la
  fuente («detalle del túnel» / «familia manager»), tipo, tamaño y forma,
  nunca el contenido.
- **Cambiar de servidor.** Evidencia: GET /api/v1/hosts/ → 400
  {"detail":"Se debe proporcionar 'tunnel_id'"} → la lista se pide POR
  TÚNEL. La hoja carga ahora GET /api/v1/hosts/?tunnel_id=<id>; el
  Diagnóstico registra la forma entera de la respuesta (claves del sobre
  y del primer item). La lista se muestra con nombre legible + estado; el
  servidor actual va marcado si la respuesta lo indica (current/
  is_current/selected/is_selected) o, si no, por coincidencia con el host
  actual del túnel. Si esta ruta también falla, queda el respaldo «en
  uso» etiquetado, como hasta ahora. El cambio: confirmación → UN POST a
  /api/v1/tunnels/<id>/reassign_host/ con {"host_id": <id>} (lección
  tunnel_id: esta API nombra los ids con sufijo _id; antes iba «host»).
- **Regenerar túnel.** Evidencia: POST .../regenerate/ → 404 genérico.
  Nueva candidata única: POST /api/v1/tunnels/<id>/regenerate_config/
  con {}. Misma hoja de confirmación con su consecuencia (las claves
  cambian).
- **Deshabilitar / Habilitar.** Evidencia: PATCH /api/v1/tunnels/<id>/
  con {status} → 404 genérico. Dejan el PATCH al detalle: candidata única
  por toque estilo reprovision/ — POST /api/v1/tunnels/<id>/disable/ o
  …/enable/, cuerpo {}.
- **Editar nota (túnel).** Evidencia: PATCH /api/v1/tunnels/<id>/ con
  {notes} → 404 genérico. Nueva candidata única: PATCH
  /api/manager/v1/tunnels/<id>/ con {"notes": <texto>} (mismo mecanismo
  de escritura, con su override si HttpURLConnection lo exige). La nota
  de CUENTA no se toca: sigue PATCH /api/manager/v1/accounts/users/<id>/
  (aún sin probar — entra en el orden de prueba de esta ronda).

versionName 0.7.0-lite, versionCode 10 (manifiesto y build.sh
alineados). APK verificado sobre el archivo entregado: apksigner v1
true, v2 true (v3 también true), 1 firmante debug; aapt2 dump badging
confirma código 10 / 0.7.0-lite y label «Admin 3FORCE»; 82 347 bytes. El
dex contiene las rutas nuevas (regenerate_config/, disable/, enable/,
hosts/?tunnel_id=, la familia manager de túneles con download_config/,
host_id) y las etiquetas de la sonda.

## Orden de prueba al instalar la 0.7.0 (sobre DIEGO)

0. Diego sigue desmarcado de octubre (lo desmarcó Gennis desde la web):
   ningún paso de esta ronda depende de la matriz.
1. **Información primero** — abre el detalle de Diego y espera unos
   segundos: la sonda debe pintar sus filas (campos nuevos del detalle,
   o «Sin campos nuevos frente a la lista», o «Configuración: Disponible
   en el detalle»). Su entrada «Detalle de túnel (sonda)» en el
   Diagnóstico es la pieza clave de la ronda.
2. **Descargar configuración** — cadena nueva: detalle → manager. Si
   baja, el .conf queda en Descargas y se ofrece «Compartir».
3. **Cambiar de servidor** — primero SOLO VER la lista que devuelve
   ?tunnel_id= (¿lista del panel o respaldo «en uso»?, ¿el actual sale
   marcado?). El cambio en sí es opcional en esta ronda; si se hace,
   que sea a otro servidor y de vuelta, y queda registrado.
4. **Editar nota del túnel** (ruta manager nueva) **y la de la CUENTA**
   de Diego, que quedó pendiente de la ronda 1: texto cualquiera,
   guardar, y verificar en el detalle que quedó.
5. **Regenerar túnel** — candidata regenerate_config/. Ojo real: si
   acierta, las claves de Diego cambian y su config actual deja de
   funcionar (la nueva se baja con el paso 2 si la descarga ya funciona).
6. **Deshabilitar → Habilitar** — acciones disable//enable/: deshabilita
   a Diego, deja que recargue y verifica el estado; luego habilítalo de
   nuevo.
7. **Eliminar: NO probarlo** (sigue listo y guardado, como acordado).

**Al terminar la ronda:** Diagnóstico API → «Copiar diagnóstico
completo» → pegar en el chat. Con las entradas de la sonda, la descarga,
los servidores y cada «Escritura — …» se fija la ruta definitiva de cada
acción, acierte o falle el primer intento.

## v0.7.1 — diagnóstico copiable por bloques (2026-10-31)

**Entregable:** `Admin3FORCE-0.7.1-lite.apk` — paquete
`com.gennis.admin3force`, versionCode **11** / versionName **0.7.1-lite**
(manifiesto y build.sh alineados), minSdk 21 / target 34, firmas v1+v2.

**El cambio (único; orden directa de Gennis: «separa los diagnósticos
para que no se haga uno tan grande»):** la pantalla Diagnóstico API ya no
tiene los dos botones «Copiar diagnóstico completo». Arriba de la lista
hay ahora TRES botones de copia, cada uno con su toast:

- **«Copiar intentos y escrituras»** (toast «Intentos copiados»): bloque
  corto y autoidentificado — cabecera mínima (app + versión, fecha-hora,
  panel, usuario y rol de la sesión) + SOLO las entradas de intento en
  orden de llegada: todas las «Escritura — …», «Descarga de
  configuración», «Servidores del panel», «Detalle de túnel (sonda)» y
  «Refresh» si las hay.
- **«Copiar matriz»** (toast «Matriz copiada»): solo la matriz fila por
  fila — cabecera con la forma de las filas, las filas y los totales —
  con su mes indicado.
- **«Copiar diagnóstico general»** (toast «Diagnóstico general
  copiado»): el resto — app, sesión guardada, llamadas rutinarias
  (Sesión / Usuarios / Túneles / Matriz / Tasa), detalle de sesión con
  stats y agregados de usuarios y túneles — SIN la matriz fila por fila
  y SIN repetir las entradas de intento.

La lista en pantalla no cambia: se siguen viendo todas las entradas.
`DiagFull` genera los tres textos por separado desde los mismos cuerpos
privados (la clasificación intento/rutinaria vive en
`DiagFull.isAttemptLabel`); ninguna otra lógica se tocó — acciones,
escrituras y vistas quedan como en la v0.7.0.

**Nota de uso:** desde esta versión, tras una ronda de pruebas basta
pegar en el chat el bloque **«intentos y escrituras»**; la matriz y el
general solo cuando hagan falta.

## v0.8.0 — acciones con las rutas DOCUMENTADAS (2026-10-31)

Las candidatas de las rondas 1–2 se sustituyen por las rutas exactas del
índice Developers del propio panel (API v2.1.0, leído el 2026-10-31;
registro en `../API-INSPECTION.md`, sección «ÍNDICE DEVELOPERS LEÍDO»).
Patrón seguro intacto: sin reintentos, UNA ruta por toque en escrituras,
entrada completa en «Intentos y escrituras», error literal visible,
éxito = toast + refresco real. Fuera de estas acciones, la app no cambia.

Mapa acción → ruta:

| Acción | Ruta documentada |
|---|---|
| Descargar configuración | `GET /api/v1/tunnels/<id>/download-config/` (con GUION, un solo intento; se retira la cadena detalle→manager) |
| Regenerar | `POST /api/v1/tunnels/<id>/regenerate/` con `{}` (sustituye a `regenerate_config/`) |
| Cambiar de servidor | `POST /api/v1/tunnels/<id>/reassign-host/` con `{"host_id": <id>}` (con GUION; la lista sigue `GET /api/v1/hosts/?tunnel_id=<id>`, probada) |
| Editar nota (túnel) | `PATCH /api/v1/tunnels/<id>/` con `{"notes": "…"}` (vía POST + `X-HTTP-Method-Override: PATCH`; no hay endpoint de notas) |
| Deshabilitar / Habilitar | `PATCH /api/v1/tunnels/<id>/` con `{"status":"disabled"}` / `{"status":"active"}` (no existe endpoint dedicado; si el servidor rechaza el valor, su error literal queda en Intentos y se ajusta — no se prueban otros valores en cadena) |
| Información (sonda) | `GET /api/v1/tunnels/<id>/` — se mantiene (documentado como detalle; en la práctica dio 404 para túneles de clientes; la vista sigue cayendo a los datos de la lista) |
| Eliminar | `DELETE /api/v1/tunnels/<id>/` (soft-delete documentado) — guarda intacta: escribir el nombre exacto del túnel |

Sin cambios: Marcar pagado (probado), Reaprovisionar (probado), nota de
CUENTA (`PATCH /api/manager/v1/accounts/users/<id>/ {notes}`, aún sin
probar — queda como está), hosts y el Diagnóstico en tres bloques de la
0.7.1.

Entregable: `Admin3FORCE-0.8.0-lite.apk` — versionCode **12** /
versionName **0.8.0-lite**, 82 347 bytes, `apksigner verify` → **v1 true,
v2 true**; badging confirma código 12 / 0.8.0-lite y label «Admin
3FORCE». Chequeo del dex: están `/download-config/` y `reassign-host/`,
los cuerpos `{"status":…}` y los sufijos `regenerate/` / `reprovision/`;
ya no están `download_config/` (ruta), `reassign_host/`,
`regenerate_config/` ni `disable/`.

**Orden de prueba sobre Diego:** Descargar → Regenerar → Cambiar de
servidor (a otro y de vuelta) → Editar nota del túnel → Deshabilitar →
Habilitar; después pegar solo el bloque «Intentos y escrituras».

## v0.8.1 — sonda de UNA sola variable: ?personal=false en la familia del detalle (2026-10-31)

**La hipótesis** (registro en `../API-INSPECTION.md`, «Ronda 3»): con
las rutas documentadas de la 0.8.0, TODA la familia del detalle (GET
detalle, GET download-config, PATCH {notes}, PATCH {status}) dio el
404 JSON genérico para el túnel de cliente 525 con el JWT del
reseller, mientras en la misma sesión la lista `?personal=false`,
`hosts?tunnel_id=` y `reprovision/` respondían 200. Hipótesis de
trabajo: ese viewset solo ve los túneles PROPIOS de la cuenta salvo
que se le pase el mismo parámetro de alcance que usa la lista. La
0.8.1 cambia SOLO eso en la familia del detalle — una variable, para
que la reprueba diga algo concluyente.

Cambios exactos (solo URLs de la familia del detalle):

| Pieza | Antes (0.8.0) | Ahora (0.8.1) |
|---|---|---|
| Información (sonda) | `GET /api/v1/tunnels/<id>/` | `GET /api/v1/tunnels/<id>/?personal=false` |
| Descargar configuración | `GET …/download-config/` (un intento) | Cadena de LECTURAS, parando en el primer 200: (1) `GET …/download-config/?personal=false`; (2) si 404, `GET /api/manager/v1/tunnels/<id>/download-config/` (familia manager CON GUION, nunca probada). Cada intento registrado por separado en Intentos. |
| Editar nota (túnel) | `PATCH /api/v1/tunnels/<id>/` | `PATCH /api/v1/tunnels/<id>/?personal=false` |
| Deshabilitar / Habilitar | `PATCH /api/v1/tunnels/<id>/` `{status}` | `PATCH /api/v1/tunnels/<id>/?personal=false` `{status}` |
| Eliminar | `DELETE /api/v1/tunnels/<id>/` | `DELETE /api/v1/tunnels/<id>/?personal=false` (guarda de nombre exacto intacta) |

En el código: `PanelConfig.tunnelDetailUrl(id)` nueva (detalle +
`?personal=false`) para sonda/nota/estado/eliminar, y
`PanelConfig.downloadConfigManagerUrl(id)` para el segundo candidato
de descarga. SIN CAMBIOS: Regenerar (`POST …/regenerate/ {}`) y
Cambiar de servidor (`POST …/reassign-host/ {host_id}`) quedan
exactamente como en la 0.8.0 (las acciones con sufijo siguen colgando
de `tunnelUrl`, sin el parámetro); también intactos Marcar pagado,
Reaprovisionar, hosts, nota de cuenta y el Diagnóstico en tres
bloques. Patrón seguro intacto: escrituras sin reintentos y una ruta
por toque; la cadena es solo de lecturas.

Entregable: `Admin3FORCE-0.8.1-lite.apk` — versionCode **13** /
versionName **0.8.1-lite**, 82 347 bytes, `apksigner verify` → **v1
true, v2 true**; badging confirma código 13 / 0.8.1-lite y label
«Admin 3FORCE». Chequeo del dex: presentes `?personal=false` y la
cadena de la familia manager `/api/manager/v1/tunnels/` +
`download-config/`.

**Orden de reprueba sobre Diego:** Descargar → Editar nota del túnel
→ Deshabilitar → Habilitar (la sonda corre sola al abrir Información);
después pegar solo el bloque «Intentos y escrituras». Quedan además
pendientes de la ronda en curso los intentos de Regenerar y Cambiar
de servidor en la 0.8.0/0.8.1 (sin cambios de ruta).

## v0.8.2 — micro-corrección: Regenerar gana ?personal=false (2026-10-31)

**El motivo** (registro en `../API-INSPECTION.md`, «Ronda 4»): la
reprueba de la 0.8.1 confirmó la hipótesis — `?personal=false`
desbloquea la familia del detalle (nota del túnel **200**, descarga
**200**, deshabilitar **200**) y `reassign-host/` funciona sin él
(**200**). El ÚNICO fallo restante fue Regenerar, que la 0.8.1 había
dejado deliberadamente sin el parámetro y volvió a dar el 404 JSON
genérico.

**Cambio único:** `PanelConfig.REGENERATE_SUFFIX` pasa de
`regenerate/` a `regenerate/?personal=false`; el punto de llamada
(`openRegenerate`) no cambia. La escritura queda `POST
/api/v1/tunnels/<id>/regenerate/?personal=false` con `{}`, mismo
patrón seguro: un intento por toque, entrada completa en «Intentos y
escrituras», error literal, éxito = toast + refresco real. TODO lo
demás queda byte a byte como en la 0.8.1 (detalle con
`?personal=false`, descarga en cadena, nota, deshabilitar/habilitar,
eliminar guardado, reassign-host sin parámetro, reaprovisionar,
marcar pagado, hosts, nota de cuenta, Diagnóstico en tres bloques).

Entregable: `Admin3FORCE-0.8.2-lite.apk` — versionCode **14** /
versionName **0.8.2-lite**, 82 347 bytes, `apksigner verify` → **v1
true, v2 true**; badging confirma código 14 / 0.8.2-lite y label
«Admin 3FORCE». Chequeo del dex sobre el entregable: presente la
constante `regenerate/?personal=false`; `reassign-host/` y
`reprovision/` siguen sin parámetro; ya no existe `regenerate/` como
constante suelta.

**Reprueba:** un toque en Regenerar sobre Diego; después pegar solo
el bloque «Intentos y escrituras». Pendiente además de rondas
anteriores: Habilitar (tras el Deshabilitar 200 de la ronda 4) y la
nota de la CUENTA de Diego, sin cambios de ruta.

## v0.8.3 — la 0.8.2 + descarga .conf de verdad + Deshabilitar con {is_active} (2026-10-31)

Es la ÚNICA instalación que hace falta ahora: parte de la v0.8.2 ya
construida y suma dos correcciones con evidencia fresca (registro en
`../API-INSPECTION.md`, «Ronda 4» y «Verificación en el panel»).

**Pieza heredada de la 0.8.2:** Regenerar con alcance —
`POST /api/v1/tunnels/<id>/regenerate/?personal=false` con `{}`
(`PanelConfig.REGENERATE_SUFFIX`), sin tocar desde la 0.8.2.

**Corrección 1 — la descarga llega como .conf** (reporte de Gennis:
le llegó .txt). Causa: el guardado por MediaStore (Android 10+)
declaraba `MIME_TYPE "text/plain"` y el proveedor añade al nombre
mostrado la extensión derivada de ese MIME. Cambios en
`showDownloadResult`:
- El archivo se llama **`<nombre del túnel>.conf`**: el nombre sale
  de `name_for_user` si existe; si no, del nombre interno (saneado
  como nombre de archivo). El contenido no cambia.
- El `ContentValues` de MediaStore ya **no declara MIME_TYPE**: sin
  extensión derivada de ningún MIME, el `.conf` se conserva en el
  archivo guardado/compartido.
- Tras guardar se lee el nombre real de la fila
  (`queryDisplayName`); si difiriera del deseado, se renombra la fila
  a `<tunel>.conf` vía `ContentResolver.update` y la fila «Archivo»
  muestra el nombre realmente almacenado.
- La ruta heredada (Android ≤ 28, archivo directo) ya escribía el
  nombre literal con `.conf` y no cambia.

**Corrección 2 — Deshabilitar/Habilitar con el campo del
interruptor** (verificación en el panel, 2026-10-31, solo lectura):
el PATCH `{"status":"disabled"}` de la ronda 4 devolvió 200 pero el
túnel siguió **ACTIVE** (`status` es un valor de salida, «online»,
que la API ignora al escribir); la web usa el interruptor «Tunnel
status», que apunta a un booleano. Cambios:
- Payload nuevo sobre la MISMA ruta (`PATCH
  /api/v1/tunnels/<id>/?personal=false`, vía POST +
  X-HTTP-Method-Override): Deshabilitar = `{"is_active": false}`,
  Habilitar = `{"is_active": true}` (en `toggleWrite`).
- Registro enriquecido SOLO para estas dos escrituras
  (`doWrite(..., enrichState=true)` → `ApiClient.write(...,
  enrichState)` → `appendStateSummary`): la entrada de Intentos
  incluye además las **claves de nivel superior** del objeto
  respuesta y los **valores de `status`, `is_active` y
  `disabled_date`** si vienen — para confirmar en un solo toque si
  el campo se aplicó.

TODO lo demás queda como en la v0.8.2 (detalle con
`?personal=false`, descarga en cadena de lecturas, nota de túnel,
eliminar guardado, reassign-host sin parámetro, reaprovisionar,
marcar pagado, hosts, nota de cuenta, Diagnóstico en tres bloques).
Patrón seguro intacto: escrituras sin reintentos, una ruta por toque.

Entregable: `Admin3FORCE-0.8.3-lite.apk` — versionCode **15** /
versionName **0.8.3-lite**, 82 347 bytes, `apksigner verify` → **v1
true, v2 true**; badging confirma código 15 / 0.8.3-lite y label
«Admin 3FORCE». Chequeo del dex sobre el entregable: presentes
`regenerate/?personal=false`, `{"is_active":false}`,
`{"is_active":true}`, `.conf`, `name_for_user` y el resumen «claves
de la respuesta»; ausentes los payloads viejos `{"status":"disabled"}`
y `{"status":"active"}`.

**Reprueba sobre Diego:** Regenerar → Descargar (verificar que el
archivo llega como **.conf**) → Deshabilitar (verificar tras la
recarga que la lista lo muestra deshabilitado, y que la entrada de
Intentos trae `is_active` entre las claves/valores) → Habilitar;
después pegar solo el bloque «Intentos y escrituras». Pendiente de
rondas anteriores, sin cambios de ruta: la nota de la CUENTA de
Diego.

## v0.8.4 — sonda precisa para el cambio de servidor (2026-10-31)

**Hipótesis** (registro en `../API-INSPECTION.md`, «Ronda 5»): el
POST documentado `…/reassign-host/` con `{"host_id"}` respondió 200
tres veces, pero el túnel aterrizó en OTRO host (43→interfaz 50
«S3-ATLANTA», 47→interfaz 39 «S1-CUBA», 41→interfaz 47 «S3-CUBA»):
o la API auto-asigna ignorando el host pedido, o ese no es el
mecanismo. El formulario Configure de la web guarda el host
(«Selected Host», pestaña Network) — la vía a probar es el mismo
PATCH del detalle que ya aceptó `{"notes"}` e `{"is_active"}`. Y
la ronda 5 dejó un hueco de registro: el diagnóstico no guardaba el
NOMBRE tocado en el selector, así que ni siquiera se podía cruzar
lo pedido con lo aplicado. Esta build cambia la vía y cierra el
hueco; nada más se toca.

**Cambio 1 — nueva vía de escritura** (en `openReassignConfirm`):
al confirmar un servidor, la app envía PATCH (POST +
`X-HTTP-Method-Override: PATCH`, como las demás ediciones) a
`https://reseller.3forcevpn.com/api/v1/tunnels/<id>/?personal=false`
con `{"host": <id del host tocado>}`. Sustituye al POST
`reassign-host/` (la constante `REASSIGN_SUFFIX` sale de
`PanelConfig` y la ruta desaparece de la app; reaprovisionar sigue
intacto con su POST propio).

**Cambio 2 — registro que discrimina:**
- La entrada «Servidores del panel» añade los pares compactos
  id=nombre de TODOS los ítems («pares: 43=S2-CUBA · 47=S3-CUBA…»),
  vía `ApiClient.getWithPairs` → `recordEntry(..., withPairs)` →
  `idNamePairs` (excepción deliberada y anotada a la regla de
  `describe()`: sin esos nombres no hay forma de cruzar la lista con
  la escritura).
- La entrada «Escritura — cambiar de servidor» anota lo
  seleccionado («seleccionado: <nombre> (id <n>)», parámetro
  `extraNote` de `ApiClient.write`) y, con el resumen de estado
  enriquecido de la 0.8.3 (`enrichState`: claves de la respuesta +
  `status` / `is_active` / `disabled_date`), el **host resultante**
  si la respuesta trae el objeto del túnel («host resultante: <id>
  <nombre>», `appendHostSummary`). Cruzando pares + seleccionado +
  resultante, un solo toque dice si la API honra el host pedido por
  esta vía.

TODO lo demás queda como en la 0.8.3: regenerar con
`?personal=false` (probado, 200 en la ronda 5), descargar (.conf),
nota de túnel y de cuenta, deshabilitar/habilitar con `{is_active}`,
detalle/sonda, eliminar guardado, reaprovisionar, marcar pagado,
hosts y el Diagnóstico en tres bloques. Patrón seguro intacto:
escrituras sin reintentos, una ruta por toque, error literal.

Entregable: `Admin3FORCE-0.8.4-lite.apk` — versionCode **16** /
versionName **0.8.4-lite**, 82 347 bytes, `apksigner verify` → **v1
true, v2 true**; badging confirma código 16 / 0.8.4-lite y label
«Admin 3FORCE». Chequeo del dex sobre el entregable: presentes la
clave `"host"` del cuerpo hacia el detalle con `?personal=false`
(×5 en el dex), `seleccionado:`, `host resultante:`, `pares:` y
`regenerate/?personal=false`; **ausentes** `reassign-host/` y
`host_id` (la vía vieja salió entera; las apariciones de
`download_config` son el campo `can_download_config` del
serializador, sin relación).

**Reprueba sobre DIEGO** (esta vez sobre Diego, no sobre un
cliente): abrir «Cambiar de servidor» en su túnel, elegir UN
servidor concreto y confirmar — una sola vez —; después, en otra
pasada, devolverlo al suyo. Pegar solo el bloque «Intentos y
escrituras»: la entrada «Servidores del panel» traerá los pares
id=nombre, y la escritura dirá el seleccionado y el host
resultante. Si coinciden, la vía PATCH `{"host"}` queda probada;
si difieren, la API no honra el host pedido por ninguna de las dos
vías y el dato queda fijado con precisión.

## v0.8.5 — cambios al instante + Deshabilitar guiado por el esquema OPTIONS (2026-10-31)

**Evidencia** (registro en `../API-INSPECTION.md`, «Ronda 6» y
«Ronda 6b»): el PATCH `{"host"}` quedó probado (seleccionado =
resultante en 411 y en Diego, que quedó restaurado en
S2-MIAMI-WINDSCRIBE); el PATCH `{"is_active":false}` devolvió 200
SIN cambiar nada (`status=active`, `disabled_date=null`), igual
que `{"status":"disabled"}` en la ronda 4. Y Gennis pidió que los
cambios **se muestren inmediatos**: tras una escritura la app
esperaba la recarga completa del Home (sesión + usuarios + dos
alcances de túneles + matriz + tasa) antes de repintar, y no se
sabía si el cambio se había aplicado.

**Cambio 1 — aplicación inmediata de las escrituras**
(`doWrite` forma completa + ayudantes nuevos):
- Al éxito, el objeto de la respuesta se fusiona clave a clave en
  el ítem local (`mergeResponseObject`: túnel por su forma, cuenta
  si trae `tunnels_count`/`telegram_username`; la «zone» anidada
  sin nombre no pisa una etiqueta local legible) y la vista actual
  se repinta AL MOMENTO (`renderCurrentMain`; en Home, las cards se
  recalculan desde lo local con `refreshHomeCardsFromLocal`).
- Después, UNA lectura puntual del detalle
  (`verifyTunnelNow`, ~medio segundo) fusiona el estado real y el
  toast final dice la verdad: con estado deseado
  (deshabilitar/habilitar) confirma «confirmado en el panel» o
  avisa «SIN CAMBIO: el panel sigue mostrando el túnel ACTIVO…»;
  sin él, añade el host resultante.
- Respuesta ambigua de un túnel: misma verificación puntual en
  vez de la recarga completa.
- Eliminar usa `skipInstant` (su objeto no se fusiona) y sigue
  con la recarga completa.
- Marcar pagado: `refreshMatrixOnly()` recarga SOLO la matriz
  (una llamada) y repinta Facturación/Pagos; sustituye a la
  recarga completa en éxito y en respuesta ambigua.

**Cambio 2 — Deshabilitar/Habilitar según el esquema de la API**
(`toggleWrite` nuevo + `ApiClient.rawOptions`):
- Al tocar Deshabilitar/Habilitar, la app pide primero
  `OPTIONS /api/v1/tunnels/<id>/?personal=false` (lectura de
  metadatos, con el refresh-en-401 de las lecturas) y registra el
  esquema entero en Intentos («Esquema de estado del túnel»:
  campos PATCH con tipo, solo-lectura y opciones).
- `chooseTogglePayload` elige UNA escritura: (1) booleano
  escribible `is_active`/`enabled`/`is_enabled`/`active` (o
  `is_disabled` invertido); (2) `status`/`state` escribible con el
  valor de sus opciones (si no declara, «inactive»/«active»);
  (3) `disabled_date` escribible (ahora en UTC / null). Sin
  esquema utilizable: `status` «inactive»/«active» (el valor
  «inactive» nunca se había probado; «disabled» sí, y fue
  ignorado). Sin candidato en el esquema: NO se envía nada y la
  hoja lo explica.
- La escritura va por el `doWrite` completo con el estado
  deseado: la verificación puntual confirma si se aplicó.
- `HomeStats.statusOf`: `disabled_date`/`deleted_at` no vacíos
  mandan sobre el `status` de salida (mismo criterio en el conteo
  del Home), para que un túnel deshabilitado por fecha no se
  pinte activo.

TODO lo demás queda como en la 0.8.4 (PATCH `{"host"}` probado,
regenerar con `?personal=false`, descarga .conf, notas, eliminar
guardado, reaprovisionar, Diagnóstico en tres bloques). Patrón
seguro intacto: escrituras sin reintentos, una ruta por toque; el
OPTIONS es lectura y no cuenta como intento de escritura.

Entregable: `Admin3FORCE-0.8.5-lite.apk` — versionCode **17** /
versionName **0.8.5-lite**, 86 443 bytes, `apksigner verify` →
**v1 true, v2 true**; badging confirma código 17 / 0.8.5-lite y
label «Admin 3FORCE». Chequeo del dex: presentes «Esquema de
estado del túnel», «campo según el esquema OPTIONS»,
«confirmado en el panel», «SIN CAMBIO», «Verificación tras
escritura», `OPTIONS`, `is_disabled`.

**Reprueba sobre Diego:** Deshabilitar → la app consulta el
esquema y escribe el campo que la API declare; la lista debe
mostrarlo INACTIVO al momento y el toast debe decir «confirmado
en el panel». Después Habilitar igual. Pegar solo el bloque
«Intentos y escrituras» (traerá el esquema OPTIONS y las dos
escrituras). Pendientes sin cambios: la nota de la CUENTA y la
confirmación del nombre .conf de las descargas.
