package app.radiacode.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Управление самим прибором: то, что он делает БЕЗ телефона.
 *
 * Отдельный хаб, а не строчка в настройках приложения, ровно по одной
 * причине: звук приложения и звук прибора — разные вещи, и человек должен
 * понимать, будет ли прибор пищать, когда телефон в кармане или выключен.
 * Смешать их в один тумблер значило бы соврать про поведение железа.
 *
 * ## Чего здесь нет и почему
 *
 * Только два переключателя — звук и вибрация (`VSFR SOUND_ON`, `VIBRO_ON`,
 * та же пара, что в референсной реализации `cdump/radiacode`). Пороги
 * прибора, яркость, ориентация экрана и время не трогаются: их регистры мы не
 * проверяли на живом приборе, а неверная запись меняет поведение чужого
 * устройства необратимо для владельца.
 *
 * ## Честность состояния
 *
 * Приложение НЕ читает эти регистры обратно: команда записи подтверждается
 * прибором, но опросить текущее состояние тем же документированным путём мы
 * не умеем. Поэтому [applied] хранит только то, что мы САМИ отправили в этом
 * сеансе, а до первой команды состояние неизвестно — и UI обязан говорить
 * «неизвестно», а не показывать выключенный тумблер как факт.
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
     * Чего ЧЕЛОВЕК попросил — независимо от того, дошло ли это до прибора.
     *
     * Полевой дефект: тумблер не срабатывал. Команда уходила в `SharedFlow` без
     * буфера воспроизведения, а слушателя у него нет, пока прибор не подключён
     * и не поднялись его задачи, — то есть нажатие в момент переподключения
     * (или единственная неудачная запись) исчезало навсегда, и повторить его
     * человеку было нечем: тумблер уже стоял в нужном положении.
     *
     * Теперь просьба ХРАНИТСЯ и применяется заново на каждом подключении. Она
     * переживает потерю связи: связь оборвалась не по воле человека, и его
     * решение о приборе от этого не отменяется.
     */
    private val _desired = MutableStateFlow(Applied())
    val desired: StateFlow<Applied> = _desired.asStateFlow()

    /** Прибор отказал в записи; UI обязан сказать это, а не молчать. */
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
     * Связь потеряна: что стоит в приборе сейчас, мы снова не знаем.
     * Просьба человека при этом остаётся — её применит следующее подключение.
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
