// data/local/AppDatabase.kt
package roy.ij.obscure.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(entities = [ChatEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {

                // 1. Get the passphrase from the SecurityManager
                val securityManager = SecurityManager(context)
                val passphrase = securityManager.getDatabasePassphrase()

                // 2. Load SQLCipher and use the passphrase to create the factory
                System.loadLibrary("sqlcipher")
                val factory = SupportOpenHelperFactory(passphrase)

                // 3. Build the database
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "baatcheet_encrypted.db"
                ).openHelperFactory(factory).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
