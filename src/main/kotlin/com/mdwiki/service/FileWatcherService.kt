package com.mdwiki.service

import com.mdwiki.config.WikiProperties
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.*
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

@Service
class FileWatcherService(
    @Lazy private val syncService: SyncService,
    private val wikiProperties: WikiProperties
) {

    private val log = LoggerFactory.getLogger(FileWatcherService::class.java)
    private val running = AtomicBoolean(false)
    private var watchThread: Thread? = null
    private val ignoredPaths = ConcurrentHashMap<String, Long>()

    fun ignoreNextChange(filePath: String) {
        // Move/write operations may emit several FS events in a short burst.
        ignoredPaths[filePath] = Instant.now().toEpochMilli() + 5000
    }

    @PostConstruct
    fun start() {
        val dir = File(wikiProperties.contentDir)
        dir.mkdirs()

        running.set(true)
        watchThread = thread(isDaemon = true, name = "file-watcher") {
            try {
                val watchService = FileSystems.getDefault().newWatchService()
                val watchKeys = mutableMapOf<WatchKey, Path>()
                registerAllDirectories(dir.toPath(), watchService, watchKeys)
                log.info("File watcher started on: ${dir.absolutePath}")

                while (running.get()) {
                    try {
                        val key = watchService.poll(1, java.util.concurrent.TimeUnit.SECONDS) ?: continue
                        val watchedDir = watchKeys[key] ?: continue
                        for (event in key.pollEvents()) {
                            val path = event.context() as? Path ?: continue
                            val fullPath = watchedDir.resolve(path).toFile()
                            val fullPathString = fullPath.absolutePath

                            when (event.kind()) {
                                StandardWatchEventKinds.ENTRY_CREATE -> {
                                    if (fullPath.isDirectory) {
                                        registerAllDirectories(fullPath.toPath(), watchService, watchKeys)
                                        continue
                                    }
                                    if (!fullPath.name.endsWith(".md")) continue
                                    if (shouldIgnore(fullPathString)) continue
                                    if (fullPath.exists()) syncService.syncSingleFile(fullPath)
                                }
                                StandardWatchEventKinds.ENTRY_MODIFY -> {
                                    if (!fullPath.name.endsWith(".md")) continue
                                    if (shouldIgnore(fullPathString)) continue
                                    if (fullPath.exists()) syncService.syncSingleFile(fullPath)
                                }
                                StandardWatchEventKinds.ENTRY_DELETE -> {
                                    if (shouldIgnore(fullPathString)) continue
                                    val fileName = fullPath.name
                                    if (fileName.endsWith(".md")) {
                                        val slug = fileName.removeSuffix(".md")
                                        syncService.removePage(slug)
                                    } else {
                                        // Directory (or other non-md) removed from disk — reconcile DB + folders.
                                        syncService.scheduleReconcileFromDisk()
                                    }
                                }
                            }
                        }
                        if (!key.reset()) {
                            watchKeys.remove(key)
                        }
                        cleanupExpiredIgnores()
                    } catch (e: Exception) {
                        // Ошибка обработки одного события не должна останавливать watcher целиком.
                        log.error("File watcher event processing error", e)
                    }
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

    private fun registerAllDirectories(
        root: Path,
        watchService: WatchService,
        watchKeys: MutableMap<WatchKey, Path>
    ) {
        if (!Files.exists(root)) return
        Files.walk(root).use { paths ->
            paths
                .filter { Files.isDirectory(it) }
                .forEach { dir ->
                    val key = dir.register(
                        watchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_DELETE
                    )
                    watchKeys[key] = dir
                }
        }
    }

    /** Removes expired entries from the ignore map to prevent unbounded growth. */
    private fun cleanupExpiredIgnores() {
        val now = Instant.now().toEpochMilli()
        ignoredPaths.entries.removeIf { it.value < now }
    }

    private fun shouldIgnore(path: String): Boolean {
        val now = Instant.now().toEpochMilli()
        val expiresAt = ignoredPaths[path] ?: return false
        return if (now <= expiresAt) {
            true
        } else {
            ignoredPaths.remove(path, expiresAt)
            false
        }
    }
}
