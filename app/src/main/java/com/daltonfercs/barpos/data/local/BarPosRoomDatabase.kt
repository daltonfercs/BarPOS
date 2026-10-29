package com.daltonfercs.barpos.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.daltonfercs.barpos.model.PedidoItem
import com.daltonfercs.barpos.model.Producto
import com.daltonfercs.barpos.model.TableModel

@Entity(tableName = "productos")
data class ProductoEntity(
    @PrimaryKey
    @ColumnInfo(name = "id_key")
    val idKey: String,

    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "nombre")
    val nombre: String,

    @ColumnInfo(name = "categoria")
    val categoria: String,

    @ColumnInfo(name = "tipo")
    val tipo: String,

    @ColumnInfo(name = "precio")
    val precio: Double,

    @ColumnInfo(name = "disponible")
    val disponible: Boolean,

    @ColumnInfo(name = "stock")
    val stock: Int
)

@Entity(tableName = "mesas")
data class MesaEntity(
    @PrimaryKey
    @ColumnInfo(name = "id_mesa")
    val idMesa: String,

    @ColumnInfo(name = "numero")
    val numero: Int,

    @ColumnInfo(name = "capacidad")
    val capacidad: Int,

    @ColumnInfo(name = "estado")
    val estado: String
)

@Entity(tableName = "pedidos")
data class PedidoItemEntity(
    @PrimaryKey
    @ColumnInfo(name = "item_key")
    val itemKey: String, // "${idPedido}_${producto}"

    @ColumnInfo(name = "id_pedido")
    val idPedido: String,

    @ColumnInfo(name = "fecha_hora")
    val fechaHora: String,

    @ColumnInfo(name = "categoria")
    val categoria: String,

    @ColumnInfo(name = "producto")
    val producto: String,

    @ColumnInfo(name = "cantidad")
    val cantidad: Int,

    @ColumnInfo(name = "precio_unitario")
    val precioUnitario: Double,

    @ColumnInfo(name = "total_item")
    val totalItem: Double,

    @ColumnInfo(name = "tipo_orden")
    val tipoOrden: String,

    @ColumnInfo(name = "id_mesa")
    val idMesa: String,

    @ColumnInfo(name = "cliente")
    val cliente: String,

    @ColumnInfo(name = "metodo_pago")
    val metodoPago: String,

    @ColumnInfo(name = "estado_cocina")
    val estadoCocina: String,

    @ColumnInfo(name = "estado_pago")
    val estadoPago: String,

    @ColumnInfo(name = "monto_efectivo")
    val montoEfectivo: Double,

    @ColumnInfo(name = "monto_yape")
    val montoYape: Double,

    @ColumnInfo(name = "start_time_millis")
    val startTimeMillis: Long
)

fun Producto.toEntity(): ProductoEntity = ProductoEntity(
    idKey = id.ifBlank { nombre },
    id = id.ifBlank { nombre },
    nombre = nombre,
    categoria = categoria,
    tipo = tipo,
    precio = precio,
    disponible = disponible,
    stock = stock
)

fun ProductoEntity.toDomain(): Producto = Producto(
    id = id,
    nombre = nombre,
    categoria = categoria,
    tipo = tipo,
    precio = precio,
    disponible = disponible,
    stock = stock
)

fun TableModel.toEntity(): MesaEntity = MesaEntity(
    idMesa = idMesa,
    numero = numero,
    capacidad = capacidad,
    estado = estado
)

fun MesaEntity.toDomain(): TableModel = TableModel(
    idMesa = idMesa,
    numero = numero,
    capacidad = capacidad,
    estado = estado
)

fun PedidoItem.toEntity(startTimeMillis: Long = System.currentTimeMillis()): PedidoItemEntity =
    PedidoItemEntity(
        itemKey = "${idPedido}_${producto}",
        idPedido = idPedido,
        fechaHora = fechaHora,
        categoria = categoria,
        producto = producto,
        cantidad = cantidad,
        precioUnitario = precioUnitario,
        totalItem = totalItem,
        tipoOrden = tipoOrden,
        idMesa = idMesa,
        cliente = cliente,
        metodoPago = metodoPago,
        estadoCocina = estadoCocina,
        estadoPago = estadoPago,
        montoEfectivo = montoEfectivo,
        montoYape = montoYape,
        startTimeMillis = startTimeMillis
    )

fun PedidoItemEntity.toDomain(): PedidoItem = PedidoItem(
    idPedido = idPedido,
    fechaHora = fechaHora,
    categoria = categoria,
    producto = producto,
    cantidad = cantidad,
    precioUnitario = precioUnitario,
    totalItem = totalItem,
    tipoOrden = tipoOrden,
    idMesa = idMesa,
    cliente = cliente,
    metodoPago = metodoPago,
    estadoCocina = estadoCocina,
    estadoPago = estadoPago,
    montoEfectivo = montoEfectivo,
    montoYape = montoYape
)

@Dao
interface BarPosDao {
    @Query("SELECT * FROM productos")
    fun getAllProductos(): List<ProductoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertProductos(productos: List<ProductoEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertProducto(producto: ProductoEntity)

    @Query("SELECT * FROM mesas ORDER BY numero ASC")
    fun getAllMesas(): List<MesaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertMesas(mesas: List<MesaEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertMesa(mesa: MesaEntity)

    @Query("SELECT * FROM pedidos")
    fun getAllPedidos(): List<PedidoItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertPedidos(pedidos: List<PedidoItemEntity>)

    @Query("DELETE FROM pedidos WHERE id_pedido = :idPedido")
    fun deletePedidosByOrderId(idPedido: String)

    @Query("DELETE FROM pedidos")
    fun clearAndReplaceAllPedidos(pedidos: List<PedidoItemEntity>)
}

@Database(
    entities = [ProductoEntity::class, MesaEntity::class, PedidoItemEntity::class],
    version = 1,
    exportSchema = false
)
abstract class BarPosRoomDatabase {
    abstract fun barPosDao(): BarPosDao

    companion object {
        private const val DB_NAME = "barpos_room.db"
        private const val DB_VERSION = 1

        @Volatile
        private var INSTANCE: BarPosRoomDatabase? = null

        fun getInstance(context: Context): BarPosRoomDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RoomSqliteDatabaseImpl(context.applicationContext, DB_NAME, DB_VERSION)
                    .also { INSTANCE = it }
            }
        }
    }
}

/**
 * Implementación directa compatible con Room y SQLite para persistir en barpos_room.db
 * sin depender de procesadores KAPT/KSP externos en Kotlin 2.3+.
 */
private class RoomSqliteDatabaseImpl(
    context: Context,
    dbName: String,
    dbVersion: Int
) : BarPosRoomDatabase(), BarPosDao {

    private val helper = object : SQLiteOpenHelper(context, dbName, null, dbVersion) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS productos (
                    id_key TEXT PRIMARY KEY NOT NULL,
                    id TEXT NOT NULL,
                    nombre TEXT NOT NULL,
                    categoria TEXT NOT NULL,
                    tipo TEXT NOT NULL,
                    precio REAL NOT NULL,
                    disponible INTEGER NOT NULL,
                    stock INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS mesas (
                    id_mesa TEXT PRIMARY KEY NOT NULL,
                    numero INTEGER NOT NULL,
                    capacidad INTEGER NOT NULL,
                    estado TEXT NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS pedidos (
                    item_key TEXT PRIMARY KEY NOT NULL,
                    id_pedido TEXT NOT NULL,
                    fecha_hora TEXT NOT NULL,
                    categoria TEXT NOT NULL,
                    producto TEXT NOT NULL,
                    cantidad INTEGER NOT NULL,
                    precio_unitario REAL NOT NULL,
                    total_item REAL NOT NULL,
                    tipo_orden TEXT NOT NULL,
                    id_mesa TEXT NOT NULL,
                    cliente TEXT NOT NULL,
                    metodo_pago TEXT NOT NULL,
                    estado_cocina TEXT NOT NULL,
                    estado_pago TEXT NOT NULL,
                    monto_efectivo REAL NOT NULL,
                    monto_yape REAL NOT NULL,
                    start_time_millis INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            onCreate(db)
        }
    }

    override fun barPosDao(): BarPosDao = this

    @Synchronized
    override fun getAllProductos(): List<ProductoEntity> {
        val list = mutableListOf<ProductoEntity>()
        val db = helper.readableDatabase
        db.rawQuery("SELECT id_key, id, nombre, categoria, tipo, precio, disponible, stock FROM productos", null)
            .use { cursor ->
                while (cursor.moveToNext()) {
                    list.add(
                        ProductoEntity(
                            idKey = cursor.getString(0) ?: "",
                            id = cursor.getString(1) ?: "",
                            nombre = cursor.getString(2) ?: "",
                            categoria = cursor.getString(3) ?: "General",
                            tipo = cursor.getString(4) ?: "Genérico",
                            precio = cursor.getDouble(5),
                            disponible = cursor.getInt(6) != 0,
                            stock = cursor.getInt(7)
                        )
                    )
                }
            }
        return list
    }

    @Synchronized
    override fun insertProductos(productos: List<ProductoEntity>) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            for (p in productos) {
                db.insertWithOnConflict("productos", null, p.toContentValues(), SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    override fun insertProducto(producto: ProductoEntity) {
        val db = helper.writableDatabase
        db.insertWithOnConflict("productos", null, producto.toContentValues(), SQLiteDatabase.CONFLICT_REPLACE)
    }

    @Synchronized
    override fun getAllMesas(): List<MesaEntity> {
        val list = mutableListOf<MesaEntity>()
        val db = helper.readableDatabase
        db.rawQuery("SELECT id_mesa, numero, capacidad, estado FROM mesas ORDER BY numero ASC", null)
            .use { cursor ->
                while (cursor.moveToNext()) {
                    list.add(
                        MesaEntity(
                            idMesa = cursor.getString(0) ?: "",
                            numero = cursor.getInt(1),
                            capacidad = cursor.getInt(2),
                            estado = cursor.getString(3) ?: "Libre"
                        )
                    )
                }
            }
        return list
    }

    @Synchronized
    override fun insertMesas(mesas: List<MesaEntity>) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            for (m in mesas) {
                db.insertWithOnConflict("mesas", null, m.toContentValues(), SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    override fun insertMesa(mesa: MesaEntity) {
        val db = helper.writableDatabase
        db.insertWithOnConflict("mesas", null, mesa.toContentValues(), SQLiteDatabase.CONFLICT_REPLACE)
    }

    @Synchronized
    override fun getAllPedidos(): List<PedidoItemEntity> {
        val list = mutableListOf<PedidoItemEntity>()
        val db = helper.readableDatabase
        db.rawQuery(
            """
            SELECT item_key, id_pedido, fecha_hora, categoria, producto, cantidad,
                   precio_unitario, total_item, tipo_orden, id_mesa, cliente,
                   metodo_pago, estado_cocina, estado_pago, monto_efectivo, monto_yape, start_time_millis
            FROM pedidos
            """.trimIndent(),
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(
                    PedidoItemEntity(
                        itemKey = cursor.getString(0) ?: "",
                        idPedido = cursor.getString(1) ?: "",
                        fechaHora = cursor.getString(2) ?: "",
                        categoria = cursor.getString(3) ?: "",
                        producto = cursor.getString(4) ?: "",
                        cantidad = cursor.getInt(5),
                        precioUnitario = cursor.getDouble(6),
                        totalItem = cursor.getDouble(7),
                        tipoOrden = cursor.getString(8) ?: "Delivery",
                        idMesa = cursor.getString(9) ?: "",
                        cliente = cursor.getString(10) ?: "",
                        metodoPago = cursor.getString(11) ?: "Pendiente",
                        estadoCocina = cursor.getString(12) ?: "Pendiente",
                        estadoPago = cursor.getString(13) ?: "Pendiente",
                        montoEfectivo = cursor.getDouble(14),
                        montoYape = cursor.getDouble(15),
                        startTimeMillis = cursor.getLong(16)
                    )
                )
            }
        }
        return list
    }

    @Synchronized
    override fun insertPedidos(pedidos: List<PedidoItemEntity>) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            for (item in pedidos) {
                db.insertWithOnConflict("pedidos", null, item.toContentValues(), SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    override fun deletePedidosByOrderId(idPedido: String) {
        val db = helper.writableDatabase
        db.delete("pedidos", "id_pedido = ?", arrayOf(idPedido))
    }

    @Synchronized
    override fun clearAndReplaceAllPedidos(pedidos: List<PedidoItemEntity>) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            db.delete("pedidos", null, null)
            for (item in pedidos) {
                db.insertWithOnConflict("pedidos", null, item.toContentValues(), SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun ProductoEntity.toContentValues() = ContentValues().apply {
        put("id_key", idKey)
        put("id", id)
        put("nombre", nombre)
        put("categoria", categoria)
        put("tipo", tipo)
        put("precio", precio)
        put("disponible", if (disponible) 1 else 0)
        put("stock", stock)
    }

    private fun MesaEntity.toContentValues() = ContentValues().apply {
        put("id_mesa", idMesa)
        put("numero", numero)
        put("capacidad", capacidad)
        put("estado", estado)
    }

    private fun PedidoItemEntity.toContentValues() = ContentValues().apply {
        put("item_key", itemKey)
        put("id_pedido", idPedido)
        put("fecha_hora", fechaHora)
        put("categoria", categoria)
        put("producto", producto)
        put("cantidad", cantidad)
        put("precio_unitario", precioUnitario)
        put("total_item", totalItem)
        put("tipo_orden", tipoOrden)
        put("id_mesa", idMesa)
        put("cliente", cliente)
        put("metodo_pago", metodoPago)
        put("estado_cocina", estadoCocina)
        put("estado_pago", estadoPago)
        put("monto_efectivo", montoEfectivo)
        put("monto_yape", montoYape)
        put("start_time_millis", startTimeMillis)
    }
}
