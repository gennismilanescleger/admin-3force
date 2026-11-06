package com.gennis.admin3force;

/**
 * Datos fijos del panel y de la API (spec + inspección de solo lectura del
 * 2026-10-30, ver API-INSPECTION.md). Las rutas de la API están documentadas
 * en la página Developers del panel (API v2.1.0); el origen es el mismo del
 * panel. La URL de login es pública y conocida.
 */
public final class PanelConfig {
    public static final String HOST = "reseller.3forcevpn.com";
    public static final String BASE_URL = "https://reseller.3forcevpn.com";
    public static final String LOGIN_URL = "https://reseller.3forcevpn.com/auth/login";
    public static final String LOGIN_PATH = "/auth/login";

    // API (bases confirmadas en la inspección): manager = cuentas/matriz/perfil;
    // v1 = auth y túneles.
    public static final String API_V1 = BASE_URL + "/api/v1";
    public static final String API_MANAGER = BASE_URL + "/api/manager/v1";

    public static final String SESSION_URL = API_V1 + "/auth/session/?context=manager";
    public static final String REFRESH_URL = API_V1 + "/auth/refresh/";
    public static final String USERS_URL = API_MANAGER + "/accounts/users/";
    public static final String TUNNELS_URL = API_V1 + "/tunnels/?personal=true";
    // Segundo alcance de túneles: ?personal=false resultó ser la lista de los
    // clientes (32) frente a los 3 propios del reseller en ?personal=true
    // (confirmado en la prueba real del 2026-10-30, ver API-INSPECTION.md).
    public static final String TUNNELS_URL_ALL = API_V1 + "/tunnels/?personal=false";
    public static final String MATRIX_URL = API_MANAGER + "/billing/manual-billing/matrix/";
    // Escritura de facturación manual (marcar pagado): endpoint documentado
    // en Developers como POST staff/reseller. Su payload NUNCA se ha
    // ejecutado: la v0.5.0 lo descubre con un intento real registrado entero
    // en el Diagnóstico (ver ApiClient.postWrite).
    public static final String UPDATE_URL = API_MANAGER + "/billing/manual-billing/update/";

    // Tasa USDT (sin auth): historial de cambiocup; se usa el último valor.
    public static final String RATE_URL = "https://www.cambiocup.com/api/history?coin=CUP";

    // ---- Acciones de túnel (v0.8.0: rutas DOCUMENTADAS) ----
    // El índice Developers del propio panel (API v2.1.0, leído el
    // 2026-10-31, ver API-INSPECTION.md «ÍNDICE DEVELOPERS LEÍDO») da las
    // rutas exactas de la sección «Túneles — v1/tunnels/»: ya no hay
    // candidatas, cada acción usa SU ruta documentada, una por toque.
    //
    //  · Descargar: GET /api/v1/tunnels/<id>/download-config/ (con GUION;
    //    el de guion bajo no existe). Las de las rondas 1–2 (detalle,
    //    manager download_config, download/, config/) dieron 404 y se
    //    retiran.
    //  · Regenerar: POST /api/v1/tunnels/<id>/regenerate/ (documentada;
    //    sin alcance dio el 404 JSON genérico en las rondas 1, 3 y 4 —
    //    desde la v0.8.2 lleva ?personal=false, ver REGENERATE_SUFFIX).
    //  · Cambiar de servidor: la ruta documentada POST
    //    /api/v1/tunnels/<id>/reassign-host/ (con GUION, staff/reseller;
    //    el de guion bajo dio 404 HTML) respondió 200 en las rondas 4–5
    //    pero NO honró el host elegido (43→S3-ATLANTA, 47→S1-CUBA,
    //    41→S3-CUBA): desde la v0.8.4 la app prueba el mecanismo del
    //    formulario Configure de la web — el PATCH del detalle con
    //    {"host": <id>} — y reassign-host/ queda fuera de la app.
    //  · Detalle/editar/eliminar: GET/PATCH/DELETE /api/v1/tunnels/<id>/
    //    (soft-delete). El GET dio 404 para túneles de clientes desde la
    //    app pese a estar documentado (posible alcance del viewset).
    //  · NO hay endpoint de notas: la nota del túnel se edita con el
    //    PATCH del detalle, {notes}.
    //  · NO hay endpoint disable/enable: deshabilitar/habilitar = PATCH
    //    del detalle. La v0.8.0/0.8.1 enviaron {status:"disabled"} y la
    //    v0.8.3/0.8.4 {is_active:false}: ambos devolvieron 200 SIN
    //    cambiar nada (verificado en el panel y en la ronda 6). Desde
    //    la v0.8.5 la app pregunta el esquema con OPTIONS y escribe el
    //    campo de estado que la API declare escribible.
    //
    // CONFIRMADO antes (probado, no se toca):
    //  · REAPROVISIONAR: POST /api/v1/tunnels/<id>/reprovision/ {} → 200.
    //  · Lista de servidores POR TÚNEL: GET /api/v1/hosts/?tunnel_id=<id>
    //    → 200 (sin tunnel_id el endpoint responde 400 pidiéndolo).

    /** Detalle de un túnel: /api/v1/tunnels/<id>/ (documentado). */
    public static String tunnelUrl(long id) {
        return API_V1 + "/tunnels/" + id + "/";
    }

    // SONDA v0.8.1 (una sola variable, ver API-INSPECTION.md «Ronda 3»):
    // con las rutas documentadas, TODA la familia del detalle (GET
    // detalle, download-config, PATCH) dio 404 genérico para túneles de
    // clientes con el JWT del reseller, mientras la lista con
    // ?personal=false, hosts y reprovision/ funcionan en la misma
    // sesión. Hipótesis: ese viewset solo ve los túneles PROPIOS salvo
    // que se le pase el mismo parámetro de alcance de la lista. La
    // familia del detalle (sonda, nota, estado, eliminar — y desde la
    // v0.8.4 también el cambio de servidor, con {"host"}) usa esta
    // URL; reprovision/ cuelga de tunnelUrl sin el parámetro y
    // funciona (200 confirmado). Regenerar fue la
    // excepción: sin alcance dio 404 en la ronda 4, así que desde la
    // v0.8.2 su sufijo lleva ?personal=false (ver REGENERATE_SUFFIX).
    /** Detalle con el alcance de la lista: /api/v1/tunnels/<id>/?personal=false. */
    public static String tunnelDetailUrl(long id) {
        return tunnelUrl(id) + "?personal=false";
    }

    /** Detalle de una cuenta: /api/manager/v1/accounts/users/<id>/. */
    public static String userUrl(long id) {
        return API_MANAGER + "/accounts/users/" + id + "/";
    }

    // Descarga de la configuración (LECTURA): v0.8.0 usó la ruta
    // documentada a secas (download-config/, con GUION) y dio el 404
    // JSON genérico de la familia del detalle. v0.8.1 encadena DOS
    // lecturas (es lectura: puede encadenar), parando en el primer 200:
    // (1) la misma ruta con el alcance de la lista, ?personal=false;
    // (2) si responde 404, la misma ruta en la familia MANAGER con
    // guion — nunca probada (la de la ronda 2 era con guion bajo).
    public static String downloadConfigUrl(long id) {
        return API_V1 + "/tunnels/" + id + "/download-config/?personal=false";
    }

    /** Descarga, segundo candidato: /api/manager/v1/tunnels/<id>/download-config/. */
    public static String downloadConfigManagerUrl(long id) {
        return API_MANAGER + "/tunnels/" + id + "/download-config/";
    }

    // Lista de servidores del panel (LECTURA): es POR TÚNEL (ronda 1:
    // sin tunnel_id el endpoint responde 400 pidiéndolo).
    public static String hostsUrl(long tunnelId) {
        return API_V1 + "/hosts/?tunnel_id=" + tunnelId;
    }

    // Escrituras (UNA ruta por toque, patrón ApiClient.write).
    // Sufijos documentados (con GUION donde el índice lo escribe así);
    // deshabilitar/habilitar NO tienen sufijo: son PATCH al detalle con
    // alcance (tunnelDetailUrl); el campo concreto lo decide el
    // esquema OPTIONS desde la v0.8.5 ({status} y {is_active} fueron
    // ignorados al escribir en las rondas 4 y 6).
    //
    // CAMBIAR DE SERVIDOR — v0.8.4 (ver API-INSPECTION.md «Ronda 5»):
    // el POST documentado …/reassign-host/ con {"host_id"} respondió
    // 200 en las rondas 4–5, pero el túnel aterrizó en OTRO host las
    // tres veces — o la API auto-asigna ignorando el host pedido, o
    // ese no es el mecanismo. La constante REASSIGN_SUFFIX sale de la
    // app: la vía que se prueba ahora es el PATCH del detalle con
    // alcance (tunnelDetailUrl) y cuerpo {"host": <id>}, el mecanismo
    // del formulario Configure de la web (pestaña Network, «Selected
    // Host») — el mismo PATCH que ya aceptó {"notes"} e {"is_active"}.
    // v0.8.2 (ronda 4): regenerate/ era el ÚNICO 404 que quedaba — la
    // familia del detalle quedó desbloqueada por ?personal=false
    // (nota, descarga y deshabilitar → 200), así que este sufijo gana
    // el parámetro de alcance (confirmado en la ronda 5: 200).
    public static final String REGENERATE_SUFFIX = "regenerate/?personal=false";
    public static final String REPROVISION_SUFFIX = "reprovision/";

    // ---- Creación y cuentas (v0.9.0: rutas DOCUMENTADAS) ----
    // Crear cuenta: POST a USERS_URL (la colección de usuarios acepta
    // GET/POST según el índice Developers). Crear túnel: POST a la
    // colección de túneles, SIN el parámetro de alcance (la creación no
    // es una búsqueda en el viewset; si el servidor la trata como la
    // familia del detalle, el Diagnóstico lo mostrará y la siguiente
    // build añade ?personal=false — una sola variable por ronda).
    public static final String TUNNELS_CREATE_URL = API_V1 + "/tunnels/";

    /** Creación por lotes: POST /api/v1/tunnels/bulk-create/ (documentado). */
    public static final String TUNNELS_BULK_CREATE_URL = API_V1 + "/tunnels/bulk-create/";

    /** Restablecer contraseña de una cuenta: POST documentado. */
    public static String resetPasswordUrl(long id) {
        return userUrl(id) + "reset-password/";
    }

    // ---- Mi cuenta (v0.69.0): el perfil PROPIO del manager ----
    // Documentados en Developers (GET/PATCH /api/manager/v1/profile/
    // y sus dos subrutas): perfil propio, ajustes de reseller y
    // estadísticas de referidos. La app los lee al abrir «Mi cuenta»;
    // el alias propio se edita con PATCH al perfil.
    public static final String OWN_PROFILE_URL = API_MANAGER + "/profile/";
    public static final String OWN_RESELLER_SETTINGS_URL =
            API_MANAGER + "/profile/reseller-settings/";
    public static final String OWN_REFERRAL_URL =
            API_MANAGER + "/profile/referral-stats/";
    // Las mismas tres rutas en la familia v1 (también
    // documentadas): la v0.69.0 solo probó la familia
    // manager y «Mi cuenta» se veía como la sesión —
    // desde la v0.70.0 la LECTURA encadena: manager y,
    // si no responde, v1; la escritura usa la familia
    // que respondió en la lectura.
    public static final String OWN_PROFILE_URL_V1 =
            API_V1 + "/profile/";
    public static final String OWN_RESELLER_SETTINGS_URL_V1 =
            API_V1 + "/profile/reseller-settings/";
    public static final String OWN_REFERRAL_URL_V1 =
            API_V1 + "/profile/referral-stats/";

    /** Descarga de un túnel PROPIO (personal=true): la familia del
     *  detalle sin parámetro de alcance — el alcance de clientes
     *  (?personal=false) es solo para los de clientes. */
    public static String ownDownloadConfigUrl(long id) {
        return tunnelUrl(id) + "download-config/";
    }

    private PanelConfig() {}
}
