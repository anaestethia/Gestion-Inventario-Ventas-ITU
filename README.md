# Nexo — Gestión comercial

Aplicación de escritorio para administrar un inventario de indumentaria, registrar ventas y movimientos de stock, consultar indicadores y exportar reportes. Está desarrollada con Java 25, Swing y Java2D; los datos se guardan en MySQL.

## Requisitos

- Java 25 o superior
- Maven 3.9 o superior
- MySQL en ejecución y un usuario con permisos para crear la base y sus tablas

La aplicación usa MySQL Connector/J para conectarse. En el primer inicio solicita los datos de conexión; por defecto propone `localhost:3306` y `tienda_de_ropa`. La configuración, incluida la contraseña protegida con Windows DPAPI, se guarda fuera del proyecto en `%APPDATA%\Nexo\database.properties`.

## Ejecutar

Desde la carpeta del proyecto:

```bash
mvn exec:java
```

También se puede compilar y verificar el proyecto con:

```bash
mvn clean verify
```

## Primer inicio y perfiles

1. Iniciá MySQL.
2. Ejecutá Nexo y completá el formulario de conexión. La aplicación crea la base y las tablas que necesita en esa instancia.
3. Ingresá con uno de los perfiles iniciales:
   - **Lourdes Torassi** — usuario `lourdes`, contraseña inicial `1234` (dueña).
   - **Lola** — usuario `lola`, contraseña inicial `1234` (empleada).
4. Cambiá las contraseñas iniciales desde **Configuración → Usuarios**. La administración de perfiles está disponible solo para la dueña; Lola puede operar inventario, ventas y movimientos.

La contraseña MySQL no se guarda en el repositorio. El esquema se amplía únicamente en la base local; el proyecto SQL remoto no se modifica.

## Funciones

- **Resumen:** indicadores y gráficos calculados con ventas, productos y movimientos guardados.
- **Inventario:** crear, editar, buscar, filtrar y eliminar productos; importar desde CSV y Excel (`.xlsx`/`.xls`) con vista previa, control de stock y registro de ingresos, salidas y ajustes.
- **Ventas:** carrito de productos, registro de pagos, historial, cancelación con reintegro de stock y exportación CSV.
- **Movimientos:** historial con filtros de tipo y fecha, registro por producto y exportación CSV.
- **Configuración:** datos del comercio, preferencias de stock, límite de cuotas, perfiles, comprobantes, escala de interfaz, exportación SQL y diagnóstico de conexión.

En **Configuración → General** podés cambiar el nombre del comercio y elegir un logotipo PNG, JPEG, GIF o BMP. El nombre se muestra en la barra lateral y el logotipo reemplaza el icono azul. El archivo del logo se guarda en `%APPDATA%\Nexo`.

Para importar, seleccioná **Inventario → Importar archivo**. El archivo debe tener encabezados para producto/descripción y stock/cantidad; se reconocen también columnas opcionales de código, categoría, marca, talle, color, temporada, costo, precios, cuotas y stock mínimo. Se muestra una vista previa antes de importar. Los repetidos se identifican por código de barras o, si no tienen código, por descripción, talle, color, marca y categoría; en esos casos se suma el stock importado. Las filas inválidas se informan y se omiten.

Las vistas se cargan bajo demanda y las consultas a MySQL se ejecutan en segundo plano para que la ventana siga respondiendo al navegar. El resumen y las ventas reutilizan una conexión para calcular las métricas; el historial de ventas muestra las 500 operaciones más recientes. Las consultas tienen tiempo límite; si fallan, la pantalla informa el problema y ofrece reintentar.

## Base de datos

La aplicación conserva las tablas `categoria`, `producto`, `venta` y `detalle_venta` del esquema original, y crea las tablas locales de usuarios, movimientos y configuración. Al arrancar, aplica las columnas adicionales que requiere Nexo y agrega categorías y perfiles iniciales únicamente cuando faltan.

El primer inicio requiere permisos suficientes para `CREATE DATABASE`, `CREATE TABLE` y cambios de esquema (`ALTER TABLE`). Los datos de demostración fueron retirados: las tablas y los gráficos empiezan vacíos salvo por las categorías y los dos perfiles iniciales.
