package com.farakhvan.text

import android.app.Application
import com.farakhvan.text.data.Repo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class FarakhvanApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // A fresh process means any campaign marked running was killed mid-way.
        try {
            runBlocking(Dispatchers.IO) { Repo.recoverInterrupted(this@FarakhvanApp) }
        } catch (e: Exception) {
            // Database problems must never prevent the app from opening.
        }
    }
}
