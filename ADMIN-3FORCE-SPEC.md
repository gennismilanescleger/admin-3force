# Admin 3FORCE — especificación (cerrada 2026-10-24, actualizada 2026-10-30)

Consolidación de lo acordado con Gennis el 2026-10-24 y de todas las decisiones
tomadas durante la fase del prototipo (2026-10-25 a 2026-10-30). Saw Poster queda
descartada y no se sigue con ella. El prototipo web (artefacto `admin-3force`,
solo visual, datos demo) está terminado y queda como referencia visual
congelada. La app real NO está construida: se empieza, en orden
Login → Home → Cuentas → Túneles → Pagos, cuando Gennis lo ordene.

## Propósito
App Android para gestionar SAWVPN, independiente de todo, completa y con datos
reales, no de adorno. Instalación por APK.

## Ruta técnica — HÍBRIDA (decidida 2026-10-24)
- Login: WebView del panel reseller.3forcevpn.com. Gennis entra y resuelve el
  captcha como humano, UNA sola vez. La app captura el token de sesión y lo
  guarda cifrado; después entra sola.
- Resto: todo por API. Si la sesión vence y el refresh no basta, se vuelve al
  WebView; sin autocaptcha.
- La biometría NO es un botón de login: es un bloqueo de la app que usa la
  huella o el rostro ya registrados en el teléfono (sistema Android, con
  PIN/patrón de respaldo). Cerrar sesión desde «Mi cuenta» obliga a un login
  completo de nuevo.
- No depende de Tasker, ni de Muse / navegador de la VM, ni de Saw Poster,
  ni de WhatsApp Web. Funciona sola en el teléfono.
- Si el panel está caído, muestra los últimos datos guardados con su hora,
  nunca números inventados (ver también la cola sin conexión, en Pagos).

## API leída el 2026-10-24 (solo lectura, como Gennis super admin, nada tocado)
- Developers del panel: reseller.3forcevpn.com/dashboard/developers, API v2.1.0,
  148 endpoints, espejo de un API.md, sin Swagger.
- No hay clave API aparte: login + JWT (access 10 min, refresh rotativo).
- El manager siempre exige captcha.
- Cubre: cuentas/usuarios (incl. reset de contraseña), túneles (solo WireGuard:
  crear, regenerar, cambiar de host, reaprovisionar, crear en lote, soft-delete)
  y facturación (incl. facturación manual / matriz, que es donde se marcan pagos).

## Qué viene de la web y qué vive en el teléfono (cerrado 2026-10-30)
- **De la web (API del panel):** cuentas (nombres, estados, fechas de creación),
  túneles (nombres, activo/inactivo, servidor asignado, fechas), facturación
  (meses pagados/pendientes por túnel desde la matriz; pagos manuales y
  automáticos), la lista de servidores del panel que usa «Cambiar de servidor»,
  **el alias de cada cuenta**, la validación del login y la ejecución de todas
  las acciones (crear/editar/deshabilitar/eliminar, regenerar, reaprovisionar,
  cambiar servidor, cambiar contraseña, marcar pagos, descargar el .conf).
- **Local en el teléfono:** lectura de SMS Transfermóvil/EnZona y su validación
  y deduplicación; la lista de tarjetas permitidas que Gennis teclea en la app;
  los números remitentes alternos; los contactos del dispositivo; las plantillas
  de WhatsApp editables; el Historial y las notificaciones de la app; el ping a
  los servidores medido desde el teléfono; los temporizadores (recién creado,
  cuenta atrás de pruebas) calculados desde las fechas de creación de la web;
  la sesión guardada cifrada y el bloqueo biométrico; la caché sin conexión
  con su hora.
- **De otra web:** la tasa USDT de cambiocup.com. Lo recaudado en USDT del Home
  se calcula en local, a partir de los pagos marcados del mes (dato de la web)
  y esa tasa.

## Notas y alias (cerrado 2026-10-30)
- **La nota es UNA sola cosa:** la nota de la app ES el comentario de la web
  (el comentario del túnel en el panel), sincronizada en los dos sentidos,
  siempre actualizada. No existen «notas locales» separadas.
- **El alias viene de la web** junto con los datos de la cuenta. No se edita en
  la app: se muestra como dato de solo lectura («viene de la web»). Una cuenta
  nueva nace sin alias hasta que la web se lo dé.

## Vistas (forma final del prototipo, 2026-10-30)
Navegación inferior: Inicio, Túneles, Cuentas, Pagos, Más. Solo se desplazan
las listas; la barra superior muestra solo el nombre de la vista y la campana
de notificaciones.

1. **Login** — diseño convencional: icono y título arriba («Admin 3FORCE» y la
   dirección del panel debajo), formulario plano sin tarjeta en la mitad
   inferior: usuario, contraseña con ojo mostrar/ocultar, captcha y «Entrar».
   Nota de texto «Sesión guardada» cuando la hay. Sin botón de biometría.
2. **Home** — cards de Cuentas y Túneles lado a lado (conteos con «/»:
   Con/Sin/Total y Activos/Inactivos/Total, el color del número = el estado);
   tira de la tasa («1 USDT = X CUP», tocarla copia el mensaje completo de la
   tasa con su plantilla); card de Facturación con filas (Recaudado este mes
   en USDT, Pagados X/Y, Realizados/Pendientes, manuales/automáticos separados
   por una línea); tira de Servidores («3 de 3 activos»); acciones rápidas
   (Túnel, Cuenta, Marcar pago); sección «Por revisar» que solo muestra las
   filas con contador > 0 (pagos sin identificar, pago inválido, prueba por
   vencer, cuentas por completar, recordatorios por enviar, marcas por
   sincronizar); línea discreta «Datos actualizados automáticamente».
3. **Túneles** — pagos y estados son POR TÚNEL. Búsqueda + filtro propio
   (Todos/Activos/Inactivos/En prueba); grupos «En prueba», «Activos»,
   «Desactivados» (la etiqueta se oculta si el grupo queda vacío). Botones de
   crear en barra fija inferior. Card: título = nombre del túnel, debajo la
   cuenta; botones rápidos solo Descargar (solo ofrece el .conf, sin QR),
   Regenerar, Cambiar de servidor y Notas (solo icono); el resto en el menú de
   3 puntos, cuya PRIMERA opción es «Información». Todos los iconos de túnel
   son una LLAVE redonda. Nombre dual al crear: «Nombre del túnel · en el
   sistema» y «Nombre que verá el cliente».
   - **Crear túnel:** selector «Un túnel / Por lotes» (por lotes: solo nombre
     base y cantidad 2–50; el sistema enumera). Zona y Facturación lado a lado,
     preseleccionadas desde el PERFIL predeterminado y editables; los perfiles
     no se mencionan dentro del modal.
   - **Recién creado:** chip junto a «Activo» durante el tiempo configurado
     (5 min por defecto; opciones 1/3/5/10/15/30 en Ajustes). Mientras es
     nuevo, Descargar se cambia por el botón de WhatsApp «Avisar» (hoja
     «Túnel creado» con vista previa y contador «Enviado ×N»); después,
     «Notificar al cliente» vive solo en el menú y el resto del tiempo el menú
     ofrece «Hablar al WhatsApp».
   - **Prueba (solo de túneles, nunca de cuentas):** al crear se puede activar
     con duración en horas (6–168). Badge ámbar «Prueba» con el mismo diseño
     que los chips de estado y cuenta atrás en la línea de datos. Antes de
     vencer, el menú permite convertirla en plan normal. **Al vencer se
     deshabilita SOLO ese túnel y nada más: la cuenta no se deshabilita ni se
     mueve** (decidido 2026-10-30).
   - **Reaprovisionar:** el chip pasa a «Provisionando» (punto pulsante) y al
     terminar vuelve al estado anterior y entra el aviso «Túnel
     reaprovisionado».
   - **Información del túnel (modal):** cuenta vinculada + alias, estado
     (incl. fecha de desactivación si la hay), prueba y su cuenta atrás, fecha
     de creación, **tira de meses** (ver abajo), servidor y salida actual, tipo
     de facturación DE ESE túnel, día de cobro y su modo, y la nota/comentario.
4. **Cuentas** — búsqueda + filtro (Todas/Con túneles/Sin túneles/
   Deshabilitadas); grupos con esas mismas etiquetas. Card: título = nombre
   de la cuenta, debajo «Alias: X» una sola vez; solo estado pagado/pendiente
   (sin botón «Marcar pago» en la card: marcar vive en Pagos); botones
   uniformes; cuentas sin túneles con contacto vinculado muestran botón de
   WhatsApp. Cuenta nueva: nace en «Sin túneles» con chip «Nueva» y sugerencia
   de crear su primer túnel. El campo de teléfono tiene botón de contactos
   (selector modal «Elegir desde contactos»; en la app real, la agenda real
   del teléfono, con permiso único; los contactos nunca salen del teléfono).
   - **Información de la cuenta (modal):** nombre + alias, estado, creación,
     contacto vinculado y número alterno, nota, y debajo sus túneles, cada uno
     con su estado y su tira de meses.
   - **Tira de meses** (en ambos modales de Información): una línea
     E F M A M J J A S O N D; mes pagado en verde con fondo verde suave; no
     pagado sin color; los meses futuros algo más tenues; si hay un mes vencido
     sin pagar, línea pequeña debajo «Pendiente: MES». En túneles de prueba,
     toda la tira sin color.
   - Enlaces cruzados: «Ver pagos de sus túneles» → Historial filtrado por la
     cuenta; «Ver sus túneles» → Túneles con la cuenta en la búsqueda.
5. **Pagos** — pestañas Recibidos / Pendientes / Pagados con badges de conteo.
   Card resumen superior: solo «Pagados» con barra de progreso y conteos a la
   derecha. Sin filtros ni botón de exportar en esta vista.
   - **Recibidos:** título = nombre del túnel (o «Remitente desconocido» /
     «Pago por EnZona»), debajo la cuenta o «Sin asignar»; a la derecha el
     monto con la vía directamente debajo («Vía Transfermóvil ✓» / «Vía
     EnZona ✓»); detalle uniforme (teléfono si lo hay, fecha y hora,
     transacción copiable, destino). Grupos: Por confirmar / Sin
     identificación / Pago inválido, lo más reciente primero dentro de cada
     grupo. EnZona: su SMS no trae número remitente ni tarjeta; su card no
     muestra Destino ni teléfono, solo fecha/hora y «No. operación».
   - **Identificación:** un pago Transfermóvil ya identificado NO muestra
     opción de asignar. «Asignar a un túnel» es solo para EnZona y remitentes
     desconocidos; en desconocidos, el número se copia tocando el número
     mismo. Destino fuera de la lista blanca → «Pago inválido», con botones
     «No es un pago» y «Revisar tarjetas permitidas».
   - **Un solo descarte: «No es un pago».** Saca la transferencia de Recibidos
     sin marcar ni contar nada; si SÍ es un pago sin identificar, el camino es
     «Asignar a un túnel». Al confirmar queda registrada en Historial como
     descartada y se ofrece Deshacer; en Historial, el filtro «Descartadas»
     permite «Restaurar transferencia».
   - **Asignar:** desplegables propios para túnel, meses (multiselección en UN
     solo desplegable
...[truncated 10507 chars], el panel se queda
     abierto, los meses se unen con « + », hace falta al menos un mes),
     «Después de asignar» y botón «Confirmar y marcar pagado». La hoja lleva
     arriba un resumen de la transferencia y el campo **«Nota del pago»**, en
     la misma posición y con el mismo diseño que en Marcar pagado.
   - **Marcar pagado:** marca el túnel de la card tocada, sin selector de
     cliente y sin selector de beneficiario. Incluye el campo «Nota del pago».
     Si después se elige WhatsApp, se abre el **modal de confirmación de
     pago** funcionando: plantilla con el túnel y los meses, botón verde
     «Abrir en WhatsApp» y contador «Enviado ×N». Ese modal se abre también
     desde el botón WhatsApp de cada card de Pagados y tras asignar.
   - **Cola sin conexión (decidido 2026-10-30):** si el panel está caído o no
     hay internet, las marcas y los cambios se guardan en una cola en el
     teléfono y se aplican solos cuando la web vuelve; el pago queda marcado
     «Por sincronizar» (con «Sincronizar ahora» disponible) y la confirmación
     de WhatsApp se ofrece SOLO después de que la marca se aplica de verdad.
6. **Más** — rejilla de Gestión (Tarjetas permitidas, Perfiles de túnel,
   Mensajes predefinidos, Sincronizar contactos, Historial, Servidores) y al
   final la sección App con «Mi cuenta» y Ajustes.
   - **Mi cuenta:** identidad (Gennis, Reseller, panel, sesión activa), datos
     de la cuenta, cambiar contraseña en su propia hoja, Seguridad y sesión, y
     el ÚNICO botón «Cerrar sesión» de toda la app.
   - **Tarjetas permitidas:** lista editable; «Añadir tarjeta» pide nombre,
     número y «Tipo de tarjeta» (BPA / BANDEC / Metropolitano / Otra, con
     «Añadir otro tipo…» en línea; el tipo nuevo entra en la misma lista para
     añadir y para editar). Guardar persiste de verdad en la lista. Los
     números se ven enmascarados y solo dentro de esta pantalla. El Destino de
     Pagos lee de aquí «Tarjeta ✓ + banco». Quitar tarjeta pasa por la hoja de
     confirmación genérica.
   - **Perfiles de túnel:** cada perfil predefine tipo de pago y zona (demo:
     Nacional/Internacional). Exactamente un perfil lleva el chip
     «Predeterminado», y ese predeterminado es el que preselecciona Zona y
     Facturación al crear un túnel. Editar perfil guarda sobre su card.
   - **Sincronizar contactos (ventana completa):** card resumen (conteos,
     última sincronización, «Sincronizar ahora», interruptor «Revisar antes
     de aplicar») y solo los contactos que necesitan enlace manual: «Por
     vincular» y «Sin número en su ficha» («Añadir número» abre la ficha). Sin
     grupo «Ya vinculados». Vive en Más > Gestión: el enlace se necesita para
     las confirmaciones y recordatorios de WhatsApp y para identificar
     remitentes.
   - **Historial («Actividad reciente»):** entradas agrupadas por día;
     búsqueda + filtro Todas / Pagos / Túneles / Cuentas / Descartadas;
     botón «Exportar historial». Las entradas descartadas NO llevan badge
     «Descartada» (quitado por orden de Gennis); el «Restaurar transferencia»
     del filtro Descartadas funciona igual.

## Notificaciones (cerrado 2026-10-30)
- **El panel** (campana) acumula los avisos **estrictamente en orden de
  llegada, el más reciente arriba, SIN grupos**. Cabecera con el conteo real
  («N por revisar» / «Todo al día») y «Borrar todas» (pide confirmación en el
  modal genérico; se oculta si está vacío). El punto ámbar de la campana sigue
  al panel. El panel no tiene X ni botón Cerrar: se cierra tocando fuera o la
  campana. Cada aviso trae su acceso: pago → «Ver pago» (aterriza en la card
  exacta, resaltada con anillo cian INTERIOR); pago identificado →
  «Confirmar pago» en el propio panel; cuenta nueva → «Crear túnel» / «Ver
  cuenta»; recordatorios → «Ver pendientes»; prueba y reaprovisionado →
  «Ver túnel»; servidor → «Ver servidores». **Regla: desde una notificación
  NO se asigna un pago** (es un aviso, no un puesto de trabajo).
- **Los grupos van en los AJUSTES:** Ajustes › Notificaciones y avisos separa
  sus opciones por grupos (Pagos, Túneles, Cuentas, Servidores,
  Recordatorios). Los 10 tipos tienen interruptor REAL que filtra el panel al
  momento (apagado: sus avisos se ocultan, dejan de contar y apagan el punto;
  encendido: vuelven): recibido, sin-identificar, invalido, recordatorios,
  nueva-cuenta, nuevo-tunel, prueba, sync (marca por sincronizar), tunel,
  servidor. Ahí viven también el «tiempo como nuevo» y el umbral de aviso de
  prueba por vencer.

## Servidores (vista local, cerrada 2026-10-30)
- Vista **local e independiente** de la lista de servidores del panel: solo
  los que Gennis añade a mano (Nombre, IP, Notas), con Editar / Deshabilitar /
  Habilitar / Eliminar. Es una vista de SOLO estado: no da acceso a los
  túneles de ese servidor. Muestra «Última revisión» con hora exacta y los ms
  solo dentro de esta vista, en tono neutro.
- Revisión automática **cada 15 segundos mientras la app está abierta y en
  pantalla**. Umbrales decididos: un servidor se marca «Sin respuesta» solo
  tras **3 verificaciones fallidas seguidas** (el aviso de caída sale solo en
  ese momento; antes, el chip muestra «Verificando…») y vuelve a «Operativo»
  tras **2 respuestas seguidas**. **Si el teléfono no tiene conexión, no se
  marca nada caído:** las verificaciones se pausan y se conserva el último
  estado conocido marcado «· sin verificar», sin avisos de caída.

## Ajustes (cada sección es título + subtítulo + chevron a su propia ventana)
- **Detección de pagos por SMS:** dos interruptores independientes,
  Transfermóvil y EnZona (viven solo aquí).
- **Seguridad y sesión.**
- **Notificaciones y avisos** (ver arriba).
- **Cobro:** «Día de cobro oficial» (10 por defecto) y «Días de gracia»
  (3 por defecto) — ver el modelo de recordatorios en la lógica de pagos.
- **Datos y respaldo:** interruptor «Actualización automática» (encendido por
  defecto) y «Exportar respaldo (JSON)». NO hay sección «Cuenta» en Ajustes
  (eso es «Mi cuenta», en Más).

## Plantillas de mensajes (exactamente 5, fijas, editables; sin añadir ni borrar)
Confirmación de pago; Recordatorio de pago pendiente; Tasa de cambio USDT;
Túnel creado · datos de acceso; Contraseña restablecida · datos de acceso.
Variables: {cliente}, {mes}, {valor}, {fecha}, {hora}, {usuario},
{contraseña}, {tunel}, {servidor}. Los recordatorios se envían **uno a uno**,
nunca en masa.

## Lógica de pagos (2026-10-24 + las respuestas de Gennis del 2026-10-30)
1. La app lee los SMS directo en el teléfono (Transfermóvil/EnZona), en local.
   Nada se sube a otro servidor.
2. Extrae: monto, número remitente si lo hay, fecha/hora, ID de
   transacción/operación y cuenta destino. (EnZona no trae remitente ni
   tarjeta; solo importe y nº de operación.)
3. Valida la cuenta destino contra las tarjetas permitidas (lista manual de
   la app). Destino no permitido → «Pago inválido».
4. Busca al cliente por el número remitente. Si coincide, el pago nace
   identificado; si no, va a «Sin identificación» para que Gennis lo asigne.
5. **Duplicado = mismo ID de transacción/operación, y solo eso.** Mismo
   cliente y mismo monto con otro ID es otro pago.
6. Solo se marca el pago en el panel (API de facturación manual) cuando
   Gennis lo confirma con un toque. Después se genera la confirmación de
   WhatsApp. Nada automático.
7. **Pago a un túnel deshabilitado por falta de pago:** al confirmarlo, la
   app PREGUNTA en ese momento si se habilita. Sin reactivación automática.
8. **Sin pagos parciales:** solo se marcan meses completos.
9. **Pago de varios meses:** los meses los elige siempre Gennis; la app nunca
   los deduce del monto.
10. **Confirmación de WhatsApp siempre al número PRINCIPAL del cliente**,
    nunca a los alternos.
11. **Pagador ≠ beneficiario** (un cliente paga el túnel de otro): se resuelve
    con el botón «Marcar pagado» del túnel beneficiario, y nada más. (Un
    selector de beneficiario dentro de la confirmación fue probado y
    RECHAZADO por Gennis el 2026-10-30: no volver a añadirlo.)
12. **Sin conexión:** cola local y aplicación automática al volver la web; la
    confirmación de WhatsApp solo tras aplicar (ver Pagos, punto 5).

## Recordatorios y día de cobro (decidido 2026-10-30)
- La facturación es **por MES de calendario, pagada por adelantado**: el día
  es solo el día de cobro dentro del mes, no un ciclo de 30 días.
- Ajustes guarda UN **día de cobro oficial** para la facturación manual
  (defecto: 10; casi todos los clientes manuales de Gennis pagan el día 10;
  unos pocos pagan el 25, 26, 30, 1…, y se les cobra el mes completo igual).
- Cada túnel guarda su modo: un túnel creado **dentro de ±3 días** del día
  oficial adopta ese día (**Oficial**); creado fuera de esa ventana, conserva
  su día de creación como **fecha personalizada**. La Información del túnel
  muestra día y modo.
- Si un mes no tiene ese día (ej. 30 en febrero), el cobro pasa al **día 1
  del mes siguiente**.
- Los túneles en modo Oficial siguen los cambios del día de Ajustes, pero el
  cambio **aplica desde el mes siguiente** (aplicarlo a mitad de mes crearía
  una incongruencia). Los personalizados nunca se mueven.
- **Días de gracia** ajustables (defecto 3): el día de recordatorio = día de
  cobro + gracia. La gracia no mueve ni perdona la deuda: solo decide cuándo
  el túnel entra en «Recordatorios por enviar».
- **Todo túnel NACE CON DEUDA**, de prueba u oficial: Gennis cobra después de
  que el usuario prueba el servicio, así que el mes de creación ya nace
  pendiente y sus recordatorios siguen la regla normal de día + gracia.
- Los túneles ya creados se guían por los **meses pagados/pendientes leídos
  de la matriz de la web**, nunca por fechas.

## Configuración manual, en la app (NO por chat)
- **Tarjetas permitidas** para validar destinos (arriba, en Gestión).
- **Plantillas de WhatsApp** (las 5 de arriba), precargadas con los textos
  actuales de Gennis y editables.
- **Números alternos por cliente**, en su ficha (el alias NO: viene de la web).
- **Arreglos especiales: solo como notas.** No existe «Arreglo activo» en
  ninguna parte, ni categoría separada de «prometieron pagar».

## Reglas de siempre (de su trabajo diario)
- Los pagos son POR TÚNEL, no por cuenta. La identificación en cualquier
  vista es solo por nombre de cuenta, nombre de túnel o alias.
- Verificar el chat exacto con «VPN» antes de cualquier envío a un cliente.
- No escribir directo a Duvian: solo a Ghost, que es quien hace los pagos.
- Diego queda fuera de los cobros.
- Reproducir sus mensajes verbatim, con toda su decoración.
- Todo mensaje enviado por WhatsApp termina con línea en blanco +
  `> Enviado con Agente IA`.
- Las tarjetas nunca van por chat: solo directo en la app.
- Feedback de la app: aviso (toast) pequeño y siempre arriba, solo para
  hechos simples ya completados, con el texto mínimo; todo lo que necesite
  decisión, confirmación o datos va en un modal/hoja. Marcar pagado, asignar,
  descartar, deshabilitar y eliminar se confirman SOLO por la barra de
  Deshacer (que también va arriba).

## Diseño
- Móvil primero, como app Android nativa: tarjetas compactas, poco scroll,
  jerarquía clara. Paleta: fondo #070F1E, tarjetas #111E33, bordes #23365A,
  cian #22D3EE, azules #3B82F6/#2563EB, texto #F1F5F9, secundario #94A3B8;
  pagado = verde suave; pendiente/prueba = ámbar #F5B544.
- Iconos de una librería consistente; **nunca emojis como iconos**.
- **Honestidad de datos:** sin gráficos ni métricas inventadas; un dato que
  no viene de la web se muestra como no disponible, nunca como cero falso.
  Las cifras del prototipo son demo y jamás se tratan como datos del negocio.
- **Regla del texto explicativo (2026-10-30):** las secciones no llevan
  párrafos explicativos largos en el diseño; la explicación va detrás de un
  botón info diminuto junto al título de la sección, que abre un popup
  centrado. El botón es una «i» minúscula dentro de un círculo, siempre con el
  mismo diseño y con el tono neutro de la campana de notificaciones
  (var(--txt2) / #9DB0CC), en todos los estados. Los datos funcionales cortos
  (conteos, estados, valores) se quedan visibles tal cual.
- Botones «atrás» de las ventanas secundarias: contenedor circular 36×36 con
  chevron en tono neutro (no cian). Barra inferior con esquinas superiores
  redondeadas (18px). Todos los desplegables son el componente oscuro propio
  de la app. Salir de una vista limpia su búsqueda y restablece su filtro.

## Actualización automática (sin botón de refrescar: Gennis lo rechazó)
- SMS de pago: en tiempo real (la app escucha en el teléfono; un SMS nuevo
  la despierta al instante aunque esté cerrada).
- Datos del panel: al abrir / volver a primer plano, ~cada 1 min mientras
  está abierta, inmediatamente tras las acciones de Gennis, y renovación del
  token invisible. En segundo plano cerrado, ~cada 15 min.
- Servidores: cada 15 s solo con la app abierta y en pantalla.
- Tasa USDT: al abrir el Home y cada hora. Cuentas atrás de pruebas:
  locales y exactas.

## Pendiente por verificar al construir (no inventar)
- URL base absoluta y esquemas completos de petición/respuesta de la API
  (la página Developers no los trae).
- Si el comentario de la **cuenta** es escribible por API, o solo el del túnel
  (de eso depende la sincronización de notas a nivel de cuenta), y el campo
  exacto del alias por cuenta.
- Campos exactos del modal de crear túnel contra el panel real actual.
- En el panel, Muse marca los pagos bajo la sesión SawVPN, no la admin de
  Gennis (regla del 2026-10-13); la app entrará con la sesión que Gennis use
  al hacer login — confirmar ese punto al construir.

## Estado a 2026-10-30
Prototipo visual terminado y congelado como referencia; esta especificación
incorpora todas las decisiones hasta el 2026-10-30. Construcción de la app
real NO ordenada todavía: el siguiente paso es que Gennis ordene empezar por
Login.
