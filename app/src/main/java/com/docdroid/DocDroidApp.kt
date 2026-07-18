package com.docdroid

import android.app.Application
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.docdroid.agent.ToolRegistry
import com.docdroid.data.FileStore

class DocDroidApp : Application() {

    lateinit var fileStore: FileStore
        private set

    lateinit var toolRegistry: ToolRegistry
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        fileStore = FileStore(this)
        toolRegistry = ToolRegistry()

        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
    }

    companion object {
        lateinit var instance: DocDroidApp
            private set
    }
}
