import android.database.sqlite.SQLiteDatabase

class AndroidTesteKt {
    fun consultas(db: SQLiteDatabase, usuario: String, id: Int) {
        // ruleid: sentinel-android-sqlite-concatenado
        db.rawQuery("select * from user where username = '" + usuario + "'", null)
        // ruleid: sentinel-android-sqlite-concatenado
        db.execSQL("delete from note where id = " + id)
        // ok: sentinel-android-sqlite-concatenado
        db.rawQuery("select * from user where username = ?", arrayOf(usuario))
        // ok: sentinel-android-sqlite-concatenado
        db.rawQuery("select * from " + TABELA + " where id = 1", null)
    }

    companion object {
        const val TABELA = "user"
    }
}
