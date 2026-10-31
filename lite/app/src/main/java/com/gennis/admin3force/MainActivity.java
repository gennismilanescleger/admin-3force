package com.gennis.admin3force;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Admin 3FORCE Lite — hitos LOGIN + HOME (app de pruebas, Java puro, solo framework).
 *
 * Flujo:
 *  - Sin sesión guardada → WebView del panel real (Gennis teclea sus datos y
 *    resuelve el captcha EN LA PÁGINA; la app no tiene campos de credenciales
 *    y su contraseña nunca se captura ni se guarda).
 *  - Al salir de /auth/login se escanea storage + cookies buscando el JWT;
 *    si aparece, se guarda cifrado (Keystore) y se pasa al Home.
 *  - Con sesión guardada → bloqueo biométrico (solo API 28+; en API 21–27 se
 *    entra directo: limitación aceptada de la Lite) → Home.
 *  - El Home lee la API con el access guardado; ante un 401 el cliente
 *    intenta el refresh UNA vez y, si no renueva, se vuelve al WebView.
 *  - La pantalla Sesión queda como «Mi sesión» (datos reales del token).
 *  - Vistas Cuentas / Túneles / Pagos (v0.5.0) alimentadas por la matriz y
 *    las listas reales; Pagos trae la primera ESCRITURA: «Marcar pagado»
 *    contra POST /billing/manual-billing/update/ (payload por descubrir con
 *    un intento real registrado entero en el Diagnóstico).
 *  - v0.6.0: ACCIONES de túnel habilitadas en TODOS los túneles (orden de
 *    Gennis: «habilita las opciones con cualquiera»), con Diego como banco
 *    de pruebas: Información, Descargar configuración, Cambiar de servidor
 *    (lista del panel por descubrir), Regenerar, Reaprovisionar, Editar nota
 *    (túnel y cuenta), Deshabilitar/Habilitar y Eliminar (con guarda de
 *    nombre escrito; listo pero NO probado en esta entrega). Las rutas de
 *    acción no están documentadas con exactitud: las lecturas prueban
 *    candidatas en orden; las escrituras disparan UNA candidata por toque
 *    con el patrón seguro de ApiClient.write y el Diagnóstico enseña.
 *  - v0.7.0: segunda ronda de acciones guiada por la EVIDENCIA de la
 *    ronda 1 (ver API-INSPECTION.md): sonda de detalle al abrir
 *    «Información» (GET /api/v1/tunnels/<id>/, claves completas al
 *    Diagnóstico, config solo por tipo/longitud), servidores por túnel
 *    (GET /api/v1/hosts/?tunnel_id=<id>) y reasignación con {host_id},
 *    descarga desde el detalle o la familia manager, regenerar por
 *    regenerate_config/, deshabilitar/habilitar como acciones
 *    disable//enable/ y nota del túnel por la familia manager.
 *    Reaprovisionar quedó confirmado en la ronda 1 y no se toca.
 *  - v0.8.0: las acciones pasan a las rutas DOCUMENTADAS en el índice
 *    Developers del propio panel (leído el 2026-10-31, ver
 *    API-INSPECTION.md): descargar por GET …/download-config/ (con
 *    guion, un solo intento; se retira la cadena detalle→manager),
 *    regenerar por POST …/regenerate/, cambiar de servidor por POST
 *    …/reassign-host/ {host_id}, nota del túnel por PATCH al detalle
 *    /api/v1/tunnels/<id>/ {notes}, y deshabilitar/habilitar por PATCH
 *    al detalle con {status} (no existe endpoint dedicado). Eliminar
 *    sigue siendo DELETE al detalle (soft-delete documentado) con su
 *    guarda de nombre. Marcar pagado, reaprovisionar, la lista de
 *    hosts, la nota de cuenta y el Diagnóstico en tres bloques no se
 *    tocan. La sonda «Información» (GET al detalle) se mantiene.
 *  - v0.8.1: sonda de UNA sola variable sobre la v0.8.0 (ver
 *    API-INSPECTION.md «Ronda 3»): con las rutas documentadas, toda la
 *    familia del detalle rebotó con 404 genérico para túneles de
 *    clientes mientras la lista ?personal=false funcionaba. La familia
 *    del detalle (sonda, nota, {status} y DELETE) pasa a pedir
 *    ?personal=false, el mismo alcance de la lista; y Descargar
 *    encadena dos LECTURAS: download-config/?personal=false y, si da
 *    404, la familia manager con guion (nunca probada). Regenerar y
 *    Cambiar de servidor quedan exactamente como en la 0.8.0.
 *  - v0.8.2: micro-corrección única — Regenerar gana el parámetro de
 *    alcance (POST …/regenerate/?personal=false): era el único 404 que
 *    quedaba tras confirmarse la hipótesis en la ronda 4.
 *  - v0.8.3: la v0.8.2 más DOS correcciones con evidencia fresca (ver
 *    API-INSPECTION.md, «Verificación en el panel»): (1) la descarga
 *    llega como .conf de verdad — el archivo se llama como el túnel
 *    para el usuario (name_for_user; si no, el interno) y el guardado
 *    por MediaStore ya no declara MIME «text/plain», que era lo que le
 *    añadía el .txt al nombre; (2) deshabilitar/habilitar dejan el
 *    payload {status} — devolvía 200 pero NO cambiaba nada: `status`
 *    es de salida — y pasan al booleano del interruptor «Tunnel
 *    status» de la web, {is_active}, con la entrada de Intentos
 *    enriquecida (claves de la respuesta + status/is_active/
 *    disabled_date) para confirmarlo en un toque.
 *  - v0.8.4: sonda precisa para el cambio de servidor (ver
 *    API-INSPECTION.md «Ronda 5»): el POST …/reassign-host/ {host_id}
 *    respondía 200 pero el túnel aterrizaba en OTRO host (43→
 *    S3-ATLANTA, 47→S1-CUBA, 41→S3-CUBA) — sale de la app y la vía
 *    que se prueba es el PATCH del detalle con {"host": <id>}, el
 *    mecanismo del formulario Configure de la web. Y el registro
 *    aprende a discriminar: la entrada «Servidores del panel» anota
 *    los pares id=nombre de todos los servidores, y la escritura
 *    anota lo seleccionado (nombre e id) y el host resultante de la
 *    respuesta. Todo lo demás queda como en la 0.8.3.
 *  - v0.8.5: DOS correcciones con la evidencia de la ronda 6 y el
 *    pedido directo de Gennis («los cambios se muestren inmediatos»):
 *    (1) CAMBIOS AL INSTANTE — la respuesta de cada escritura se
 *    fusiona en el item local y la vista se repinta al momento, con
 *    una lectura puntual del detalle que confirma el resultado real
 *    (antes todo esperaba la recarga completa del Home); al marcar
 *    pagado se recarga solo la matriz. (2) DESHABILITAR guiado por la
 *    propia API: {status:"disabled"} y {is_active:false} devolvieron
 *    200 sin cambiar nada, así que la app pregunta el esquema con
 *    OPTIONS y escribe el campo de estado que la API declare
 *    escribible; statusOf además manda disabled_date sobre el status
 *    de salida.
 *  - v0.8.6: deshabilitar/habilitar por disabled_date (ver la nota
 *    de toggleWrite): el OPTIONS del detalle no trae «actions»
 *    utilizables (ronda 7) y el respaldo {status:"inactive"} dio la
 *    pieza clave — 400 «no es una elección válida»: el status se
 *    valida al escribir («disabled» sí pasó en la ronda 4) pero el
 *    estado no se mueve, señal de que el modelo lo recalcula al
 *    guardar desde disabled_date. El OPTIONS queda como lectura
 *    diagnóstica registrada; ya no decide la escritura.
 *  - v0.8.7: ronda de ELIMINAR (autorizada por Gennis sobre los
 *    túneles desechables 558/559 que él creó en prueba_claude_3010
 *    al volver la creación del panel): Eliminar queda tal cual
 *    (DELETE al detalle con ?personal=false y su guarda de nombre)
 *    pero su aviso ya no dice que la creación está caída, porque
 *    volvió el 31/10 ~12:56. Y Deshabilitar/Habilitar dejan de
 *    llamar al OPTIONS (405: no existe; era un viaje inútil antes
 *    de cada escritura) — el intento disabled_date queda como
 *    marcador registrado hasta que el «dif de la web» revele el
 *    mecanismo real del interruptor.
 *  - v0.8.8: DESHABILITAR RESUELTO en el papel (ver la nota de
 *    toggleWrite): el «dif de la web» mostró que al apagar el 558
 *    en el panel el objeto queda status="disabled" con la
 *    disabled_date puesta por el servidor — la fecha es de salida
 *    y el campo escribible es «status». Deshabilitar/Habilitar
 *    vuelven a {"status":"disabled"} / {"status":"active"} (el
 *    payload de la ronda 4, ahora con la verificación al instante
 *    de la 0.8.5 para confirmarlo en el acto). La prueba de cierre
 *    va sobre el 558, que la web dejó deshabilitado: Habilitar y
 *    Deshabilitar desde la app, sin tocar servidores entre medias.
 *    (Cierre confirmado el mismo día, rondas 11–14: habilitar,
 *    deshabilitar, eliminar —204— y la nota de CUENTA, todo en vivo.)
 *  - v0.9.0: se cierra la parte WEB de la app (orden de Gennis:
 *    primero todo lo de la web, después lo local). NUEVO: botón
 *    «+ Crear» en Cuentas y en Túneles. Crear cuenta = POST
 *    documentado a la colección de usuarios con los campos del
 *    formulario web (usuario, contraseña inicial, alias, telegram,
 *    zona, notas, acceso habilitado). Crear túnel = POST documentado
 *    a la colección de túneles (propietario, nombre interno, nombre
 *    para el usuario, zona, protocolo WireGuard, pago manual,
 *    notas); el servidor se asigna solo y se cambia después, como
 *    manda la práctica de Gennis. Y «Restablecer contraseña» en el
 *    detalle de la cuenta (POST documentado …/reset-password/).
 *    Todo con el patrón seguro: un intento por toque, la respuesta
 *    entera en Intentos, recarga real al éxito. La prueba va sobre
 *    entidades desechables identificables; el DELETE de CUENTAS no
 *    se construye (la documentación dice que es en CASCADE).
 *  - v0.9.1: con la evidencia de la ronda 15 (ver API-INSPECTION.md):
 *    CREAR CUENTA quedó confirmado tal cual (201, id 278). Crear
 *    túnel gana (1) validación de name_for_user ≤ 20 caracteres —
 *    el panel lo rechazó con ese detalle —, (2) selector de
 *    «Servidor inicial» que envía el campo «host»: sin zona ni
 *    host el panel respondió «Host no resuelto», y al elegir
 *    propietario la zona de su cuenta se hereda si trae id; y
 *    Restablecer contraseña gana el campo de contraseña nueva:
 *    el endpoint exige {"password"} (no genera una), así que la
 *    pone el admin en la propia hoja.
 *  - v0.9.2: crear túnel y reset CONFIRMADOS en la ronda 16
 *    (túnel 560 creado con host=49 → 201; reset → 200 «Contraseña
 *    actualizada correctamente.»). La ronda enseñó además que la
 *    zona sola NO resuelve el host y que el nombre interno solo
 *    admite letras, números y guiones (un 500 lo rechazó): el
 *    formulario ahora valida ambas cosas antes de enviar. Con
 *    esto la parte WEB queda cerrada al completo; sigue lo LOCAL.
 *  - v0.9.3: AUTOMÁTICO y LOTES, con el mecanismo leído del propio
 *    código JS del panel (ronda 17, a petición de Gennis): el
 *    automático de la web es la bandera «is_auto_generated» — en
 *    ese modo el formulario web NO envía host (por eso omitir el
 *    host sin la bandera dio «Host no resuelto»); en manual envía
 *    el host elegido. El formulario individual replica eso tal
 *    cual (y añade status:"active", como la web). Y selector de
 *    modo «Un túnel / Por lotes»: el lote hace POST a la ruta
 *    documentada bulk-create con el cuerpo del formulario web
 *    ({user, count, protocol, status, manual_payment:true,
 *    name_prefix, notes} + zone + host solo en manual); cantidad
 *    2–50 según la SPEC y el panel enumera desde el nombre base.
 *  - v0.9.5: pedidos de Gennis tras la ronda 20 — (1) el formulario
 *    de crear nace en AUTOMÁTICO y desaparece la opción «sin
 *    servidor elegido» del selector; (2) el selector de propietario
 *    gana BUSCADOR por usuario o alias; (3) el formulario de lote
 *    muestra por fin su campo de nota (existía en el estado y se
 *    enviaba, pero nunca se añadió a la vista) rotulado «Nota para
 *    todos los túneles del lote»; (4) el pool de servidores para
 *    crear pasa a ser la LISTA DEL PANEL (endpoint por túnel, en
 *    caché): la ronda 20 demostró que el respaldo «en uso» mezcla
 *    otro registro — eligió el id 17 «S2-Cuba» y la creación lo
 *    rechazó («no pertenece a las zonas asignadas a este
 *    reseller») —; el respaldo se mantiene solo si la lista aún no
 *    llegó, y el registro anota qué pool se usó.
 *  - v0.9.4: ronda 19 — el AUTOMÁTICO individual quedó confirmado
 *    (túnel 561: el panel asignó solo S1-CUBA) y los LOTES con
 *    servidor manual también (201: «lote-1» y «lote-2» en
 *    S1-ATLANTA). Pero el automático de LOTES está roto EN EL
 *    PANEL: bulk-create sin host devuelve su error interno
 *    QuerySet, con y sin zona (la web manda el mismo cuerpo: es
 *    fallo del backend). Arreglo del lado de la app: en el lote,
 *    «Automático» lo resuelve la propia app eligiendo el
 *    servidor con menos túneles entre los cargados y enviándolo
 *    explícito, rotulado como lo que es en el formulario y en el
 *    registro. El automático individual no se toca (es el del
 *    panel y funciona).
 *
 * Cero datos simulados: cada número del Home se deriva de una respuesta real
 * o se muestra como «—»/«sin datos», con el porqué en el Diagnóstico API.
 */
public class MainActivity extends Activity {

    private enum State { LOGIN, LOCK, SESSION, HOME, CUENTAS, TUNELES, PAGOS, DIAG }

    // Paleta del prototipo.
    private static final int BG = Color.parseColor("#070F1E");
    private static final int CARD = Color.parseColor("#111E33");
    private static final int BORDER = Color.parseColor("#23365A");
    private static final int CYAN = Color.parseColor("#22D3EE");
    private static final int TEXT = Color.parseColor("#F1F5F9");
    private static final int TEXT2 = Color.parseColor("#94A3B8");
    private static final int GREEN = Color.parseColor("#4ADE80");
    private static final int AMBER = Color.parseColor("#F5B544");
    private static final int RED = Color.parseColor("#F87171");

    private SessionStore store;
    private SessionCapture capture;
    private WebView webView;

    private LinearLayout loginContainer;
    private LinearLayout lockContainer;
    private LinearLayout sessionContainer;
    private LinearLayout homeContainer;
    private LinearLayout diagContainer;
    private TextView loginStatus;
    private TextView lockStatus;
    private TextView txtCountdown;
    private LinearLayout claimsContainer;
    private Button btnBackToSession;

    // Home
    private TextView txtHomeUser;
    private TextView txtHomeSub;
    private TextView cardCuentasValue;
    private TextView cardCuentasSub;
    private TextView cardTunelesValue;
    private TextView cardTunelesSub;
    private TextView txtRate;
    private TextView txtFactTitle;
    private LinearLayout factRows;
    private TextView txtUpdated;
    private LinearLayout diagList;

    // Contenedor principal con barra de pestañas (Inicio · Cuentas · Túneles · Pagos)
    private LinearLayout mainContainer;
    private LinearLayout accountsContainer;
    private LinearLayout tunnelsContainer;
    private LinearLayout paymentsContainer;
    private TextView tabInicio;
    private TextView tabCuentas;
    private TextView tabTuneles;
    private TextView tabPagos;
    private LinearLayout accountsList;
    private LinearLayout tunnelsList;
    private LinearLayout pagosList;
    private Button[] accountsFilterBtns;
    private Button[] tunnelsFilterBtns;
    private String accountsFilter = "ALL";
    private String tunnelsFilter = "ALL";
    private String accountsQuery = "";
    private String tunnelsQuery = "";

    // Datos reales de la última carga del Home: alimentan las vistas
    // Cuentas/Túneles y el diagnóstico completo, sin llamadas nuevas.
    private JSONArray dataUsers;
    private Integer dataUsersCount;
    private JSONArray dataTunnels;
    private String dataTunnelsScope;
    private JSONArray dataMatrix;
    private String dataSessionBody;

    // Hoja de detalle de solo lectura (cuenta o túnel)
    private LinearLayout detailContainer;
    private TextView detailTitle;
    private LinearLayout detailBody;
    private boolean detailOpen = false;
    // Sonda de detalle de túnel (v0.7.0): caja donde la lectura en segundo
    // plano del GET /api/v1/tunnels/<id>/ añade los campos nuevos, y
    // guarda de generación para no pintar si la hoja ya cambió de túnel.
    private LinearLayout probeBox;
    private long probeTunnelId = -1;
    private int probeGen = 0;

    // Hoja «Marcar pagado» (la primera escritura de la app)
    private LinearLayout sheetContainer;
    private LinearLayout sheetBody;
    private Button sheetConfirmBtn;
    private Button sheetCancelBtn;
    private boolean sheetOpen = false;
    private boolean writeInFlight = false;
    private long sheetTunnelId = -1;
    private String sheetTunnelName;
    private String sheetUserName;
    private int[][] sheetOptions;      // [4][2] = {mes, año}: anterior, actual, +1, +2
    private boolean[] sheetSelected;
    private TextView sheetMonthsHeader;
    private LinearLayout sheetMonthsBox;
    private TextView[] sheetMonthRows;
    private EditText sheetNoteInput;
    private TextView sheetError;
    private TextView sheetStatus;

    // Menú de acciones del túnel (v0.6.0): lista de opciones sobre la vista.
    private LinearLayout menuContainer;
    private TextView menuTitle;
    private LinearLayout menuBody;
    private boolean menuOpen = false;

    // Hoja de acción genérica (v0.6.0): confirmaciones, lista de servidores,
    // editor de nota, guarda de eliminar y resultado de la descarga.
    private LinearLayout actContainer;
    private TextView actTitle;
    private LinearLayout actBody;
    private TextView actStatus;
    private TextView actError;
    private Button actPrimaryBtn;
    private Button actExtraBtn;
    private Button actCancelBtn;
    private boolean actOpen = false;
    private boolean actBusy = false;

    // Túnel sobre el que actúa el menú/hoja abiertos.
    private JSONObject actTunnel;
    private Map<String, String> actOwnerMap;

    private State state = State.LOGIN;
    private boolean browsingMode = false; // true cuando se abrió el panel desde Mi sesión
    private boolean captureDone = false;  // ya se capturó en este ciclo de login
    private boolean lockPromptShown = false;
    private long sessionExpSec = -1;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private boolean homeLoading = false;
    private boolean homeLoaded = false;
    private Long rateValue = null;

    private final Runnable countdownTick = new Runnable() {
        @Override
        public void run() {
            if (state != State.SESSION) return;
            long remaining = sessionExpSec - nowSec();
            if (remaining <= 0) {
                enterSessionOrLogin();
                return;
            }
            txtCountdown.setText("El token de acceso expira en " + fmtDuration(remaining));
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new SessionStore(this);
        buildUi();
        setupWebView();
        capture = new SessionCapture(webView);

        // Estado inicial según la sesión guardada.
        JSONObject saved = loadSavedSession();
        if (saved != null) {
            JSONObject payload = JwtUtils.decodePayload(saved.optString("accessToken"));
            long exp = payload != null ? payload.optLong("exp", -1) : -1;
            if (exp > nowSec()) {
                showLock();
            } else if (saved.has("refreshToken")) {
                // Access vencido pero hay refresh: el cliente API intentará
                // renovarlo en la primera llamada del Home.
                showLock();
            } else {
                showLogin("La sesión guardada expiró. Entra de nuevo en el panel para renovarla.");
            }
        } else {
            showLogin(null);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (state == State.SESSION) {
            handler.removeCallbacks(countdownTick);
            handler.post(countdownTick);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(countdownTick);
    }

    @Override
    public void onBackPressed() {
        if (actOpen) {
            if (!actBusy) closeAct();
            return;
        }
        if (menuOpen) {
            closeMenu();
            return;
        }
        if (sheetOpen) {
            if (!writeInFlight) closeMarkSheet();
            return;
        }
        if (detailOpen) {
            closeDetail();
            return;
        }
        if (state == State.DIAG) {
            showHomeScreen();
            return;
        }
        if (state == State.SESSION) {
            showHomeScreen();
            return;
        }
        if (state == State.CUENTAS || state == State.TUNELES || state == State.PAGOS) {
            showMain(0);
            return;
        }
        if (state == State.LOGIN && browsingMode) {
            browsingMode = false;
            showSessionFromStore();
            return;
        }
        super.onBackPressed();
    }

    // ---------------- UI programática (sin recursos ni librerías) ----------------

    private void buildUi() {
        // Raíz FrameLayout (v0.6.1): los contenedores se apilan en Z por orden
        // de inserción — main como base y detail/sheet/menu/act como overlays
        // ENCIMA. Con el antiguo LinearLayout vertical, los overlays nacían
        // apilados DEBAJO de mainContainer, fuera de pantalla.
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);

        buildLoginUi(root);
        buildLockUi(root);
        buildSessionUi(root);
        buildMainUi(root);
        buildDiagUi(root);
        buildDetailUi(root);
        buildSheetUi(root);
        buildMenuUi(root);
        buildActionUi(root);

        setContentView(root);
    }

    private void buildLoginUi(FrameLayout root) {
        loginContainer = new LinearLayout(this);
        loginContainer.setOrientation(LinearLayout.VERTICAL);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(20), dp(18), dp(20), dp(10));
        TextView title = new TextView(this);
        title.setText("Admin 3FORCE");
        title.setTextColor(CYAN);
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        TextView host = new TextView(this);
        host.setText(PanelConfig.HOST);
        host.setTextColor(TEXT2);
        host.setTextSize(13);
        header.addView(title);
        header.addView(host);
        loginContainer.addView(header);

        btnBackToSession = makeButton("← Volver a la sesión");
        btnBackToSession.setVisibility(View.GONE);
        btnBackToSession.setOnClickListener(v -> {
            browsingMode = false;
            showSessionFromStore();
        });
        LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        backLp.setMargins(dp(16), 0, dp(16), dp(8));
        loginContainer.addView(btnBackToSession, backLp);

        loginStatus = new TextView(this);
        loginStatus.setTextColor(TEXT);
        loginStatus.setTextSize(13);
        loginStatus.setPadding(dp(20), dp(4), dp(20), dp(8));
        loginStatus.setVisibility(View.GONE);
        loginContainer.addView(loginStatus);

        webView = new WebView(this);
        loginContainer.addView(webView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(loginContainer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void buildLockUi(FrameLayout root) {
        lockContainer = new LinearLayout(this);
        lockContainer.setOrientation(LinearLayout.VERTICAL);
        lockContainer.setGravity(Gravity.CENTER_HORIZONTAL);
        lockContainer.setPadding(dp(24), dp(64), dp(24), dp(24));
        TextView lockTitle = new TextView(this);
        lockTitle.setText("Sesión guardada");
        lockTitle.setTextColor(TEXT);
        lockTitle.setTextSize(20);
        lockTitle.setTypeface(Typeface.DEFAULT_BOLD);
        lockContainer.addView(lockTitle);
        TextView lockHint = new TextView(this);
        lockHint.setText("La app está bloqueada. Desbloquea para entrar.");
        lockHint.setTextColor(TEXT2);
        lockHint.setTextSize(13);
        lockHint.setPadding(0, dp(8), 0, dp(16));
        lockContainer.addView(lockHint);
        lockStatus = new TextView(this);
        lockStatus.setTextColor(TEXT);
        lockStatus.setTextSize(13);
        lockStatus.setVisibility(View.GONE);
        lockStatus.setPadding(0, 0, 0, dp(12));
        lockContainer.addView(lockStatus);
        Button btnUnlock = makeButton("Reintentar");
        btnUnlock.setOnClickListener(v -> promptUnlock());
        lockContainer.addView(btnUnlock, fullWidthLp());
        Button btnLockLogout = makeButton("Cerrar sesión");
        btnLockLogout.setOnClickListener(v -> logout());
        LinearLayout.LayoutParams ll = fullWidthLp();
        ll.topMargin = dp(10);
        lockContainer.addView(btnLockLogout, ll);
        lockContainer.setVisibility(View.GONE);
        root.addView(lockContainer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void buildSessionUi(FrameLayout root) {
        sessionContainer = new LinearLayout(this);
        sessionContainer.setOrientation(LinearLayout.VERTICAL);
        sessionContainer.setPadding(dp(20), dp(20), dp(20), dp(16));
        TextView sessionTitle = new TextView(this);
        sessionTitle.setText("Sesión activa");
        sessionTitle.setTextColor(CYAN);
        sessionTitle.setTextSize(20);
        sessionTitle.setTypeface(Typeface.DEFAULT_BOLD);
        sessionContainer.addView(sessionTitle);
        txtCountdown = new TextView(this);
        txtCountdown.setTextColor(TEXT2);
        txtCountdown.setTextSize(13);
        txtCountdown.setPadding(0, dp(6), 0, dp(12));
        sessionContainer.addView(txtCountdown);

        ScrollView scroll = new ScrollView(this);
        claimsContainer = new LinearLayout(this);
        claimsContainer.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(claimsContainer);
        sessionContainer.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Button btnHome = makeButton("← Volver al inicio");
        btnHome.setOnClickListener(v -> showHomeScreen());
        sessionContainer.addView(btnHome, fullWidthLp());
        Button btnOpenPanel = makeButton("Abrir panel");
        btnOpenPanel.setOnClickListener(v -> openPanel());
        LinearLayout.LayoutParams op = fullWidthLp();
        op.topMargin = dp(10);
        sessionContainer.addView(btnOpenPanel, op);
        Button btnLogout = makeButton("Cerrar sesión");
        btnLogout.setOnClickListener(v -> logout());
        LinearLayout.LayoutParams lo = fullWidthLp();
        lo.topMargin = dp(10);
        sessionContainer.addView(btnLogout, lo);
        sessionContainer.setVisibility(View.GONE);
        root.addView(sessionContainer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void buildHomeUi(LinearLayout root) {
        homeContainer = new LinearLayout(this);
        homeContainer.setOrientation(LinearLayout.VERTICAL);

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(18), dp(16), dp(12));

        txtHomeUser = new TextView(this);
        txtHomeUser.setText("Sesión activa");
        txtHomeUser.setTextColor(TEXT);
        txtHomeUser.setTextSize(20);
        txtHomeUser.setTypeface(Typeface.DEFAULT_BOLD);
        content.addView(txtHomeUser);
        txtHomeSub = new TextView(this);
        txtHomeSub.setText(PanelConfig.HOST);
        txtHomeSub.setTextColor(TEXT2);
        txtHomeSub.setTextSize(13);
        txtHomeSub.setPadding(0, dp(4), 0, dp(14));
        content.addView(txtHomeSub);

        // Cards Cuentas / Túneles lado a lado.
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView[] cuentasOut = new TextView[2];
        LinearLayout cardCuentas = makeStatCard("CUENTAS", cuentasOut);
        cardCuentasValue = cuentasOut[0];
        cardCuentasSub = cuentasOut[1];
        TextView[] tunelesOut = new TextView[2];
        LinearLayout cardTuneles = makeStatCard("TÚNELES", tunelesOut);
        cardTunelesValue = tunelesOut[0];
        cardTunelesSub = tunelesOut[1];
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(cardCuentas, half);
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        half2.leftMargin = dp(10);
        row.addView(cardTuneles, half2);
        content.addView(row);

        // Tira de la tasa.
        LinearLayout rateCard = makeCardBox();
        rateCard.setPadding(dp(14), dp(12), dp(14), dp(12));
        txtRate = new TextView(this);
        txtRate.setText("Tasa: cargando…");
        txtRate.setTextColor(TEXT);
        txtRate.setTextSize(15);
        rateCard.addView(txtRate);
        TextView rateHint = new TextView(this);
        rateHint.setText("Tocar para copiar el mensaje de la tasa");
        rateHint.setTextColor(TEXT2);
        rateHint.setTextSize(11);
        rateHint.setPadding(0, dp(4), 0, 0);
        rateCard.addView(rateHint);
        rateCard.setOnClickListener(v -> copyRateMessage());
        LinearLayout.LayoutParams rateLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rateLp.topMargin = dp(12);
        content.addView(rateCard, rateLp);

        // Card de Facturación.
        LinearLayout factCard = makeCardBox();
        factCard.setPadding(dp(14), dp(12), dp(14), dp(12));
        txtFactTitle = new TextView(this);
        txtFactTitle.setText("Facturación");
        txtFactTitle.setTextColor(TEXT);
        txtFactTitle.setTextSize(14);
        txtFactTitle.setTypeface(Typeface.DEFAULT_BOLD);
        factCard.addView(txtFactTitle);
        factRows = new LinearLayout(this);
        factRows.setOrientation(LinearLayout.VERTICAL);
        factCard.addView(factRows);
        LinearLayout.LayoutParams factLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        factLp.topMargin = dp(12);
        content.addView(factCard, factLp);

        txtUpdated = new TextView(this);
        txtUpdated.setTextColor(TEXT2);
        txtUpdated.setTextSize(11);
        txtUpdated.setPadding(0, dp(12), 0, 0);
        content.addView(txtUpdated);

        scroll.addView(content);
        homeContainer.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.VERTICAL);
        buttons.setPadding(dp(16), dp(8), dp(16), dp(16));
        Button btnSession = makeButton("Mi sesión");
        btnSession.setOnClickListener(v -> enterSessionOrLogin());
        buttons.addView(btnSession, fullWidthLp());
        Button btnDiag = makeButton("Diagnóstico API");
        btnDiag.setOnClickListener(v -> showDiag());
        LinearLayout.LayoutParams bd = fullWidthLp();
        bd.topMargin = dp(10);
        buttons.addView(btnDiag, bd);
        homeContainer.addView(buttons);

        homeContainer.setVisibility(View.GONE);
        root.addView(homeContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    }

    /** Contenedor principal: Inicio + Cuentas + Túneles y la barra de pestañas. */
    private void buildMainUi(FrameLayout root) {
        mainContainer = new LinearLayout(this);
        mainContainer.setOrientation(LinearLayout.VERTICAL);

        buildHomeUi(mainContainer);
        buildAccountsUi(mainContainer);
        buildTunnelsUi(mainContainer);
        buildPaymentsUi(mainContainer);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setBackgroundColor(Color.parseColor("#0B1626"));
        tabs.setPadding(dp(8), dp(6), dp(8), dp(10));
        tabInicio = makeTab("Inicio");
        tabCuentas = makeTab("Cuentas");
        tabTuneles = makeTab("Túneles");
        tabPagos = makeTab("Pagos");
        tabInicio.setOnClickListener(v -> showMain(0));
        tabCuentas.setOnClickListener(v -> showMain(1));
        tabTuneles.setOnClickListener(v -> showMain(2));
        tabPagos.setOnClickListener(v -> showMain(3));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tabs.addView(tabInicio, tp);
        LinearLayout.LayoutParams tp2 = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tp2.leftMargin = dp(6);
        tabs.addView(tabCuentas, tp2);
        LinearLayout.LayoutParams tp3 = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tp3.leftMargin = dp(6);
        tabs.addView(tabTuneles, tp3);
        LinearLayout.LayoutParams tp4 = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tp4.leftMargin = dp(6);
        tabs.addView(tabPagos, tp4);
        mainContainer.addView(tabs);

        mainContainer.setVisibility(View.GONE);
        root.addView(mainContainer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private TextView makeTab(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(13);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(8), dp(9), dp(8), dp(9));
        return t;
    }

    private void refreshTabStyles(int active) {
        TextView[] tabs = {tabInicio, tabCuentas, tabTuneles, tabPagos};
        for (int i = 0; i < tabs.length; i++) {
            boolean sel = i == active;
            tabs[i].setTextColor(sel ? CYAN : TEXT2);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(sel ? CARD : Color.TRANSPARENT);
            bg.setCornerRadius(dp(10));
            if (sel) bg.setStroke(dp(1), BORDER);
            tabs[i].setBackground(bg);
        }
    }

    private EditText makeSearch(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextColor(TEXT);
        e.setHintTextColor(TEXT2);
        e.setTextSize(13);
        e.setSingleLine(true);
        e.setPadding(dp(12), dp(9), dp(12), dp(9));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(10));
        bg.setStroke(dp(1), BORDER);
        e.setBackground(bg);
        return e;
    }

    private Button makeFilterButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(11);
        b.setAllCaps(false);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(10), dp(5), dp(10), dp(5));
        return b;
    }

    private void refreshFilterStyles(Button[] btns, int selected) {
        if (btns == null) return;
        for (int i = 0; i < btns.length; i++) {
            boolean sel = i == selected;
            btns[i].setTextColor(sel ? CYAN : TEXT2);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(CARD);
            bg.setCornerRadius(dp(16));
            bg.setStroke(dp(1), sel ? CYAN : BORDER);
            btns[i].setBackground(bg);
        }
    }

    private interface FilterCallback { void onFilter(int idx); }
    private interface QueryCallback { void onQuery(String q); }

    private LinearLayout makeListHeader(String title, String searchHint,
                                        String[] filterLabels,
                                        final FilterCallback onFilter,
                                        final QueryCallback onQuery,
                                        Button[][] btnsOut, LinearLayout[] listOut,
                                        final Runnable createAction) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(16), dp(18), dp(16), dp(6));
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextColor(TEXT);
        tvTitle.setTextSize(20);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        titleRow.addView(tvTitle, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (createAction != null) {
            Button btnCreate = makeButton("+ Crear");
            btnCreate.setTextSize(12);
            btnCreate.setPadding(dp(12), dp(7), dp(12), dp(7));
            btnCreate.setOnClickListener(v -> createAction.run());
            titleRow.addView(btnCreate);
        }
        header.addView(titleRow);
        EditText search = makeSearch(searchHint);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sLp.topMargin = dp(12);
        header.addView(search, sLp);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                onQuery.onQuery(s != null ? s.toString() : "");
            }
        });
        LinearLayout filterRow = new LinearLayout(this);
        filterRow.setOrientation(LinearLayout.HORIZONTAL);
        filterRow.setPadding(0, dp(10), 0, dp(4));
        Button[] btns = new Button[filterLabels.length];
        for (int i = 0; i < filterLabels.length; i++) {
            final int idx = i;
            btns[i] = makeFilterButton(filterLabels[i]);
            btns[i].setOnClickListener(v -> onFilter.onFilter(idx));
            LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) bLp.leftMargin = dp(6);
            filterRow.addView(btns[i], bLp);
        }
        header.addView(filterRow);
        container.addView(header);
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(16), dp(2), dp(16), dp(16));
        scroll.addView(list);
        container.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        btnsOut[0] = btns;
        listOut[0] = list;
        container.setVisibility(View.GONE);
        return container;
    }

    private void buildAccountsUi(LinearLayout parent) {
        Button[][] btnsOut = new Button[1][];
        LinearLayout[] listOut = new LinearLayout[1];
        accountsContainer = makeListHeader("Cuentas",
                "Buscar por usuario, alias o nombre…",
                new String[]{"Todas", "Con túneles", "Sin túneles", "Deshabilitadas"},
                idx -> {
                    accountsFilter = new String[]{"ALL", "CON", "SIN", "DESH"}[idx];
                    renderAccounts();
                },
                q -> {
                    accountsQuery = q;
                    renderAccounts();
                },
                btnsOut, listOut,
                () -> openCreateAccount(new NewAccountForm()));
        accountsFilterBtns = btnsOut[0];
        accountsList = listOut[0];
        parent.addView(accountsContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    }

    private void buildTunnelsUi(LinearLayout parent) {
        Button[][] btnsOut = new Button[1][];
        LinearLayout[] listOut = new LinearLayout[1];
        tunnelsContainer = makeListHeader("Túneles",
                "Buscar por nombre, cliente o propietario…",
                new String[]{"Todos", "Activos", "Inactivos"},
                idx -> {
                    tunnelsFilter = new String[]{"ALL", "ACT", "INACT"}[idx];
                    renderTunnels();
                },
                q -> {
                    tunnelsQuery = q;
                    renderTunnels();
                },
                btnsOut, listOut,
                () -> openCreateTunnel(new NewTunnelForm()));
        tunnelsFilterBtns = btnsOut[0];
        tunnelsList = listOut[0];
        parent.addView(tunnelsContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    }

    /** Vista Pagos: secciones Pendientes / Pagados / Recibidos, desde la matriz. */
    private void buildPaymentsUi(LinearLayout parent) {
        paymentsContainer = new LinearLayout(this);
        paymentsContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(16), dp(18), dp(16), dp(6));
        TextView tvTitle = new TextView(this);
        tvTitle.setText("Pagos");
        tvTitle.setTextColor(TEXT);
        tvTitle.setTextSize(20);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(tvTitle);
        paymentsContainer.addView(header);
        ScrollView scroll = new ScrollView(this);
        pagosList = new LinearLayout(this);
        pagosList.setOrientation(LinearLayout.VERTICAL);
        pagosList.setPadding(dp(16), dp(2), dp(16), dp(16));
        scroll.addView(pagosList);
        paymentsContainer.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        paymentsContainer.setVisibility(View.GONE);
        parent.addView(paymentsContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    }

    /** Hoja de detalle de solo lectura, encima de cualquier vista principal. */
    private void buildDetailUi(FrameLayout root) {
        detailContainer = new LinearLayout(this);
        detailContainer.setOrientation(LinearLayout.VERTICAL);
        detailContainer.setBackgroundColor(BG);
        detailContainer.setPadding(dp(20), dp(20), dp(20), dp(16));
        detailTitle = new TextView(this);
        detailTitle.setTextColor(CYAN);
        detailTitle.setTextSize(20);
        detailTitle.setTypeface(Typeface.DEFAULT_BOLD);
        detailContainer.addView(detailTitle);
        ScrollView scroll = new ScrollView(this);
        detailBody = new LinearLayout(this);
        detailBody.setOrientation(LinearLayout.VERTICAL);
        detailBody.setPadding(0, dp(10), 0, dp(10));
        scroll.addView(detailBody);
        detailContainer.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        Button btnClose = makeButton("← Volver");
        btnClose.setOnClickListener(v -> closeDetail());
        detailContainer.addView(btnClose, fullWidthLp());
        detailContainer.setVisibility(View.GONE);
        root.addView(detailContainer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /**
     * Hoja «Marcar pagado»: contenedor fijo (título, cuerpo dinámico y los
     * botones Confirmar/Cancelar) que se rellena en cada apertura. Queda por
     * encima de la hoja de detalle, para poder abrirla desde ella.
     */
    private void buildSheetUi(FrameLayout root) {
        sheetContainer = new LinearLayout(this);
        sheetContainer.setOrientation(LinearLayout.VERTICAL);
        sheetContainer.setBackgroundColor(BG);
        sheetContainer.setPadding(dp(20), dp(20), dp(20), dp(16));
        TextView title = new TextView(this);
        title.setText("Marcar pagado");
        title.setTextColor(CYAN);
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        sheetContainer.addView(title);
        ScrollView scroll = new ScrollView(this);
        sheetBody = new LinearLayout(this);
        sheetBody.setOrientation(LinearLayout.VERTICAL);
        sheetBody.setPadding(0, dp(10), 0, dp(10));
        scroll.addView(sheetBody);
        sheetContainer.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        sheetConfirmBtn = makeButton("Confirmar pago");
        sheetConfirmBtn.setOnClickListener(v -> confirmMarkPaid());
        sheetContainer.addView(sheetConfirmBtn, fullWidthLp());
        sheetCancelBtn = makeButton("Cancelar");
        sheetCancelBtn.setOnClickListener(v -> {
            if (!writeInFlight) closeMarkSheet();
        });
        LinearLayout.LayoutParams cl = fullWidthLp();
        cl.topMargin = dp(10);
        sheetContainer.addView(sheetCancelBtn, cl);
        sheetContainer.setVisibility(View.GONE);
        root.addView(sheetContainer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void showDetail() {
        detailContainer.setVisibility(View.VISIBLE);
        detailOpen = true;
    }

    private void closeDetail() {
        detailContainer.setVisibility(View.GONE);
        detailOpen = false;
        probeGen++;
        probeTunnelId = -1;
    }

    private void detailRow(String label, String value) {
        detailBody.addView(makeDetailRow(label, value));
    }

    /** Fila etiqueta/valor de las hojas de detalle (reutilizable). */
    private LinearLayout makeDetailRow(String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(6), 0, dp(6));
        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextColor(TEXT2);
        tvLabel.setTextSize(13);
        row.addView(tvLabel, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0.9f));
        TextView tvValue = new TextView(this);
        tvValue.setText(value != null ? value : "—");
        tvValue.setTextColor(TEXT);
        tvValue.setTextSize(13);
        tvValue.setTextIsSelectable(true);
        row.addView(tvValue, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1.6f));
        return row;
    }

    /** Bloque de la nota completa (solo si hay nota real). */
    private void detailNotes(String notes) {
        if (notes == null) return;
        TextView tvLabel = new TextView(this);
        tvLabel.setText("Notas");
        tvLabel.setTextColor(TEXT2);
        tvLabel.setTextSize(13);
        tvLabel.setPadding(0, dp(12), 0, dp(4));
        detailBody.addView(tvLabel);
        LinearLayout box = makeCardBox();
        box.setPadding(dp(12), dp(10), dp(12), dp(10));
        TextView tvNotes = new TextView(this);
        tvNotes.setText(notes);
        tvNotes.setTextColor(TEXT);
        tvNotes.setTextSize(13);
        tvNotes.setTextIsSelectable(true);
        box.addView(tvNotes);
        detailBody.addView(box);
    }

    private void buildDiagUi(FrameLayout root) {
        diagContainer = new LinearLayout(this);
        diagContainer.setOrientation(LinearLayout.VERTICAL);
        diagContainer.setPadding(dp(20), dp(20), dp(20), dp(16));
        TextView title = new TextView(this);
        title.setText("Diagnóstico API");
        title.setTextColor(CYAN);
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        diagContainer.addView(title);
        TextView sub = new TextView(this);
        sub.setText("Llamadas reales de esta sesión: código y claves de la respuesta. "
                + "Sin valores de tokens. El copiado va por bloques: tras una ronda "
                + "de pruebas basta «intentos y escrituras»; la matriz y el general, "
                + "cuando hagan falta.");
        sub.setTextColor(TEXT2);
        sub.setTextSize(12);
        sub.setPadding(0, dp(6), 0, dp(10));
        diagContainer.addView(sub);

        // Copiado por bloques (v0.7.1): tres botones arriba, ninguno abajo.
        Button btnCopyAttempts = makeButton("Copiar intentos y escrituras");
        btnCopyAttempts.setOnClickListener(v -> copyAttempts());
        diagContainer.addView(btnCopyAttempts, fullWidthLp());
        Button btnCopyMatrix = makeButton("Copiar matriz");
        btnCopyMatrix.setOnClickListener(v -> copyMatrixDiag());
        LinearLayout.LayoutParams cmLp = fullWidthLp();
        cmLp.topMargin = dp(8);
        diagContainer.addView(btnCopyMatrix, cmLp);
        Button btnCopyGeneral = makeButton("Copiar diagnóstico general");
        btnCopyGeneral.setOnClickListener(v -> copyGeneralDiagnostic());
        LinearLayout.LayoutParams cgLp = fullWidthLp();
        cgLp.topMargin = dp(8);
        diagContainer.addView(btnCopyGeneral, cgLp);

        ScrollView scroll = new ScrollView(this);
        diagList = new LinearLayout(this);
        diagList.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(diagList);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        scrollLp.topMargin = dp(8);
        diagContainer.addView(scroll, scrollLp);

        Button btnBack = makeButton("← Volver");
        btnBack.setOnClickListener(v -> showHomeScreen());
        LinearLayout.LayoutParams backLp2 = fullWidthLp();
        backLp2.topMargin = dp(10);
        diagContainer.addView(btnBack, backLp2);
        diagContainer.setVisibility(View.GONE);
        root.addView(diagContainer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private LinearLayout makeCardBox() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), BORDER);
        card.setBackground(bg);
        return card;
    }

    /** Card de conteo: etiqueta pequeña, número grande y línea de detalle. */
    private LinearLayout makeStatCard(String label, TextView[] out) {
        LinearLayout card = makeCardBox();
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextColor(TEXT2);
        tvLabel.setTextSize(11);
        tvLabel.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(tvLabel);
        TextView tvValue = new TextView(this);
        tvValue.setText("…");
        tvValue.setTextColor(TEXT);
        tvValue.setTextSize(26);
        tvValue.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(tvValue);
        TextView tvSub = new TextView(this);
        tvSub.setText("Cargando…");
        tvSub.setTextColor(TEXT2);
        tvSub.setTextSize(12);
        card.addView(tvSub);
        out[0] = tvValue;
        out[1] = tvSub;
        return card;
    }

    private Button makeButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(TEXT);
        b.setTextSize(14);
        b.setAllCaps(false);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(10));
        bg.setStroke(dp(1), BORDER);
        b.setBackground(bg);
        return b;
    }

    private LinearLayout.LayoutParams fullWidthLp() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void hideAll() {
        loginContainer.setVisibility(View.GONE);
        lockContainer.setVisibility(View.GONE);
        sessionContainer.setVisibility(View.GONE);
        mainContainer.setVisibility(View.GONE);
        diagContainer.setVisibility(View.GONE);
        detailContainer.setVisibility(View.GONE);
        detailOpen = false;
        sheetContainer.setVisibility(View.GONE);
        sheetOpen = false;
        menuContainer.setVisibility(View.GONE);
        menuOpen = false;
        actContainer.setVisibility(View.GONE);
        actOpen = false;
        actBusy = false;
    }

    // ---------------- WebView / Login ----------------

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(webView, true);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setLoadsImagesAutomatically(true);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                maybeCapture(url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                maybeCapture(url);
            }

            @Override
            public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                maybeCapture(url);
            }
        });
    }

    /** Si ya salimos de /auth/login (y no estamos navegando desde Mi sesión), busca el JWT. */
    private void maybeCapture(String url) {
        if (url == null || state != State.LOGIN || browsingMode || captureDone) return;
        Uri uri;
        try {
            uri = Uri.parse(url);
        } catch (Exception e) {
            return;
        }
        if (!PanelConfig.HOST.equals(uri.getHost())) return;
        String path = uri.getPath();
        if (path != null && path.contains(PanelConfig.LOGIN_PATH)) return;
        // Varios intentos: el token puede escribirse un instante después de navegar.
        long[] delays = {0, 900, 2200};
        for (long delay : delays) {
            handler.postDelayed(() -> {
                if (state == State.LOGIN && !browsingMode && !captureDone) {
                    capture.attempt(sessionJson -> {
                        captureDone = true;
                        store.save(sessionJson.toString());
                        Toast.makeText(MainActivity.this,
                                "Sesión detectada y guardada de forma cifrada",
                                Toast.LENGTH_LONG).show();
                        enterApp();
                    });
                }
            }, delay);
        }
    }

    private void showLogin(String statusMsg) {
        state = State.LOGIN;
        handler.removeCallbacks(countdownTick);
        browsingMode = false;
        btnBackToSession.setVisibility(View.GONE);
        hideAll();
        loginContainer.setVisibility(View.VISIBLE);
        if (statusMsg != null) {
            loginStatus.setText(statusMsg);
            loginStatus.setVisibility(View.VISIBLE);
        } else {
            loginStatus.setVisibility(View.GONE);
        }
        if (webView.getUrl() == null || statusMsg != null) {
            webView.loadUrl(PanelConfig.LOGIN_URL);
        }
    }

    private void openPanel() {
        browsingMode = true;
        state = State.LOGIN;
        handler.removeCallbacks(countdownTick);
        btnBackToSession.setVisibility(View.VISIBLE);
        loginStatus.setVisibility(View.GONE);
        hideAll();
        loginContainer.setVisibility(View.VISIBLE);
        webView.loadUrl(PanelConfig.BASE_URL);
    }

    // ---------------- Bloqueo de la app ----------------

    private void showLock() {
        // La Lite solo bloquea en API 28+ (framework BiometricPrompt).
        // En API 21–27, o si el teléfono no tiene con qué bloquear, se entra directo.
        if (Build.VERSION.SDK_INT < 28 || !BiometricLock.canLock(this)) {
            enterApp();
            return;
        }
        state = State.LOCK;
        hideAll();
        lockContainer.setVisibility(View.VISIBLE);
        lockStatus.setVisibility(View.GONE);
        if (!lockPromptShown) {
            lockPromptShown = true;
            promptUnlock();
        }
    }

    private void promptUnlock() {
        if (Build.VERSION.SDK_INT < 28) {
            enterApp();
            return;
        }
        BiometricLock.prompt(this, new BiometricLock.Callback() {
            @Override
            public void onSuccess() {
                enterApp();
            }

            @Override
            public void onError(String message) {
                // Pantalla bloqueada: el mensaje queda visible y «Reintentar» reabre el prompt.
                lockStatus.setText(message);
                lockStatus.setVisibility(View.VISIBLE);
            }
        });
    }

    /** Entrada a la app con sesión válida: el Home es la pantalla principal. */
    private void enterApp() {
        showHomeScreen();
        if (!homeLoaded && !homeLoading) loadHomeData();
    }

    private void showHomeScreen() {
        showMain(0);
    }

    /** Muestra el contenedor principal: 0 Inicio, 1 Cuentas, 2 Túneles, 3 Pagos. */
    private void showMain(int tab) {
        handler.removeCallbacks(countdownTick);
        hideAll();
        mainContainer.setVisibility(View.VISIBLE);
        homeContainer.setVisibility(tab == 0 ? View.VISIBLE : View.GONE);
        accountsContainer.setVisibility(tab == 1 ? View.VISIBLE : View.GONE);
        tunnelsContainer.setVisibility(tab == 2 ? View.VISIBLE : View.GONE);
        paymentsContainer.setVisibility(tab == 3 ? View.VISIBLE : View.GONE);
        state = tab == 0 ? State.HOME : tab == 1 ? State.CUENTAS
                : tab == 2 ? State.TUNELES : State.PAGOS;
        refreshTabStyles(tab);
        if (tab == 1) renderAccounts();
        if (tab == 2) renderTunnels();
        if (tab == 3) renderPagos();
    }

    // ---------------- HOME: carga de datos reales ----------------

    private void loadHomeData() {
        if (homeLoading) return;
        homeLoading = true;
        cardCuentasValue.setText("…");
        cardCuentasSub.setText("Cargando…");
        cardTunelesValue.setText("…");
        cardTunelesSub.setText("Cargando…");
        txtRate.setText("Tasa: cargando…");
        txtFactTitle.setText("Facturación · " + currentMonthLabel());
        factRows.removeAllViews();
        addFactRow("Estado", "Cargando…", TEXT2);
        txtUpdated.setText("");

        exec.execute(() -> {
            ApiClient api = new ApiClient(store);

            // 1) Sesión por POST (el GET dio 405 en la prueba real: el
            // endpoint es POST). Usuario/rol solo si la API los devuelve.
            ApiClient.Result rs = api.post("Sesión", PanelConfig.SESSION_URL, "{}");
            if (abortIfAuthFailed(rs.authFailed)) return;
            if (rs.error == null && rs.code >= 200 && rs.code < 300) {
                dataSessionBody = rs.body;
                String sdiag = HomeStats.sessionUserDiag(rs.body);
                if (sdiag != null) ApiDiag.annotate("Sesión", sdiag);
                String stdiag = HomeStats.sessionStatsDiag(rs.body);
                if (stdiag != null) ApiDiag.annotate("Sesión", stdiag);
                String[] ur = HomeStats.sessionUser(rs.body);
                handler.post(() -> {
                    if (ur[0] != null) txtHomeUser.setText(ur[0]);
                    txtHomeSub.setText(ur[1] != null
                            ? ur[1] + " · " + PanelConfig.HOST
                            : PanelConfig.HOST);
                });
            }

            // 2) Usuarios → card Cuentas.
            ApiClient.ItemsResult ru = api.getAllItems("Usuarios", PanelConfig.USERS_URL);
            if (abortIfAuthFailed(ru.authFailed)) return;
            if (ru.items.length() > 0) {
                dataUsers = ru.items;
                dataUsersCount = ru.count;
            }
            if (ru.items.length() > 0 || ru.error == null) {
                HomeStats.UsersStats us = HomeStats.users(ru.items, ru.count);
                ApiDiag.annotate("Usuarios", us.note);
                handler.post(() -> {
                    cardCuentasValue.setText(String.valueOf(us.total));
                    if (us.conTuneles != null) {
                        cardCuentasSub.setText("Con túneles " + us.conTuneles
                                + " · Sin túneles " + us.sinTuneles);
                    } else {
                        cardCuentasSub.setText("Con/Sin túneles: —");
                    }
                });
            } else {
                handler.post(() -> {
                    cardCuentasValue.setText("sin datos");
                    cardCuentasSub.setText(ru.error != null ? ru.error : "");
                });
            }

            // 3) Túneles en DOS alcances (prueba real: ?personal=true trae
            // solo los 3 propios del reseller; los de los clientes vienen en
            // el otro alcance). La card usa el alcance con MÁS items y el
            // Diagnóstico anota cuál se usó; cada alcance tiene su entrada.
            ApiClient.ItemsResult rtT = api.getAllItems(
                    "Túneles (personal=true)", PanelConfig.TUNNELS_URL);
            ApiClient.ItemsResult rtF = api.getAllItems(
                    "Túneles (personal=false)", PanelConfig.TUNNELS_URL_ALL);
            if (rtT.authFailed && rtF.authFailed) {
                abortIfAuthFailed(true);
                return;
            }
            boolean useFalse = rtF.items.length() > rtT.items.length();
            ApiClient.ItemsResult rt = useFalse ? rtF : rtT;
            String scopeLabel = useFalse
                    ? "Túneles (personal=false)" : "Túneles (personal=true)";
            if (rt.items.length() > 0) {
                dataTunnels = rt.items;
                dataTunnelsScope = useFalse ? "personal=false" : "personal=true";
            }
            HomeStats.TunnelsStats ts = null;
            if (rt.items.length() > 0 || rt.error == null) {
                ts = HomeStats.tunnels(rt.items, rt.count);
                ApiDiag.annotate(scopeLabel, ts.note + " · alcance usado por el Home");
                final HomeStats.TunnelsStats fts = ts;
                handler.post(() -> {
                    cardTunelesValue.setText(String.valueOf(fts.total));
                    if (fts.activos != null) {
                        cardTunelesSub.setText("Activos " + fts.activos
                                + " · Inactivos " + fts.inactivos);
                    } else {
                        cardTunelesSub.setText("Activos/Inactivos: —");
                    }
                });
            } else {
                handler.post(() -> {
                    cardTunelesValue.setText("sin datos");
                    cardTunelesSub.setText(rt.error != null ? rt.error : "");
                });
            }

            // 4) Matriz → card Facturación (mes actual).
            ApiClient.ItemsResult rm = api.getAllItems("Matriz", PanelConfig.MATRIX_URL);
            if (abortIfAuthFailed(rm.authFailed)) return;
            Calendar cal = Calendar.getInstance();
            int year = cal.get(Calendar.YEAR);
            int month = cal.get(Calendar.MONTH) + 1;
            final HomeStats.TunnelsStats fts2 = ts;
            if (rm.items.length() > 0) dataMatrix = rm.items;
            if (rm.items.length() > 0 || rm.error == null) {
                HomeStats.MatrixStats ms = HomeStats.matrix(rm.items, year, month);
                ApiDiag.annotate("Matriz", ms.note);
                String mdiag = HomeStats.matrixDiag(rm.items, year, month);
                if (!mdiag.isEmpty()) ApiDiag.annotate("Matriz", mdiag);
                handler.post(() -> fillFacturacion(ms, fts2));
            } else {
                handler.post(() -> {
                    factRows.removeAllViews();
                    addFactRow("Matriz", "sin datos (" + rm.error + ")", AMBER);
                    addFactRow("Automáticos", fts2 != null && fts2.automaticos != null
                            ? String.valueOf(fts2.automaticos) : "—", TEXT);
                    // Manuales por la lista de túneles SOLO si la matriz falló
                    // (si la matriz respondió, los manuales son sus filas).
                    if (fts2 != null && fts2.manuales != null) {
                        addFactRow("Manuales (lista de túneles)",
                                String.valueOf(fts2.manuales), TEXT);
                    }
                });
            }

            // 5) Tasa USDT (sin auth).
            ApiClient.Result rr = api.getPublic("Tasa", PanelConfig.RATE_URL);
            Long rate = (rr.error == null && rr.code >= 200 && rr.code < 300)
                    ? HomeStats.rateValue(rr.body) : null;
            rateValue = rate;
            handler.post(() -> txtRate.setText(rate != null
                    ? "1 USDT = " + fmtGrouped(rate) + " CUP"
                    : "Tasa no disponible"));

            handler.post(() -> {
                txtUpdated.setText("Datos actualizados automáticamente · "
                        + new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date()));
                homeLoaded = true;
                homeLoading = false;
                // Si el propietario ya está en Cuentas/Túneles/Pagos, la
                // lista se rellena con los datos que acaban de llegar.
                if (state == State.CUENTAS) renderAccounts();
                else if (state == State.TUNELES) renderTunnels();
                else if (state == State.PAGOS) renderPagos();
            });
        });
    }

    /** Si la API rechazó la sesión incluso tras el refresh, se vuelve al login. */
    private boolean abortIfAuthFailed(boolean authFailed) {
        if (!authFailed) return false;
        handler.post(() -> {
            homeLoading = false;
            homeLoaded = false;
            showLogin("La API rechazó la sesión guardada y no se pudo renovar. "
                    + "Entra de nuevo en el panel.");
        });
        return true;
    }

    private void fillFacturacion(HomeStats.MatrixStats ms, HomeStats.TunnelsStats ts) {
        factRows.removeAllViews();
        addFactRow("Túneles manuales (matriz)", String.valueOf(ms.filas), TEXT);
        addFactRow("Pagados del mes", ms.pagados != null
                ? String.valueOf(ms.pagados) : "—", ms.pagados != null ? GREEN : TEXT2);
        // Regla confirmada el 31/10: pendientes = celdas no pagadas + filas
        // sin celda del mes. La fila es TOCABLE: abre la vista Pagos, cuya
        // primera sección es exactamente esa lista (túnel + cuenta), para
        // que el número sea transparente.
        LinearLayout pendRow = addFactRow("Pendientes del mes",
                ms.pendientes != null ? ms.pendientes + "  ›" : "—",
                ms.pendientes != null ? AMBER : TEXT2);
        pendRow.setOnClickListener(v -> showMain(3));
        addFactRow("Automáticos", ts != null && ts.automaticos != null
                ? String.valueOf(ts.automaticos) : "—", TEXT);
    }

    private LinearLayout addFactRow(String label, String value, int valueColor) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(6), 0, dp(6));
        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextColor(TEXT2);
        tvLabel.setTextSize(13);
        row.addView(tvLabel, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f));
        TextView tvValue = new TextView(this);
        tvValue.setText(value);
        tvValue.setTextColor(valueColor);
        tvValue.setTextSize(13);
        tvValue.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(tvValue, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0.6f));
        factRows.addView(row);
        return row;
    }

    private String currentMonthLabel() {
        String s = new SimpleDateFormat("MMMM yyyy", new Locale("es", "ES"))
                .format(new Date());
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private String fmtGrouped(long v) {
        return String.format(Locale.US, "%,d", v).replace(",", " ");
    }

    /** Copia el mensaje completo de la tasa (plantilla de la spec) al portapapeles. */
    private void copyRateMessage() {
        if (rateValue == null) {
            Toast.makeText(this, "La tasa aún no está disponible", Toast.LENGTH_SHORT).show();
            return;
        }
        String fecha = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date());
        String hora = new SimpleDateFormat("h:mm:ss a", Locale.getDefault()).format(new Date());
        String msg = "💱 *TASA DE CAMBIO ACTUAL*\n"
                + "💵 *1 USDT = " + rateValue + " CUP*\n"
                + "> 📅 *Fecha:* " + fecha + "\n"
                + "> 🕐 *Hora:* " + hora + "\n"
                + "🔎 *Verificar tasa de cambio:*\n"
                + "> https://www.cambiocup.com/";
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("tasa", msg));
            Toast.makeText(this, "Mensaje de la tasa copiado", Toast.LENGTH_SHORT).show();
        }
    }

    // ---------------- Diagnóstico API ----------------

    private void addDiagSection(String[] section) {
        TextView tvTitle = new TextView(this);
        tvTitle.setText(section[0]);
        tvTitle.setTextColor(CYAN);
        tvTitle.setTextSize(14);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setPadding(0, dp(14), 0, dp(4));
        diagList.addView(tvTitle);
        TextView tvBody = new TextView(this);
        tvBody.setText(section[1]);
        tvBody.setTextColor(TEXT);
        tvBody.setTextSize(12);
        tvBody.setTextIsSelectable(true);
        diagList.addView(tvBody);
    }

    private void showDiag() {
        state = State.DIAG;
        hideAll();
        diagContainer.setVisibility(View.VISIBLE);
        diagList.removeAllViews();
        DiagFull full = buildDiagFull();
        addDiagSection(full.sectionApp());
        addDiagSection(full.sectionSavedSession());
        TextView callsTitle = new TextView(this);
        callsTitle.setText("Llamadas");
        callsTitle.setTextColor(CYAN);
        callsTitle.setTextSize(14);
        callsTitle.setTypeface(Typeface.DEFAULT_BOLD);
        callsTitle.setPadding(0, dp(14), 0, dp(2));
        diagList.addView(callsTitle);
        java.util.List<ApiDiag.Entry> entries = ApiDiag.entries();
        if (entries.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Todavía no hay llamadas registradas.");
            empty.setTextColor(TEXT2);
            empty.setTextSize(13);
            diagList.addView(empty);
        }
        for (ApiDiag.Entry e : entries) {
            LinearLayout block = new LinearLayout(this);
            block.setOrientation(LinearLayout.VERTICAL);
            block.setPadding(0, dp(8), 0, dp(8));
            TextView tvLabel = new TextView(this);
            tvLabel.setText(e.label + " · " + e.time);
            tvLabel.setTextColor(TEXT);
            tvLabel.setTextSize(13);
            tvLabel.setTypeface(Typeface.DEFAULT_BOLD);
            block.addView(tvLabel);
            TextView tvReq = new TextView(this);
            tvReq.setText(e.method + " " + e.url);
            tvReq.setTextColor(TEXT2);
            tvReq.setTextSize(11);
            block.addView(tvReq);
            TextView tvCode = new TextView(this);
            tvCode.setText(e.code > 0 ? "HTTP " + e.code + " · " + e.ms + " ms"
                    : "Sin respuesta · " + e.ms + " ms");
            tvCode.setTextColor(e.code >= 200 && e.code < 300 ? GREEN : AMBER);
            tvCode.setTextSize(11);
            block.addView(tvCode);
            TextView tvDetail = new TextView(this);
            tvDetail.setText(e.detail);
            tvDetail.setTextColor(TEXT);
            tvDetail.setTextSize(12);
            tvDetail.setTextIsSelectable(true);
            block.addView(tvDetail);
            diagList.addView(block);
        }
        addDiagSection(full.sectionSessionDetail());
        addDiagSection(full.sectionUsers());
        addDiagSection(full.sectionTunnels());
        addDiagSection(full.sectionMatrix());
    }

    // ---------------- Diagnóstico por bloques (copiable) ----------------

    private String[] appVersion() {
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            return new String[]{pi.versionName, String.valueOf(pi.versionCode)};
        } catch (Exception e) {
            return new String[]{"?", "?"};
        }
    }

    private DiagFull buildDiagFull() {
        String[] v = appVersion();
        Calendar cal = Calendar.getInstance();
        return new DiagFull(v[0], v[1],
                "API " + Build.VERSION.SDK_INT + " (Android " + Build.VERSION.RELEASE + ")",
                loadSavedSession(), dataSessionBody,
                dataUsers, dataUsersCount,
                dataTunnels, dataTunnelsScope,
                dataMatrix,
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1);
    }

    private void copyToClipboard(String clipLabel, String text, String toast) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null) {
            Toast.makeText(this, "Portapapeles no disponible", Toast.LENGTH_SHORT).show();
            return;
        }
        cm.setPrimaryClip(ClipData.newPlainText(clipLabel, text));
        Toast.makeText(this, toast, Toast.LENGTH_SHORT).show();
    }

    /** Bloque corto de la ronda de pruebas: solo intentos y escrituras. */
    private void copyAttempts() {
        copyToClipboard("intentos", buildDiagFull().attemptsText(), "Intentos copiados");
    }

    /** Bloque de la matriz fila por fila, con su mes. */
    private void copyMatrixDiag() {
        copyToClipboard("matriz", buildDiagFull().matrixText(), "Matriz copiada");
    }

    /** Bloque general: app, sesión, llamadas rutinarias y agregados. */
    private void copyGeneralDiagnostic() {
        copyToClipboard("diagnóstico general", buildDiagFull().generalText(),
                "Diagnóstico general copiado");
    }

    // ---------------- Vistas Cuentas y Túneles (SOLO LECTURA) ----------------

    private void addPlaceholder(LinearLayout list, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(TEXT2);
        tv.setTextSize(13);
        tv.setPadding(0, dp(14), 0, 0);
        list.addView(tv);
    }

    private void addGroupHeader(LinearLayout list, String title, int count) {
        TextView tv = new TextView(this);
        tv.setText(title + " (" + count + ")");
        tv.setTextColor(TEXT2);
        tv.setTextSize(12);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(0, dp(14), 0, dp(2));
        list.addView(tv);
    }

    /** Chip «Nota» de las cards: indica que el item trae nota real en la web. */
    private TextView makeNoteChip() {
        TextView chip = new TextView(this);
        chip.setText("Nota");
        chip.setTextColor(CYAN);
        chip.setTextSize(10);
        chip.setTypeface(Typeface.DEFAULT_BOLD);
        chip.setPadding(dp(8), dp(3), dp(8), dp(3));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.TRANSPARENT);
        bg.setCornerRadius(dp(10));
        bg.setStroke(dp(1), BORDER);
        chip.setBackground(bg);
        return chip;
    }

    private int accountsFilterIndex() {
        switch (accountsFilter) {
            case "CON": return 1;
            case "SIN": return 2;
            case "DESH": return 3;
            default: return 0;
        }
    }

    private int tunnelsFilterIndex() {
        switch (tunnelsFilter) {
            case "ACT": return 1;
            case "INACT": return 2;
            default: return 0;
        }
    }

    private boolean accountMatches(JSONObject u, String q) {
        String[] fields = {"username", "alias", "full_name"};
        for (String f : fields) {
            String v = DataHelp.fieldStr(u, f);
            if (v != null && v.toLowerCase(Locale.ROOT).contains(q)) return true;
        }
        return false;
    }

    private void renderAccounts() {
        if (accountsList == null) return;
        accountsList.removeAllViews();
        refreshFilterStyles(accountsFilterBtns, accountsFilterIndex());
        if (dataUsers == null) {
            addPlaceholder(accountsList, "Cargando datos…");
            return;
        }
        String q = accountsQuery.trim().toLowerCase(Locale.ROOT);
        List<JSONObject> con = new ArrayList<>();
        List<JSONObject> sin = new ArrayList<>();
        List<JSONObject> desh = new ArrayList<>();
        for (int i = 0; i < dataUsers.length(); i++) {
            JSONObject u = dataUsers.optJSONObject(i);
            if (u == null) continue;
            if (!q.isEmpty() && !accountMatches(u, q)) continue;
            Object ia = u.opt("is_active");
            boolean disabled = ia instanceof Boolean && !((Boolean) ia);
            if (disabled) {
                desh.add(u);
            } else {
                Object tc = u.opt("tunnels_count");
                int n = tc instanceof Number ? ((Number) tc).intValue() : 0;
                if (n > 0) con.add(u); else sin.add(u);
            }
        }
        boolean any = false;
        if (accountsFilter.equals("ALL") || accountsFilter.equals("CON")) {
            any |= renderAccountGroup("Con túneles", con);
        }
        if (accountsFilter.equals("ALL") || accountsFilter.equals("SIN")) {
            any |= renderAccountGroup("Sin túneles", sin);
        }
        if (accountsFilter.equals("ALL") || accountsFilter.equals("DESH")) {
            any |= renderAccountGroup("Deshabilitadas", desh);
        }
        if (!any) addPlaceholder(accountsList, "Sin resultados.");
    }

    private boolean renderAccountGroup(String title, List<JSONObject> items) {
        if (items.isEmpty()) return false;
        addGroupHeader(accountsList, title, items.size());
        for (JSONObject u : items) accountsList.addView(accountCard(u));
        return true;
    }

    private LinearLayout accountCard(final JSONObject u) {
        LinearLayout card = makeCardBox();
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        card.setLayoutParams(lp);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        TextView tvName = new TextView(this);
        String username = DataHelp.fieldStr(u, "username");
        tvName.setText(username != null ? username : "—");
        tvName.setTextColor(TEXT);
        tvName.setTextSize(15);
        tvName.setTypeface(Typeface.DEFAULT_BOLD);
        top.addView(tvName, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (DataHelp.fieldStr(u, "notes") != null) top.addView(makeNoteChip());
        card.addView(top);

        String alias = DataHelp.fieldStr(u, "alias");
        if (alias != null) {
            TextView tvAlias = new TextView(this);
            tvAlias.setText("Alias: " + alias);
            tvAlias.setTextColor(TEXT2);
            tvAlias.setTextSize(12);
            tvAlias.setPadding(0, dp(3), 0, 0);
            card.addView(tvAlias);
        }
        Object tc = u.opt("tunnels_count");
        StringBuilder line = new StringBuilder();
        if (tc instanceof Number) {
            int n = ((Number) tc).intValue();
            line.append(n).append(n == 1 ? " túnel" : " túneles");
        } else {
            line.append("Túneles: —");
        }
        Object ia = u.opt("is_active");
        if (ia instanceof Boolean) {
            line.append(" · ").append((Boolean) ia ? "Activa" : "Deshabilitada");
        }
        TextView tvLine = new TextView(this);
        tvLine.setText(line.toString());
        tvLine.setTextColor(TEXT2);
        tvLine.setTextSize(12);
        tvLine.setPadding(0, dp(3), 0, 0);
        card.addView(tvLine);

        card.setOnClickListener(v -> openAccountDetail(u));
        return card;
    }

    private void openAccountDetail(JSONObject u) {
        String username = DataHelp.fieldStr(u, "username");
        detailTitle.setText(username != null ? username : "Cuenta");
        detailBody.removeAllViews();
        detailRow("Usuario", username);
        detailRow("Alias", DataHelp.fieldStr(u, "alias"));
        detailRow("Nombre completo", DataHelp.fieldStr(u, "full_name"));
        detailRow("Zona", DataHelp.zoneLabel(u));
        Object ia = u.opt("is_active");
        detailRow("Estado", ia instanceof Boolean
                ? ((Boolean) ia ? "Activa" : "Deshabilitada") : null);
        Object tc = u.opt("tunnels_count");
        detailRow("Túneles", tc instanceof Number ? String.valueOf(((Number) tc).intValue()) : null);
        detailRow("Creada", fmtIsoField(u, "created_at"));
        detailRow("Último login", fmtIsoField(u, "last_login"));
        String tg = DataHelp.fieldStr(u, "telegram_username");
        if (tg != null) {
            detailRow("Telegram", tg);
        } else if (Boolean.TRUE.equals(u.opt("is_telegram_verified"))) {
            detailRow("Telegram", "Vinculado");
        }
        detailNotes(DataHelp.fieldStr(u, "notes"));
        // Editar la nota de la cuenta (v0.6.0): PATCH al detalle de usuario.
        Object uid = u.opt("id");
        if (uid instanceof Number) {
            final long userId = ((Number) uid).longValue();
            final JSONObject fu = u;
            Button btnNote = makeButton("Editar nota");
            btnNote.setOnClickListener(v -> openAccountNoteEditor(fu, userId));
            LinearLayout.LayoutParams nl = fullWidthLp();
            nl.topMargin = dp(14);
            detailBody.addView(btnNote, nl);
            Button btnReset = makeButton("Restablecer contraseña");
            btnReset.setOnClickListener(v -> openResetPassword(fu, userId));
            LinearLayout.LayoutParams rl = fullWidthLp();
            rl.topMargin = dp(8);
            detailBody.addView(btnReset, rl);
        }
        showDetail();
    }

    /** Fecha ISO de un campo, formateada; si no se reconoce, el valor crudo real. */
    private String fmtIsoField(JSONObject o, String key) {
        String raw = DataHelp.fieldStr(o, key);
        if (raw == null) return null;
        String f = DataHelp.fmtIsoDateTime(raw);
        return f != null ? f : raw;
    }

    private Map<String, String> buildOwnerMap() {
        Map<String, String> map = new HashMap<>();
        if (dataUsers == null) return map;
        for (int i = 0; i < dataUsers.length(); i++) {
            JSONObject u = dataUsers.optJSONObject(i);
            if (u == null) continue;
            Object id = u.opt("id");
            String username = DataHelp.fieldStr(u, "username");
            if (id != null && id != JSONObject.NULL && username != null) {
                map.put(String.valueOf(id), username);
            }
        }
        return map;
    }

    private boolean tunnelMatches(JSONObject t, String q, Map<String, String> ownerMap) {
        String name = DataHelp.fieldStr(t, "name");
        if (name != null && name.toLowerCase(Locale.ROOT).contains(q)) return true;
        String nfu = DataHelp.fieldStr(t, "name_for_user");
        if (nfu != null && nfu.toLowerCase(Locale.ROOT).contains(q)) return true;
        String owner = DataHelp.ownerName(t, ownerMap);
        return owner != null && owner.toLowerCase(Locale.ROOT).contains(q);
    }

    private void renderTunnels() {
        if (tunnelsList == null) return;
        tunnelsList.removeAllViews();
        refreshFilterStyles(tunnelsFilterBtns, tunnelsFilterIndex());
        if (dataTunnels == null) {
            addPlaceholder(tunnelsList, "Cargando datos…");
            return;
        }
        Map<String, String> ownerMap = buildOwnerMap();
        String q = tunnelsQuery.trim().toLowerCase(Locale.ROOT);
        List<JSONObject> act = new ArrayList<>();
        List<JSONObject> inact = new ArrayList<>();
        List<JSONObject> unk = new ArrayList<>();
        for (int i = 0; i < dataTunnels.length(); i++) {
            JSONObject t = dataTunnels.optJSONObject(i);
            if (t == null) continue;
            if (!q.isEmpty() && !tunnelMatches(t, q, ownerMap)) continue;
            int st = HomeStats.statusOf(t);
            if (st == 1) act.add(t);
            else if (st == 0) inact.add(t);
            else unk.add(t);
        }
        boolean any = false;
        if (tunnelsFilter.equals("ALL") || tunnelsFilter.equals("ACT")) {
            any |= renderTunnelGroup("Activos", act, ownerMap);
        }
        if (tunnelsFilter.equals("ALL") || tunnelsFilter.equals("INACT")) {
            any |= renderTunnelGroup("Desactivados", inact, ownerMap);
        }
        if (tunnelsFilter.equals("ALL") && !unk.isEmpty()) {
            any |= renderTunnelGroup("Sin estado reconocido", unk, ownerMap);
        }
        if (!any) addPlaceholder(tunnelsList, "Sin resultados.");
    }

    private boolean renderTunnelGroup(String title, List<JSONObject> items,
                                       Map<String, String> ownerMap) {
        if (items.isEmpty()) return false;
        addGroupHeader(tunnelsList, title, items.size());
        for (JSONObject t : items) tunnelsList.addView(tunnelCard(t, ownerMap));
        return true;
    }

    private TextView makeStatusChip(int st) {
        TextView chip = new TextView(this);
        chip.setTextSize(10);
        chip.setTypeface(Typeface.DEFAULT_BOLD);
        chip.setPadding(dp(8), dp(3), dp(8), dp(3));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(10));
        if (st == 1) {
            chip.setText("Activo");
            chip.setTextColor(GREEN);
            bg.setColor(Color.parseColor("#173D28"));
        } else if (st == 0) {
            chip.setText("Inactivo");
            chip.setTextColor(RED);
            bg.setColor(Color.parseColor("#3D2323"));
        } else {
            chip.setText("—");
            chip.setTextColor(TEXT2);
            bg.setColor(CARD);
            bg.setStroke(dp(1), BORDER);
        }
        chip.setBackground(bg);
        return chip;
    }

    private LinearLayout tunnelCard(final JSONObject t, final Map<String, String> ownerMap) {
        LinearLayout card = makeCardBox();
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        card.setLayoutParams(lp);

        String name = DataHelp.fieldStr(t, "name");
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        TextView tvName = new TextView(this);
        tvName.setText(name != null ? name : "—");
        tvName.setTextColor(TEXT);
        tvName.setTextSize(15);
        tvName.setTypeface(Typeface.DEFAULT_BOLD);
        top.addView(tvName, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (DataHelp.fieldStr(t, "notes") != null) top.addView(makeNoteChip());
        card.addView(top);

        String owner = DataHelp.ownerName(t, ownerMap);
        if (owner != null) {
            TextView tvOwner = new TextView(this);
            tvOwner.setText(owner);
            tvOwner.setTextColor(TEXT2);
            tvOwner.setTextSize(12);
            tvOwner.setPadding(0, dp(3), 0, 0);
            card.addView(tvOwner);
        }

        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.HORIZONTAL);
        mid.setGravity(Gravity.CENTER_VERTICAL);
        mid.setPadding(0, dp(6), 0, 0);
        String host = DataHelp.hostLabel(t);
        TextView tvHost = new TextView(this);
        tvHost.setText(host != null ? host : "Host: —");
        tvHost.setTextColor(TEXT2);
        tvHost.setTextSize(12);
        mid.addView(tvHost, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        mid.addView(makeStatusChip(HomeStats.statusOf(t)));
        card.addView(mid);

        String nfu = DataHelp.fieldStr(t, "name_for_user");
        if (nfu != null && !nfu.equals(name)) {
            TextView tvNfu = new TextView(this);
            tvNfu.setText("El cliente lo ve como: " + nfu);
            tvNfu.setTextColor(TEXT2);
            tvNfu.setTextSize(12);
            tvNfu.setPadding(0, dp(4), 0, 0);
            card.addView(tvNfu);
        }
        String created = DataHelp.fieldStr(t, "created_at");
        String createdFmt = created != null ? DataHelp.fmtIsoDate(created) : null;
        if (createdFmt != null) {
            TextView tvCreated = new TextView(this);
            tvCreated.setText("Creado: " + createdFmt);
            tvCreated.setTextColor(TEXT2);
            tvCreated.setTextSize(11);
            tvCreated.setPadding(0, dp(4), 0, 0);
            card.addView(tvCreated);
        }

        // Menú de acciones del túnel (v0.6.0): habilitado en TODOS los
        // túneles por orden del propietario.
        Button btnActions = makeButton("Acciones");
        btnActions.setTextSize(12);
        btnActions.setPadding(dp(12), dp(7), dp(12), dp(7));
        LinearLayout.LayoutParams abLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        abLp.topMargin = dp(10);
        btnActions.setLayoutParams(abLp);
        btnActions.setOnClickListener(v -> openTunnelMenu(t, ownerMap));
        card.addView(btnActions);

        card.setOnClickListener(v -> openTunnelDetail(t, ownerMap));
        return card;
    }

    private void openTunnelDetail(JSONObject t, Map<String, String> ownerMap) {
        String name = DataHelp.fieldStr(t, "name");
        detailTitle.setText(name != null ? name : "Túnel");
        detailBody.removeAllViews();
        detailRow("Nombre", name);
        detailRow("Nombre para el usuario", DataHelp.fieldStr(t, "name_for_user"));
        detailRow("Propietario", DataHelp.ownerName(t, ownerMap));
        Object id = t.opt("id");
        detailRow("ID", id != null && id != JSONObject.NULL ? String.valueOf(id) : null);
        detailRow("Protocolo", DataHelp.fieldStr(t, "protocol"));
        String statusRaw = DataHelp.fieldStr(t, "status");
        int st = HomeStats.statusOf(t);
        detailRow("Estado", statusRaw != null ? statusRaw
                : st == 1 ? "Activo" : st == 0 ? "Inactivo" : null);
        detailRow("Provisioning", DataHelp.fieldStr(t, "provisioning_status"));
        detailRow("Zona", DataHelp.zoneLabel(t));
        detailRow("Host", DataHelp.hostLabel(t));
        detailRow("Creado", fmtIsoField(t, "created_at"));
        String exp = DataHelp.fieldStr(t, "expiration_date");
        if (exp != null) detailRow("Expiración", fmtIsoField(t, "expiration_date"));
        String dis = DataHelp.fieldStr(t, "disabled_date");
        if (dis != null) detailRow("Deshabilitado", fmtIsoField(t, "disabled_date"));
        detailNotes(DataHelp.fieldStr(t, "notes"));
        // Primera escritura de la app: «Marcar pagado» abre la hoja de
        // confirmación (mes por defecto: el mes actual). Sin id numérico no
        // hay escritura posible y el botón no se ofrece.
        Object tid = t.opt("id");
        if (tid instanceof Number) {
            final long tunnelId = ((Number) tid).longValue();
            final String tName = name;
            final String owner = DataHelp.ownerName(t, ownerMap);
            // Sonda de detalle (v0.7.0): caja propia entre las notas y los
            // botones; la rellena la lectura en segundo plano del detalle.
            probeBox = new LinearLayout(this);
            probeBox.setOrientation(LinearLayout.VERTICAL);
            detailBody.addView(probeBox);
            startTunnelProbe(t, tunnelId);
            Button btnMark = makeButton("Marcar pagado");
            btnMark.setOnClickListener(v -> {
                Calendar c = Calendar.getInstance();
                openMarkSheet(tunnelId, tName, owner,
                        c.get(Calendar.MONTH) + 1, c.get(Calendar.YEAR));
            });
            LinearLayout.LayoutParams bl = fullWidthLp();
            bl.topMargin = dp(14);
            detailBody.addView(btnMark, bl);
            // Menú de acciones del túnel (v0.6.0), también desde el detalle.
            Button btnActions = makeButton("Acciones");
            btnActions.setOnClickListener(v -> openTunnelMenu(t, ownerMap));
            LinearLayout.LayoutParams al = fullWidthLp();
            al.topMargin = dp(10);
            detailBody.addView(btnActions, al);
        }
        showDetail();
    }

    // ---------------- Sonda de detalle del túnel (v0.7.0, LECTURA) ----------------

    /** ¿El nombre de la clave huele a configuración? (para NO volcarla). */
    private boolean smellsLikeConfigKey(String key) {
        String k = key.toLowerCase(Locale.US);
        return k.contains("config") || k.contains("wireguard")
                || k.contains("qr") || k.contains("conf") || k.contains("file");
    }

    /** ¿El nombre de la clave es sensible? Su valor nunca se muestra. */
    private boolean sensitiveKey(String key) {
        String k = key.toLowerCase(Locale.US);
        return k.contains("password") || k.contains("secret")
                || k.contains("token") || k.contains("private");
    }

    /** Tipo y longitud de un valor, sin su contenido (para el Diagnóstico). */
    private String probeShape(Object v) {
        if (v == null || v == JSONObject.NULL) return "nulo";
        if (v instanceof String) return "texto · " + ((String) v).length() + " caracteres";
        if (v instanceof JSONObject) return "objeto · " + ((JSONObject) v).length() + " claves";
        if (v instanceof JSONArray) return "arreglo · " + ((JSONArray) v).length() + " items";
        if (v instanceof Number) return "número";
        if (v instanceof Boolean) return "booleano";
        return v.getClass().getSimpleName();
    }

    /**
     * Sonda de detalle: GET /api/v1/tunnels/<id>/?personal=false (v0.8.1:
     * con el alcance de la lista) en segundo plano al abrir
     * «Información». Registra en el Diagnóstico las claves completas y, de
     * las que huelen a configuración, solo tipo y longitud (el contenido
     * JAMÁS se anota ni se vuelca en la hoja). Si el detalle trae campos
     * que la lista no trae, los muestra en la caja de la sonda (valores
     * legibles no sensibles; la config solo como «disponible en el
     * detalle»). Es lectura: si falla, su error literal es el resultado.
     */
    private void startTunnelProbe(final JSONObject listItem, final long tunnelId) {
        probeGen++;
        final int gen = probeGen;
        probeTunnelId = tunnelId;
        // v0.8.1: la sonda pide el detalle con el alcance de la lista
        // (?personal=false) — la ruta a secas dio 404 genérico en la
        // ronda 3 para túneles de clientes.
        final String url = PanelConfig.tunnelDetailUrl(tunnelId);
        exec.execute(() -> {
            ApiClient.Result r = new ApiClient(store).rawGet(url);
            final List<String[]> rows = new ArrayList<>();
            boolean configFound = false;
            String note;
            if (r.error != null) {
                note = "sin respuesta: " + r.error;
            } else if (r.body == null) {
                note = "sin cuerpo";
            } else {
                Object parsed = JsonHelp.parse(r.body);
                if (parsed instanceof JSONObject) {
                    JSONObject d = (JSONObject) parsed;
                    StringBuilder sb = new StringBuilder();
                    sb.append("claves: {").append(JsonHelp.keyList(d)).append("}");
                    List<String> newKeys = new ArrayList<>();
                    java.util.Iterator<String> it = d.keys();
                    while (it.hasNext()) {
                        String k = it.next();
                        if (smellsLikeConfigKey(k)) {
                            sb.append(" · «").append(k).append("»: ")
                                    .append(probeShape(d.opt(k)))
                                    .append(" (contenido NO anotado)");
                        }
                        if (!listItem.has(k)) newKeys.add(k);
                    }
                    sb.append(newKeys.isEmpty()
                            ? " · sin claves nuevas frente a la lista"
                            : " · claves nuevas frente a la lista: " + newKeys);
                    note = sb.toString();
                    // «Configuración disponible» se decide por CONTENIDO
                    // (extractConfig reconoce el texto WireGuard venga bajo
                    // la clave que sea), no por el nombre de la clave: un
                    // booleano tipo can_download_config no es la config.
                    configFound = extractConfig(d) != null;
                    for (String k : newKeys) {
                        Object v = d.opt(k);
                        if (v == null || v == JSONObject.NULL) continue;
                        if (v instanceof String && looksLikeConfig((String) v)) {
                            continue; // es la config: no se vuelca en la hoja
                        }
                        if (sensitiveKey(k)) {
                            rows.add(new String[]{k,
                                    "presente en el detalle (valor no mostrado)"});
                        } else if (v instanceof String) {
                            String s = (String) v;
                            if (!s.isEmpty()) {
                                rows.add(new String[]{k, s.length() > 140
                                        ? s.substring(0, 140) + "…" : s});
                            }
                        } else if (v instanceof Number || v instanceof Boolean) {
                            rows.add(new String[]{k, String.valueOf(v)});
                        } else if (v instanceof JSONObject) {
                            String nm = DataHelp.str((JSONObject) v,
                                    "name", "label", "hostname", "title");
                            rows.add(new String[]{k, nm != null ? nm
                                    : "objeto · " + ((JSONObject) v).length() + " claves"});
                        } else if (v instanceof JSONArray) {
                            rows.add(new String[]{k,
                                    "arreglo · " + ((JSONArray) v).length() + " items"});
                        }
                    }
                } else {
                    note = JsonHelp.describe(r.body);
                }
            }
            ApiDiag.add("Detalle de túnel (sonda)", "GET", url, r.code, r.ms, note);
            final boolean fConfig = configFound;
            final boolean failed = r.error != null || r.code < 200 || r.code >= 300;
            handler.post(() -> {
                if (!detailOpen || probeTunnelId != tunnelId
                        || probeGen != gen || probeBox == null) return;
                probeBox.removeAllViews();
                if (failed) {
                    probeBox.addView(makeDetailRow("Detalle del panel",
                            "No se pudo leer (ver Diagnóstico)"));
                    return;
                }
                for (String[] row : rows) {
                    probeBox.addView(makeDetailRow(row[0], row[1]));
                }
                if (fConfig) {
                    probeBox.addView(makeDetailRow("Configuración",
                            "Disponible en el detalle"));
                }
                if (rows.isEmpty() && !fConfig) {
                    probeBox.addView(makeDetailRow("Detalle del panel",
                            "Sin campos nuevos frente a la lista"));
                }
            });
        });
    }

    // ---------------- Vista Pagos (matriz real) ----------------

    private void addSectionTitle(LinearLayout list, String title) {
        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextColor(TEXT);
        tv.setTextSize(15);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(0, dp(16), 0, dp(2));
        list.addView(tv);
    }

    private void renderPagos() {
        if (pagosList == null) return;
        pagosList.removeAllViews();
        if (dataMatrix == null) {
            addPlaceholder(pagosList, "Cargando datos…");
            return;
        }
        Calendar cal = Calendar.getInstance();
        int cy = cal.get(Calendar.YEAR);
        int cm = cal.get(Calendar.MONTH) + 1;
        List<HomeStats.PayEntry> pend = HomeStats.pendingEntries(dataMatrix, cy, cm);
        List<HomeStats.PayEntry> paid = HomeStats.paidEntries(dataMatrix);

        // --- Pendientes: mes actual primero, luego meses anteriores ---
        addSectionTitle(pagosList, "Pendientes");
        if (pend.isEmpty()) {
            addPlaceholder(pagosList, "Sin pendientes registrados en la matriz.");
        } else {
            int i = 0;
            while (i < pend.size()) {
                int key = pend.get(i).year * 100 + pend.get(i).month;
                int j = i;
                while (j < pend.size()
                        && pend.get(j).year * 100 + pend.get(j).month == key) j++;
                addGroupHeader(pagosList,
                        HomeStats.monthLabel(pend.get(i).year, pend.get(i).month), j - i);
                for (int k = i; k < j; k++) pagosList.addView(pendingRow(pend.get(k)));
                i = j;
            }
        }

        // --- Pagados: todas las celdas pagadas, por mes descendente ---
        addSectionTitle(pagosList, "Pagados");
        if (paid.isEmpty()) {
            addPlaceholder(pagosList, "Sin pagos registrados en la matriz.");
        } else {
            int i = 0;
            while (i < paid.size()) {
                int key = paid.get(i).year * 100 + paid.get(i).month;
                int j = i;
                while (j < paid.size()
                        && paid.get(j).year * 100 + paid.get(j).month == key) j++;
                addGroupHeader(pagosList,
                        HomeStats.monthLabel(paid.get(i).year, paid.get(i).month), j - i);
                for (int k = i; k < j; k++) pagosList.addView(paidRow(paid.get(k)));
                i = j;
            }
        }

        // --- Recibidos: todavía sin fuente real (SMS llega en otra etapa) ---
        addSectionTitle(pagosList, "Recibidos");
        LinearLayout card = makeCardBox();
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        card.setLayoutParams(lp);
        TextView tv = new TextView(this);
        tv.setText("La lectura de SMS llega en la próxima etapa de la Lite. "
                + "Cuando esté, aquí aparecerán los pagos recibidos por SMS "
                + "para confirmarlos y marcarlos.");
        tv.setTextColor(TEXT2);
        tv.setTextSize(12);
        card.addView(tv);
        pagosList.addView(card);
    }

    /** Fila de pendiente: túnel + cuenta y el botón «Marcar pagado». */
    private LinearLayout pendingRow(final HomeStats.PayEntry e) {
        LinearLayout card = makeCardBox();
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        card.setLayoutParams(lp);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView tvName = new TextView(this);
        tvName.setText(e.tunnelName != null ? e.tunnelName : "—");
        tvName.setTextColor(TEXT);
        tvName.setTextSize(15);
        tvName.setTypeface(Typeface.DEFAULT_BOLD);
        texts.addView(tvName);
        TextView tvUser = new TextView(this);
        tvUser.setText(e.userName != null ? e.userName : "—");
        tvUser.setTextColor(TEXT2);
        tvUser.setTextSize(12);
        tvUser.setPadding(0, dp(3), 0, 0);
        texts.addView(tvUser);
        card.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (e.tunnelId >= 0) {
            Button btn = makeButton("Marcar pagado");
            btn.setTextSize(12);
            btn.setPadding(dp(12), dp(8), dp(12), dp(8));
            btn.setOnClickListener(v -> openMarkSheet(
                    e.tunnelId, e.tunnelName, e.userName, e.month, e.year));
            card.addView(btn);
        }
        return card;
    }

    /** Fila de pagado: túnel + cuenta; al tocarla, el detalle de la celda. */
    private LinearLayout paidRow(final HomeStats.PayEntry e) {
        LinearLayout card = makeCardBox();
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        card.setLayoutParams(lp);
        TextView tvName = new TextView(this);
        tvName.setText(e.tunnelName != null ? e.tunnelName : "—");
        tvName.setTextColor(TEXT);
        tvName.setTextSize(15);
        tvName.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(tvName);
        TextView tvUser = new TextView(this);
        tvUser.setText(e.userName != null ? e.userName : "—");
        tvUser.setTextColor(TEXT2);
        tvUser.setTextSize(12);
        tvUser.setPadding(0, dp(3), 0, 0);
        card.addView(tvUser);
        card.setOnClickListener(v -> openPaidDetail(e));
        return card;
    }

    /** Detalle de una celda pagada: lo que la matriz trae de verdad. */
    private void openPaidDetail(HomeStats.PayEntry e) {
        detailTitle.setText(e.tunnelName != null ? e.tunnelName : "Pago");
        detailBody.removeAllViews();
        detailRow("Túnel", e.tunnelName);
        detailRow("Cuenta", e.userName);
        detailRow("Mes", HomeStats.monthLabel(e.year, e.month));
        detailRow("Estado", "Pagado");
        if (e.markedAt != null) {
            String f = DataHelp.fmtIsoDateTime(e.markedAt);
            detailRow("Marcado el", f != null ? f : e.markedAt);
        }
        if (e.markedBy != null) detailRow("Marcado por", e.markedBy);
        detailNotes(e.notes);
        showDetail();
    }

    // ---------------- Hoja «Marcar pagado» (escritura real) ----------------

    private void sheetRow(String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(6), 0, dp(6));
        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextColor(TEXT2);
        tvLabel.setTextSize(13);
        row.addView(tvLabel, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0.9f));
        TextView tvValue = new TextView(this);
        tvValue.setText(value != null ? value : "—");
        tvValue.setTextColor(TEXT);
        tvValue.setTextSize(13);
        row.addView(tvValue, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1.6f));
        sheetBody.addView(row);
    }

    private void sheetLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(TEXT2);
        tv.setTextSize(13);
        tv.setPadding(0, dp(14), 0, dp(6));
        sheetBody.addView(tv);
    }

    /**
     * Abre la hoja de confirmación: túnel y cuenta, selector de meses en UN
     * desplegable con los 4 meses nombrables (anterior, actual, siguiente y
     * el siguiente — multiselección; el propietario elige, la app no sugiere
     * por importe) y la «Nota del pago» opcional. defMonth/defYear preselecciona
     * un mes si está entre los 4; si no, se preselecciona el mes actual.
     */
    private void openMarkSheet(long tunnelId, String tunnelName, String userName,
                               int defMonth, int defYear) {
        sheetTunnelId = tunnelId;
        sheetTunnelName = tunnelName;
        sheetUserName = userName;
        writeInFlight = false;
        sheetConfirmBtn.setEnabled(true);
        sheetCancelBtn.setEnabled(true);

        // Las 4 opciones: mes anterior, actual, siguiente y el siguiente.
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        sheetOptions = new int[4][2];
        sheetSelected = new boolean[4];
        int curIdx = 1;
        for (int i = 0; i < 4; i++) {
            Calendar c = (Calendar) cal.clone();
            c.add(Calendar.MONTH, i - 1);
            sheetOptions[i][0] = c.get(Calendar.MONTH) + 1;
            sheetOptions[i][1] = c.get(Calendar.YEAR);
            if (sheetOptions[i][0] == defMonth && sheetOptions[i][1] == defYear) {
                curIdx = i;
            }
        }
        sheetSelected[curIdx] = true;

        sheetBody.removeAllViews();
        sheetRow("Túnel", tunnelName);
        sheetRow("Cuenta", userName);

        sheetLabel("Meses");
        LinearLayout dropHead = makeCardBox();
        dropHead.setPadding(dp(12), dp(11), dp(12), dp(11));
        sheetMonthsHeader = new TextView(this);
        sheetMonthsHeader.setTextColor(TEXT);
        sheetMonthsHeader.setTextSize(13);
        dropHead.addView(sheetMonthsHeader);
        sheetBody.addView(dropHead);
        sheetMonthsBox = new LinearLayout(this);
        sheetMonthsBox.setOrientation(LinearLayout.VERTICAL);
        sheetMonthsBox.setVisibility(View.GONE);
        sheetMonthRows = new TextView[4];
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            TextView opt = new TextView(this);
            opt.setTextSize(13);
            opt.setPadding(dp(12), dp(10), dp(12), dp(10));
            opt.setOnClickListener(v -> {
                sheetSelected[idx] = !sheetSelected[idx];
                refreshSheetMonths();
            });
            sheetMonthsBox.addView(opt);
            sheetMonthRows[i] = opt;
        }
        sheetBody.addView(sheetMonthsBox);
        dropHead.setOnClickListener(v -> sheetMonthsBox.setVisibility(
                sheetMonthsBox.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        refreshSheetMonths();

        sheetLabel("Nota del pago");
        sheetNoteInput = new EditText(this);
        sheetNoteInput.setHint("Opcional");
        sheetNoteInput.setTextColor(TEXT);
        sheetNoteInput.setHintTextColor(TEXT2);
        sheetNoteInput.setTextSize(13);
        sheetNoteInput.setMinLines(2);
        sheetNoteInput.setPadding(dp(12), dp(9), dp(12), dp(9));
        GradientDrawable nbg = new GradientDrawable();
        nbg.setColor(CARD);
        nbg.setCornerRadius(dp(10));
        nbg.setStroke(dp(1), BORDER);
        sheetNoteInput.setBackground(nbg);
        sheetBody.addView(sheetNoteInput);

        sheetError = new TextView(this);
        sheetError.setTextColor(RED);
        sheetError.setTextSize(13);
        sheetError.setPadding(0, dp(12), 0, 0);
        sheetError.setVisibility(View.GONE);
        sheetBody.addView(sheetError);
        sheetStatus = new TextView(this);
        sheetStatus.setTextColor(TEXT2);
        sheetStatus.setTextSize(13);
        sheetStatus.setPadding(0, dp(12), 0, 0);
        sheetStatus.setVisibility(View.GONE);
        sheetBody.addView(sheetStatus);

        sheetContainer.setVisibility(View.VISIBLE);
        sheetOpen = true;
    }

    /** Refresca el desplegable: cabecera con los meses unidos por « + ». */
    private void refreshSheetMonths() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            String label = HomeStats.monthLabel(sheetOptions[i][1], sheetOptions[i][0]);
            sheetMonthRows[i].setText((sheetSelected[i] ? "☑ " : "☐ ") + label);
            sheetMonthRows[i].setTextColor(sheetSelected[i] ? TEXT : TEXT2);
            if (sheetSelected[i]) {
                if (sb.length() > 0) sb.append(" + ");
                sb.append(label);
            }
        }
        sheetMonthsHeader.setText((sb.length() > 0 ? sb.toString() : "Elige los meses…") + "  ▾");
    }

    private void closeMarkSheet() {
        sheetContainer.setVisibility(View.GONE);
        sheetOpen = false;
    }

    private void sheetShowError(String msg) {
        sheetError.setText(msg);
        sheetError.setVisibility(View.VISIBLE);
        sheetStatus.setVisibility(View.GONE);
    }

    /**
     * Ejecuta la escritura: UN POST por cada mes elegido, en orden
     * cronológico, sin reintentos (ver ApiClient.postWrite). Éxito total →
     * toast «Pago marcado» y recarga real de los datos. Error HTTP → el
     * error literal del servidor queda en la hoja y nada cambia localmente.
     * Respuesta ambigua → no se reintenta: se recarga la matriz y se muestra
     * el estado real.
     */
    private void confirmMarkPaid() {
        if (writeInFlight) return;
        final List<int[]> chosen = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            if (sheetSelected[i]) chosen.add(sheetOptions[i]);
        }
        if (chosen.isEmpty()) {
            sheetShowError("Elige al menos un mes.");
            return;
        }
        if (sheetTunnelId < 0) {
            sheetShowError("Este túnel no trae id numérico: no se puede marcar.");
            return;
        }
        final String note = sheetNoteInput.getText() != null
                ? sheetNoteInput.getText().toString().trim() : "";
        final long tunnelId = sheetTunnelId;
        writeInFlight = true;
        sheetConfirmBtn.setEnabled(false);
        sheetCancelBtn.setEnabled(false);
        sheetError.setVisibility(View.GONE);
        sheetStatus.setText("Marcando en el panel…");
        sheetStatus.setVisibility(View.VISIBLE);

        exec.execute(() -> {
            ApiClient api = new ApiClient(store);
            for (int[] my : chosen) {
                String body;
                try {
                    body = new JSONObject()
                            .put("tunnel_id", tunnelId)
                            .put("month", my[0])
                            .put("year", my[1])
                            .put("is_paid", true)
                            .put("notes", note)
                            .toString();
                } catch (Exception e) {
                    handler.post(() -> {
                        writeInFlight = false;
                        sheetConfirmBtn.setEnabled(true);
                        sheetCancelBtn.setEnabled(true);
                        sheetShowError("No se pudo construir la petición.");
                    });
                    return;
                }
                ApiClient.Result r = api.postWrite(
                        "Escritura — marcar pagado", PanelConfig.UPDATE_URL, body);
                if (r.authFailed) {
                    handler.post(() -> {
                        writeInFlight = false;
                        closeMarkSheet();
                        abortIfAuthFailed(true);
                    });
                    return;
                }
                if (r.ambiguous) {
                    // Pudo haberse aplicado: jamás reenviar. Se recarga la
                    // matriz y la app muestra el estado real.
                    handler.post(() -> {
                        writeInFlight = false;
                        closeMarkSheet();
                        Toast.makeText(MainActivity.this,
                                "La respuesta se perdió: recargando la matriz "
                                        + "para mostrar el estado real (no se "
                                        + "reintentó la marca)",
                                Toast.LENGTH_LONG).show();
                        refreshMatrixOnly();
                    });
                    return;
                }
                if (r.code < 200 || r.code >= 300) {
                    final String literal = "HTTP " + r.code + " · "
                            + ApiDiag.scrub(ApiDiag.truncate(
                                    r.body != null ? r.body : "(sin cuerpo)", 600));
                    handler.post(() -> {
                        writeInFlight = false;
                        sheetConfirmBtn.setEnabled(true);
                        sheetCancelBtn.setEnabled(true);
                        sheetShowError("El panel rechazó la marca. " + literal);
                    });
                    return;
                }
                // 2xx: este mes quedó marcado; sigue el siguiente elegido.
            }
            handler.post(() -> {
                writeInFlight = false;
                closeMarkSheet();
                Toast.makeText(MainActivity.this, "Pago marcado", Toast.LENGTH_SHORT).show();
                // v0.8.5: recarga SOLO la matriz (una llamada) y repinta
                // Pagos/Facturación al momento, sin la recarga completa.
                refreshMatrixOnly();
            });
        });
    }

    /**
     * Recarga SOLO la matriz de facturación (v0.8.5): una llamada, y al
     * volver repinta la card de Facturación y la vista Pagos si está en
     * pantalla. Sustituye a la recarga completa del Home tras marcar un
     * pago, que tardaba varios segundos y dejaba la duda de si la marca
     * se había aplicado.
     */
    private void refreshMatrixOnly() {
        exec.execute(() -> {
            ApiClient.ItemsResult rm = new ApiClient(store).getAllItems(
                    "Matriz", PanelConfig.MATRIX_URL);
            if (rm.authFailed) {
                handler.post(() -> abortIfAuthFailed(true));
                return;
            }
            if (rm.items.length() == 0) {
                if (rm.error != null) {
                    handler.post(() -> Toast.makeText(MainActivity.this,
                            "La matriz no se pudo recargar ahora: " + rm.error,
                            Toast.LENGTH_LONG).show());
                }
                return;
            }
            dataMatrix = rm.items;
            Calendar cal = Calendar.getInstance();
            HomeStats.MatrixStats ms = HomeStats.matrix(rm.items,
                    cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1);
            final HomeStats.TunnelsStats ts = dataTunnels != null
                    ? HomeStats.tunnels(dataTunnels, null) : null;
            handler.post(() -> {
                fillFacturacion(ms, ts);
                if (state == State.PAGOS) renderPagos();
            });
        });
    }

    // ---------------- Pantalla de Sesión («Mi sesión») ----------------

    private JSONObject loadSavedSession() {
        String raw = store.load();
        if (raw == null) return null;
        try {
            return new JSONObject(raw);
        } catch (Exception e) {
            return null;
        }
    }

    private void showSessionFromStore() {
        JSONObject saved = loadSavedSession();
        if (saved == null) {
            showLogin(null);
            return;
        }
        showSession(saved);
    }

    /**
     * Entra a «Mi sesión». Si el access venció pero hay refresh guardado,
     * primero intenta renovarlo en segundo plano; si no se puede, al login.
     */
    private void enterSessionOrLogin() {
        JSONObject saved = loadSavedSession();
        if (saved == null) {
            showLogin(null);
            return;
        }
        JSONObject payload = JwtUtils.decodePayload(saved.optString("accessToken"));
        long exp = payload != null ? payload.optLong("exp", -1) : -1;
        if (exp > 0 && exp <= nowSec() && saved.has("refreshToken")) {
            Toast.makeText(this, "Renovando la sesión…", Toast.LENGTH_SHORT).show();
            exec.execute(() -> {
                boolean ok = new ApiClient(store).refreshNow();
                handler.post(() -> {
                    if (ok) showSessionFromStore();
                    else showLogin("La sesión expiró y no se pudo renovar. "
                            + "Entra de nuevo en el panel.");
                });
            });
            return;
        }
        showSessionFromStore();
    }

    /** Muestra SOLO datos reales derivados del token capturado. Sin claim → sin fila. */
    private void showSession(JSONObject sessionJson) {
        String accessToken = sessionJson.optString("accessToken");
        JSONObject payload = JwtUtils.decodePayload(accessToken);
        long exp = payload != null ? payload.optLong("exp", -1) : -1;
        if (exp > 0 && exp <= nowSec()) {
            showLogin("El token de acceso expiró. Entra de nuevo en el panel para renovar la sesión.");
            return;
        }
        sessionExpSec = exp;
        state = State.SESSION;
        hideAll();
        sessionContainer.setVisibility(View.VISIBLE);

        claimsContainer.removeAllViews();
        addRow("Panel", sessionJson.optString("panelHost", PanelConfig.HOST));
        if (payload != null) {
            claimRow(payload, "sub", "Usuario (sub)", false);
            claimRow(payload, "username", "Usuario", false);
            claimRow(payload, "name", "Nombre", false);
            claimRow(payload, "preferred_username", "Usuario", false);
            claimRow(payload, "email", "Correo", false);
            claimRow(payload, "role", "Rol", false);
            claimRow(payload, "roles", "Roles", false);
            claimRow(payload, "iss", "Emisor", false);
            claimRow(payload, "iat", "Emitido el", true);
            claimRow(payload, "exp", "Expira el", true);
        }
        boolean hasRefresh = sessionJson.has("refreshToken");
        addRow("Tokens guardados", hasRefresh ? "access + refresh" : "access");
        String src = sessionJson.optString("accessSource", "");
        String key = sessionJson.optString("accessKey", "");
        addRow("Fuente del access", key.isEmpty() ? src : src + " · " + key);
        long capturedAt = sessionJson.optLong("capturedAt", 0);
        if (capturedAt > 0) addRow("Sesión capturada", fmtDate(capturedAt / 1000));

        handler.removeCallbacks(countdownTick);
        handler.post(countdownTick);
    }

    private void claimRow(JSONObject payload, String key, String label, boolean isDate) {
        if (!payload.has(key)) return;
        Object raw = payload.opt(key);
        if (raw == null) return;
        String value;
        if (isDate) {
            value = (raw instanceof Number) ? fmtDate(((Number) raw).longValue()) : raw.toString();
        } else if (raw instanceof JSONArray) {
            JSONArray arr = (JSONArray) raw;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < arr.length(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(arr.optString(i));
            }
            value = sb.toString();
        } else {
            value = raw.toString();
        }
        if (!value.isEmpty()) addRow(label, value);
    }

    private void addRow(String label, String value) {
        if (value == null || value.isEmpty()) return;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(6), 0, dp(6));
        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextColor(TEXT2);
        tvLabel.setTextSize(13);
        row.addView(tvLabel, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0.9f));
        TextView tvValue = new TextView(this);
        tvValue.setText(value);
        tvValue.setTextColor(TEXT);
        tvValue.setTextSize(13);
        row.addView(tvValue, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1.6f));
        claimsContainer.addView(row);
    }

    // ---------------- Cerrar sesión ----------------

    private void logout() {
        store.clear();
        ApiDiag.clear();
        homeLoaded = false;
        homeLoading = false;
        rateValue = null;
        dataUsers = null;
        dataUsersCount = null;
        dataTunnels = null;
        dataTunnelsScope = null;
        dataMatrix = null;
        dataSessionBody = null;
        CookieManager cm = CookieManager.getInstance();
        cm.removeAllCookies(null);
        cm.flush();
        WebStorage.getInstance().deleteAllData();
        webView.stopLoading();
        webView.clearCache(true);
        webView.clearHistory();
        webView.clearFormData();
        captureDone = false;
        lockPromptShown = false;
        showLogin("Sesión cerrada. Entra de nuevo en el panel.");
    }

    // ---------------- Menú de acciones del túnel (v0.6.0) ----------------

    /** Menú de acciones: lista de opciones a pantalla completa sobre la vista. */
    private void buildMenuUi(FrameLayout root) {
        menuContainer = new LinearLayout(this);
        menuContainer.setOrientation(LinearLayout.VERTICAL);
        menuContainer.setBackgroundColor(BG);
        menuContainer.setPadding(dp(20), dp(20), dp(20), dp(16));
        menuTitle = new TextView(this);
        menuTitle.setTextColor(CYAN);
        menuTitle.setTextSize(20);
        menuTitle.setTypeface(Typeface.DEFAULT_BOLD);
        menuContainer.addView(menuTitle);
        ScrollView scroll = new ScrollView(this);
        menuBody = new LinearLayout(this);
        menuBody.setOrientation(LinearLayout.VERTICAL);
        menuBody.setPadding(0, dp(10), 0, dp(10));
        scroll.addView(menuBody);
        menuContainer.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        Button btnClose = makeButton("← Volver");
        btnClose.setOnClickListener(v -> closeMenu());
        menuContainer.addView(btnClose, fullWidthLp());
        menuContainer.setVisibility(View.GONE);
        root.addView(menuContainer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void closeMenu() {
        menuContainer.setVisibility(View.GONE);
        menuOpen = false;
    }

    private void addMenuButton(String text, View.OnClickListener listener) {
        Button b = makeButton(text);
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = fullWidthLp();
        if (menuBody.getChildCount() > 0) lp.topMargin = dp(10);
        menuBody.addView(b, lp);
    }

    /**
     * Menú de acciones del túnel (spec: la primera opción es «Información»).
     * Habilitado en TODOS los túneles por orden del propietario; Diego es el
     * banco de pruebas. Las acciones de escritura disparan UNA sola ruta
     * candidata por toque (ver PanelConfig) con el patrón seguro.
     */
    private void openTunnelMenu(final JSONObject t, final Map<String, String> ownerMap) {
        actTunnel = t;
        actOwnerMap = ownerMap;
        String name = DataHelp.fieldStr(t, "name");
        menuTitle.setText(name != null ? name : "Túnel");
        menuBody.removeAllViews();
        addMenuButton("Información", v -> {
            closeMenu();
            openTunnelDetail(t, ownerMap);
        });
        addMenuButton("Descargar configuración", v -> startDownload(t));
        addMenuButton("Cambiar de servidor", v -> openServers(t));
        addMenuButton("Regenerar túnel", v -> openRegenerate(t));
        addMenuButton("Reaprovisionar", v -> openReprovision(t));
        addMenuButton("Editar nota", v -> openTunnelNoteEditor(t));
        int st = HomeStats.statusOf(t);
        addMenuButton(st == 1 ? "Deshabilitar" : st == 0 ? "Habilitar"
                : "Deshabilitar / Habilitar", v -> openToggle(t));
        addMenuButton("Eliminar", v -> openDeleteGuard(t));
        menuContainer.setVisibility(View.VISIBLE);
        menuOpen = true;
    }

    /** Id numérico del túnel o -1 (con aviso): sin id no hay acción posible. */
    private long requireId(JSONObject t) {
        Object id = t != null ? t.opt("id") : null;
        if (id instanceof Number) return ((Number) id).longValue();
        Toast.makeText(this, "Este túnel no trae id numérico: la acción no está disponible.",
                Toast.LENGTH_LONG).show();
        return -1;
    }

    // ---------------- Hoja de acción genérica (v0.6.0) ----------------

    private void buildActionUi(FrameLayout root) {
        actContainer = new LinearLayout(this);
        actContainer.setOrientation(LinearLayout.VERTICAL);
        actContainer.setBackgroundColor(BG);
        actContainer.setPadding(dp(20), dp(20), dp(20), dp(16));
        actTitle = new TextView(this);
        actTitle.setTextColor(CYAN);
        actTitle.setTextSize(20);
        actTitle.setTypeface(Typeface.DEFAULT_BOLD);
        actContainer.addView(actTitle);
        ScrollView scroll = new ScrollView(this);
        actBody = new LinearLayout(this);
        actBody.setOrientation(LinearLayout.VERTICAL);
        actBody.setPadding(0, dp(10), 0, dp(10));
        scroll.addView(actBody);
        actContainer.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        actStatus = new TextView(this);
        actStatus.setTextColor(TEXT2);
        actStatus.setTextSize(13);
        actStatus.setPadding(0, dp(4), 0, dp(8));
        actStatus.setVisibility(View.GONE);
        actContainer.addView(actStatus);
        actError = new TextView(this);
        actError.setTextColor(RED);
        actError.setTextSize(13);
        actError.setPadding(0, dp(4), 0, dp(8));
        actError.setVisibility(View.GONE);
        actContainer.addView(actError);
        actPrimaryBtn = makeButton("Confirmar");
        actContainer.addView(actPrimaryBtn, fullWidthLp());
        actExtraBtn = makeButton("Extra");
        actExtraBtn.setVisibility(View.GONE);
        LinearLayout.LayoutParams xl = fullWidthLp();
        xl.topMargin = dp(10);
        actContainer.addView(actExtraBtn, xl);
        actCancelBtn = makeButton("Cancelar");
        actCancelBtn.setOnClickListener(v -> {
            if (!actBusy) closeAct();
        });
        LinearLayout.LayoutParams cl = fullWidthLp();
        cl.topMargin = dp(10);
        actContainer.addView(actCancelBtn, cl);
        actContainer.setVisibility(View.GONE);
        root.addView(actContainer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /** Abre la hoja de acción limpia: título, cuerpo vacío, botones en reposo. */
    private void openAct(String title) {
        actTitle.setText(title);
        actBody.removeAllViews();
        actStatus.setVisibility(View.GONE);
        actError.setVisibility(View.GONE);
        actPrimaryBtn.setVisibility(View.VISIBLE);
        actPrimaryBtn.setEnabled(true);
        actPrimaryBtn.setOnClickListener(null);
        actExtraBtn.setVisibility(View.GONE);
        actExtraBtn.setEnabled(true);
        actExtraBtn.setOnClickListener(null);
        actCancelBtn.setEnabled(true);
        actCancelBtn.setText("Cancelar");
        actBusy = false;
        actContainer.setVisibility(View.VISIBLE);
        actOpen = true;
    }

    private void closeAct() {
        actContainer.setVisibility(View.GONE);
        actOpen = false;
        actBusy = false;
    }

    private void actText(String text, int color, int sizeSp) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(sizeSp);
        tv.setPadding(0, dp(6), 0, dp(6));
        actBody.addView(tv);
    }

    private void actRow(String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(6), 0, dp(6));
        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextColor(TEXT2);
        tvLabel.setTextSize(13);
        row.addView(tvLabel, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0.9f));
        TextView tvValue = new TextView(this);
        tvValue.setText(value != null ? value : "—");
        tvValue.setTextColor(TEXT);
        tvValue.setTextSize(13);
        row.addView(tvValue, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1.6f));
        actBody.addView(row);
    }

    private void setActPrimary(String label, final Runnable action) {
        actPrimaryBtn.setText(label);
        actPrimaryBtn.setVisibility(View.VISIBLE);
        actPrimaryBtn.setOnClickListener(v -> action.run());
    }

    private void setActExtra(String label, final Runnable action) {
        actExtraBtn.setText(label);
        actExtraBtn.setVisibility(View.VISIBLE);
        actExtraBtn.setOnClickListener(v -> action.run());
    }

    private EditText makeTextInput(String hint, boolean multiline) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextColor(TEXT);
        e.setHintTextColor(TEXT2);
        e.setTextSize(13);
        if (multiline) e.setMinLines(3);
        e.setPadding(dp(12), dp(9), dp(12), dp(9));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(10));
        bg.setStroke(dp(1), BORDER);
        e.setBackground(bg);
        return e;
    }

    private void actShowError(String msg) {
        actStatus.setVisibility(View.GONE);
        actError.setText(msg);
        actError.setVisibility(View.VISIBLE);
    }

    /**
     * Ejecutor único de las escrituras de la hoja de acción: patrón seguro
     * de ApiClient.write (sin reintentos; UNA re-llamada tras refresh en
     * 401; el intento entero queda en el Diagnóstico como «Escritura — …»).
     * Éxito → cierra las hojas, toast y RECARGA real (el estado mostrado
     * sale siempre del refetch, nunca de un cambio local fingido).
     * Error HTTP → el literal del servidor en la hoja y nada cambia.
     * Respuesta ambigua → no se reintentó: se recarga y se mira lo real.
     */
    private void doWrite(final String label, final String method, final String url,
                         final String body, final String successMsg) {
        doWrite(label, method, url, body, successMsg, false);
    }

    /**
     * Variante con registro enriquecido de la respuesta (v0.8.3; la usan
     * SOLO deshabilitar/habilitar): la entrada de Intentos añade las
     * claves de nivel superior del objeto respuesta y los valores de
     * status / is_active / disabled_date si vienen, para confirmar en
     * un solo toque si el campo enviado se aplicó de verdad.
     */
    private void doWrite(final String label, final String method, final String url,
                         final String body, final String successMsg,
                         final boolean enrichState) {
        doWrite(label, method, url, body, successMsg, enrichState, null, false);
    }

    /**
     * Variante con anotaciones discriminantes (v0.8.4; la usa SOLO
     * cambiar de servidor): «extraNote» anota en Intentos lo
     * seleccionado en la UI («seleccionado: <nombre> (id <n>)») y
     * «enrichHost» añade el host resultante de la respuesta. Son los
     * dos datos que faltaron en la ronda 5 para saber si la API honra
     * el host pedido o auto-asigna.
     */
    private void doWrite(final String label, final String method, final String url,
                         final String body, final String successMsg,
                         final boolean enrichState, final String extraNote,
                         final boolean enrichHost) {
        doWrite(label, method, url, body, successMsg, enrichState, extraNote,
                enrichHost, null, false);
    }

    /**
     * Forma completa (v0.8.5): «desiredStatus» es el estado que una
     * escritura de Deshabilitar/Habilitar quiere dejar (1/0; null en las
     * demás) y «skipInstant» desactiva la aplicación inmediata de la
     * respuesta (lo usa Eliminar: su objeto no debe fusionarse en la
     * lista). Con aplicación inmediata, al éxito la respuesta se fusiona
     * en el item local y la vista se repinta AL MOMENTO; después una
     * lectura puntual del detalle confirma el estado real y el toast
     * dice la verdad (si el panel no aplicó el cambio, lo dice).
     */
    private void doWrite(final String label, final String method, final String url,
                         final String body, final String successMsg,
                         final boolean enrichState, final String extraNote,
                         final boolean enrichHost, final Integer desiredStatus,
                         final boolean skipInstant) {
        if (actBusy) return;
        actBusy = true;
        actPrimaryBtn.setEnabled(false);
        actExtraBtn.setEnabled(false);
        actCancelBtn.setEnabled(false);
        actError.setVisibility(View.GONE);
        actStatus.setText("Enviando al panel…");
        actStatus.setVisibility(View.VISIBLE);
        exec.execute(() -> {
            ApiClient.Result r = new ApiClient(store).write(
                    label, method, url, body, enrichState, extraNote, enrichHost);
            handler.post(() -> {
                actBusy = false;
                if (r.authFailed) {
                    closeAct();
                    closeMenu();
                    abortIfAuthFailed(true);
                    return;
                }
                if (r.ambiguous) {
                    closeAct();
                    closeMenu();
                    closeDetail();
                    Toast.makeText(MainActivity.this,
                            "La respuesta se perdió: recargando para mostrar el "
                                    + "estado real (no se reintentó la acción)",
                            Toast.LENGTH_LONG).show();
                    // v0.8.5: si la escritura era de un túnel, basta UNA
                    // lectura de su detalle para mostrar el estado real
                    // al momento, sin esperar la recarga completa.
                    long ambId = skipInstant ? -1 : tunnelIdFromUrl(url);
                    if (ambId > 0) verifyTunnelNow(ambId, null, desiredStatus);
                    else {
                        homeLoaded = false;
                        loadHomeData();
                    }
                    return;
                }
                if (r.error != null || r.code < 200 || r.code >= 300) {
                    actPrimaryBtn.setEnabled(true);
                    actExtraBtn.setEnabled(true);
                    actCancelBtn.setEnabled(true);
                    actStatus.setVisibility(View.GONE);
                    String lit = r.error != null
                            ? "Sin respuesta: " + r.error
                            : "HTTP " + r.code + " · " + ApiDiag.scrub(ApiDiag.truncate(
                                    r.body != null ? r.body : "(sin cuerpo)", 600));
                    actShowError("El panel rechazó la acción. " + lit);
                    return;
                }
                closeAct();
                closeMenu();
                closeDetail();
                if (skipInstant) {
                    Toast.makeText(MainActivity.this, successMsg,
                            Toast.LENGTH_SHORT).show();
                    homeLoaded = false;
                    loadHomeData();
                    return;
                }
                // v0.8.5 — CAMBIOS AL INSTANTE (pedido de Gennis): la
                // respuesta de la escritura se fusiona en el item local
                // y la vista actual se repinta AHORA MISMO, sin esperar
                // a que termine la recarga completa del Home (que tarda
                // varios segundos y dejaba la duda de si se aplicó).
                long mergedId = mergeResponseObject(r.body);
                renderCurrentMain();
                if (mergedId > 0) {
                    // Confirmación puntual: una sola lectura del detalle
                    // (~medio segundo) y el toast dice el resultado REAL.
                    verifyTunnelNow(mergedId, successMsg, desiredStatus);
                } else {
                    Toast.makeText(MainActivity.this, successMsg,
                            Toast.LENGTH_SHORT).show();
                    if (mergedId == -1) {
                        homeLoaded = false;
                        loadHomeData();
                    }
                }
            });
        });
    }

    // ---------------- Aplicación inmediata de escrituras (v0.8.5) ----------------

    /** Id de túnel dentro de una URL «…/tunnels/<id>/…»; -1 si no hay. */
    private long tunnelIdFromUrl(String url) {
        if (url == null) return -1;
        Matcher m = Pattern.compile("/tunnels/(\\d+)/").matcher(url);
        if (m.find()) {
            try {
                return Long.parseLong(m.group(1));
            } catch (Exception e) {
                return -1;
            }
        }
        return -1;
    }

    /** Item local de un túnel por id (el mismo objeto de dataTunnels). */
    private JSONObject findTunnel(long id) {
        if (dataTunnels == null) return null;
        for (int i = 0; i < dataTunnels.length(); i++) {
            JSONObject t = dataTunnels.optJSONObject(i);
            if (t == null) continue;
            Object v = t.opt("id");
            if (v instanceof Number && ((Number) v).longValue() == id) return t;
        }
        return null;
    }

    /** Item local de una cuenta por id (el mismo objeto de dataUsers). */
    private JSONObject findUser(long id) {
        if (dataUsers == null) return null;
        for (int i = 0; i < dataUsers.length(); i++) {
            JSONObject u = dataUsers.optJSONObject(i);
            if (u == null) continue;
            Object v = u.opt("id");
            if (v instanceof Number && ((Number) v).longValue() == id) return u;
        }
        return null;
    }

    /**
     * Fusiona el objeto de una respuesta de escritura en el item local
     * correspondiente (túnel o cuenta), clave a clave. Devuelve: el id
     * del túnel si era un túnel, -2 si era una cuenta, -1 si la
     * respuesta no es un objeto reconocible. La «zone» de la respuesta
     * anidada no trae nombre legible: si la local sí lo tiene, se
     * conserva la local para no perder la etiqueta hasta la recarga.
     */
    private long mergeResponseObject(String body) {
        Object root = JsonHelp.parse(body);
        if (!(root instanceof JSONObject)) return -1;
        JSONObject obj = (JSONObject) root;
        Object idv = obj.opt("id");
        if (!(idv instanceof Number)) return -1;
        long id = ((Number) idv).longValue();
        boolean looksUser = obj.has("tunnels_count") || obj.has("telegram_username");
        JSONObject local = looksUser ? findUser(id) : findTunnel(id);
        if (local == null) return looksUser ? -2 : -1;
        Iterator<String> it = obj.keys();
        while (it.hasNext()) {
            String k = it.next();
            if ("zone".equals(k) && obj.opt(k) instanceof JSONObject
                    && DataHelp.zoneLabel(obj) == null
                    && DataHelp.zoneLabel(local) != null) {
                continue;
            }
            try {
                local.put(k, obj.opt(k));
            } catch (Exception ignored) {
                // clave no fusionable: la recarga completa la traerá
            }
        }
        return looksUser ? -2 : id;
    }

    /** Repinta la vista principal actual con los datos locales de ahora. */
    private void renderCurrentMain() {
        if (state == State.CUENTAS) renderAccounts();
        else if (state == State.TUNELES) renderTunnels();
        else if (state == State.PAGOS) renderPagos();
        else if (state == State.HOME) refreshHomeCardsFromLocal();
    }

    /** Cards del Home recalculadas desde los datos locales (sin red). */
    private void refreshHomeCardsFromLocal() {
        if (dataUsers != null) {
            HomeStats.UsersStats us = HomeStats.users(dataUsers, dataUsersCount);
            cardCuentasValue.setText(String.valueOf(us.total));
            if (us.conTuneles != null) {
                cardCuentasSub.setText("Con túneles " + us.conTuneles
                        + " · Sin túneles " + us.sinTuneles);
            }
        }
        if (dataTunnels != null) {
            HomeStats.TunnelsStats ts = HomeStats.tunnels(dataTunnels, null);
            cardTunelesValue.setText(String.valueOf(ts.total));
            if (ts.activos != null) {
                cardTunelesSub.setText("Activos " + ts.activos
                        + " · Inactivos " + ts.inactivos);
            }
        }
    }

    /**
     * Confirmación puntual tras una escritura de túnel: UNA lectura del
     * detalle, fusiona lo que devuelva y repinta. El toast final dice
     * el resultado REAL: con «desiredStatus» (deshabilitar/habilitar)
     * confirma si el estado quedó como se pidió o avisa que el panel
     * sigue mostrando el anterior; sin él, añade el host resultante.
     * Si la lectura falla, manda el mensaje de éxito de la escritura
     * (su respuesta ya se fusionó y se está viendo).
     */
    private void verifyTunnelNow(final long tunnelId, final String okMsg,
                                 final Integer desiredStatus) {
        exec.execute(() -> {
            ApiClient.Result r = new ApiClient(store).rawGet(
                    PanelConfig.tunnelDetailUrl(tunnelId));
            if (r.authFailed) {
                handler.post(() -> abortIfAuthFailed(true));
                return;
            }
            final boolean read = r.error == null && r.code >= 200 && r.code < 300
                    && r.body != null;
            if (read) {
                ApiDiag.add("Verificación tras escritura", "GET",
                        PanelConfig.tunnelDetailUrl(tunnelId), r.code, r.ms,
                        "lectura puntual del detalle para mostrar el estado "
                                + "real tras la escritura");
            }
            handler.post(() -> {
                if (read) mergeResponseObject(r.body);
                renderCurrentMain();
                JSONObject item = findTunnel(tunnelId);
                if (desiredStatus != null) {
                    int st = item != null ? HomeStats.statusOf(item) : -1;
                    if (st == desiredStatus) {
                        Toast.makeText(MainActivity.this,
                                okMsg + " · confirmado en el panel",
                                Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(MainActivity.this,
                                "SIN CAMBIO: el panel sigue mostrando el túnel "
                                        + (st == 1 ? "ACTIVO" : st == 0
                                        ? "INACTIVO" : "en un estado no reconocido")
                                        + ". El detalle está en el Diagnóstico.",
                                Toast.LENGTH_LONG).show();
                    }
                } else if (okMsg != null) {
                    String host = item != null ? DataHelp.hostLabel(item) : null;
                    Toast.makeText(MainActivity.this,
                            host != null ? okMsg + " · " + host : okMsg,
                            Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    // ---------------- Descargar configuración (lectura) ----------------

    /** ¿El texto parece una configuración WireGuard? (solo marcadores). */
    private boolean looksLikeConfig(String body) {
        if (body == null) return false;
        return body.contains("[Interface]")
                || body.contains("PrivateKey =")
                || (body.contains("Address =") && body.contains("Endpoint ="));
    }

    /** Busca la config como campo de texto dentro de un JSON (2 niveles). */
    private String extractConfig(JSONObject obj) {
        String[] preferred = {"config", "conf", "content", "file", "text",
                "data", "value", "wireguard", "configuration"};
        for (String k : preferred) {
            Object v = obj.opt(k);
            if (v instanceof String && looksLikeConfig((String) v)) return (String) v;
            if (v instanceof JSONObject) {
                String nested = extractConfig((JSONObject) v);
                if (nested != null) return nested;
            }
        }
        java.util.Iterator<String> it = obj.keys();
        while (it.hasNext()) {
            Object v = obj.opt(it.next());
            if (v instanceof String && looksLikeConfig((String) v)) return (String) v;
        }
        return null;
    }

    /**
     * Procesa una respuesta de lectura buscando la configuración: devuelve
     * {configONula, formaLegible}. La forma describe SOLO estructura (tipo
     * JSON, claves, marcadores) — el contenido jamás se anota.
     */
    private String[] configFromResponse(ApiClient.Result r) {
        String found = null;
        String forma;
        if (r.error != null) {
            forma = "sin respuesta: " + r.error;
        } else if (r.body == null) {
            forma = "sin cuerpo";
        } else {
            Object parsed = JsonHelp.parse(r.body);
            if (parsed instanceof JSONObject) {
                found = extractConfig((JSONObject) parsed);
                forma = found != null
                        ? "JSON con la configuración en un campo de texto"
                        : JsonHelp.describe(r.body);
            } else if (looksLikeConfig(r.body)) {
                found = r.body;
                forma = "texto con marcadores de configuración WireGuard";
            } else {
                forma = JsonHelp.describe(r.body);
            }
        }
        return new String[]{found, forma};
    }

    /**
     * Descarga la .conf del túnel. v0.8.1: cadena de LECTURAS (las
     * lecturas sí encadenan candidatos), parando en el primer 200:
     * (1) GET /api/v1/tunnels/<id>/download-config/?personal=false
     * (la ruta documentada con el alcance de la lista — a secas dio el
     * 404 genérico de la familia del detalle en la ronda 3);
     * (2) si responde 404, GET /api/manager/v1/tunnels/<id>/download-config/
     * (la familia manager CON GUION, nunca probada).
     * Cada intento queda registrado POR SEPARADO en el Diagnóstico,
     * SOLO con tipo de contenido, tamaño y forma — el contenido jamás
     * se anota. Si el item declara can_download_config=false, avisa y
     * no intenta.
     */
    private void startDownload(final JSONObject t) {
        if (Boolean.FALSE.equals(t.opt("can_download_config"))) {
            Object ido = t.opt("id");
            ApiDiag.add("Descarga de configuración", "—",
                    ido instanceof Number
                            ? PanelConfig.tunnelUrl(((Number) ido).longValue())
                            : PanelConfig.TUNNELS_URL_ALL,
                    0, 0, "el item declara can_download_config=false: no se intentó");
            Toast.makeText(this, "Este túnel declara can_download_config = false: "
                    + "el panel no ofrece su configuración.", Toast.LENGTH_LONG).show();
            return;
        }
        final long id = requireId(t);
        if (id < 0) return;
        final String name = DataHelp.fieldStr(t, "name");
        openAct("Descargar configuración");
        actTunnel = t;
        actRow("Túnel", name);
        actText("Buscando la configuración en el panel…", TEXT2, 13);
        actPrimaryBtn.setVisibility(View.GONE);
        exec.execute(() -> {
            ApiClient api = new ApiClient(store);
            // v0.8.1: cadena de lecturas, parando en el primer 200.
            // Intento 1: la ruta documentada con el alcance de la lista.
            String url = PanelConfig.downloadConfigUrl(id);
            ApiClient.Result r = api.rawGet(url);
            String[] res = configFromResponse(r);
            ApiDiag.add("Descarga de configuración", "GET", url, r.code, r.ms,
                    "fuente: ruta documentada con ?personal=false"
                            + " · tipo: " + (r.contentType != null ? r.contentType : "—")
                            + " · tamaño: " + (r.body != null ? r.body.length() : 0)
                            + " caracteres · forma: " + res[1]
                            + " · contenido NO anotado");
            // Intento 2 (solo si el primero respondió 404): la misma
            // ruta en la familia manager, con guion (nunca probada).
            if (r.code == 404 && !r.authFailed) {
                String url2 = PanelConfig.downloadConfigManagerUrl(id);
                r = api.rawGet(url2);
                res = configFromResponse(r);
                ApiDiag.add("Descarga de configuración", "GET", url2, r.code, r.ms,
                        "fuente: familia manager (download-config)"
                                + " · tipo: " + (r.contentType != null ? r.contentType : "—")
                                + " · tamaño: " + (r.body != null ? r.body.length() : 0)
                                + " caracteres · forma: " + res[1]
                                + " · contenido NO anotado");
            }
            if (r.authFailed) {
                handler.post(() -> {
                    if (actOpen) actShowError("La sesión fue rechazada "
                            + "incluso tras el refresh.");
                });
                return;
            }
            final String cfg = res[0];
            handler.post(() -> {
                if (!actOpen || actTunnel != t) return;
                if (cfg == null) {
                    actShowError("Ninguna de las rutas probadas devolvió "
                            + "una configuración reconocible. Los intentos "
                            + "y la forma de cada respuesta están en el "
                            + "Diagnóstico.");
                    return;
                }
                showDownloadResult(t, name, cfg);
            });
        });
    }

    /** Nombre real con el que MediaStore guardó una fila de Descargas. */
    private String queryDisplayName(ContentResolver cr, Uri uri) {
        Cursor c = null;
        try {
            c = cr.query(uri, new String[]{MediaStore.Downloads.DISPLAY_NAME},
                    null, null, null);
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(MediaStore.Downloads.DISPLAY_NAME);
                if (idx >= 0) return c.getString(idx);
            }
        } catch (Exception ignored) {
            // sin nombre verificable: manda el deseado
        } finally {
            if (c != null) c.close();
        }
        return null;
    }

    /**
     * Guarda la config como <tunel>.conf (MediaStore en Android 10+; en
     * versiones viejas, Descargas pública o la carpeta de la app) y muestra
     * el resultado con botón «Compartir».
     * v0.8.3: el nombre sale del nombre PARA EL USUARIO del túnel
     * (name_for_user) si existe; si no, del interno. Y el guardado por
     * MediaStore ya NO declara MIME «text/plain»: con ese MIME el
     * proveedor añade al nombre la extensión derivada y el archivo
     * llegaba como .txt (reporte de Gennis). Tras guardar se verifica
     * el nombre real de la fila y, si difiere, se renombra a .conf; el
     * nombre mostrado es el almacenado. El contenido no cambia.
     */
    private void showDownloadResult(JSONObject t, String name, final String config) {
        String base = DataHelp.fieldStr(t, "name_for_user");
        if (base == null) base = name;
        String fileName = (base != null ? base : "tunel")
                .replaceAll("[^A-Za-z0-9._-]", "_") + ".conf";
        String shownName = fileName;
        Uri sharedUri = null;
        String lugar = null;
        try {
            byte[] bytes = config.getBytes("UTF-8");
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                // Sin MIME_TYPE: ver la nota v0.8.3 de arriba (el .txt
                // venía de la extensión derivada del MIME declarado).
                cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                ContentResolver cr = getContentResolver();
                Uri uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                if (uri != null) {
                    OutputStream os = cr.openOutputStream(uri);
                    if (os != null) {
                        os.write(bytes);
                        os.close();
                        sharedUri = uri;
                        lugar = "Descargas";
                        String stored = queryDisplayName(cr, uri);
                        if (stored != null && !stored.equals(fileName)) {
                            ContentValues fix = new ContentValues();
                            fix.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                            try {
                                cr.update(uri, fix, null, null);
                            } catch (Exception ignored) {
                                // si el renombre falla, manda el nombre real
                            }
                            String again = queryDisplayName(cr, uri);
                            if (again != null) stored = again;
                        }
                        if (stored != null) shownName = stored;
                    }
                }
            }
            if (lugar == null) {
                // Android ≤ 28: primero Descargas pública (con el permiso
                // heredado); si falla, la carpeta de la app (sin permiso).
                try {
                    File pub = Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS);
                    if (pub != null) {
                        if (!pub.exists()) pub.mkdirs();
                        FileOutputStream fos = new FileOutputStream(new File(pub, fileName));
                        fos.write(bytes);
                        fos.close();
                        lugar = "Descargas";
                    }
                } catch (Exception ignored) {
                    // cae al respaldo de la carpeta de la app
                }
                if (lugar == null) {
                    File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                    if (dir != null) {
                        FileOutputStream fos = new FileOutputStream(new File(dir, fileName));
                        fos.write(bytes);
                        fos.close();
                        lugar = "carpeta de la app";
                    }
                }
            }
        } catch (Exception e) {
            lugar = null;
        }
        actBody.removeAllViews();
        actRow("Túnel", name);
        actRow("Archivo", shownName);
        actRow("Tamaño", config.length() + " caracteres");
        final Uri fUri = sharedUri;
        if (lugar != null) {
            actRow("Guardado en", lugar);
            actText("La configuración quedó guardada. Compártela para "
                    + "importarla donde la necesites.", TEXT2, 13);
            actPrimaryBtn.setVisibility(View.GONE);
            setActExtra("Compartir", () -> {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                if (fUri != null) {
                    send.putExtra(Intent.EXTRA_STREAM, fUri);
                    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } else {
                    send.putExtra(Intent.EXTRA_TEXT, config);
                }
                startActivity(Intent.createChooser(send, "Compartir configuración"));
            });
            actCancelBtn.setText("← Volver");
        } else {
            actText("No se pudo guardar el archivo en este teléfono. Puedes "
                    + "copiar la configuración al portapapeles.", RED, 13);
            actPrimaryBtn.setVisibility(View.GONE);
            setActExtra("Copiar configuración", () -> {
                ClipboardManager cm = (ClipboardManager)
                        getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("config", config));
                    Toast.makeText(MainActivity.this, "Configuración copiada",
                            Toast.LENGTH_SHORT).show();
                }
            });
            actCancelBtn.setText("← Volver");
        }
    }

    // ---------------- Cambiar de servidor (descubrimiento + intento) ----------------

    private static class HostEntry {
        Object id;          // Number o String; null si la fuente no trae id
        String name;
        String status;
        boolean current;    // la propia respuesta lo marca como el actual
    }

    private HostEntry parseHost(Object item) {
        HostEntry h = new HostEntry();
        if (item instanceof JSONObject) {
            JSONObject o = (JSONObject) item;
            Object id = o.opt("id");
            if (id != null && id != JSONObject.NULL
                    && (id instanceof Number || id instanceof String)) h.id = id;
            h.name = DataHelp.str(o, "name", "label", "hostname", "title",
                    "display_name", "code");
            h.status = DataHelp.fieldStr(o, "status");
            if (h.status == null) h.status = DataHelp.fieldStr(o, "agent_status");
            if (h.status == null) {
                Object ia = o.opt("is_active");
                if (ia instanceof Boolean) h.status = (Boolean) ia ? "Activo" : "Inactivo";
            }
            for (String ck : new String[]{"current", "is_current",
                    "selected", "is_selected"}) {
                Object cv = o.opt(ck);
                if (cv instanceof Boolean && (Boolean) cv) { h.current = true; break; }
            }
        } else if (item instanceof String) {
            String s = ((String) item).trim();
            if (!s.isEmpty()) h.name = s;
        }
        return h.name != null ? h : null;
    }

    private List<HostEntry> hostsFromTunnels() {
        Map<String, HostEntry> byKey = new java.util.LinkedHashMap<>();
        if (dataTunnels == null) return new ArrayList<>();
        for (int i = 0; i < dataTunnels.length(); i++) {
            JSONObject t = dataTunnels.optJSONObject(i);
            if (t == null) continue;
            Object hv = t.opt("host");
            HostEntry h = hv != null ? parseHost(hv) : null;
            if (h == null) continue;
            String key = h.id != null ? "id:" + h.id : "name:" + h.name;
            if (!byKey.containsKey(key)) byKey.put(key, h);
        }
        return new ArrayList<>(byKey.values());
    }

    private boolean isCurrentHost(JSONObject t, HostEntry h) {
        Object hv = t.opt("host");
        if (hv instanceof JSONObject) {
            Object cid = ((JSONObject) hv).opt("id");
            if (cid != null && cid != JSONObject.NULL && h.id != null
                    && String.valueOf(cid).equals(String.valueOf(h.id))) return true;
        }
        String curName = DataHelp.hostLabel(t);
        return curName != null && curName.equals(h.name);
    }

    /**
     * Hoja «Cambiar de servidor»: primero CARGA la lista de servidores del
     * panel CON la ruta por túnel de la ronda 2 (GET /api/v1/hosts/
     * ?tunnel_id=<id> — en la ronda 1 el endpoint sin tunnel_id respondió
     * 400 pidiéndolo; api.get registra la forma entera de la respuesta:
     * claves del sobre y del primer item). Desde la v0.8.4 el registro
     * usa getWithPairs: la entrada añade los pares id=nombre de TODOS
     * los servidores, para cruzarlos con lo seleccionado y con el host
     * resultante de la escritura (hueco de registro de la ronda 5).
     * Si esta ruta también falla, muestra como respaldo los servidores
     * en uso vistos en los túneles, etiquetados como tales. El cambio
     * es UNA escritura por toque.
     */
    private void openServers(final JSONObject t) {
        final long id = requireId(t);
        if (id < 0) return;
        final String name = DataHelp.fieldStr(t, "name");
        openAct("Cambiar de servidor");
        actTunnel = t;
        actRow("Túnel", name);
        actRow("Servidor actual", DataHelp.hostLabel(t));
        actText("Cargando la lista de servidores del panel…", TEXT2, 13);
        actPrimaryBtn.setVisibility(View.GONE);
        exec.execute(() -> {
            ApiClient api = new ApiClient(store);
            List<HostEntry> hosts = null;
            boolean fromPanel = false;
            ApiClient.Result r = api.getWithPairs("Servidores del panel",
                    PanelConfig.hostsUrl(id));
            if (r.authFailed) {
                handler.post(() -> {
                    if (actOpen && actTunnel == t) {
                        actShowError("La sesión fue rechazada incluso "
                                + "tras el refresh.");
                    }
                });
                return;
            }
            if (r.error == null && r.code >= 200 && r.code < 300 && r.body != null) {
                JSONArray items = JsonHelp.itemsArray(JsonHelp.parse(r.body));
                if (items != null && items.length() > 0) {
                    List<HostEntry> parsed = new ArrayList<>();
                    for (int i = 0; i < items.length(); i++) {
                        HostEntry h = parseHost(items.opt(i));
                        if (h != null) parsed.add(h);
                    }
                    if (!parsed.isEmpty()) {
                        hosts = parsed;
                        fromPanel = true;
                    }
                }
            }
            if (hosts == null) hosts = hostsFromTunnels();
            final List<HostEntry> fHosts = hosts;
            final boolean fPanel = fromPanel;
            handler.post(() -> {
                if (!actOpen || actTunnel != t) return;
                actBody.removeAllViews();
                actRow("Túnel", name);
                actRow("Servidor actual", DataHelp.hostLabel(t));
                if (!fPanel) {
                    actText("La lista de servidores del panel no respondió. "
                            + "Estos son los servidores EN USO, vistos en tus "
                            + "túneles (respaldo, no la lista del panel).",
                            AMBER, 12);
                }
                if (fHosts.isEmpty()) {
                    actText("No se encontró ningún servidor.", TEXT2, 13);
                    return;
                }
                for (final HostEntry h : fHosts) renderServerRow(t, id, h);
            });
        });
    }

    private void renderServerRow(final JSONObject t, final long tunnelId,
                                 final HostEntry h) {
        LinearLayout card = makeCardBox();
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        card.setLayoutParams(lp);
        TextView tvName = new TextView(this);
        tvName.setText(h.name);
        tvName.setTextColor(TEXT);
        tvName.setTextSize(14);
        tvName.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(tvName);
        if (h.status != null) {
            TextView tvStatus = new TextView(this);
            tvStatus.setText(h.status);
            tvStatus.setTextColor(TEXT2);
            tvStatus.setTextSize(12);
            tvStatus.setPadding(0, dp(3), 0, 0);
            card.addView(tvStatus);
        }
        boolean current = h.current || isCurrentHost(t, h);
        if (current) {
            TextView tvCur = new TextView(this);
            tvCur.setText("● Servidor actual");
            tvCur.setTextColor(GREEN);
            tvCur.setTextSize(12);
            tvCur.setTypeface(Typeface.DEFAULT_BOLD);
            tvCur.setPadding(0, dp(6), 0, 0);
            card.addView(tvCur);
        } else if (h.id != null) {
            Button btn = makeButton("Cambiar a este servidor");
            btn.setTextSize(12);
            btn.setPadding(dp(12), dp(8), dp(12), dp(8));
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            bl.topMargin = dp(8);
            btn.setLayoutParams(bl);
            btn.setOnClickListener(v -> openReassignConfirm(t, tunnelId, h));
            card.addView(btn);
        } else {
            TextView tvNoId = new TextView(this);
            tvNoId.setText("Sin id en la respuesta: no se puede reasignar desde aquí.");
            tvNoId.setTextColor(TEXT2);
            tvNoId.setTextSize(11);
            tvNoId.setPadding(0, dp(6), 0, 0);
            card.addView(tvNoId);
        }
        actBody.addView(card);
    }

    private void openReassignConfirm(final JSONObject t, final long tunnelId,
                                     final HostEntry h) {
        String name = DataHelp.fieldStr(t, "name");
        openAct("Cambiar de servidor");
        actTunnel = t;
        actRow("Túnel", name);
        actRow("Servidor actual", DataHelp.hostLabel(t));
        actRow("Servidor nuevo", h.name);
        actText("El túnel pasará a funcionar en el servidor nuevo. Es una "
                + "escritura real en el panel.", AMBER, 13);
        setActPrimary("Cambiar servidor", () -> {
            String body;
            try {
                JSONObject b = new JSONObject();
                // v0.8.4: vía NUEVA — el PATCH del detalle con alcance y
                // cuerpo {"host": <id del host tocado>}: es como guarda el
                // servidor el formulario Configure de la web (pestaña
                // Network, «Selected Host»), sobre el mismo PATCH que ya
                // aceptó {notes} e {is_active}. El POST …/reassign-host/
                // con {"host_id"} de las rondas 4–5 respondía 200 pero el
                // túnel aterrizaba en OTRO host (43→S3-ATLANTA, 47→S1-CUBA,
                // 41→S3-CUBA): queda fuera de la app.
                if (h.id instanceof Number) b.put("host", ((Number) h.id).longValue());
                else b.put("host", String.valueOf(h.id));
                body = b.toString();
            } catch (Exception e) {
                actShowError("No se pudo construir la petición.");
                return;
            }
            // Registro que discrimina (ronda 5): lo seleccionado (nombre e
            // id) viaja en la entrada de Intentos, y la respuesta aporta el
            // host resultante — si difieren, la API no honra el host
            // pedido ni por esta vía; si coinciden, la vía queda probada.
            doWrite("Escritura — cambiar de servidor", "PATCH",
                    PanelConfig.tunnelDetailUrl(tunnelId), body,
                    "Servidor cambiado", true,
                    "seleccionado: " + h.name + " (id " + h.id + ")", true);
        });
    }

    // ---------------- Regenerar / Reaprovisionar ----------------

    private void openRegenerate(final JSONObject t) {
        final long id = requireId(t);
        if (id < 0) return;
        openAct("Regenerar túnel");
        actTunnel = t;
        actRow("Túnel", DataHelp.fieldStr(t, "name"));
        actText("Regenerar cambia las CLAVES del túnel: la configuración "
                + "actual dejará de funcionar y el cliente tendrá que "
                + "importar la configuración nueva.", AMBER, 13);
        setActPrimary("Regenerar", () -> doWrite(
                "Escritura — regenerar túnel", "POST",
                PanelConfig.tunnelUrl(id) + PanelConfig.REGENERATE_SUFFIX,
                "{}", "Túnel regenerado"));
    }

    private void openReprovision(final JSONObject t) {
        final long id = requireId(t);
        if (id < 0) return;
        openAct("Reaprovisionar");
        actTunnel = t;
        actRow("Túnel", DataHelp.fieldStr(t, "name"));
        actText("Reaprovisionar vuelve a aprovisionar el túnel en el "
                + "servidor (se recrea su configuración en el host).",
                AMBER, 13);
        setActPrimary("Reaprovisionar", () -> doWrite(
                "Escritura — reaprovisionar", "POST",
                PanelConfig.tunnelUrl(id) + PanelConfig.REPROVISION_SUFFIX,
                "{}", "Túnel reaprovisionado"));
    }

    // ---------------- Deshabilitar / Habilitar ----------------

    private void openToggle(final JSONObject t) {
        final long id = requireId(t);
        if (id < 0) return;
        int st = HomeStats.statusOf(t);
        openAct(st == 0 ? "Habilitar túnel" : "Deshabilitar túnel");
        actTunnel = t;
        actRow("Túnel", DataHelp.fieldStr(t, "name"));
        if (st == 1) {
            actText("El túnel dejará de funcionar hasta que lo habilites. "
                    + "La cuenta y los demás túneles no se tocan.", AMBER, 13);
            setActPrimary("Deshabilitar", () -> toggleWrite(id, false));
        } else if (st == 0) {
            actText("El túnel volverá a estar activo.", TEXT2, 13);
            setActPrimary("Habilitar", () -> toggleWrite(id, true));
        } else {
            actText("El estado actual no se reconoció en la respuesta del "
                    + "panel. Elige la acción: el estado real se confirma con "
                    + "la recarga posterior.", AMBER, 13);
            setActPrimary("Deshabilitar", () -> toggleWrite(id, false));
            setActExtra("Habilitar", () -> toggleWrite(id, true));
        }
    }

    /**
     * Deshabilitar/Habilitar — v0.8.8: por {status}, EL MECANISMO.
     *
     * El «dif de la web» lo resolvió (ronda 10, 2026-10-31 14:29):
     * al apagar el 558 con el interruptor del panel, el objeto pasó
     * a status="disabled" CON disabled_date puesta POR EL SERVIDOR
     * (la hora exacta del guardado web). Es decir: disabled_date
     * es un campo de SALIDA que el backend rellena al cambiar el
     * estado — por eso escribirlo desde la app se ignoró siempre
     * (rondas 8/9) — y el campo que SÍ se escribe es «status»: la
     * prueba es el 400 de la ronda 7 («inactive» no es una elección
     * válida): un campo de solo lectura no valida opciones al
     * escribir, se ignora en silencio. Vocabulario: active / grace
     * / disabled / expired. Reinterpretación de la ronda 4: el
     * {"status":"disabled"} de la 0.8.1 (200, 11:13:34) casi
     * seguro SÍ deshabilitó a Diego, y el cambio de servidor de las
     * 11:15:36 (reassign-host = reaprovisiona) lo reactivó antes de
     * la verificación del panel de las 11:17 — el veredicto «no se
     * aplicó» queda corregido: no hubo ninguna lectura entre la
     * escritura y el reaprovisionamiento que lo desmintiera.
     * {is_active:false} (ronda 6) sí fue un no-op genuino: ese
     * campo es del USUARIO, no del túnel. La v0.8.8 escribe
     * {"status":"disabled"} / {"status":"active"} y la verificación
     * puntual confirma (la respuesta trae status y la disabled_date
     * que el servidor haya puesto o limpiado).
     */
    private void toggleWrite(final long tunnelId, final boolean enable) {
        final String body;
        try {
            body = new JSONObject()
                    .put("status", enable ? "active" : "disabled")
                    .toString();
        } catch (Exception e) {
            actShowError("No se pudo preparar el cambio de estado. "
                    + "No se envió ningún cambio.");
            return;
        }
        doWrite(enable ? "Escritura — habilitar túnel"
                        : "Escritura — deshabilitar túnel",
                "PATCH", PanelConfig.tunnelDetailUrl(tunnelId), body,
                enable ? "Túnel habilitado" : "Túnel deshabilitado",
                true, "campo: status=" + (enable ? "active" : "disabled")
                        + " · el servidor pone/limpia disabled_date "
                        + "(dif de la web, ronda 10)",
                false, enable ? 1 : 0, false);
    }

    // ---------------- Editar nota (túnel y cuenta) ----------------

    private void openTunnelNoteEditor(final JSONObject t) {
        final long id = requireId(t);
        if (id < 0) return;
        // v0.8.0: NO hay endpoint de notas en el índice Developers; la
        // nota del túnel se edita con el PATCH del detalle documentado
        // (PATCH /api/v1/tunnels/<id>/ {notes}). Sustituye a la ruta
        // manager de la ronda 2 (404: no hay recurso manager de
        // túneles). La nota de cuenta no se toca.
        // v0.8.1: el PATCH va al detalle con ?personal=false (sonda de
        // una sola variable tras el 404 genérico de la ronda 3).
        openNoteEditor("Editar nota del túnel",
                DataHelp.fieldStr(t, "name"),
                DataHelp.fieldStr(t, "notes"),
                "Escritura — editar nota (túnel)",
                PanelConfig.tunnelDetailUrl(id));
        actTunnel = t;
    }

    private void openAccountNoteEditor(final JSONObject u, final long userId) {
        openNoteEditor("Editar nota de la cuenta",
                DataHelp.fieldStr(u, "username"),
                DataHelp.fieldStr(u, "notes"),
                "Escritura — editar nota (cuenta)",
                PanelConfig.userUrl(userId));
    }

    private void openNoteEditor(String title, String ownerLabel, String current,
                                final String diagLabel, final String url) {
        openAct(title);
        if (ownerLabel != null) actRow("De", ownerLabel);
        final EditText input = makeTextInput("Nota", true);
        input.setText(current != null ? current : "");
        LinearLayout.LayoutParams il = fullWidthLp();
        il.topMargin = dp(6);
        actBody.addView(input, il);
        actText("La nota se guarda en el panel (campo «notes»), la misma que "
                + "se ve y se edita en la web.", TEXT2, 12);
        setActPrimary("Guardar nota", () -> {
            String body;
            try {
                body = new JSONObject()
                        .put("notes", input.getText() != null
                                ? input.getText().toString() : "")
                        .toString();
            } catch (Exception e) {
                actShowError("No se pudo construir la petición.");
                return;
            }
            doWrite(diagLabel, "PATCH", url, body, "Nota guardada");
        });
    }

    // ---------------- Creación (v0.9.0: cerrar la parte WEB) ----------------

    /** Estado del formulario de cuenta nueva (sobrevive a los selectores). */
    private static class NewAccountForm {
        String username = "", password = "", alias = "", telegram = "", notes = "";
        long zoneId = -1;
        String zoneName = null;
        boolean active = true;
    }

    /** Estado del formulario de túnel nuevo (sobrevive a los selectores). */
    private static class NewTunnelForm {
        long ownerId = -1;
        String ownerName = null;
        String name = "", nameForUser = "", notes = "";
        long zoneId = -1;
        String zoneName = null;
        long hostId = -1;
        String hostName = null;
        // Por defecto AUTOMÁTICO (pedido de Gennis, ronda 20): el
        // formulario nace en automático hasta que se elige otro.
        boolean autoHost = true;
        boolean manual = true;
        // Por lotes (v0.9.3): nombre base y cantidad; el panel enumera.
        String basePrefix = "";
        String countText = "2";
    }

    private String fieldText(EditText e) {
        return e.getText() != null ? e.getText().toString().trim() : "";
    }

    /** Fila interruptor Sí/No para formularios de la hoja de acción. */
    private void actToggleRow(String label, final boolean[] holder) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, 0);
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(TEXT);
        tv.setTextSize(13);
        row.addView(tv, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final Button b = makeButton(holder[0] ? "Sí" : "No");
        b.setTextSize(12);
        b.setPadding(dp(16), dp(7), dp(16), dp(7));
        b.setOnClickListener(v -> {
            holder[0] = !holder[0];
            b.setText(holder[0] ? "Sí" : "No");
        });
        row.addView(b);
        actBody.addView(row);
    }

    /** Botón selector de formulario: muestra la elección actual. */
    private Button actSelector(String label, String current, View.OnClickListener onTap) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(TEXT2);
        tv.setTextSize(12);
        tv.setPadding(0, dp(10), 0, dp(4));
        actBody.addView(tv);
        Button b = makeButton(current);
        LinearLayout.LayoutParams lp = fullWidthLp();
        actBody.addView(b, lp);
        b.setOnClickListener(onTap);
        return b;
    }

    /** Id de zona de un item, si su «zone» trae uno utilizable. */
    private Long zoneIdOf(JSONObject u) {
        if (u == null) return null;
        Object z = u.opt("zone");
        if (z instanceof JSONObject) {
            Object ido = ((JSONObject) z).opt("id");
            if (ido instanceof Number) return ((Number) ido).longValue();
        } else if (z instanceof Number) {
            return ((Number) z).longValue();
        }
        return null;
    }

    /** Zonas con id vistas en las cuentas cargadas: pares {id, nombre}. */
    private List<Object[]> knownZones() {
        List<Object[]> out = new ArrayList<>();
        if (dataUsers == null) return out;
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (int i = 0; i < dataUsers.length(); i++) {
            JSONObject u = dataUsers.optJSONObject(i);
            if (u == null) continue;
            Long zid = zoneIdOf(u);
            if (zid != null && seen.add(zid)) {
                String zn = DataHelp.zoneLabel(u);
                out.add(new Object[]{zid, zn != null ? zn : "Zona #" + zid});
            }
        }
        return out;
    }

    /** Fila tocable de un selector (tarjeta compacta con título y detalle). */
    private void pickerRow(String title, String detail, View.OnClickListener onTap) {
        pickerRowInto(actBody, title, detail, onTap);
    }

    private void pickerRowInto(LinearLayout box, String title, String detail,
                               View.OnClickListener onTap) {
        LinearLayout card = makeCardBox();
        card.setPadding(dp(14), dp(11), dp(14), dp(11));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        card.setLayoutParams(lp);
        TextView tvT = new TextView(this);
        tvT.setText(title);
        tvT.setTextColor(TEXT);
        tvT.setTextSize(14);
        tvT.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(tvT);
        if (detail != null) {
            TextView tvD = new TextView(this);
            tvD.setText(detail);
            tvD.setTextColor(TEXT2);
            tvD.setTextSize(12);
            tvD.setPadding(0, dp(3), 0, 0);
            card.addView(tvD);
        }
        card.setOnClickListener(onTap);
        box.addView(card);
    }

    /**
     * Selector de propietario (cuentas cargadas) para crear, CON
     * BUSCADOR (pedido de Gennis, ronda 20: con 60+ cuentas no se
     * puede ir desplazando). Filtra por usuario o alias al escribir.
     */
    private void openOwnerPicker(final NewTunnelForm f, final Runnable back) {
        openAct("Seleccionar propietario");
        actPrimaryBtn.setVisibility(View.GONE);
        setActExtra("← Volver", () -> back.run());
        if (dataUsers == null || dataUsers.length() == 0) {
            actText("Las cuentas aún no cargaron. Cierra y vuelve a intentarlo.",
                    TEXT2, 13);
            return;
        }
        EditText search = makeTextInput("Buscar por usuario o alias…", false);
        LinearLayout.LayoutParams sl = fullWidthLp();
        sl.topMargin = dp(4);
        actBody.addView(search, sl);
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        actBody.addView(box, fullWidthLp());
        renderOwnerRows(box, f, back, "");
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                renderOwnerRows(box, f, back, s != null ? s.toString() : "");
            }
        });
    }

    private void renderOwnerRows(LinearLayout box, final NewTunnelForm f,
                                 final Runnable back, String query) {
        box.removeAllViews();
        String q = query.trim().toLowerCase(Locale.ROOT);
        int shown = 0;
        for (int i = 0; i < dataUsers.length(); i++) {
            final JSONObject u = dataUsers.optJSONObject(i);
            if (u == null) continue;
            Object ido = u.opt("id");
            if (!(ido instanceof Number)) continue;
            final long uid = ((Number) ido).longValue();
            final String uname = DataHelp.fieldStr(u, "username");
            if (uname == null) continue;
            String alias = DataHelp.fieldStr(u, "alias");
            if (!q.isEmpty()
                    && !uname.toLowerCase(Locale.ROOT).contains(q)
                    && (alias == null
                            || !alias.toLowerCase(Locale.ROOT).contains(q))) {
                continue;
            }
            Object tc = u.opt("tunnels_count");
            String detail = (alias != null ? "Alias: " + alias : null);
            if (tc instanceof Number) {
                detail = (detail != null ? detail + " · " : "")
                        + ((Number) tc).intValue() + " túneles";
            }
            shown++;
            pickerRowInto(box, uname, detail, v -> {
                f.ownerId = uid;
                f.ownerName = uname;
                // Si el formulario aún no tiene zona y la cuenta sí, se
                // hereda la suya: es de donde el panel resuelve el host.
                if (f.zoneId < 0) {
                    Long zid = zoneIdOf(u);
                    if (zid != null) {
                        f.zoneId = zid;
                        String zn = DataHelp.zoneLabel(u);
                        f.zoneName = zn != null ? zn : "Zona #" + zid;
                    }
                }
                back.run();
            });
        }
        if (shown == 0) {
            TextView tv = new TextView(this);
            tv.setText("Sin cuentas que coincidan con la búsqueda.");
            tv.setTextColor(TEXT2);
            tv.setTextSize(13);
            tv.setPadding(0, dp(10), 0, 0);
            box.addView(tv);
        }
    }

    /** Selector de zona para los formularios de creación. */
    private void openZonePicker(final long[] idHolder, final String[] nameHolder,
                                final Runnable back) {
        openAct("Seleccionar zona");
        actPrimaryBtn.setVisibility(View.GONE);
        setActExtra("← Volver", () -> back.run());
        pickerRow("Sin zona", "No se envía el campo: el panel aplica su criterio.",
                v -> {
                    idHolder[0] = -1;
                    nameHolder[0] = null;
                    back.run();
                });
        List<Object[]> zones = knownZones();
        if (zones.isEmpty()) {
            actText("En las cuentas cargadas no se ven zonas con id; por eso "
                    + "solo se ofrece «Sin zona».", TEXT2, 12);
        }
        for (final Object[] z : zones) {
            pickerRow((String) z[1], "Zona #" + z[0], v -> {
                idHolder[0] = (Long) z[0];
                nameHolder[0] = (String) z[1];
                back.run();
            });
        }
    }

    /** Añade a la hoja un campo ya creado, con su etiqueta. */
    private void actAddField(String label, EditText e) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(TEXT2);
        tv.setTextSize(12);
        tv.setPadding(0, dp(10), 0, dp(4));
        actBody.addView(tv);
        actBody.addView(e, fullWidthLp());
    }

    /**
     * Selector de servidor inicial para el túnel nuevo (v0.9.1): la
     * ronda 15 respondió «Host no resuelto» a una creación sin zona ni
     * host. La lista sale de los servidores EN USO en los túneles
     * cargados (los mismos del respaldo de «Cambiar de servidor»);
     * elegir uno envía el campo «host», el que el PATCH del detalle
     * ya aceptó en la ronda 6.
     */
    private void openCreateHostPicker(final NewTunnelForm f, final Runnable back) {
        openAct("Servidor inicial");
        actPrimaryBtn.setVisibility(View.GONE);
        setActExtra("← Volver", () -> back.run());
        pickerRow("Automático (como la web)",
                "En el túnel individual lo asigna el panel "
                        + "(«is_auto_generated», ronda 17). En lotes lo "
                        + "elige la app: el automático de lotes del "
                        + "panel está roto (ronda 19).",
                v -> {
                    f.autoHost = true;
                    f.hostId = -1;
                    f.hostName = null;
                    back.run();
                });
        List<HostEntry> hosts = createHostPool();
        if (hosts.isEmpty()) {
            actText("No se ven servidores en los túneles cargados.", TEXT2, 12);
            return;
        }
        for (final HostEntry h : hosts) {
            if (h.id == null || h.name == null) continue;
            final long hid;
            if (h.id instanceof Number) {
                hid = ((Number) h.id).longValue();
            } else {
                try {
                    hid = Long.parseLong(String.valueOf(h.id).trim());
                } catch (Exception e) {
                    continue;
                }
            }
            pickerRow(h.name, h.status, v -> {
                f.autoHost = false;
                f.hostId = hid;
                f.hostName = h.name;
                back.run();
            });
        }
    }

    /** Formulario «Crear cuenta» — POST documentado a la colección de usuarios. */
    private void openCreateAccount(final NewAccountForm f) {
        openAct("Crear cuenta");
        final long[] zid = {f.zoneId};
        final String[] zname = {f.zoneName};
        final boolean[] act = {f.active};
        final EditText inUser = makeTextInput("Nombre de usuario", false);
        inUser.setText(f.username);
        final EditText inPass = makeTextInput("Contraseña inicial", false);
        inPass.setText(f.password);
        inPass.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        final EditText inAlias = makeTextInput("Alias (opcional)", false);
        inAlias.setText(f.alias);
        final EditText inTg = makeTextInput("Usuario de Telegram (opcional)", false);
        inTg.setText(f.telegram);
        final EditText inNotes = makeTextInput("Notas (opcional)", true);
        inNotes.setText(f.notes);
        final Runnable capture = () -> {
            f.username = fieldText(inUser);
            f.password = inPass.getText() != null ? inPass.getText().toString() : "";
            f.alias = fieldText(inAlias);
            f.telegram = fieldText(inTg);
            f.notes = fieldText(inNotes);
            f.zoneId = zid[0];
            f.zoneName = zname[0];
            f.active = act[0];
        };
        actAddField("Usuario *", inUser);
        actAddField("Contraseña inicial *", inPass);
        actAddField("Alias", inAlias);
        actAddField("Telegram", inTg);
        actSelector("Zona", f.zoneName != null ? f.zoneName : "Sin zona",
                v -> {
                    capture.run();
                    openZonePicker(zid, zname, () -> {
                        f.zoneId = zid[0];
                        f.zoneName = zname[0];
                        openCreateAccount(f);
                    });
                });
        actToggleRow("Acceso habilitado", act);
        actAddField("Notas internas", inNotes);
        actText("Se crea con la ruta documentada del panel (POST a la "
                + "colección de usuarios), con los campos del formulario "
                + "web de creación. La respuesta del servidor queda entera "
                + "en el Diagnóstico.", TEXT2, 12);
        setActPrimary("Crear cuenta", () -> {
            capture.run();
            if (f.username.isEmpty() || f.password.isEmpty()) {
                actShowError("Faltan el usuario y la contraseña inicial.");
                return;
            }
            String body;
            try {
                JSONObject o = new JSONObject();
                o.put("username", f.username);
                o.put("password", f.password);
                if (!f.alias.isEmpty()) o.put("alias", f.alias);
                if (!f.telegram.isEmpty()) o.put("telegram_username", f.telegram);
                if (f.zoneId > 0) o.put("zone", f.zoneId);
                if (!f.notes.isEmpty()) o.put("notes", f.notes);
                o.put("is_active", f.active);
                body = o.toString();
            } catch (Exception e) {
                actShowError("No se pudo construir la petición.");
                return;
            }
            doWrite("Escritura — crear cuenta", "POST", PanelConfig.USERS_URL,
                    body, "Cuenta creada", false,
                    "ruta documentada: POST /api/manager/v1/accounts/users/ · "
                            + "campos del formulario web de creación",
                    false, null, true);
        });
    }

    /** Fila de modo «Un túnel / Por lotes» de los formularios de creación. */
    private void actModeRow(boolean batchActive, final Runnable toSingle,
                            final Runnable toBatch) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(4), 0, dp(2));
        Button b1 = makeButton("Un túnel");
        Button b2 = makeButton("Por lotes");
        styleModeBtn(b1, !batchActive);
        styleModeBtn(b2, batchActive);
        b1.setOnClickListener(v -> { if (batchActive) toSingle.run(); });
        b2.setOnClickListener(v -> { if (!batchActive) toBatch.run(); });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(b1, lp);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp2.leftMargin = dp(8);
        row.addView(b2, lp2);
        actBody.addView(row);
    }

    private void styleModeBtn(Button b, boolean selected) {
        b.setTextSize(12);
        b.setTextColor(selected ? CYAN : TEXT2);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(10));
        bg.setStroke(dp(1), selected ? CYAN : BORDER);
        b.setBackground(bg);
        b.setPadding(dp(10), dp(8), dp(10), dp(8));
    }

    /** Formulario «Crear túnel» — POST documentado a la colección de túneles. */
    private void openCreateTunnel(final NewTunnelForm f) {
        openAct("Crear túnel");
        ensurePanelHosts();
        final long[] zid = {f.zoneId};
        final String[] zname = {f.zoneName};
        final boolean[] man = {f.manual};
        final EditText inName = makeTextInput("Nombre del túnel", false);
        inName.setText(f.name);
        final EditText inNfu = makeTextInput("Nombre visible (opcional)", false);
        inNfu.setText(f.nameForUser);
        final EditText inNotes = makeTextInput("Notas (opcional)", true);
        inNotes.setText(f.notes);
        final Runnable capture = () -> {
            f.name = fieldText(inName);
            f.nameForUser = fieldText(inNfu);
            f.notes = fieldText(inNotes);
            f.zoneId = zid[0];
            f.zoneName = zname[0];
            f.manual = man[0];
        };
        actModeRow(false, () -> {}, () -> {
            capture.run();
            openCreateBatch(f);
        });
        actSelector("Propietario *",
                f.ownerName != null ? f.ownerName : "Seleccionar propietario…",
                v -> {
                    capture.run();
                    openOwnerPicker(f, () -> openCreateTunnel(f));
                });
        actAddField("Nombre interno * (letras, números y guiones)", inName);
        actAddField("Nombre para el usuario (máx. 20)", inNfu);
        actSelector("Zona", f.zoneName != null ? f.zoneName : "Sin zona",
                v -> {
                    capture.run();
                    openZonePicker(zid, zname, () -> {
                        f.zoneId = zid[0];
                        f.zoneName = zname[0];
                        openCreateTunnel(f);
                    });
                });
        actSelector("Servidor inicial",
                f.autoHost ? "Automático (como la web)"
                        : f.hostName != null ? f.hostName : "Seleccionar servidor…",
                v -> {
                    capture.run();
                    openCreateHostPicker(f, () -> openCreateTunnel(f));
                });
        actToggleRow("Pago manual", man);
        actAddField("Notas administrativas", inNotes);
        actText("El servidor inicial es obligatorio salvo en modo "
                + "Automático: sin él (y sin la bandera de automático) "
                + "el panel responde «Host no resuelto», y la zona sola "
                + "no basta (rondas 15–16). Protocolo: WireGuard. La "
                + "respuesta del servidor queda entera en el "
                + "Diagnóstico.", TEXT2, 12);
        setActPrimary("Crear túnel", () -> {
            capture.run();
            if (f.ownerId < 0) {
                actShowError("Selecciona el propietario del túnel.");
                return;
            }
            if (f.name.isEmpty()) {
                actShowError("Falta el nombre interno del túnel.");
                return;
            }
            if (!f.name.matches("[A-Za-z0-9-]+")) {
                actShowError("El nombre solo puede contener letras, "
                        + "números y guiones (sin espacios ni guiones "
                        + "bajos): el panel lo rechaza (ronda 16).");
                return;
            }
            if (!f.autoHost && f.hostId < 0) {
                actShowError("Elige el servidor inicial (o Automático): "
                        + "sin él el panel responde «Host no resuelto» y "
                        + "la zona sola no basta (ronda 16).");
                return;
            }
            if (f.nameForUser.length() > 20) {
                actShowError("El nombre para el usuario pasa de 20 "
                        + "caracteres: el panel lo rechaza (ronda 15).");
                return;
            }
            String body;
            try {
                JSONObject o = new JSONObject();
                o.put("user", f.ownerId);
                o.put("name", f.name);
                o.put("protocol", "wireguard");
                o.put("manual_payment", f.manual);
                if (!f.nameForUser.isEmpty()) o.put("name_for_user", f.nameForUser);
                if (f.zoneId > 0) o.put("zone", f.zoneId);
                // Como la web (su código, ronda 17): en automático se
                // envía is_auto_generated=true y NINGÚN host; en manual,
                // is_auto_generated=false y el host elegido.
                o.put("is_auto_generated", f.autoHost);
                o.put("status", "active");
                if (!f.autoHost && f.hostId > 0) o.put("host", f.hostId);
                if (!f.notes.isEmpty()) o.put("notes", f.notes);
                body = o.toString();
            } catch (Exception e) {
                actShowError("No se pudo construir la petición.");
                return;
            }
            doWrite("Escritura — crear túnel", "POST",
                    PanelConfig.TUNNELS_CREATE_URL, body, "Túnel creado", false,
                    "ruta documentada: POST /api/v1/tunnels/ · ronda 15: sin "
                            + "zona ni host el panel responde «Host no "
                            + "resuelto»; «host» es el campo que el PATCH del "
                            + "detalle aceptó en la ronda 6"
                            + (f.autoHost
                                    ? " · modo Automático: "
                                            + "is_auto_generated=true y sin "
                                            + "host, como la web (su código, "
                                            + "ronda 17); omitirlo sin la "
                                            + "bandera dio «Host no resuelto»"
                                    : f.hostName != null
                                            ? " · servidor elegido: " + f.hostName
                                                    + " (id " + f.hostId + ")"
                                            : " · sin servidor elegido"),
                    false, null, true);
        });
    }

    /**
     * Lista de servidores DEL PANEL en caché (la del endpoint por
     * túnel): es el pool VÁLIDO para crear. El respaldo «en uso»
     * mezcla otro registro — la ronda 20 eligió el id 17 «S2-Cuba»
     * y la creación lo rechazó: «El host seleccionado no pertenece
     * a las zonas asignadas a este reseller». Se pide una vez, con
     * el primer túnel cargado como referencia.
     */
    private List<HostEntry> panelHostsCache;

    private void ensurePanelHosts() {
        if (panelHostsCache != null || dataTunnels == null) return;
        long refId = -1;
        for (int i = 0; i < dataTunnels.length(); i++) {
            JSONObject t = dataTunnels.optJSONObject(i);
            if (t == null) continue;
            Object ido = t.opt("id");
            if (ido instanceof Number) {
                refId = ((Number) ido).longValue();
                break;
            }
        }
        if (refId < 0) return;
        final long ref = refId;
        exec.execute(() -> {
            ApiClient api = new ApiClient(store);
            ApiClient.Result r = api.getWithPairs(
                    "Servidores del panel (crear)", PanelConfig.hostsUrl(ref));
            if (r.error == null && r.code >= 200 && r.code < 300
                    && r.body != null) {
                JSONArray items = JsonHelp.itemsArray(JsonHelp.parse(r.body));
                if (items != null && items.length() > 0) {
                    final List<HostEntry> parsed = new ArrayList<>();
                    for (int i = 0; i < items.length(); i++) {
                        HostEntry h = parseHost(items.opt(i));
                        if (h != null && hostIdLong(h) >= 0) parsed.add(h);
                    }
                    if (!parsed.isEmpty()) {
                        handler.post(() -> panelHostsCache = parsed);
                    }
                }
            }
        });
    }

    /** Pool para crear: la lista del panel si ya llegó; si no, el respaldo. */
    private List<HostEntry> createHostPool() {
        if (panelHostsCache != null && !panelHostsCache.isEmpty()) {
            return panelHostsCache;
        }
        return hostsFromTunnels();
    }

    /** Id numérico de un HostEntry, o -1 si no se puede saber. */
    private long hostIdLong(HostEntry h) {
        if (h == null || h.id == null) return -1;
        if (h.id instanceof Number) return ((Number) h.id).longValue();
        try {
            return Long.parseLong(String.valueOf(h.id).trim());
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * Servidor con menos túneles entre los cargados: es el
     * «automático» de la app para los LOTES, porque el automático
     * del bulk-create del panel está roto (error QuerySet, ronda
     * 19). Cuenta los túneles cargados por id de host.
     */
    private HostEntry leastLoadedHost() {
        List<HostEntry> hosts = createHostPool();
        if (hosts.isEmpty()) return null;
        java.util.Map<String, Integer> counts = new java.util.HashMap<>();
        if (dataTunnels != null) {
            for (int i = 0; i < dataTunnels.length(); i++) {
                JSONObject t = dataTunnels.optJSONObject(i);
                if (t == null) continue;
                Object hv = t.opt("host");
                if (hv instanceof JSONObject) {
                    Object ido = ((JSONObject) hv).opt("id");
                    if (ido != null && ido != JSONObject.NULL) {
                        String k = String.valueOf(ido);
                        Integer c = counts.get(k);
                        counts.put(k, c == null ? 1 : c + 1);
                    }
                }
            }
        }
        HostEntry best = null;
        int bestCount = Integer.MAX_VALUE;
        for (HostEntry h : hosts) {
            if (hostIdLong(h) < 0) continue;
            Integer c = counts.get(String.valueOf(h.id));
            int n = c == null ? 0 : c;
            if (n < bestCount) {
                bestCount = n;
                best = h;
            }
        }
        return best;
    }

    /**
     * Formulario «Crear por lotes» (v0.9.3) — POST documentado a
     * /api/v1/tunnels/bulk-create/. El cuerpo replica el del
     * formulario web (leído de su propio código, ronda 17):
     * {user, count, protocol, status:"active", manual_payment:true,
     * name_prefix, notes} + zone si se elige + host SOLO en modo
     * manual (en automático no se envía host). La SPEC fija la
     * cantidad entre 2 y 50; el panel enumera desde el nombre base.
     */
    private void openCreateBatch(final NewTunnelForm f) {
        openAct("Crear túneles por lotes");
        ensurePanelHosts();
        final long[] zid = {f.zoneId};
        final String[] zname = {f.zoneName};
        final EditText inPrefix = makeTextInput("Nombre base", false);
        inPrefix.setText(f.basePrefix);
        final EditText inCount = makeTextInput("Cantidad (2–50)", false);
        inCount.setText(f.countText);
        inCount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        final EditText inNotes = makeTextInput("Notas (opcional)", true);
        inNotes.setText(f.notes);
        final Runnable capture = () -> {
            f.basePrefix = fieldText(inPrefix);
            f.countText = fieldText(inCount);
            f.notes = fieldText(inNotes);
            f.zoneId = zid[0];
            f.zoneName = zname[0];
        };
        actModeRow(true,
                () -> {
                    capture.run();
                    openCreateTunnel(f);
                },
                () -> {});
        actSelector("Propietario *",
                f.ownerName != null ? f.ownerName : "Seleccionar propietario…",
                v -> {
                    capture.run();
                    openOwnerPicker(f, () -> openCreateBatch(f));
                });
        actAddField("Nombre base * (letras, números y guiones)", inPrefix);
        actAddField("Cantidad *", inCount);
        actSelector("Zona", f.zoneName != null ? f.zoneName : "Sin zona",
                v -> {
                    capture.run();
                    openZonePicker(zid, zname, () -> {
                        f.zoneId = zid[0];
                        f.zoneName = zname[0];
                        openCreateBatch(f);
                    });
                });
        actSelector("Servidor inicial",
                f.autoHost ? "Automático (como la web)"
                        : f.hostName != null ? f.hostName : "Seleccionar servidor…",
                v -> {
                    capture.run();
                    openCreateHostPicker(f, () -> openCreateBatch(f));
                });
        actAddField("Nota para todos los túneles del lote", inNotes);
        actText("El lote se crea de una vez, con pago manual, como en "
                + "la web; el panel enumera los túneles desde el "
                + "nombre base. En Automático, la app elige el "
                + "servidor con menos túneles cargados y lo envía: el "
                + "automático de lotes del panel está roto (su error "
                + "QuerySet, ronda 19). La respuesta queda entera en "
                + "el Diagnóstico.", TEXT2, 12);
        setActPrimary("Crear lote", () -> {
            capture.run();
            if (f.ownerId < 0) {
                actShowError("Selecciona el propietario del lote.");
                return;
            }
            if (f.basePrefix.isEmpty()
                    || !f.basePrefix.matches("[A-Za-z0-9-]+")) {
                actShowError("El nombre base solo puede contener "
                        + "letras, números y guiones (ronda 16).");
                return;
            }
            final int count;
            try {
                count = Integer.parseInt(f.countText);
            } catch (Exception e) {
                actShowError("La cantidad debe ser un número entre 2 y 50.");
                return;
            }
            if (count < 2 || count > 50) {
                actShowError("La cantidad debe estar entre 2 y 50.");
                return;
            }
            if (!f.autoHost && f.hostId < 0) {
                actShowError("Elige el servidor inicial (o Automático): "
                        + "el lote es de pago manual y sin servidor el "
                        + "panel no resuelve el host.");
                return;
            }
            // Automático de lote = lo elige la APP (el del panel está
            // roto, ronda 19): el servidor con menos túneles cargados.
            long sendHostId = f.hostId;
            String hostNote;
            if (f.autoHost) {
                HostEntry pick = leastLoadedHost();
                long pid = hostIdLong(pick);
                if (pick == null || pid < 0) {
                    actShowError("No se pudo elegir servidor "
                            + "automáticamente (sin datos de servidores "
                            + "cargados); elige uno manualmente.");
                    return;
                }
                sendHostId = pid;
                hostNote = " · automático de la APP: " + pick.name
                        + " (id " + pid + "), el de menos túneles "
                        + "cargados"
                        + (panelHostsCache != null
                                ? " (pool: lista del panel)"
                                : " (pool: respaldo en uso)")
                        + " — el bulk-create del panel en "
                        + "automático devuelve su error QuerySet "
                        + "(ronda 19)";
            } else {
                hostNote = " · servidor: " + f.hostName
                        + " (id " + f.hostId + ")";
            }
            String body;
            try {
                JSONObject o = new JSONObject();
                o.put("user", f.ownerId);
                o.put("count", count);
                o.put("protocol", "wireguard");
                o.put("status", "active");
                o.put("manual_payment", true);
                o.put("name_prefix", f.basePrefix);
                o.put("notes", f.notes.isEmpty() ? JSONObject.NULL : f.notes);
                if (f.zoneId > 0) o.put("zone", f.zoneId);
                if (sendHostId > 0) o.put("host", sendHostId);
                body = o.toString();
            } catch (Exception e) {
                actShowError("No se pudo construir la petición.");
                return;
            }
            doWrite("Escritura — crear túneles por lotes", "POST",
                    PanelConfig.TUNNELS_BULK_CREATE_URL, body, "Lote creado",
                    false,
                    "ruta documentada: POST /api/v1/tunnels/bulk-create/ · "
                            + "cuerpo como el del formulario web (su "
                            + "código, ronda 17), con el host siempre "
                            + "explícito" + hostNote,
                    false, null, true);
        });
    }

    /**
     * Restablecer la contraseña de una cuenta: POST documentado a
     * …/accounts/users/<id>/reset-password/. La ronda 15 enseñó que
     * el endpoint NO genera la contraseña: exige el campo «password»
     * en el cuerpo — la contraseña nueva la pone el admin aquí, y
     * después se le pasa al cliente con la plantilla de datos de
     * acceso. El intento entero queda en el Diagnóstico.
     */
    private void openResetPassword(final JSONObject u, final long userId) {
        final String username = DataHelp.fieldStr(u, "username");
        openAct("Restablecer contraseña");
        actRow("Cuenta", username);
        final EditText inPass = makeTextInput("Contraseña nueva", false);
        inPass.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        actAddField("Contraseña nueva *", inPass);
        actText("La contraseña nueva la pones tú: el panel exige el "
                + "campo «password» (ronda 15). Tras restablecer, pásala "
                + "al cliente con la plantilla de datos de acceso.",
                TEXT2, 12);
        setActPrimary("Restablecer", () -> {
            String pw = inPass.getText() != null ? inPass.getText().toString() : "";
            if (pw.isEmpty()) {
                actShowError("Escribe la contraseña nueva.");
                return;
            }
            String body;
            try {
                body = new JSONObject().put("password", pw).toString();
            } catch (Exception e) {
                actShowError("No se pudo construir la petición.");
                return;
            }
            doWrite("Escritura — restablecer contraseña (cuenta)", "POST",
                    PanelConfig.resetPasswordUrl(userId), body,
                    "Contraseña restablecida", false,
                    "cuenta: " + username, false, null, true);
        });
    }

    // ---------------- Eliminar túnel (guarda extra; a probar en desechables) ----------------

    /**
     * Eliminar queda LISTO pero con guarda extra: hay que ESCRIBIR el
     * nombre exacto del túnel. La documentación dice que es soft-delete,
     * pero con la creación de túneles caída en el panel, un túnel eliminado
     * ahora NO se puede recrear. En esta entrega no se prueba.
     */
    private void openDeleteGuard(final JSONObject t) {
        final long id = requireId(t);
        if (id < 0) return;
        final String name = DataHelp.fieldStr(t, "name");
        openAct("Eliminar túnel");
        actTunnel = t;
        actRow("Túnel", name);
        actText("Según la documentación del panel es un borrado suave "
                + "(soft-delete): el túnel desaparece de la lista y su "
                + "configuración deja de funcionar. La creación de "
                + "túneles ya funciona otra vez (31/10), pero lo "
                + "eliminado NO se puede deshacer desde la app.",
                RED, 13);
        actText("Escribe el nombre exacto del túnel para habilitar el botón:",
                TEXT2, 13);
        final EditText input = makeTextInput("Nombre del túnel", false);
        LinearLayout.LayoutParams il = fullWidthLp();
        il.topMargin = dp(4);
        actBody.addView(input, il);
        setActPrimary("Eliminar túnel", () -> doWrite(
                "Escritura — eliminar túnel", "DELETE",
                PanelConfig.tunnelDetailUrl(id), null, "Túnel eliminado",
                false, null, false, null, true));
        actPrimaryBtn.setEnabled(false);
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                String typed = s != null ? s.toString().trim() : "";
                actPrimaryBtn.setEnabled(name != null && typed.equals(name));
            }
        });
    }

    // ---------------- Utilidades ----------------

    private long nowSec() {
        return System.currentTimeMillis() / 1000;
    }

    private String fmtDate(long epochSec) {
        return new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                .format(new Date(epochSec * 1000));
    }

    private String fmtDuration(long sec) {
        long h = sec / 3600;
        long m = (sec % 3600) / 60;
        long s = sec % 60;
        if (h > 0) return String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s);
        return String.format(Locale.getDefault(), "%02d:%02d", m, s);
    }
}
