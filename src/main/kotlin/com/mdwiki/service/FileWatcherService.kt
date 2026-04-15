package com.mdwiki.service

import com.mdwiki.config.WikiProperties
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

@Service
class FileWatcherService(
    private val syncService: SyncService,
    private val wikiProperties: WikiProperties
) {

    private val log = LoggerFactory.getLogger(FileWatcherService::class.java)
    private val running = AtomicBoolean(false)
    private var watchThread: Thread? = null
    private val ignoredPaths = ConcurrentHashMap.newKeySet<String>()

    fun ignoreNextChange(filePath: String) {
        ignoredPaths.add(filePath)
    }

    @PostConstruct
    fun start() {
        val dir = File(wikiProperties.contentDir)
        dir.mkdirs()

        running.set(true)
        watchThread = thread(isDaemon = true, name = "file-watcher") {
            try {
                val watchService = FileSystems.getDefault().newWatchService()
                dir.toPath().register(
                    watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE
                )
                log.info("File watcher started on: ${dir.absolutePath}")

                while (running.get()) {
                    val key = watchService.poll(1, java.util.concurrent.TimeUnit.SECONDS) ?: continue
                    for (event in key.pollEvents()) {
                        val path = event.context() as? Path ?: continue
                        val fileName = path.toString()
                        if (!fileName.endsWith(".md")) continue

                        val fullPath = dir.resolve(fileName).absolutePath
                        if (ignoredPaths.remove(fullPath)) continue

                        when (event.kind()) {
                            StandardWatchEventKinds.ENTRY_CREATE,
                            StandardWatchEventKinds.ENTRY_MODIFY -> {
                                val file = File(dir, fileName)
                                if (file.exists()) syncService.syncSingleFile(file)
                            }
                            StandardWatchEventKinds.ENTRY_DELETE -> {
                                val slug = fileName.removeSuffix(".md")
                                syncService.removePage(slug)
                            }
                        }
                    }
                    key.reset()
                }
            } catch (e: Exception) {
                log.error("File watcher error", e)
            }
        }
    }

    @PreDestroy
    fun stop() {
        running.set(false)
        watchThread?.join(5000)
    }
}
