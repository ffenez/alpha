package app.alpha.smoke

import android.net.Uri
import app.alpha.data.BackupJob
import app.alpha.data.export.backup.BackupReader
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Копия, записанная в НАСТОЯЩИЙ файл, читается как копия.
 *
 * Обычные проверки формата пишут в память: они ловят ошибки формата, но не
 * ловят обрыв на границе потоков — не дописанный конец архива, не сброшенный
 * буфер, закрытый раньше времени файл. Именно так копия и превращается в
 * «это не резервная копия приложения»: архив без конца ZIP читается как пустой,
 * а пустой архив — это файл без манифеста.
 */
@RunWith(RobolectricTestRunner::class)
class BackupFileRoundTripTest {

    @Test
    fun `копия из файла читается как копия`() = runBlocking {
        val graph = Smoke.graph()
        Smoke.seed(graph)
        val target = File.createTempFile("backup", ".radbackup")
        target.deleteOnExit()

        graph.backupManager.save(Uri.fromFile(target))
        val saved = withTimeout(60_000) {
            graph.backupManager.state.first { it is BackupJob.Saved || it is BackupJob.Failed }
        }
        assertTrue(saved is BackupJob.Saved, "копия не записалась: $saved")
        assertTrue(target.length() > 0, "файл копии пуст")

        val info = BackupReader.inspect { target.inputStream() }.getOrThrow()
        assertEquals("alpha-backup", info.manifest.format)
        assertTrue(info.counts.measurements > 0, "измерения не попали в копию")
    }
}
