package app.alpha.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Управление самим прибором: то, что он делает без телефона.
 *
 * Отдельный хаб, а не строка в настройках приложения: звук приложения и звук
 * прибора — разные вещи, и поведение железа при выключенном телефоне обязано
 * быть названо отдельно.
 *
 * ## Состав
 *
 * Только звук и вибрация (`VSFR SOUND_ON`, `VIBRO_ON` — та же пара, что в
 * референсной реализации `cdump/radiacode`). Пороги прибора, яркость,
 * ориентация экрана и время не трогаются: их регистры не проверены на живом
 * приборе, а неверная запись меняет поведение устройства необратимо.
 *
 * ## Состояние
 *
 * Регистры обратно не читаются: команда записи подтверждается прибором, но
 * опросить текущее состояние документированным путём нельзя. [applied] хранит
 * только отправленное в этом сеансе; до первой команды состояние неизвестно,
 * и UI обязан говорить «неизвестно».
 */
class DeviceControlHub {

    /** Что можно попросить у прибора. */
    sealed interface Command {
        data class Sound(val on: Boolean) : Command
        data class Vibro(val on: Boolean) : Command
    }

    /**
     * Отправленные в этом сеансе значения; null — мы прибору ничего не
     * говорили и его состояния не знаем.
     */
    data class Applied(val sound: Boolean? = null, val vibro: Boolean? = null)

    private val _applied = MutableStateFlow(Applied())
    val applied: StateFlow<Applied> = _applied.asStateFlow()

    /**
     * Чего попросил человек — независимо от того, дошло ли это до прибора.
     *
     * Просьба хранится и применяется заново на каждом подключении: у потока
     * команд нет буфера воспроизведения, и нажатие в момент переподключения
     * (или единственная неудачная запись) иначе исчезало бы навсегда.
     */
    private val _desired = MutableStateFlow(Applied())
    val desired: StateFlow<Applied> = _desired.asStateFlow()

    /** Прибор отказал в записи; UI обязан это сказать. */
    private val _failed = MutableStateFlow(Applied())
    val failed: StateFlow<Applied> = _failed.asStateFlow()

    private val _commands = MutableSharedFlow<Command>(extraBufferCapacity = 8)
    val commands: SharedFlow<Command> = _commands.asSharedFlow()

    fun request(command: Command) {
        _desired.value = _desired.value.with(command)
        _failed.value = _failed.value.clear(command)
        _commands.tryEmit(command)
    }

    /** Что осталось донести до прибора при (пере)подключении. */
    fun pending(): List<Command> {
        val desired = _desired.value
        val applied = _applied.value
        return listOfNotNull(
            desired.sound?.takeIf { it != applied.sound }?.let { Command.Sound(it) },
            desired.vibro?.takeIf { it != applied.vibro }?.let { Command.Vibro(it) },
        )
    }

    /** Вызывается сервисом ПОСЛЕ того, как прибор подтвердил запись. */
    internal fun onApplied(command: Command) {
        _applied.value = _applied.value.with(command)
        _failed.value = _failed.value.clear(command)
    }

    /** Прибор не принял запись: пусть это будет видно, а не пропадёт. */
    internal fun onFailed(command: Command) {
        _failed.value = when (command) {
            is Command.Sound -> _failed.value.copy(sound = true)
            is Command.Vibro -> _failed.value.copy(vibro = true)
        }
    }

    /**
     * Связь потеряна: текущее состояние прибора снова неизвестно. Просьба
     * человека остаётся и применяется следующим подключением.
     */
    internal fun onDisconnected() {
        _applied.value = Applied()
    }

    private fun Applied.with(command: Command): Applied = when (command) {
        is Command.Sound -> copy(sound = command.on)
        is Command.Vibro -> copy(vibro = command.on)
    }

    private fun Applied.clear(command: Command): Applied = when (command) {
        is Command.Sound -> copy(sound = null)
        is Command.Vibro -> copy(vibro = null)
    }
}
