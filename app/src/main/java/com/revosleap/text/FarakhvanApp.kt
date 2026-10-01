package com.revosleap.text

import android.app.Application
import androidx.room.Room
import com.revosleap.text.data.AppDatabase
import com.revosleap.text.data.Preferences
import com.revosleap.text.data.Repository

class FarakhvanApp : Application() {
    val database by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "farakhvan.db").build()
    }
    val repository by lazy { Repository(database, this) }
    val preferences by lazy { Preferences(this) }
}
