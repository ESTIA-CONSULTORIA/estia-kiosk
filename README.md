# Estia Kiosk – Bloqueo de tablets Android (Expendio)

Deja la tablet usable **solo** para las apps que el administrador marque
(Soft Restaurant y Calculadora), sin restablecer el equipo.

## Cómo funciona

| Modo | Cuándo | Qué logra |
|------|--------|-----------|
| **Plan A – Device Owner** | Se activa por ADB (ver abajo) | Control real del sistema: suspende todas las apps no autorizadas, bloquea barra de estado, ajustes, instalación, USB, Bluetooth, cámara, cuentas, reset de fábrica y más. Botón Inicio siempre regresa al kiosco. |
| **Plan B – Vigilante** | Si el modelo no permite Device Owner | Servicio de accesibilidad que cierra cualquier app no autorizada y regresa al kiosco. Menos hermético. |

Toda la configuración se hace **en la propia tablet**: toque 7 veces seguidas el
título "Expendio" → PIN de administrador. No requiere internet.

## 1. Compilar

1. Abra la carpeta `EstiaKiosk` en **Android Studio** (genera el wrapper de Gradle solo).
2. `Build > Build APK(s)`. El APK queda en `app/build/outputs/apk/debug/app-debug.apk`.

## 2. Preparar cada tablet (sin borrar Soft Restaurant)

1. Ajustes > Acerca de la tablet > Información de software > toque 7 veces "Número de compilación".
2. Ajustes > Opciones de desarrollador > active **Depuración USB**.
3. **Quite temporalmente las cuentas** (Google, Samsung) en Ajustes > Cuentas.
   Android solo permite Device Owner si no hay cuentas. Los datos de Soft Restaurant no se tocan.
4. Conecte la tablet a una PC con ADB y ejecute:

```bash
adb install app-debug.apk
adb shell dpm set-device-owner com.estia.kiosk/.AdminReceiver
```

Debe responder `Success: Device owner set to package ...`.

5. Abra **Estia Kiosk**, cree el PIN, marque las apps permitidas, ajuste bloqueos/horario y pulse **Guardar y aplicar**.
6. Opcional: vuelva a agregar las cuentas (la opción "Bloquear cuentas" evita nuevas altas).

### Errores comunes
- `Not allowed to set the device owner because there are already some accounts` → quite todas las cuentas.
- `...already some users on the device` → quite usuarios secundarios y Secure Folder.
- Si sigue fallando, use el **Plan B**: ponga Estia Kiosk como app de inicio y active el vigilante en Ajustes > Accesibilidad.

## 3. Uso diario del administrador
- **Guardar y aplicar**: cambia apps, bloqueos y horario al instante.
- **Modo mantenimiento (15 min)**: libera la tablet para cambiar Wi‑Fi o actualizar apps; el kiosco se reactiva solo.
- **Desactivar kiosco por completo**: quita todo y deja de ser Device Owner.
- Si pierde el PIN: `adb shell dpm remove-active-admin com.estia.kiosk/.AdminReceiver` (requiere depuración USB activa; por eso conviene dejar "Bloquear depuración USB" apagado).

## Pendiente (fase 2)
- Panel web sobre Supabase para administrar varias tablets desde un solo lugar (la configuración local ya está separada en `Prefs.kt`, solo faltaría sincronizarla).
- Filtro de sitios web por DNS (con lista blanca de apps ya no hay navegador disponible, por lo que la web queda bloqueada de hecho).

## Notas
- No se pudo compilar en este entorno (sin Android SDK): hay que probar en una tablet real y ajustar si el modelo Samsung se comporta distinto.
