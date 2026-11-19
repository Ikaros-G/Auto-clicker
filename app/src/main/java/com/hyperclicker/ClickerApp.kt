package com.hyperclicker

import android.app.Application
import com.hyperclicker.data.AppDatabase
import com.hyperclicker.data.SchemeRepository

class ClickerApp : Application() {
    val database by lazy { AppDatabase.get(this) }
    val repository by lazy { SchemeRepository(database.schemeDao()) }
}
