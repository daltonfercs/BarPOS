# 🍻 BarPOS — Sistema Ágil de Pedidos y Cocina

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-green?logo=android" alt="Platform">
  <img src="https://img.shields.io/badge/Language-Kotlin-purple?logo=kotlin" alt="Language">
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-blue?logo=jetpackcompose" alt="Compose">
  <img src="https://img.shields.io/badge/Database-Google%20Sheets-success?logo=googlesheets" alt="Google Sheets">
  <img src="https://img.shields.io/badge/License-MIT-yellow" alt="License">
</p>

**BarPOS** es una aplicación móvil nativa para Android diseñada para bares, restobares y locales de comida rápida. Permite registrar comandas ágilmente, calcular totales en tiempo real, sincronizar pedidos con cocina (KDS) con temporizador estimado y generar reportes clasificados por categoría de consumo (Bebidas vs. Comidas) y método de pago (Efectivo, Yape, Mixto), utilizando **Google Sheets como base de datos serverless sin costo de infraestructura**.

---

## 🚀 Características Principales

- **📱 Toma Rápida de Pedidos**: Catálogo segmentado por categorías (Bebidas, Alitas, Salchipapas, etc.).
- **💳 Múltiples Medios de Pago**: Registro de Efectivo, Yape o Mixto, indicando si es pedido en Mesa o Para Llevar.
- **👨🍳 Vista de Cocina (KDS)**: Tablero de comandas pendientes en orden de llegada con cuenta regresiva estimada de 15 minutos por ticket.
- **📊 Reportes Automáticos**: Agrupación instantánea de ventas del día por categoría (Bebida vs. Comida) y totales recaudados.
- **☁️ Backend Serverless con Google Drive**: Registro directo en una hoja de cálculo mediante Google Apps Script.

---

## 🛠️ Stack Tecnológico

- **Lenguaje**: Kotlin
- **Interfaz de Usuario**: Jetpack Compose (Material 3)
- **Arquitectura**: MVVM (Model-View-ViewModel) + Clean Architecture
- **Red y Serialización**: Retrofit 2 + Gson / Kotlinx Serialization + OkHttp
- **Base de Datos / Backend**: Google Sheets API + Google Apps Script Web App

---

## 📋 Requisitos Previos

1. Dispositivo Android con versión 8.0 (API 26) o superior.
2. Cuenta personal o corporativa de **Google**.
3. Android Studio (Ladybug / Koala o superior) para compilar el proyecto.

---

## ⚙️ Configuración del Backend (Google Drive & Sheets)

Para que la aplicación funcione, es necesario crear y desplegar una hoja de cálculo en tu propio Google Drive que actuará como base de datos. Sigue estos pasos:

### Paso 1: Crear la Hoja de Cálculo
1. Ve a [Google Sheets](https://sheets.new) y crea un nuevo documento llamado **`Base_Datos_BarPOS`**.
2. En la primera fila (Fila 1), crea exactamente las siguientes 10 columnas como encabezados:

| A | B | C | D | E | F | G | H | I | J |
|---|---|---|---|---|---|---|---|---|---|
| **ID_Pedido** | **Fecha_Hora** | **Categoria** | **Producto** | **Cantidad** | **Precio_Unitario** | **Total_Item** | **Tipo_Orden** | **Metodo_Pago** | **Estado_Cocina** |

---

### Paso 2: Crear el Script de Apps Script
1. En el menú superior de la hoja de cálculo, ve a **Extensiones** > **Apps Script**.
2. Borra el código por defecto y pega el siguiente script:

```javascript
const SHEET_NAME = "Ventas_Bar";

function setupSheet() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  let sheet = ss.getSheetByName(SHEET_NAME);
  if (!sheet) {
    sheet = ss.insertSheet(SHEET_NAME);
  }
  const headers = [
    "ID_Pedido", "Fecha_Hora", "Categoria", "Producto",
    "Cantidad", "Precio_Unitario", "Total_Item", "Tipo_Orden",
    "Metodo_Pago", "Estado_Cocina"
  ];
  if (sheet.getLastRow() === 0) {
    sheet.appendRow(headers);
    sheet.getRange(1, 1, 1, headers.length).setFontWeight("bold");
  }
}

function doGet(e) {
  const lock = LockService.getScriptLock();
  lock.tryLock(10000);
  try {
    const ss = SpreadsheetApp.getActiveSpreadsheet();
    const sheet = ss.getSheetByName(SHEET_NAME) || ss.getSheets()[0];
    if (sheet.getLastRow() <= 1) return jsonResponse({ status: "success", data: [] });

    const rows = sheet.getDataRange().getValues();
    const headers = rows[0];
    const data = [];
    const filtroEstado = e && e.parameter ? e.parameter.estado : null;

    for (let i = 1; i < rows.length; i++) {
      const row = rows[i];
      const item = {};
      headers.forEach((header, index) => { item[header] = row[index]; });
      if (!filtroEstado || item.Estado_Cocina === filtroEstado) {
        data.push(item);
      }
    }
    return jsonResponse({ status: "success", data: data });
  } catch (error) {
    return jsonResponse({ status: "error", message: error.toString() });
  } finally {
    lock.releaseLock();
  }
}

function doPost(e) {
  const lock = LockService.getScriptLock();
  lock.tryLock(30000);
  try {
    const ss = SpreadsheetApp.getActiveSpreadsheet();
    let sheet = ss.getSheetByName(SHEET_NAME) || ss.getSheets()[0];
    const body = JSON.parse(e.postData.contents);
    const action = body.action || "crear_pedido";

    if (action === "crear_pedido") {
      const p = body.pedido;
      const fechaHora = p.fecha_hora || new Date().toISOString();
      const idPedido = p.id_pedido || Utilities.getUuid().substring(0, 8);
      const filas = [];

      p.items.forEach(item => {
        filas.push([
          idPedido, fechaHora, item.categoria, item.producto,
          item.cantidad, item.precio_unitario, item.total_item,
          p.tipo_orden, p.metodo_pago, "Pendiente"
        ]);
      });

      if (filas.length > 0) {
        const nextRow = sheet.getLastRow() + 1;
        sheet.getRange(nextRow, 1, filas.length, 10).setValues(filas);
      }
      return jsonResponse({ status: "success", id_pedido: idPedido });
    } else if (action === "marcar_listo") {
      const idPedido = body.id_pedido;
      const data = sheet.getDataRange().getValues();
      for (let i = 1; i < data.length; i++) {
        if (data[i][0] == idPedido) {
          sheet.getRange(i + 1, 10).setValue("Listo");
        }
      }
      return jsonResponse({ status: "success" });
    }
    return jsonResponse({ status: "error", message: "Acción no válida" });
  } catch (err) {
    return jsonResponse({ status: "error", message: err.toString() });
  } finally {
    lock.releaseLock();
  }
}

function jsonResponse(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}
```
### Paso 3: Despliegue de la API Web en Google Apps Script

Para que la app Android pueda enviar y leer datos sin necesidad de autenticaciones complejas, la hoja debe publicarse como un servicio web accesible:

1. **Abrir el asistente de despliegue**:
   - En el menú superior del editor de Google Apps Script, haz clic en el botón azul **Implementar** y selecciona **Nueva implementación**.
2. **Definir el tipo de servicio**:
   - Pulsa en el engranaje (**Seleccionar tipo**) y escoge **Aplicación web**.
3. **Completar los parámetros**:
   - **Descripción**: `API BarPOS v1`
   - **Ejecutar como**: `Yo (tu correo de Google)`
   - **Quién tiene acceso**: `Cualquier usuario` *(Permite que las peticiones HTTP desde el móvil se procesen sin trabas)*.
4. **Conceder autorizaciones**:
   - Pulsa **Implementar**.
   - Haz clic en **Revisar permisos** y selecciona tu cuenta de Google.
   - En la pantalla de aviso de seguridad, pulsa en **Configuración avanzada** y luego en **Ir a Proyecto (no seguro)**.
   - Presiona **Permitir**.
5. **Guardar el endpoint**:
   - Copia la **URL de la aplicación web** generada (termina en `/exec`). Esta dirección será tu `BASE_URL` en Kotlin.

---

### Paso 4: Integración en la App Android (Jetpack Compose & Retrofit)

Con el backend desplegado, se procede a enlazar el cliente HTTP en el código nativo:

#### 1. Dependencias de red en `app/build.gradle.kts`
Asegúrate de incluir Retrofit, el convertidor JSON y OkHttp con soporte para logging:

```kotlin
dependencies {
    // Red y serialización
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Ciclo de vida y ViewModel en Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
}
```
📄 Licencia

Este proyecto está bajo la Licencia MIT. 
