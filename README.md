# 🍰 Estación Dulce - Gestión de Pastelería

Una aplicación Android moderna para la gestión integral de una pastelería, desarrollada con Kotlin y Firebase.

## 📱 Descripción

Estación Dulce es una aplicación móvil diseñada para gestionar todos los aspectos de una pastelería, desde el inventario de productos hasta las recetas, clientes y movimientos comerciales. La aplicación utiliza Firebase como backend para sincronización en tiempo real y almacenamiento de datos.

## ✨ Características Principales

### 🏠 Dashboard Principal
- Vista general con métricas clave del negocio
- Navegación intuitiva entre módulos
- Información de versión y estado del sistema

### 📦 Gestión de Productos
- Catálogo completo de productos
- Control de inventario en tiempo real
- Gestión de categorías y medidas
- Búsqueda y filtrado avanzado

### 👨‍🍳 Gestión de Recetas
- Creación y edición de recetas complejas
- Cálculo automático de costos
- Estructura jerárquica con secciones
- Recetas anidadas para mayor flexibilidad
- Gestión de precios de venta

### 👥 Gestión de Personas
- Registro de clientes y proveedores
- Información de contacto completa
- Gestión de direcciones múltiples
- Historial de interacciones

### 📊 Gestión de Movimientos
- Registro de entradas y salidas
- Seguimiento de inventario
- Reportes de movimientos
- Integración con productos y personas

## 🛠️ Tecnologías Utilizadas

- **Lenguaje**: Kotlin
- **Plataforma**: Android (API 30+)
- **Backend**: Firebase
  - Firestore (Base de datos)
  - Authentication (Autenticación)
  - Storage (Almacenamiento de archivos)
  - App Check (Seguridad)
- **UI/UX**: Material Design
- **Arquitectura**: MVVM con LiveData
- **Binding**: View Binding

## 📋 Requisitos del Sistema

- **Android**: 11.0 (API 30) o superior
- **RAM**: Mínimo 2GB recomendado
- **Almacenamiento**: 50MB libres
- **Conexión**: Internet para sincronización

## 🚀 Instalación

### Prerrequisitos
- Android Studio Arctic Fox o superior
- JDK 11 o superior
- Cuenta de Firebase configurada

### Pasos de Instalación

1. **Clonar el repositorio**
   ```bash
   git clone https://github.com/MaxMayoris/estacion-dulce-android.git
   cd estacion-dulce-android
   ```

2. **Configurar Firebase**
   - Crear un proyecto en [Firebase Console](https://console.firebase.google.com/)
   - Descargar los archivos `google-services.json` para los entornos `dev` y `prod`
   - Colocar los archivos en:
     - `app/src/dev/google-services.json`
     - `app/src/prod/google-services.json`

3. **Configurar variables de entorno** (para builds de release)
   ```bash
   export ESTACION_KEYSTORE_PASSWORD="tu_password_keystore"
   export ESTACION_KEY_ALIAS="tu_alias"
   export ESTACION_KEY_PASSWORD="tu_password_key"
   ```

4. **Compilar y ejecutar**
   ```bash
   ./gradlew assembleDevDebug
   ```

## 🏗️ Estructura del Proyecto

```
app/
├── src/
│   ├── main/
│   │   ├── java/com/estaciondulce/app/
│   │   │   ├── activities/          # Actividades principales
│   │   │   ├── fragments/           # Fragmentos de UI
│   │   │   ├── models/              # Modelos de datos
│   │   │   ├── repository/          # Repositorio de datos
│   │   │   ├── helpers/             # Utilidades y helpers
│   │   │   └── utils/               # Componentes UI personalizados
│   │   ├── res/                     # Recursos (layouts, drawables, etc.)
│   │   └── AndroidManifest.xml
│   ├── dev/                         # Configuración desarrollo
│   └── prod/                        # Configuración producción
├── build.gradle.kts                 # Configuración del módulo
└── proguard-rules.pro              # Reglas de ofuscación
```

## 🔧 Configuración de Build

El proyecto utiliza **product flavors** para diferentes entornos:

- **dev**: Entorno de desarrollo
  - Application ID: `com.estaciondulce.app.dev`
  - Sufijo de versión: `-dev`

- **prod**: Entorno de producción
  - Application ID: `com.estaciondulce.app.prod`
  - Sufijo de versión: `-prod`

## 🔐 Seguridad

- **Firebase App Check**: Protección contra abuso
- **Autenticación**: Sistema de login seguro
- **Validación**: Verificación de datos en cliente y servidor
- **Archivos sensibles**: Excluidos del control de versiones

## 📱 Capturas de Pantalla

*[Aquí puedes agregar capturas de pantalla de la aplicación]*

## 🤝 Contribución

1. Fork el proyecto
2. Crea una rama para tu feature (`git checkout -b feature/AmazingFeature`)
3. Commit tus cambios (`git commit -m 'Add some AmazingFeature'`)
4. Push a la rama (`git push origin feature/AmazingFeature`)
5. Abre un Pull Request

## 📝 Licencia

Este proyecto está bajo la Licencia MIT. Ver el archivo `LICENSE` para más detalles.

## 👨‍💻 Autor

**Maximiliano Roldan**
- Email: maxir.unsj@gmail.com
- GitHub: [@MaxMayoris](https://github.com/MaxMayoris)

## 📞 Soporte

Si tienes preguntas o necesitas ayuda, puedes:
- Abrir un issue en GitHub
- Contactar al desarrollador por email

## 🔄 Historial de Versiones

- **v2.1** - Versión actual con mejoras de UI y funcionalidades
- **v2.0** - Refactorización completa del código
- **v1.x** - Versiones iniciales

---

⭐ Si te gusta este proyecto, ¡dale una estrella en GitHub!
