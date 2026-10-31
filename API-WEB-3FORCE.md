# API de 3ForceVPN — documentación oficial del panel

Fuente: página **Developers** del panel — https://reseller.3forcevpn.com/dashboard/developers
Extraída en solo lectura el 2026-10-31. Este documento contiene ÚNICAMENTE lo que la propia web documenta de su API, tal cual está escrito en esa página.

- **«API v2.1.0»** — **148 endpoints**
- Título de la página: «Developers»
- «Complete API v2 reference for integrations (client, mobile and management). Mirrors API.md at the repo root.»
- «No Swagger: when routing changes, update API.md + api-reference.ts.»
- Grupos de la página: «/api/v1/ — Dashboard cliente + app móvil (JWT)» · «/api/manager/v1/ — Panel manager puerto 5001 (staff + reseller)» · «/api/hooks/ — Máquina-a-máquina (sin JWT)» · «/api/ — Sin versionar + WebSockets»
- La página no muestra una URL base separada distinta de los prefijos de grupo; la autenticación se indica por endpoint con su rol (public, JWT, staff, staff/reseller, superuser, admin, webhook, WS token).

---

## /api/v1/

### Auth — v1/auth/ (5)

1. **POST** — `/api/v1/auth/login/` — public — Login. Body { username, password }. Rate-limit 5/min por IP; tras 3 fallos por IP exige captcha Turnstile en cliente/móvil (sin acercar a la pausa); solo con 10+ fallos de credencial del par IP+usuario en 10 min pausa suave 2 min. Todo 429 lleva retry_after. Manager siempre exige captcha.
2. **POST** — `/api/v1/auth/register/` — public — Registro. ?ref=<code> opcional. Turnstile obligatorio. Asigna zona default si existe.
3. **POST** — `/api/v1/auth/refresh/` — JWT — Refresh rotativo con blacklist. Renueva el access (10 min). Rate-limit suave 30/min por IP.
4. **POST** — `/api/v1/auth/logout/` — public — Logout. Pone el refresh en blacklist.
5. **GET/POST** — `/api/v1/auth/session/?context=client|manager|mobile` — JWT — Sesión: devuelve requestData (user + flags). El manager la llama con context=manager.

### Perfil — v1/profile/ (3)

1. **GET/PATCH** — `/api/v1/profile/` — JWT — Perfil propio. PATCH edita alias (único, alfanumérico 2–20; requerido para P2P) y toggles de notificación por rol.
2. **GET** — `/api/v1/profile/referral-stats/` — JWT — Estadísticas de referidos y comisiones ganadas.
3. **GET/PATCH** — `/api/v1/profile/reseller-settings/` — JWT — Configuración propia del reseller.

### Túneles — v1/tunnels/ (dual v1-tunnels) (9)

1. **GET/POST** — `/api/v1/tunnels/?personal=true|false` — JWT — Lista (scope reseller por ?personal) / crea túnel. Regla IP: todo túnel no soft-deleted ocupa su IP. POST acepta queue_profile (id, solo WireGuard; NULL=hereda plan).
2. **GET/PATCH/DELETE** — `/api/v1/tunnels/<id>/` — JWT — Detalle / edita / elimina (soft-delete; el outbox propaga el remove al agente). PATCH acepta queue_profile (override directo, solo WireGuard; fuerza re-sync de la simple queue). Shaping WG: solo ACTIVE/GRACE con QoS efectivo tienen simple queue en el CHR (comment tm:t<id>), y solo en interfaces con enforce_limits=true; move/delete limpian la IP vieja y el monitor barre zombies por rango de la interfaz.
3. **POST** — `/api/v1/tunnels/<id>/regenerate/` — JWT — Regenera claves/config. Notifica a staff + reseller.
4. **POST** — `/api/v1/tunnels/<id>/reassign-host/` — staff/reseller — Mueve el túnel a otro host.
5. **POST** — `/api/v1/tunnels/<id>/reprovision/` — staff — Reaprovisiona tras fallo (respeta regla de IPs ocupadas).
6. **GET** — `/api/v1/tunnels/<id>/download-config/` — JWT — Descarga .conf WireGuard (PersistentKeepalive se omite si 0/None).
7. **POST** — `/api/v1/tunnels/bulk-create/` — staff/reseller — Creación masiva. Rechaza user == request.user para reseller.
8. **POST** — `/api/v1/tunnels/order-create/` — JWT — Orden de creación para terceros. 403 si target_user == reseller. Planes room: sin protocol/name/host; provisiona suscripción a nivel usuario (tunnel=None).
9. **POST** — `/api/v1/tunnels/<id>/order-upgrade/` — JWT — Orden de añadir plan. 403 si target_user == reseller.

### Planes / suscripciones / órdenes — v1 (duales) (14)

1. **GET** — `/api/v1/plans/?is_active=&is_visible=&tunnel_type=` — JWT — Planes. Clientes: solo activos + visibles. Vista cliente expone tunnel_type (vpn/room). Búsqueda por name/description. Límites (NULL=ilimitado): max_connections (proxy), traffic_total/up/down_bytes por periodo (agotan→expira), rate_up/down_mbps (solo visibles si show_speed_limits_to_user). queue_profile FK (shaping WG) + show_speed_limits_to_user. zones M2M (hosts elegibles; tunnel.zone/user.zone sobrescriben, defaults no entran). Proxy (http/socks5) siempre por TLS, sin toggle.
2. **POST/PATCH/DELETE** — `/api/v1/plans/` + `<id>/` — staff — CRUD de planes (staff). Editar NO muta suscripciones existentes (frozen_limits congelado); solo aplica a nuevas ventas.
3. **GET/POST** — `/api/v1/subscriptions/?personal=true|false` — JWT — Suscripciones (scope reseller). Incluye túneles y salas (tunnel=None, user directo, plan room). Sin PUT/DELETE. end_date desde plan.validity si no viene.
4. **PATCH** — `/api/v1/subscriptions/<id>/` — staff — Solo custom_limits en vivo (override parcial, None=hereda snapshot). Valida TLS y encola tunnel.operation update al agente.
5. **POST** — `/api/v1/subscriptions/<id>/toggle-auto-renew/` — JWT — Activa/desactiva renovación automática (túneles y salas; sala = una sola por usuario).
6. **POST** — `/api/v1/subscriptions/<id>/cancel/` — JWT — Cancela la suscripción (túneles y salas). PENDING reembolsa a wallet; ACTIVE solo staff/manager.
7. **POST** — `/api/v1/subscriptions/<id>/refund/` — JWT — Reembolso de suscripción.
8. **GET/POST** — `/api/v1/orders/?personal=true|false` — JWT — Órdenes (scope reseller). PATCH/DELETE bloqueados (405). POST crea orden + factura. Vista cliente: plan (nombre), plan_id, plan_tunnel_type (vpn/room), tunnel/tunnel_name, paid (bool), is_provisioned, provisioning_error. Sala: operation_type=create, tunnel=null.
9. **GET/POST** — `/api/v1/port-forwards/?personal=true|false` — JWT — Port forwarding incluido en el plan (solo WireGuard en CHR, modos direct y agent, scope reseller). POST { tunnel_id, internal_port, protocol tcp|udp|both } habilita gratis y devuelve la regla. Cupo global 1..10 default 1.
10. **GET** — `/api/v1/port-forwards/quote/?tunnel=<id>` — JWT — Elegibilidad sin activar: plan, allow_port_forwarding, server_supports_pf, slots, end_date.
11. **GET/PATCH/DELETE** — `/api/v1/port-forwards/<id>/` — JWT — Detalle / edita interno+protocolo (público sticky; cooldown 5 min, 429 al cliente, staff exento; reabre ciclo) / elimina (retira NAT, también desde failed). Expone provisioning_status/error (paridad túneles).
12. **POST** — `/api/v1/port-forwards/retry-failed/` — staff — Reenvía TODAS las PF fallidas con CHR online (active→pf_update, si no→pf_remove). Botón en Túneles del manager. Backoff silencioso 5/10/30/60 min + aviso staff al agotar.
13. **POST** — `/api/v1/orders/<id>/provision/` — staff — Reaprovisiona orden paid/failed. En éxito limpia provisioning_error y resetea retry_count.
14. **POST** — `/api/v1/orders/<id>/refund/` — JWT — Reembolso de orden.

### Billing — v1/{invoices,wallets,payments} (dual) (11)

1. **GET/POST** — `/api/v1/invoices/?personal=true|false` — JWT — Facturas service/wallet_deposit (base_amount/amount/net_amount). Número atómico.
2. **POST** — `/api/v1/invoices/new-order-invoice/` — JWT — Crea factura para una orden de servicio.
3. **POST** — `/api/v1/invoices/<id>/check/` — JWT — Verifica estado de la factura contra el provider.
4. **GET** — `/api/v1/wallets/?personal=true|false` — JWT — Wallets (scope reseller). Staff/superuser no tienen wallet.
5. **GET** — `/api/v1/wallets/me/` — JWT — Wallet propia.
6. **GET** — `/api/v1/wallets/reseller-summary/` — staff/reseller — Dashboard de facturación del reseller.
7. **POST** — `/api/v1/wallets/transfer/` — JWT — P2P { alias, amount }. Requiere alias en ambos + allow_p2p. Transacciones pareadas.
8. **POST** — `/api/v1/wallets/<id>/adjustment/` — staff/reseller — Ajuste manual de saldo.
9. **GET** — `/api/v1/payments/?personal=true|false` — JWT — Pagos recibidos de providers (scope reseller).
10. **POST** — `/api/v1/payments/checkout/<provider>/` — JWT — Inicia un pago contra el provider.
11. **POST** — `/api/v1/payments/parse-mjs/` — JWT — Parser MJS (también en manager).

### Hosts, errores, anuncios, rooms — v1 (27)

1. **GET** — `/api/v1/hosts/` — JWT — Hosts disponibles para el usuario.
2. **GET** — `/api/v1/apps/latest/?platform=<p>&current=<v>` — public — Check de actualización del updater (sin JWT): latest/min/download/changelog/size/sha256 + update_available/mandatory. Sin publicar → configured:false.
3. **GET** — `/api/v1/apps/status/` — JWT — Resumen personalizado (banner + /dashboard/download): por plataforma visible + has_used/current_version/never_used/outdated/highlight (cruza con Device).
4. **POST** — `/api/v1/error-reports/` — JWT — Reporta error de dispositivo (headers X-Device-* o body). critical notifica a staff.
5. **GET** — `/api/v1/announcements/` — JWT — Anuncios visibles (scope por usuario). Escritura: staff/reseller.
6. **GET** — `/api/v1/rooms/` — JWT — Salas (scope owner/miembro; staff todo). Incluye provisioning_status/is_provisioned/provisioned_at (+provisioning_error solo dueño/staff), is_static, egress. metadata {total_rooms,active_rooms,inactive_rooms,recent_rooms} con el mismo scope.
7. **POST** — `/api/v1/rooms/` — JWT — Crea sala { alias*, password?, interface?, max_members?, is_static? }. Alias requerido, minúsculas, inmutable. Sin interface → sharding. Técnica derivada del lógico (tope /28, 13).
8. **PATCH** — `/api/v1/rooms/<id>/` — JWT — Edita sala. interface rechazado (usar move); egress rechazado 400 (solo LAN); password con regla mín. 6 alnum (vacío = sin protección); max_members más allá de capacidad → usar resize.
9. **POST** — `/api/v1/rooms/<id|alias>/join/` — JWT — Acepta tu invitación pendiente para la sala (sin id de invite). Sin invitación → 400. Emite create + notifica al owner.
10. **GET** — `/api/v1/rooms/<id|alias>/join-info/` — JWT — Ficha mínima para unirse (cualquier autenticado): id, alias, has_password, owner_alias. Sin secretos.
11. **POST** — `/api/v1/rooms/<id|alias>/join-by-password/` — JWT — Unión por alias/enlace {password} (throttle 10/min por usuario+sala). Exige contraseña fijada, plan y cupo; crea membership (cuota 1) + peer principal.
12. **POST** — `/api/v1/rooms/<id>/leave/` — JWT — Abandonar sala (device por headers o body; no propietario).
13. **POST** — `/api/v1/rooms/<id>/kick/` — JWT — Expulsa miembro { device_id }. Solo propietario o staff.
14. **GET** — `/api/v1/rooms/<id>/config/` — JWT — INI WireGuard del peer propio (?device=<id|nombre>). Solo dueño del device o staff (el owner de la sala no descarga .conf ajenos). MTU 1420, DNS, keepalive unificado.
15. **POST** — `/api/v1/rooms/<id>/resize/` — JWT — Cambia lógico { max_members, confirm_static? }. Más allá de capacidad → re-subnet auto (IPs nuevas, claves intactas).
16. **POST** — `/api/v1/rooms/<id>/move/` — JWT — Mueve a otra interfaz { agent_interface_id } con reasignación total (build-before-cut).
17. **POST** — `/api/v1/rooms/` — JWT — AGENTE: crea sala { alias*, target=agent, agent_interface?, max_members? 3..25, initial_device?, password? (mín. 6 alnum) }. Alias requerido en minúsculas e inmutable; device inicial del owner (default su alias); egress siempre block (solo LAN). Requiere plan+cupo. Subnet /27 auto. Nace en provisioning (emite room-updated create).
18. **GET** — `/api/v1/rooms/quotas/` — JWT — Estado comercial: has_room_plan/plan + plans[] (vigente + cola PENDING por created_at, con amount_paid/service_order_id), owned/max_owned, joined/max_joined, capacidad devices (siempre). rate_up/down SOLO si show_rate_limits_to_clients está activo (o staff).
19. **GET/POST** — `/api/v1/room-peers/` — JWT — Devices del agente (owner: todos; miembro: suyos). Cada usuario crea solo los suyos (owner_alias ajeno → 403; solo staff asigna a terceros). Requiere membresía + plan + sala activa. Expone plan_suspended. Tasas heredadas global/agente 2..50, no por device. rate_up/down omitidos si el toggle está apagado.
20. **POST** — `/api/v1/room-peers/<id>/{suspend,unsuspend}/` — JWT — Deshabilitado (410 Gone): pausa/reactivación solo automática por plan vigente (tasks devices.*). Sin botón manual.
21. **DELETE** — `/api/v1/room-peers/<id>/?confirm=true` — JWT — Borra device. Miembro borrando su último peer: 400 will_leave_room → confirma con ?confirm=true y sale (left_room). Propietario a cero: 400 owner_must_keep_peer (debe eliminar la sala).
22. **POST** — `/api/v1/room-invites/` — JWT — Solo dueño/staff invita. SOLO alias público, case-insensitive { room, alias, device_quota? 1..cap sala, days? } (username no resuelve; staff invisible: mismo error que alias inexistente). Responde invitee_has_plan (aviso si el invitado no tiene plan). accept/decline exigen plan vigente.
23. **GET** — `/api/v1/room-invites/my-invites/` — JWT — Solo invitaciones RECIBIDAS por el solicitante (la lista base mezcla enviadas+recibidas). Pestaña Invitaciones del cliente.
24. **GET** — `/api/v1/rooms/<id>/roster/` — JWT — Roster del agente: memberships con cuota + devices con presencia. rate_up/down solo si el toggle está activo.
25. **POST** — `/api/v1/rooms/<id>/move-member/` — JWT — Traslada device a otra sala del agente { device_id, target_room, confirm_static? }. Re-IP, config_version++. Gate allow_member_move.
26. **GET/PATCH** — `/api/v1/room-memberships/` — JWT — Cuotas de miembro (solo dueño/staff): device_quota 1..cap sala, sin bajar de los devices activos.
27. **GET** — `/api/v1/room-audit/` — JWT — Trail de auditoría (staff todo; resto sus salas). Filtra ?room=&actor=&action=.

---

## /api/manager/v1/

### Agentes Go — manager (12)

1. **GET/POST** — `/api/manager/v1/network/agent-interfaces/` — staff — Interfaces wg0..wg3 por agente (+provision con wg*_port, ocupación, endpoint_host con fallback public_domain/public_ip). Pools únicos, nunca sobre excluidos.
2. **POST** — `/api/manager/v1/network/agent-interfaces/<id>/rotate/` — staff — Rota el /21 con sus salas (?dry_run=true → plan new_pool/rooms/peers/fits; confirm:true + confirm_static si hay estáticas → re-subnet + re-IP, claves intactas).
3. **POST** — `/api/manager/v1/network/agent-interfaces/<id>/redistribute/` — staff — Vacía la interfaz repartiendo uniforme en hermanas activas (?dry_run=true → moves/unfitted/fits; confirm:true + deactivate:false opcional → re-subnet + re-IP, config_version++, outbox + room-updated + bump + audit, sin avisos en app; luego desactiva).
4. **POST** — `/api/manager/v1/network/agents/<id>/rebalance/` — staff — Uniforma salas entre interfaces activas (dif ≤ 1; ?dry_run=true → moves; confirm:true ejecuta con mismos eventos, sin desactivar).
5. **GET** — `/api/manager/v1/network/agents/<id>/compiled-policy/` — staff — Preview sin secretos (private_key/psk omitidos) de dns+router+rooms del agente.
6. **GET/PATCH** — `/api/manager/v1/network/agent-dns/` — staff — Upstream por agente (vacío = hereda global).
7. **GET/POST** — `/api/manager/v1/network/dns-zones/` — staff — Zonas .internal→IPs (global|agent). Anti-rebinding y anti-colisión con salas.
8. **GET/POST** — `/api/manager/v1/network/dns-rules/` — staff — Reglas allow/block por patrón (dns|proxy|global; both legacy→global). service=proxy exige proxy activo. Topes 500/200.
9. **GET/POST** — `/api/manager/v1/network/address-lists/` — staff — Listas CIDR/IP por agente (nombre único ^[a-z0-9-]+$).
10. **GET/POST** — `/api/manager/v1/network/firewall-rules/` — staff — Reglas por agente/servicio (wg|proxy|room-ports). 0.0.0.0/0 exige confirm_open. Block compila primero.
11. **GET/PATCH** — `/api/manager/v1/network/net-config/` — staff — Singleton red del agente (base_pool/prefix/max_per_iface solo lectura; excluidos, cuotas 3..8/3..26, tasas globales 2..50, show_rate_limits_to_clients default False, wg*_port, DoT 1.1.1.1:853). Excluidos exigen confirm.
12. **GET** — `/api/manager/v1/network/operations/` — staff — Cola outbox por agente/estado (solo lectura).

### Cuentas — accounts/ (solo manager) (9)

1. **GET/POST** — `/api/manager/v1/accounts/users/` — staff/reseller — Usuarios. Reseller ve solo sus clientes. Excluye al propio orderer. DELETE transfiere saldo a sistema y elimina en CASCADE.
2. **POST** — `/api/manager/v1/accounts/users/<id>/reset-password/` — staff/reseller — Reseteo de contraseña por admin.
3. **GET** — `/api/manager/v1/accounts/users/<id>/referral-stats/` — staff — Stats de referidos del usuario.
4. **GET** — `/api/manager/v1/accounts/users/<id>/reseller-info/` — staff — Resumen de negocio del reseller.
5. **PATCH** — `/api/manager/v1/accounts/users/<id>/update-reseller-profile/` — staff — Edita el ResellerProfile.
6. **PATCH** — `/api/manager/v1/accounts/users/<id>/toggle-reseller/` — staff — Activa (crea perfil + clona providers) / desactiva (elimina providers no-sistema).
7. **POST** — `/api/manager/v1/accounts/users/<id>/impersonate/` — superuser — Impersonación de usuario.
8. **GET/POST/PATCH/DELETE** — `/api/manager/v1/accounts/admins/` — superuser — CRUD administradores/moderadores.
9. **GET/PATCH** — `/api/manager/v1/profile/` (+ referral-stats/, reseller-settings/) — JWT — Perfil propio del manager.

### Billing gestión — billing/ (solo manager) (22)

1. **GET** — `/api/manager/v1/billing/user-transactions/?personal=true|false` — staff/reseller — Transacciones contables (scope reseller).
2. **GET** — `/api/manager/v1/billing/system-wallet/` — superuser — Wallet del sistema.
3. **GET** — `/api/manager/v1/billing/system-wallet/metrics/` — superuser — Métricas de negocio.
4. **GET** — `/api/manager/v1/billing/system-wallet/transactions/` — superuser — Movimientos de la wallet del sistema.
5. **GET** — `/api/manager/v1/billing/financial-report/` — staff/reseller — Reporte financiero.
6. **GET/POST/PATCH/DELETE** — `/api/manager/v1/billing/providers/` — staff/reseller — Providers (config cifrado). Sistema read-only para reseller, propios editables.
7. **POST** — `/api/manager/v1/billing/providers/<id>/check/` — staff/reseller — Verifica configuración contra la API del provider.
8. **POST** — `/api/manager/v1/billing/providers/<id>/clear-config/` — staff/reseller — Limpia config (solo providers del reseller, no system).
9. **GET** — `/api/manager/v1/billing/providers/<id>/credentials/` — staff/reseller — Credenciales (vista filtrada).
10. **POST** — `/api/manager/v1/billing/providers/<id>/verify/` — staff — Verificación de token del provider.
11. **POST** — `/api/manager/v1/billing/providers/<id>/reassign/` — staff — Reasigna el provider.
12. **POST** — `/api/manager/v1/billing/providers/<id>/balance/` — staff — Consulta balance remoto.
13. **POST** — `/api/manager/v1/billing/providers/<id>/transactions/` — staff — Lista transacciones remotas.
14. **POST** — `/api/manager/v1/billing/providers/create-missing/` — staff — Crea los providers faltantes por defecto.
15. **GET/PATCH** — `/api/manager/v1/billing/onchain-wallets/` (+ `<id>/`) — staff — Wallets on-chain (solo is_active/label en PATCH).
16. **POST** — `/api/manager/v1/billing/onchain-wallets/<id>/reassign/` — staff — Fuerza reasignación de address.
17. **POST** — `/api/manager/v1/billing/onchain-wallets/<id>/balance/` — staff — Consulta balance on-chain.
18. **POST** — `/api/manager/v1/billing/onchain-wallets/<id>/transactions/` — staff — Lista transacciones on-chain.
19. **POST** — `/api/manager/v1/billing/onchain-wallets/reconcile/` — staff — Limpieza de mantenimiento de wallets.
20. **GET** — `/api/manager/v1/billing/manual-billing/matrix/` — staff/reseller — Matriz mensual de pago manual por túnel.
21. **POST** — `/api/manager/v1/billing/manual-billing/update/` — staff/reseller — Actualiza la matriz de facturación manual.
22. **GET/POST** — `/api/manager/v1/services/reseller/plans/` — staff/reseller — Planes reseller. Admin CRUD (base_price editable, default plan.price; reseller_price >= base_price; plan.price sin cascada); reseller solo PATCH reseller_price. Plan anidado con zones[], rates si show_speed_limits_to_user, tunnel_type, allow_port_forwarding y description_for_user (fallback al plan).

### Network gestión — network/ (solo manager) (13)

1. **GET/POST/PATCH/DELETE** — `/api/manager/v1/network/agents/` — staff — Agentes proxy. Lee rooms_count/rooms_capacity (= activas × operativo, capado por max_rooms)/rooms_per_iface/active_ifaces. status solo unknown/disabled (no reenviar sin cambios). Filtros status/is_proxy_active. Zonas M2M: lee zones[], escribe zones_ids (omitido/null=no toca, []=limpia). Cambiar cf_dns_api_token lo valida el agente (400 sin guardar si offline/inválido). Cambio de zonas emite agent_updated por WS.
2. **POST** — `/api/manager/v1/network/agents/<id>/regenerate-url/` — staff — Rota el token → { id, token }.
3. **POST** — `/api/manager/v1/network/agents/<id>/resync/` — staff — Reconciliación manual (lock 60s; 400 si offline, 409 si en progreso).
4. **POST** — `/api/manager/v1/network/agents/<id>/renew-cert/` — staff — Renovación manual del cert proxy (cert.renew forzado a producción; 400 si offline/sin dominio/DNS sin apuntar salvo skip_dns_check, 409 si en progreso; 202 + reporte).
5. **POST** — `/api/manager/v1/network/agents/<id>/logs/` — staff — Logs cacheados por categoría (body: force, count, include_debug, category=all|general|rooms|proxy). En vivo: force+stream pide snapshot+push cada interval; stream sin force es heartbeat (30s); stop cancela.
6. **GET/POST/PATCH/DELETE** — `/api/manager/v1/network/mikrotik/` — staff — CHR L2TP/WG. agent NULL en direct, obligatorio en agent. Nuevos en direct por defecto. Zonas M2M: lee zones[], escribe zones_ids (omitido/null=no toca, []=limpia).
7. **PATCH** — `/api/manager/v1/network/mikrotik/<id>/interfaces/` — staff — Actualiza interfaz WG { internal_id (obligatorio, solo lookup), ... }. Zonas M2M: zones_ids (omitido/null=no toca, []=limpia). enforce_limits (default false): aplica shaping en la interfaz; OFF=no se tocan queues; ON->OFF purga una vez las propias (direct: monitor; agent: reconcile). Requiere rango IP. Emite interface_updated por WS.
8. **POST** — `/api/manager/v1/network/mikrotik/<id>/monitor/` — staff — Encola monitor_mikrotik_direct. 400 si no es direct o está disabled.
9. **POST** — `/api/manager/v1/network/mikrotik/monitor-all/` — staff — Programa monitoreo de todos los CHR directos.
10. **GET/POST/PATCH/DELETE** — `/api/manager/v1/network/zones/` — staff/reseller — Zonas lógicas (solo una default=True).
11. **GET/POST/PATCH/DELETE** — `/api/manager/v1/network/outs/` — staff — Salidas con país opcional.
12. **GET/POST/PATCH/DELETE** — `/api/manager/v1/network/reseller/zones/` — staff/reseller — Zonas reseller. Admin CRUD; reseller lectura + PATCH is_default. hosts con sensibles filtrados.
13. **GET/POST/PATCH/DELETE** — `/api/manager/v1/network/queue-profiles/` — staff — Perfiles de queue preconfigurados (solo WireGuard, fase 1). Tasas 1..1000 Mbps (NULL/NULL=sin regla), queue up/down/total (default cake), prioridad 1..8, limit_at/burst opcionales. Expone plans_count + tunnels_count. Solo se aplican en interfaces con enforce_limits=true.

### Devices, Telegram, logs, sistema (solo manager) (17)

1. **GET/PATCH/DELETE** — `/api/manager/v1/devices/management/` (+ `<id>/`) — staff — Todos los dispositivos. Sin POST. Búsqueda device_name/app_hash/user__username/user__email/user__alias + históricos (logins__user__*) + ID exacto. Filtros device_type/is_active. Expone last_users[10] + last_user y metadata {total_devices,active_devices,recent_devices}.
2. **GET/POST/PATCH/DELETE** — `/api/manager/v1/devices/errors/` — staff — Reportes de error de dispositivos.
3. **POST** — `/api/manager/v1/devices/errors/<id>/resolve/` — staff — Marca el error como resuelto.
4. **POST** — `/api/manager/v1/devices/errors/bulk-delete/` — staff — Borrado masivo { ids: [...] }.
5. **GET/PUT/PATCH** — `/api/manager/v1/telegram/config/` — superuser — Singleton TelegramBotConfig (token cifrado).
6. **GET/POST** — `/api/manager/v1/telegram/messages/` — staff/reseller — Mensajería masiva (sin PUT/PATCH/DELETE).
7. **POST** — `/api/manager/v1/telegram/messages/preview/` — staff/reseller — Previsualiza mensaje masivo.
8. **GET** — `/api/manager/v1/telegram/messages/<id>/logs/` — staff/reseller — Logs de envío del mensaje.
9. **POST** — `/api/manager/v1/telegram/messages/<id>/retry/` — staff/reseller — Reintenta el envío.
10. **GET/DELETE** — `/api/manager/v1/logs/system-errors/` (+ `<id>/`) — superuser — Errores del sistema. Sin POST/PUT/PATCH (405). Filtros level; metadata con conteos.
11. **POST** — `/api/manager/v1/logs/system-errors/delete/` — superuser — Borrado bulk { ids: [...] }.
12. **POST** — `/api/manager/v1/system/run-backup/` — admin — Ejecuta backup (IsAdminUser).
13. **GET** — `/api/manager/v1/system/apps/` — superuser — Lista releases windows/android/linux (crea filas faltantes).
14. **PATCH** — `/api/manager/v1/system/apps/<platform>/` — superuser — Edita release: versiones semver, URL, changelog, size, sha256, show_in_banner, is_published.
15. **GET** — `/api/manager/v1/system/backups/` — admin — Lista backups.
16. **GET** — `/api/manager/v1/system/backups/<filename>/download/` — admin — Descarga backup.
17. **DELETE** — `/api/manager/v1/system/backups/<filename>/` — admin — Elimina backup.

---

## /api/hooks/

### Webhooks (2)

1. **POST** — `/api/hooks/payments/<provider>/<token>/` — webhook — Callback de provider (AllowAny, secreto en URL). Token inválido → 400/403/404. Aguas abajo: process_invoice_payment().
2. **POST** — `/api/hooks/telegram/` — webhook — Webhook de Telegram (firma externa). Re-registrar tras cambio de dominio.

---

## /api/

### Sin versionar (2)

1. **GET** — `/api/site-info/` — public — Gate web: companyName, maintenanceMode, flags de registro/P2P/comisiones. El login lo consulta sin caché.
2. **PATCH** — `/api/site-info/` — superuser — Edita SiteConfiguration. Cambiar maintenance_mode notifica a staff + emite WS global.

### WebSockets (2)

1. **WS** — `/api/ws/?token=<refresh_token>` — WS token — WS usuarios. Grupos global/client/staff/superuser/<user_id>/<session_id>. Eventos: site-configuration, new-notification, new-announcement, entidades *-created|*-updated|*-deleted con {ids, action} (tunnel-updated, room-updated a dueño+miembros+staff+reseller). Throttle 1/s.
2. **WS** — `/api/agents/?token=<agent_token>` — WS token — WS agentes (grupo sha256(token)). agent.init, mikrotik.report, heartbeat, ack/error/logs. Cierres 4001/4003/4029.
